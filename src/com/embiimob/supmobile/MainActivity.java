package com.embiimob.supmobile;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import android.os.AsyncTask;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.provider.DocumentsContract;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE_OPEN_DIR = 1001;
    private static final String PREFS_NAME = "SupMobilePrefs";
    private TextView statusIndicator, feedOutput;
    private Button btnRefresh, btnMint, btnSelectFolder;
    private ToggleButton toggleLocalMode;
    private EditText inputMsg;
    private boolean isTestnet = true;
    private Uri localBlockchainUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusIndicator = findViewById(R.id.statusIndicator);
        feedOutput = findViewById(R.id.feedOutput);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnMint = findViewById(R.id.btnMint);
        inputMsg = findViewById(R.id.inputMsg);
        toggleLocalMode = findViewById(R.id.toggleLocalMode);
        btnSelectFolder = findViewById(R.id.btnSelectFolder);
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        String savedUri = settings.getString("blockchain_uri", null);
        if (savedUri != null) {
            localBlockchainUri = Uri.parse(savedUri);
            btnSelectFolder.setText("Loc: " + localBlockchainUri.getLastPathSegment());
        }
        statusIndicator.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { isTestnet = !isTestnet; updateTheme(); }
        });
        toggleLocalMode.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (toggleLocalMode.isChecked()) {
                    btnSelectFolder.setVisibility(View.VISIBLE);
                    if (localBlockchainUri == null) openDirectoryPicker();
                } else Toast.makeText(MainActivity.this, "Web Mode", Toast.LENGTH_SHORT).show();
            }
        });
        btnSelectFolder.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openDirectoryPicker(); }
        });
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (toggleLocalMode.isChecked()) {
                    if (localBlockchainUri != null) new LocalDiskScanTask().execute(localBlockchainUri);
                    else Toast.makeText(MainActivity.this, "Select Folder First", Toast.LENGTH_SHORT).show();
                } else new FetchFeedTask().execute();
            }
        });
        updateTheme();
    }
    private void openDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CODE_OPEN_DIR);
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == REQUEST_CODE_OPEN_DIR && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                localBlockchainUri = resultData.getData();
                getContentResolver().takePersistableUriPermission(localBlockchainUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, 0).edit();
                editor.putString("blockchain_uri", localBlockchainUri.toString());
                editor.commit();
                btnSelectFolder.setText("Loc: " + localBlockchainUri.getLastPathSegment());
                new LocalDiskScanTask().execute(localBlockchainUri);
            }
        }
    }
    private void updateTheme() {
        if (isTestnet) {
            statusIndicator.setBackgroundColor(0xFF00FF00);
            feedOutput.setTextColor(0xFF00FF00);
        } else {
            statusIndicator.setBackgroundColor(0xFFFF9900);
            feedOutput.setTextColor(0xFFFF9900);
        }
    }
    private class LocalDiskScanTask extends AsyncTask<Uri, String, String> {
        @Override
        protected String doInBackground(Uri... uris) {
            Uri treeUri = uris[0];
            StringBuilder sb = new StringBuilder();
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
            try (Cursor c = getContentResolver().query(childrenUri, new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_SIZE }, null, null, null)) {
                if (c != null) {
                    sb.append("Scanning: " + treeUri.getLastPathSegment() + "\n----------------\n");
                    int count = 0;
                    while (c.moveToNext()) {
                        if (count < 15) sb.append("[" + c.getString(0) + "] " + c.getLong(1) + " bytes\n");
                        count++;
                    }
                    sb.append("\nTotal Files: " + count);
                }
            } catch (Exception e) { return "Error: " + e.getMessage(); }
            return sb.toString();
        }
        @Override
        protected void onPostExecute(String result) { feedOutput.setText(result); }
    }
    private class FetchFeedTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... voids) { return "CONNECTED TO P2FK.IO NODE...\n[obj:8354] Sup!? Decentralized world!"; }
        @Override
        protected void onPostExecute(String result) { feedOutput.setText(result); }
    }
}
