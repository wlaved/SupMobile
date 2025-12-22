package com.embiimob.supmobile;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTransaction;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

public class SupJSInterface {
    private Context mContext;
    private WebView mWebView;
    private NetworkParameters params;
    private Wallet wallet;
    private PeerGroup peerGroup;
    private BlockStore blockStore;
    private BlockChain blockChain;
    private File walletFile;
    private File chainFile;
    private boolean isMainnet = false;
    private String customStoragePath = null;
    private boolean isRunning = false;

    public SupJSInterface(Context context, WebView webView) {
        mContext = context;
        mWebView = webView;
        // Default to Testnet
        params = TestNet3Params.get();
    }

    // --- Bridge Methods ---

    @JavascriptInterface
    public void startNode() {
        if (isRunning) {
            showToast("Node is already running.");
            return;
        }
        new Thread(() -> {
            try {
                showToast("Starting Bitcoin Node...");
                setupBitcoinJ();
                isRunning = true;
                showToast("Node Started! Peers: 0");
            } catch (Exception e) {
                e.printStackTrace();
                showToast("Error starting node: " + e.getMessage());
            }
        }).start();
    }

    @JavascriptInterface
    public void setNetwork(boolean useMainnet) {
        if (isRunning) {
            showToast("Stop the node before switching networks.");
            return;
        }
        this.isMainnet = useMainnet;
        this.params = useMainnet ? MainNetParams.get() : TestNet3Params.get();
        showToast("Switched to " + (useMainnet ? "Mainnet" : "Testnet"));
    }

