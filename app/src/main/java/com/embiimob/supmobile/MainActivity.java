package com.embiimob.supmobile;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.os.Environment;
import android.content.SharedPreferences;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.net.HttpURLConnection;
import java.net.URL;

// Import BitcoinJ classes
import org.bitcoinj.core.*;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTransaction;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.PeerGroup;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        String savedUri = settings.getString("storage_uri", null);
        if (savedUri != null) {
            storageUri = Uri.parse(savedUri);
            if (storageUri.getPath() != null && storageUri.getPath().contains(":")) {
                String[] parts = storageUri.getPath().split(":");
                if (parts.length > 1) {
                    customStorageDir = new File(Environment.getExternalStorageDirectory(), parts[1]);
                }
            }
        }

        startBitcoinNode();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);

        webView.addJavascriptInterface(new SupJSInterface(), "SupApp");
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/dashboard.html");
    }

    private void startBitcoinNode() {
        new Thread(() -> {
            try {
                params = TestNet3Params.get();
                File chainFile;
                if (customStorageDir != null && customStorageDir.exists() && customStorageDir.canWrite()) {
                    chainFile = new File(customStorageDir, "sup_mobile.spvchain");
                } else {
                    chainFile = new File(getExternalFilesDir(null), "sup_mobile.spvchain");
                }

                wallet = new Wallet(params);

                // Add Listener for Watch List / Auto-Pinning
                wallet.addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
                    @Override
                    public void onCoinsReceived(Wallet w, Transaction tx, Coin prevBalance, Coin newBalance) {
                        checkForSupContent(tx);
                    }
                });

                blockStore = new SPVBlockStore(params, chainFile);
                blockChain = new BlockChain(params, wallet, blockStore);

                peerGroup = new PeerGroup(params, blockChain);
                peerGroup.addWallet(wallet);
                peerGroup.addPeerDiscovery(new DnsDiscovery(params));

                peerGroup.startAsync();
                peerGroup.startBlockChainDownload(null);

                runOnUiThread(() -> Toast.makeText(this, "Node Started! Height: " + blockChain.getBestChainHeight(), Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Logic to parse transactions for Sup/IPFS data
    private void checkForSupContent(Transaction tx) {
        try {
            // Very basic parser: look for OP_RETURN
            for (TransactionOutput out : tx.getOutputs()) {
                Script script = out.getScriptPubKey();
                if (script.isOpReturn()) {
                    String data = new String(script.getChunks().get(1).data);
                    // Hypothetical format: "SUP01 IPFS:<hash>"
                    if (data.contains("IPFS:")) {
                        String ipfsHash = data.substring(data.indexOf("IPFS:") + 5).trim();
                        // Trigger IPFS Pin
                        new SupJSInterface().pinIpfs(ipfsHash);
                        runOnUiThread(() -> Toast.makeText(this, "Auto-Pinning: " + ipfsHash, Toast.LENGTH_SHORT).show());
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
                editor.commit();

                String path = storageUri.getPath();
                if (path.contains(":")) {
                    String[] parts = path.split(":");
                    if (parts.length > 1) {
                         customStorageDir = new File(Environment.getExternalStorageDirectory(), parts[1]);
                    }
                }
                Toast.makeText(this, "Storage Selected.", Toast.LENGTH_SHORT).show();
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
        public String getStoragePath() {
            if (storageUri != null) return storageUri.toString();
            return null;
        }

        @JavascriptInterface
        public String getNodeStatus() {
            if (peerGroup == null) return "Initializing...";
            if (peerGroup.isRunning()) {
                return "Running. Peers: " + peerGroup.numConnectedPeers() + ". Height: " + blockChain.getBestChainHeight();
            }
            return "Stopped";
        }

        // Watch a specific address/URN (Add to Wallet Bloom Filter)
        @JavascriptInterface
        public void watchProfile(String address) {
            try {
                Address addr = Address.fromBase58(params, address);
                wallet.addWatchedAddress(addr);
                Toast.makeText(MainActivity.this, "Watching: " + address, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Invalid Address: " + address, Toast.LENGTH_SHORT).show();
            }
        }

        // Bridge to Local IPFS Node (Termux)
        @JavascriptInterface
        public String pinIpfs(String hash) {
            new Thread(() -> {
                try {
                    URL url = new URL("http://127.0.0.1:5001/api/v0/pin/add?arg=" + hash);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.getResponseCode(); // Trigger request
                    conn.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            return "Pin request sent for " + hash;
        }

        @JavascriptInterface
        public String getWalletAddress() {
            return wallet.currentReceiveAddress().toString();
        }

        @JavascriptInterface
        public String mint(String data) {
            try {
                String payload = "SUP01" + data;
                Script opReturnScript = ScriptBuilder.createOpReturnScript(payload.getBytes());
                return "Transaction Constructed: " + payload;
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public String listFiles(String subPath) {
            if (storageUri == null) {
                 File dir = Environment.getExternalStorageDirectory();
                 return listFilesNative(dir);
            }
            try {
                DocumentFile pickedDir = DocumentFile.fromTreeUri(MainActivity.this, storageUri);
                if (pickedDir == null || !pickedDir.isDirectory()) return "[]";

                DocumentFile[] files = pickedDir.listFiles();
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < files.length; i++) {
                    json.append("\"").append(files[i].getName()).append("\"");
                    if (i < files.length - 1) json.append(",");
                }
                json.append("]");
                return json.toString();
            } catch (Exception e) {
                return "[\"Error: " + e.getMessage() + "\"]";
            }
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
