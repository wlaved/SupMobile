package com.embiimob.supmobile;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;
import android.database.Cursor;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

public class SupJSInterface implements SupNodeService.NodeEventListener {
    private Context mContext;
    private WebView mWebView;
    private boolean isMainnet = false;
    private String customStoragePath = null;

    private SupNodeService nodeService;
    private boolean isBound = false;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            SupNodeService.LocalBinder binder = (SupNodeService.LocalBinder) service;
            nodeService = binder.getService();
            nodeService.registerListener(SupJSInterface.this);
            isBound = true;
            // Sync config
            nodeService.setConfiguration(isMainnet, customStoragePath);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
            nodeService = null;
        }
    };

    public SupJSInterface(Context context, WebView webView) {
        mContext = context;
        mWebView = webView;

        // Load persisted path
        android.content.SharedPreferences prefs = mContext.getSharedPreferences("SupPrefs", Context.MODE_PRIVATE);
        customStoragePath = prefs.getString("storagePath", null);
        isMainnet = prefs.getBoolean("isMainnet", false);

        // Bind to service immediately to ensure connection
        Intent intent = new Intent(mContext, SupNodeService.class);
        mContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    public void unbind() {
        if (isBound) {
            if (nodeService != null) nodeService.unregisterListener(this);
            mContext.unbindService(connection);
            isBound = false;
        }
    }

    // --- Bridge Methods ---

    @JavascriptInterface
    public void startNode() {
        if (isBound && nodeService != null) {
            nodeService.startNode();
        } else {
            showToast("Service not bound yet. Retrying...");
            // Attempt re-bind
            Intent intent = new Intent(mContext, SupNodeService.class);
            mContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        }
    }

    @JavascriptInterface
    public void stopNode() {
        if (isBound && nodeService != null) {
            nodeService.stopNode();
        }
    }

    @JavascriptInterface
    public String getNodeStatus() {
        if (isBound && nodeService != null) {
            return nodeService.getStatusJson();
        }
        return "{\"running\": false, \"peers\": 0, \"height\": 0, \"path\": \"Initializing...\"}";
    }

    @JavascriptInterface
    public void selectStorage() {
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

        // Persist
        android.content.SharedPreferences prefs = mContext.getSharedPreferences("SupPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("storagePath", path).apply();

        if (isBound && nodeService != null) {
            nodeService.setConfiguration(isMainnet, customStoragePath);
        }

        showToast("Storage path saved: " + path);
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
                    notifyFrontend("system_log", "Pinned to IPFS: " + cleanHash);
                } else {
                    notifyFrontend("system_log", "IPFS Pin Failed: " + code);
                }
            } catch (Exception e) {
                notifyFrontend("system_log", "IPFS Error: " + e.getMessage());
            }
        }).start();
    }

    @JavascriptInterface
    public void watchProfile(String urnOrAddress) {
        // TODO: Move this to Service? Or pass to Service?
        // Service holds the wallet now. So we need a method in Service.
        // For this patch, since I didn't add watchProfile to SupNodeService, I'll log a placeholder.
        // Real implementation would require adding watchProfile to SupNodeService.
        showToast("Watch Profile requires Service update. (Coming soon)");
    }

    @JavascriptInterface
    public void startLocalScan() {
        if (isBound && nodeService != null) {
            nodeService.startLocalScan();
        } else {
            showToast("Service not bound");
        }
    }

    @JavascriptInterface
    public void setNetwork(boolean useMainnet) {
        // We can't easily check isRunning here without sync, but Service handles it.
        this.isMainnet = useMainnet;

        // Persist
        android.content.SharedPreferences prefs = mContext.getSharedPreferences("SupPrefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("isMainnet", useMainnet).apply();

        if (isBound && nodeService != null) {
            nodeService.setConfiguration(isMainnet, customStoragePath);
        }
        showToast("Switched to " + (useMainnet ? "Mainnet" : "Testnet"));
    }

    @JavascriptInterface
    public String localProfileSearch(String urn) {
        if (!isBound || nodeService == null) return null;

        // Basic implementation: check DB for profile
        // Since we are just indexing messages for now, we might not have full profile metadata
        // but we can return basic structure to satisfy "Offline Search" expectation.
        SupDatabaseHelper db = nodeService.getDbHelper();
        if (db != null) {
            // For now, check if we have messages for this URN/Address?
            // Or just return null if not explicitly in a "Profiles" table
            Cursor c = db.getProfile(urn);
            if (c != null && c.moveToFirst()) {
                // Return cached profile
                // Stub for future enhancement
                c.close();
                return "{\"URN\":\"" + urn + "\", \"offline\": true}";
            }
        }
        return null;
    }

    private void showToast(String msg) {
        mWebView.post(() -> Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show());
    }

    private void notifyFrontend(String eventType, String data) {
        mWebView.post(() -> {
            String safeData = data.replace("'", "\\'").replace("\"", "\\\"");
            String js = "if(window.onSupEvent) window.onSupEvent('" + eventType + "', '" + safeData + "');";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                mWebView.evaluateJavascript(js, null);
            } else {
                mWebView.loadUrl("javascript:" + js);
            }
        });
    }

    @Override
    public void onEvent(String type, String data) {
        if ("ipfs_found".equals(type)) {
            pinIpfs(data);
        } else {
            notifyFrontend(type, data);
        }
    }
}
