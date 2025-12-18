package com.embiimob.supmobile;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.os.Environment;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

// Import BitcoinJ classes (will be available via Gradle)
import org.bitcoinj.core.*;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.Wallet;

public class MainActivity extends Activity {
    private WebView webView;
    private Wallet wallet;
    private NetworkParameters params;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize BitcoinJ (Testnet for now)
        params = TestNet3Params.get();
        wallet = new Wallet(params); // In a real app, load this from file

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

    // The Bridge Class
    public class SupJSInterface {
        @JavascriptInterface
        public void showToast(String toast) {
            Toast.makeText(MainActivity.this, toast, Toast.LENGTH_SHORT).show();
        }

        @JavascriptInterface
        public String getWalletAddress() {
            // Return a fresh receive address
            return wallet.currentReceiveAddress().toString();
        }

        @JavascriptInterface
        public String mint(String data) {
            try {
                // Simplified "Mint" logic: Create an OP_RETURN transaction
                // NOTE: This is a stub implementation. In a real scenario, you need UTXOs.
                // Here we just demonstrate constructing the OpReturn script.

                // Placeholder protocol ID: "SUP01"
                String payload = "SUP01" + data;

                // Create the OP_RETURN script
                Script opReturnScript = ScriptBuilder.createOpReturnScript(payload.getBytes());

                // In a real wallet, we would do:
                // Transaction tx = new Transaction(params);
                // tx.addOutput(Coin.ZERO, opReturnScript);
                // wallet.sendCoins(peerGroup, tx);

                return "Transaction Constructed (Simulated): " + payload;
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public String listFiles(String path) {
            // Simple file lister for the "Connected Storage" requirement
            File dir = new File(path);
            if (!dir.exists() || !dir.isDirectory()) {
                // Fallback to external storage root if path is invalid
                dir = Environment.getExternalStorageDirectory();
            }

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

    // Handle back button for WebView navigation
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
