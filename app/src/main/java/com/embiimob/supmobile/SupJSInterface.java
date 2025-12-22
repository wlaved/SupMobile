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
        if (isBound && nodeService != null) {
            nodeService.addWatchedAddress(urnOrAddress);
        } else {
            showToast("Service not bound");
        }
    }

    @JavascriptInterface
    public String getSocialFeed(int limit) {
        // Need to add this method to DB Helper first, plan step 4 covers it.
        // But I need to add it here to be ready.
        if (isBound && nodeService != null) {
             // For now return empty or stub until DB Helper update in next step
             // Actually, I can't call a method that doesn't exist yet or it won't compile.
             // I will skip adding the call until step 4 is done.
             // Wait, I can add it now if I implemented SupDatabaseHelper.getMessages in step 4?
             // No, step 4 is "Modify SupDatabaseHelper.java". I am in step 2.
             return "[]";
        }
        return "[]";
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
    public void setAutoScan(boolean enabled) {
        android.content.SharedPreferences prefs = mContext.getSharedPreferences("SupPrefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("autoScan", enabled).apply();
        showToast("Auto-Scan " + (enabled ? "Enabled" : "Disabled"));
    }

    @JavascriptInterface
    public String localProfileSearch(String urn) {
        if (!isBound || nodeService == null) return null;

        SupDatabaseHelper db = nodeService.getDbHelper();
        if (db != null) {
            Cursor c = db.getProfile(urn);
            if (c != null && c.moveToFirst()) {
                c.close();
                return "{\"URN\":\"" + urn + "\", \"offline\": true}";
            }
        }
        return null;
    }

    @JavascriptInterface
    public String localObjectSearch(String address) {
        if (!isBound || nodeService == null) return "[]";
        SupDatabaseHelper db = nodeService.getDbHelper();
        if (db != null) {
            return db.getObjectsJson(address);
        }
        return "[]";
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
