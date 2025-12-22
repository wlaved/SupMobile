package com.embiimob.supmobile;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class SupDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "sup_data.db";
    private static final int DATABASE_VERSION = 2; // Incremented version

    // Table: Profiles
    public static final String TABLE_PROFILES = "profiles";
    public static final String COL_URN = "urn";
    public static final String COL_ADDRESS = "address";
    public static final String COL_LAST_TXID = "last_txid";
    public static final String COL_IPFS_HASH = "ipfs_hash";
    public static final String COL_UPDATED = "updated";

    // Table: Messages
    public static final String TABLE_MESSAGES = "messages";
    public static final String COL_TXID = "txid";
    public static final String COL_SENDER = "sender";
    public static final String COL_RECEIVER = "receiver"; // New Column
    public static final String COL_CONTENT = "content";
    public static final String COL_TIMESTAMP = "timestamp";

    public SupDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createProfiles = "CREATE TABLE " + TABLE_PROFILES + " (" +
                COL_URN + " TEXT PRIMARY KEY, " +
                COL_ADDRESS + " TEXT, " +
                COL_LAST_TXID + " TEXT, " +
                COL_IPFS_HASH + " TEXT, " +
                COL_UPDATED + " INTEGER)";

        String createMessages = "CREATE TABLE " + TABLE_MESSAGES + " (" +
                COL_TXID + " TEXT PRIMARY KEY, " +
                COL_SENDER + " TEXT, " +
                COL_RECEIVER + " TEXT, " +
                COL_CONTENT + " TEXT, " +
                COL_TIMESTAMP + " INTEGER)";

        db.execSQL(createProfiles);
        db.execSQL(createMessages);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PROFILES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
        onCreate(db);
    }

    // --- Helper Methods ---

    public void addMessage(String txid, String sender, String receiver, String content) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TXID, txid);
        values.put(COL_SENDER, sender);
        values.put(COL_RECEIVER, receiver);
        values.put(COL_CONTENT, content);
        values.put(COL_TIMESTAMP, System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void updateProfile(String urn, String address, String txid, String ipfsHash) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_URN, urn);
        values.put(COL_ADDRESS, address);
        values.put(COL_LAST_TXID, txid);
        values.put(COL_IPFS_HASH, ipfsHash);
        values.put(COL_UPDATED, System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_PROFILES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Cursor getProfile(String urn) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_PROFILES, null, COL_URN + "=?", new String[]{urn}, null, null, null);
    }

    public String getMessagesJson(int limit) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_MESSAGES, null, null, null, null, null, COL_TIMESTAMP + " DESC", String.valueOf(limit));

        StringBuilder json = new StringBuilder("[");
        while (cursor.moveToNext()) {
            if (json.length() > 1) json.append(",");
            String txid = cursor.getString(cursor.getColumnIndexOrThrow(COL_TXID));
            String sender = cursor.getString(cursor.getColumnIndexOrThrow(COL_SENDER));
            String receiver = cursor.isNull(cursor.getColumnIndexOrThrow(COL_RECEIVER)) ? "" : cursor.getString(cursor.getColumnIndexOrThrow(COL_RECEIVER));
            String content = cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT));
            long time = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));

            // Format date for UI compatibility (YYYY-MM-DD...)
            // UI expects BlockDate usually, but we use timestamp here.
            String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(time));

            json.append(String.format("{\"TransactionId\":\"%s\",\"FromAddress\":\"%s\",\"ToAddress\":\"%s\",\"Message\":\"%s\",\"BlockDate\":\"%s\"}",
                txid, sender, receiver, content.replace("\"", "\\\"").replace("\n", " "), dateStr));
        }
        cursor.close();
        json.append("]");
        return json.toString();
    }

    public String getMessagesByAddressJson(String address) {
        SQLiteDatabase db = this.getReadableDatabase();
        // Query sender OR receiver
        String selection = COL_SENDER + "=? OR " + COL_RECEIVER + "=?";
        String[] selectionArgs = new String[]{address, address};

        Cursor cursor = db.query(TABLE_MESSAGES, null, selection, selectionArgs, null, null, COL_TIMESTAMP + " DESC");

        StringBuilder json = new StringBuilder("[");
        while (cursor.moveToNext()) {
            if (json.length() > 1) json.append(",");
            String txid = cursor.getString(cursor.getColumnIndexOrThrow(COL_TXID));
            String sender = cursor.getString(cursor.getColumnIndexOrThrow(COL_SENDER));
            String receiver = cursor.isNull(cursor.getColumnIndexOrThrow(COL_RECEIVER)) ? "" : cursor.getString(cursor.getColumnIndexOrThrow(COL_RECEIVER));
            String content = cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT));
            long time = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
             String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(time));

            json.append(String.format("{\"TransactionId\":\"%s\",\"FromAddress\":\"%s\",\"ToAddress\":\"%s\",\"Message\":\"%s\",\"BlockDate\":\"%s\"}",
                txid, sender, receiver, content.replace("\"", "\\\"").replace("\n", " "), dateStr));
        }
        cursor.close();
        json.append("]");
        return json.toString();
    }

    public String getObjectsJson(String address) {
        SQLiteDatabase db = this.getReadableDatabase();
        // Objects are typically created by the user (sender)
        Cursor cursor = db.query(TABLE_MESSAGES, null, COL_SENDER + "=?", new String[]{address}, null, null, COL_TIMESTAMP + " DESC");

        StringBuilder json = new StringBuilder("[");
        while (cursor.moveToNext()) {
            if (json.length() > 1) json.append(",");
            String content = cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT));

            String trimmed = content.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                json.append(trimmed);
            } else {
                json.append(String.format("{\"Description\":\"%s\",\"Creators\":{\"%s\":\"\"}}",
                    content.replace("\"", "\\\"").replace("\n", " "), address));
            }
        }
        cursor.close();
        json.append("]");
        return json.toString();
    }
}
