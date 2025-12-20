package com.embiimob.supmobile;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

// Import BitcoinJ classes
import org.bitcoinj.core.*;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.net.discovery.DnsDiscovery;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE_OPEN_DIR = 1001;
    private static final String PREFS_NAME = "SupMobilePrefs";

    private WebView webView;
    private Wallet wallet;
    private NetworkParameters params;
    private Uri storageUri = null;

    // Bitcoin Node Components
    private BlockStore blockStore;
    private BlockChain blockChain;
    private PeerGroup peerGroup;
    private File customStorageDir = null;

    // Config
    private boolean isTestnet = true; // Default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check for All Files Access (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Please allow 'All Files Access' for Hybrid Storage", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        String manualPath = settings.getString("manual_storage_path", null);
        String savedUri = settings.getString("storage_uri", null);
        isTestnet = settings.getBoolean("is_testnet", true);

        // Priority: Manual Path > SAF URI
        if (manualPath != null) {
            File manualDir = new File(manualPath);
            // We don't strictly check exists() here because the USB drive might not be mounted yet
            customStorageDir = manualDir;
        }

        if (customStorageDir == null && savedUri != null) {
            storageUri = Uri.parse(savedUri);
            resolveStoragePath(storageUri);
        }

        webView = new WebView(this);
        setContentView(webView);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        // Important for accessing local assets/content via JS
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);

        webView.addJavascriptInterface(new SupJSInterface(), "SupApp");
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/dashboard.html");

        // Auto-start if we have a valid configuration
        if (customStorageDir != null || storageUri != null) {
            startBitcoinNode();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBitcoinNode();
    }

    // Helper to resolve physical path from SAF URI (Best Effort)
    private void resolveStoragePath(Uri uri) {
        try {
            String path = uri.getPath();
            // Expected format: /tree/primary:Sup or /tree/5D51-1410:Sup
            if (path != null && path.contains(":")) {
                String[] parts = path.split(":");
                if (parts.length > 1) {
                    String volumeId = parts[0].substring(parts[0].lastIndexOf("/") + 1);
                    String folderPath = parts[1];

                    if ("primary".equalsIgnoreCase(volumeId)) {
                        customStorageDir = new File(Environment.getExternalStorageDirectory(), folderPath);
                    } else {
                        // Handle External Volume (e.g., 5D51-1410)
                        File storageRoot = new File("/storage/" + volumeId);
                        if (storageRoot.exists()) {
                            customStorageDir = new File(storageRoot, folderPath);
                        } else {
                            // Fallback attempts for /mnt/media_rw
                             customStorageDir = new File("/mnt/media_rw/" + volumeId, folderPath);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopBitcoinNode() {
        new Thread(() -> {
            if (peerGroup != null && peerGroup.isRunning()) {
                peerGroup.stop();
                peerGroup = null;
                try {
                    if (blockStore != null) blockStore.close();
                } catch (Exception e) {}
                runOnUiThread(() -> Toast.makeText(this, "Node Stopped.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void startBitcoinNode() {
        if (peerGroup != null && peerGroup.isRunning()) {
            return; // Already running
        }

        new Thread(() -> {
            try {
                // Select Network
                params = isTestnet ? TestNet3Params.get() : MainNetParams.get();

                // Smart Path Logic
                File chainFile = null;

                // 1. Try Custom Storage (USB/SD)
                if (customStorageDir != null) {
                    if (!customStorageDir.exists()) {
                         // Attempt to create if we have permission
                         customStorageDir.mkdirs();
                    }

                    if (customStorageDir.exists()) {
                        File bitcoinDir = new File(customStorageDir, "bitcoin");
                        if (bitcoinDir.exists()) {
                             File netDir = isTestnet ? new File(bitcoinDir, "testnet3") : bitcoinDir;
                             // Look for standard core headers first
                             if (new File(netDir, "headers.mweb").exists()) {
                                 // Note: bitcoinj can't read Core's headers directly easily,
                                 // but we place our spvchain here to share the folder structure.
                                 chainFile = new File(netDir, "sup_mobile.spvchain");
                             }
                        }
                        if (chainFile == null) {
                            chainFile = new File(customStorageDir, "sup_mobile.spvchain");
                        }
                    }
                }

                // 2. Fallback to Internal App Storage
                if (chainFile == null) {
                    chainFile = new File(getExternalFilesDir(null), "sup_mobile.spvchain");
                }

                final String finalPath = chainFile.getAbsolutePath();

                // Wallet setup
                File walletFile = new File(chainFile.getParent(), isTestnet ? "sup_testnet.wallet" : "sup.wallet");
                if (walletFile.exists()) {
                    wallet = Wallet.loadFromFile(walletFile, params);
                } else {
                    wallet = new Wallet(params);
                    wallet.saveToFile(walletFile);
                }

                wallet.addCoinsReceivedEventListener((w, tx, prevBalance, newBalance) -> checkForSupContent(tx));

                blockStore = new SPVBlockStore(params, chainFile);
                blockChain = new BlockChain(params, wallet, blockStore);

                peerGroup = new PeerGroup(params, blockChain);
                peerGroup.addWallet(wallet);
                peerGroup.addPeerDiscovery(new DnsDiscovery(params));

                peerGroup.startAsync();
                peerGroup.startBlockChainDownload(null);

                runOnUiThread(() -> Toast.makeText(this, "Node Started (" + (isTestnet?"Testnet":"Mainnet") + ") @ " + finalPath, Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void checkForSupContent(Transaction tx) {
        try {
            for (TransactionOutput out : tx.getOutputs()) {
                Script script = out.getScriptPubKey();
                if (script.isOpReturn()) {
                    // Safety check for chunks
                    if (script.getChunks().size() > 1 && script.getChunks().get(1).data != null) {
                        String data = new String(script.getChunks().get(1).data);
                        if (data.contains("IPFS:")) {
                            String ipfsHash = data.substring(data.indexOf("IPFS:") + 5).trim();
                            new SupJSInterface().pinIpfs(ipfsHash);
                            runOnUiThread(() -> Toast.makeText(this, "Auto-Pinning: " + ipfsHash, Toast.LENGTH_SHORT).show());
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parse errors
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == REQUEST_CODE_OPEN_DIR && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                storageUri = resultData.getData();
                getContentResolver().takePersistableUriPermission(storageUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, 0).edit();
                editor.putString("storage_uri", storageUri.toString());
                editor.remove("manual_storage_path"); // Clear manual override
                editor.commit();

                resolveStoragePath(storageUri);

                Toast.makeText(this, "Storage Selected. Restarting Node...", Toast.LENGTH_SHORT).show();
                stopBitcoinNode();
                startBitcoinNode();
                webView.reload();
            }
        }
    }

    public class SupJSInterface {
        @JavascriptInterface
        public void showToast(String toast) {
            Toast.makeText(MainActivity.this, toast, Toast.LENGTH_SHORT).show();
        }

        @JavascriptInterface
        public void selectStorage() {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_CODE_OPEN_DIR);
        }

        @JavascriptInterface
        public void setManualStoragePath(String path) {
            File dir = new File(path);

            // On Android 11+ with MANAGE_EXTERNAL_STORAGE, we can just check if we can write
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                 Toast.makeText(MainActivity.this, "Requires 'All Files Access' permission", Toast.LENGTH_LONG).show();
                 return;
            }

            customStorageDir = dir;

            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, 0).edit();
            editor.putString("manual_storage_path", path);
            editor.remove("storage_uri");
            editor.commit();

            Toast.makeText(MainActivity.this, "Manual Path Set. Restarting Node...", Toast.LENGTH_SHORT).show();
            stopBitcoinNode();
            startBitcoinNode();
        }

        @JavascriptInterface
        public void startNode() {
            startBitcoinNode();
        }

        @JavascriptInterface
        public void stopNode() {
            stopBitcoinNode();
        }

        @JavascriptInterface
        public void toggleNetwork() {
            stopBitcoinNode();
            isTestnet = !isTestnet;
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, 0).edit();
            editor.putBoolean("is_testnet", isTestnet);
            editor.commit();
            startBitcoinNode();
        }

        @JavascriptInterface
        public boolean isTestnet() {
            return isTestnet;
        }

        @JavascriptInterface
        public String getStoragePath() {
            if (customStorageDir != null) return customStorageDir.getAbsolutePath();
            if (storageUri != null) return storageUri.toString();
            return "Internal Storage";
        }

        @JavascriptInterface
        public String getNodeStatus() {
            if (peerGroup == null) return "Stopped";
            if (peerGroup.isRunning()) {
                return (isTestnet?"[TEST]":"[MAIN]") + " Peers: " + peerGroup.numConnectedPeers() + ". Height: " + blockChain.getBestChainHeight();
            }
            return "Starting...";
        }

        @JavascriptInterface
        public void watchProfile(String address) {
            try {
                Address addr = Address.fromString(params, address);
                wallet.addWatchedAddress(addr);
                wallet.saveToFile(new File(wallet.getFile().getAbsolutePath()));
                Toast.makeText(MainActivity.this, "Watching: " + address, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Invalid Address: " + address, Toast.LENGTH_SHORT).show();
            }
        }

        @JavascriptInterface
        public String pinIpfs(String hash) {
            new Thread(() -> {
                try {
                    // Assumes IPFS is running in Termux at port 5001
                    URL url = new URL("http://127.0.0.1:5001/api/v0/pin/add?arg=" + hash);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.getResponseCode();
                    conn.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            return "Pin request sent for " + hash;
        }

        @JavascriptInterface
        public String getWalletAddress() {
            return wallet != null ? wallet.currentReceiveAddress().toString() : "No Wallet Loaded";
        }

        @JavascriptInterface
        public String mint(String data) {
            try {
                String payload = "SUP01" + data;
                return "Transaction Constructed: " + payload;
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public String listFiles(String subPath) {
            if (customStorageDir != null && customStorageDir.exists()) {
                return listFilesNative(customStorageDir);
            }
            return listFilesNative(Environment.getExternalStorageDirectory());
        }

        private String listFilesNative(File dir) {
            File[] files = dir.listFiles();
            if (files == null) return "[]";
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < files.length; i++) {
                json.append("\"").append(files[i].getName()).append("\"");
                if (i < files.length - 1) json.append(",");
            }
            json.append("]");
            return json.toString();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
