package com.embiimob.supmobile;

import org.bitcoinj.core.*;
import org.bitcoinj.utils.BlockFileLoader;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptChunk;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * BlockchainScanner
 * Uses bitcoinj's BlockFileLoader to iterate through local blk.dat files.
 * Optimized for Termux/Mobile environments to extract OP_RETURN data.
 */
public class BlockchainScanner {
    private final NetworkParameters params;
    private final List<File> blockFiles;

    public BlockchainScanner(NetworkParameters params, String blocksPath) {
        this.params = params;
        this.blockFiles = new ArrayList<>();

        File dir = new File(blocksPath);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.startsWith("blk") && name.endsWith(".dat"));
            if (files != null) {
                for (File f : files) blockFiles.add(f);
            }
        }
    }

    /**
     * Scans all found block files and extracts OP_RETURN data.
     * This is a blocking operation and should be run in a background thread.
     */
    public void scanForOpReturn(OpReturnListener listener) {
        if (blockFiles.isEmpty()) {
            listener.onScanError("No blk.dat files found in specified path.");
            return;
        }

        try {
            // Ensure context is initialized for this thread
            Context.getOrCreate(params);

            BlockFileLoader loader = new BlockFileLoader(params, blockFiles);

            int blocksScanned = 0;
            // Crude progress reporting since we don't know total blocks easily without pre-scan
            // But we can report periodic updates
            for (Block block : loader) {
                blocksScanned++;
                if (blocksScanned % 100 == 0) {
                     listener.onProgress(blocksScanned);
                }

                for (Transaction tx : block.getTransactions()) {
                    for (TransactionOutput output : tx.getOutputs()) {
                        try {
                            Script script = output.getScriptPubKey();
                            if (script.isOpReturn()) {
                                byte[] data = extractOpReturnData(script);
                                if (data != null && data.length > 0) {
                                    listener.onOpReturnFound(tx.getTxId().toString(), data);
                                }
                            }
                        } catch (Exception e) {
                            // Skip scripts that fail to parse
                        }
                    }
                }
            }
            listener.onScanComplete();
        } catch (Exception e) {
            listener.onScanError("Scanning failed: " + e.getMessage());
        }
    }

    private byte[] extractOpReturnData(Script script) {
        List<ScriptChunk> chunks = script.getChunks();
        // OP_RETURN is the first chunk, data is the second
        if (chunks.size() > 1) {
            return chunks.get(1).data;
        }
        return null;
    }

    public interface OpReturnListener {
        void onOpReturnFound(String txId, byte[] data);
        void onProgress(int blocksScanned);
        void onScanComplete();
        void onScanError(String error);
    }
}
