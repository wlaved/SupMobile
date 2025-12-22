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
        scanForOpReturn(listener, null);
    }

    public void scanForOpReturn(OpReturnListener listener, String skipUntilFile) {
        if (blockFiles.isEmpty()) {
            listener.onScanError("No blk.dat files found in specified path.");
            return;
        }

        try {
            Context.getOrCreate(params);

            // Filter files if skip requested
            List<File> filesToScan = new ArrayList<>();
            boolean skipping = (skipUntilFile != null && !skipUntilFile.isEmpty());

            for (File f : blockFiles) {
                if (skipping) {
                    if (f.getName().equals(skipUntilFile)) {
                        skipping = false; // Start scanning AFTER this file? Or from?
                        // Let's scan from the NEXT file to be safe/simple, assuming completed.
                    }
                    continue;
                }
                filesToScan.add(f);
            }

            if (filesToScan.isEmpty()) {
                listener.onScanComplete();
                return;
            }

            int totalBlocks = 0;

            // Iterate file by file to allow precise resume saving
            for (File file : filesToScan) {
                List<File> singleFile = new ArrayList<>();
                singleFile.add(file);
                BlockFileLoader loader = new BlockFileLoader(params, singleFile);

                for (Block block : loader) {
                    totalBlocks++;
                    if (totalBlocks % 1000 == 0) {
                         listener.onProgress(totalBlocks);
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
                // Notify file complete (we reuse onProgress or add new method, but explicit save in Service is better)
                // We'll treat onProgress as generic heartbeat.
                // To allow Service to save state, we need to expose current file or callback.
                // Let's cast listener to something else or just add method?
                if (listener instanceof FileScanListener) {
                    ((FileScanListener) listener).onFileComplete(file.getName());
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

    public interface FileScanListener extends OpReturnListener {
        void onFileComplete(String fileName);
    }
}
