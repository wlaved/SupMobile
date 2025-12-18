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

// Import BitcoinJ classes
import org.bitcoinj.core.*;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.Wallet;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE_OPEN_DIR = 1001;
    private static final String PREFS_NAME = "SupMobilePrefs";

    private WebView webView;
    private Wallet wallet;
    private NetworkParameters params;
    private Uri storageUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Restore saved storage URI
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        String savedUri = settings.getString("storage_uri", null);
        if (savedUri != null) {
            storageUri = Uri.parse(savedUri);
        }

        // Initialize BitcoinJ (Testnet for now)
        // TODO: Update this to use storageUri for BlockStore if permitted
        params = TestNet3Params.get();
        wallet = new Wallet(params);

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

    // Handle the Folder Picker result
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == REQUEST_CODE_OPEN_DIR && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                storageUri = resultData.getData();

                // Persist permissions so we can access it later without asking again
                getContentResolver().takePersistableUriPermission(storageUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                // Save preference
                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, 0).edit();
                editor.putString("storage_uri", storageUri.toString());
                editor.commit();

                Toast.makeText(this, "Storage Selected: " + storageUri.getLastPathSegment(), Toast.LENGTH_LONG).show();

                // Refresh WebView to let it know
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
            // Open the System Folder Picker
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
        public String getWalletAddress() {
            return wallet.currentReceiveAddress().toString();
        }

        @JavascriptInterface
        public String mint(String data) {
            try {
                String payload = "SUP01" + data;
                Script opReturnScript = ScriptBuilder.createOpReturnScript(payload.getBytes());
                return "Transaction Constructed (Simulated): " + payload;
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public String listFiles(String subPath) {
            // If no storage selected, try external root (fallback)
            if (storageUri == null) {
                 File dir = Environment.getExternalStorageDirectory();
                 return listFilesNative(dir);
            }

            // Use DocumentFile to list files from the selected Tree URI
            try {
                DocumentFile pickedDir = DocumentFile.fromTreeUri(MainActivity.this, storageUri);
                if (pickedDir == null || !pickedDir.isDirectory()) return "[]";

                // Note: Simple listing for now. Navigating subdirectories would require more logic.
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