    @JavascriptInterface
    public void stopNode() {
        if (!isRunning) return;
        new Thread(() -> {
            try {
                if (peerGroup != null) {
                    peerGroup.stop();
                    peerGroup = null;
                }
                if (blockStore != null) {
                    blockStore.close();
                    blockStore = null;
                }
                isRunning = false;
                showToast("Node Stopped.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @JavascriptInterface
    public String getNodeStatus() {
        int peers = (peerGroup != null) ? peerGroup.numConnectedPeers() : 0;
        int height = (blockChain != null) ? blockChain.getBestChainHeight() : 0;
        String path = (chainFile != null) ? chainFile.getAbsolutePath() : "Not initialized";
        return String.format("{\"running\": %b, \"peers\": %d, \"height\": %d, \"path\": \"%s\"}",
                isRunning, peers, height, path);
    }

    @JavascriptInterface
    public void selectStorage() {
        // Trigger generic Storage Access Framework intent in MainActivity
        // For now, we simulate this or rely on manual path setting in the simple version
        showToast("Use 'Set Path' to define storage manually in this version.");
    }

    @JavascriptInterface
    public void setManualStoragePath(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            showToast("Path does not exist: " + path);
            return;
        }
        customStoragePath = path;
        showToast("Storage path set to: " + path);
    }

    @JavascriptInterface
    public void requestPermissions() {
        if (mContext instanceof MainActivity) {
            ((MainActivity) mContext).requestAppPermissions();
        } else {
            showToast("Cannot request permissions: Invalid Context");
        }
    }

    @JavascriptInterface
    public void pinIpfs(String hash) {
        new Thread(() -> {
            try {
                // Remove prefix if present
                String cleanHash = hash.replace("ipfs://", "").replace("IPFS:", "");
                URL url = new URL("http://127.0.0.1:5001/api/v0/pin/add?arg=" + cleanHash);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                int code = conn.getResponseCode();
                if (code == 200) {
                    showToast("Pinned to IPFS: " + cleanHash);
                } else {
                    showToast("IPFS Pin Failed: " + code);
                }
            } catch (Exception e) {
                showToast("IPFS Error: " + e.getMessage());
            }
        }).start();
    }

    @JavascriptInterface
    public void watchProfile(String urnOrAddress) {
        if (wallet == null) {
            showToast("Wallet not initialized. Start Node first.");
            return;
        }
        try {
            Address address = Address.fromString(params, urnOrAddress);
            if (wallet.isWatchedScript(ScriptBuilder.createOutputScript(address))) {
                showToast("Already watching " + urnOrAddress);
                return;
            }
            wallet.addWatchedAddress(address);
            wallet.saveToFile(walletFile);
            showToast("Added to watch list: " + urnOrAddress);
        } catch (Exception e) {
            showToast("Invalid Address: " + e.getMessage());
        }
    }

    @JavascriptInterface
    public void mint(String data) {
         showToast("Minting not implemented in this demo (requires funds). Data: " + data);
    }

    @JavascriptInterface
    public void startLocalScan() {
        new Thread(() -> {
            try {
                showToast("Starting Local Blockchain Scan...");

                // Determine 'blocks' directory based on custom path
                File rootDir;
                if (customStoragePath != null) {
                    File customRoot = new File(customStoragePath); // e.g. .../SUP
                    if (isMainnet) {
                        rootDir = new File(customRoot, "bitcoin");
                    } else {
                        rootDir = new File(new File(customRoot, "bitcoin"), "testnet3");
                    }
                } else {
                    rootDir = mContext.getExternalFilesDir(null);
                }

                File blocksDir = new File(rootDir, "blocks");
                // Check if standard 'blocks' folder exists, otherwise try the root testnet3 folder
                File scanTarget = blocksDir.exists() ? blocksDir : rootDir;

                // Verify blk files exist
                boolean hasBlocks = false;
                if (scanTarget.exists() && scanTarget.isDirectory()) {
                    File[] check = scanTarget.listFiles((d, name) -> name.startsWith("blk") && name.endsWith(".dat"));
                    hasBlocks = check != null && check.length > 0;
                }

                if (!hasBlocks) {
                    showToast("No blk*.dat files found in: " + scanTarget.getAbsolutePath());
                    return;
                }

                BlockchainScanner scanner = new BlockchainScanner(params, scanTarget.getAbsolutePath());
                scanner.scanForOpReturn(new BlockchainScanner.OpReturnListener() {
                    @Override
                    public void onOpReturnFound(String txId, byte[] data) {
                        String hexData = bytesToHex(data);
                        String asciiData = new String(data); // Try ASCII
                        if (asciiData.startsWith("IPFS")) {
                             // Bridge it!
                             pinIpfs(asciiData.substring(5)); // Remove IPFS: prefix
                             notifyFrontend("live_feed", "Scanner Found IPFS: " + asciiData);
                        } else {
                             // Just log/toast for now
                             // notifyFrontend("live_feed", "Scanner Found OP_RETURN: " + txId);
                        }
                    }

                    @Override
                    public void onProgress(int blocksScanned) {
                        notifyFrontend("live_feed", "Scanning... Processed " + blocksScanned + " blocks");
                    }

                    @Override
                    public void onScanComplete() {
                        showToast("Blockchain Scan Complete!");
                        notifyFrontend("live_feed", "Scan Job Finished.");
                    }

                    @Override
                    public void onScanError(String error) {
                        showToast("Scan Error: " + error);
                        notifyFrontend("live_feed", "Scan Error: " + error);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                showToast("Scanner Failed: " + e.getMessage());
            }
        }).start();
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    // --- Internal Logic ---

    private void setupBitcoinJ() throws Exception {
        // Determine path
        File directory;
        if (customStoragePath != null) {
            File root = new File(customStoragePath);
            // Smart Structure: SUP/bitcoin/testnet3
            if (isMainnet) {
                directory = new File(root, "bitcoin");
            } else {
                directory = new File(new File(root, "bitcoin"), "testnet3");
            }
            if (!directory.exists()) {
                directory.mkdirs();
            }
        } else {
            directory = mContext.getExternalFilesDir(null);
        }

        String filePrefix = isMainnet ? "sup-mainnet" : "sup-testnet";
        walletFile = new File(directory, filePrefix + ".wallet");
        chainFile = new File(directory, filePrefix + ".spvchain");

        // Wallet
        if (walletFile.exists()) {
            wallet = Wallet.loadFromFile(walletFile);
        } else {
            org.bitcoinj.core.Context ctx = org.bitcoinj.core.Context.getOrCreate(params);
            wallet = Wallet.createDeterministic(ctx, Script.ScriptType.P2PKH);
            wallet.saveToFile(walletFile);
        }

        // BlockStore
        blockStore = new SPVBlockStore(params, chainFile);

        // Chain
        blockChain = new BlockChain(params, wallet, blockStore);

        // PeerGroup
        peerGroup = new PeerGroup(params, blockChain);
        peerGroup.addWallet(wallet);
        // Explicitly add DNS Discovery for Android/Termux environments where default might fail
        peerGroup.addPeerDiscovery(new DnsDiscovery(params));

        // Listener for incoming TX
        wallet.addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet w, Transaction tx, Coin prevBalance, Coin newBalance) {
                String txId = tx.getTxId().toString();

                // Scan for OP_RETURN to populate Social Feed
                for (TransactionOutput out : tx.getOutputs()) {
                    Script script = out.getScriptPubKey();
                    if (script.isOpReturn()) {
                        try {
                            byte[] dataBytes = null;
                            if (script.getChunks().size() > 1) {
                                dataBytes = script.getChunks().get(1).data;
                            }

                            if (dataBytes != null) {
                                String data = new String(dataBytes);
                                String sender = "Mempool";

                                // Push structured data for Social Feed
                                String json = String.format("{\"type\":\"message\", \"sender\":\"%s\", \"content\":\"%s\", \"txid\":\"%s\"}",
                                    sender, data.replace("\"", "\\\"").replace("\n", " "), txId);

                                notifyFrontend("new_post", json);

                                if (data.startsWith("IPFS:")) {
                                    String hash = data.substring(5);
                                    pinIpfs(hash);
                                    notifyFrontend("system_log", "Auto-Pinning IPFS: " + hash);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });

        peerGroup.setConnectTimeoutMillis(5000);
        peerGroup.start();
        peerGroup.startBlockChainDownload(null);
    }

    private void showToast(String msg) {
        mWebView.post(() -> Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show());
    }

    private void notifyFrontend(String eventType, String data) {
        mWebView.post(() -> {
            // Escape quotes for JS safety
            String safeData = data.replace("'", "\\'").replace("\"", "\\\"");
            String js = "if(window.onSupEvent) window.onSupEvent('" + eventType + "', '" + safeData + "');";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                mWebView.evaluateJavascript(js, null);
            } else {
                mWebView.loadUrl("javascript:" + js);
            }
        });
    }
}
