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

// Import BitcoinJ classes
import org.bitcoinj.core.*;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.Wallet;
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

        // Restore saved storage URI
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        String savedUri = settings.getString("storage_uri", null);
        if (savedUri != null) {
            storageUri = Uri.parse(savedUri);
            // Attempt to resolve a File path from URI (Best Effort for Termux/Legacy)
            if (storageUri.getPath() != null && storageUri.getPath().contains(":")) {
                // Very crude path resolution for standard external storage
                String[] parts = storageUri.getPath().split(":");
                if (parts.length > 1) {
                    customStorageDir = new File(Environment.getExternalStorageDirectory(), parts[1]);
                }
            }
        }

        // Initialize BitcoinJ
        startBitcoinNode();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);

        // Bind the Bridge
        webView.addJavascriptInterface(new SupJSInterface(), "SupApp");

        webView.setWebViewClient(new WebViewClient());

        // Load the dashboard
        webView.loadUrl("file:///android_asset/dashboard.html");
    }

    private void startBitcoinNode() {
        new Thread(() -> {
            try {
                params = TestNet3Params.get();

                // Location for the SPV chain file
                File chainFile;
                if (customStorageDir != null && customStorageDir.exists() && customStorageDir.canWrite()) {
                    chainFile = new File(customStorageDir, "sup_mobile.spvchain");
                } else {
                    chainFile = new File(getExternalFilesDir(null), "sup_mobile.spvchain");
                }

                // Initialize Wallet
                wallet = new Wallet(params);

                // Initialize BlockStore (SPV)
                blockStore = new SPVBlockStore(params, chainFile);

                // Initialize Chain
                blockChain = new BlockChain(params, wallet, blockStore);

                // Initialize PeerGroup (The Network Connection)
                peerGroup = new PeerGroup(params, blockChain);
                peerGroup.addWallet(wallet);
                peerGroup.addPeerDiscovery(new DnsDiscovery(params));

                // Start Async
                peerGroup.startAsync();
                peerGroup.startBlockChainDownload(null);

                runOnUiThread(() -> Toast.makeText(this, "Bitcoin Node Started! Storing in: " + chainFile.getParent(), Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Node Start Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
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

                // Try to resolve path for next restart
                String path = storageUri.getPath();
                if (path.contains(":")) {
                    String[] parts = path.split(":");
                    if (parts.length > 1) {
                         customStorageDir = new File(Environment.getExternalStorageDirectory(), parts[1]);
                    }
                }

                Toast.makeText(this, "Storage Selected. Restart app to use for Node.", Toast.LENGTH_LONG).show();
                webView.reload();
            }
        }
    }

    // The Bridge Class
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
