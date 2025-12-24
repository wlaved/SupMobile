package com.embiimob.supmobile;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class SupDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "sup_data.db";
    private static final int DATABASE_VERSION = 3; // Incremented version for IS_WATCHED

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
    public static final String COL_RECEIVER = "receiver";
    public static final String COL_CONTENT = "content";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_IS_WATCHED = "is_watched"; // New Column

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
                COL_TIMESTAMP + " INTEGER, " +
                COL_IS_WATCHED + " INTEGER DEFAULT 0)";

        db.execSQL(createProfiles);
        db.execSQL(createMessages);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            // Migration for version 3: Add is_watched column
            // We can try to alter table if table exists, or drop/create.
            // Since this is dev, drop/create is safer/easier but loses data.
            // Given "Local Scan" can be re-run, dropping is acceptable for this stage.
            // However, a proper migration is better practice.
            // Let's drop for now to ensure clean schema.
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PROFILES);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
            onCreate(db);
        }
    }

    // --- Helper Methods ---

    public void addMessage(String txid, String sender, String receiver, String content, boolean isWatched) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TXID, txid);
        values.put(COL_SENDER, sender);
        values.put(COL_RECEIVER, receiver);
        values.put(COL_CONTENT, content);
        values.put(COL_TIMESTAMP, System.currentTimeMillis());
        values.put(COL_IS_WATCHED, isWatched ? 1 : 0);
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
        return getMessagesJsonInternal(limit, false);
    }

    public String getWatchedMessagesJson(int limit) {
        return getMessagesJsonInternal(limit, true);
    }

    private String getMessagesJsonInternal(int limit, boolean onlyWatched) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = onlyWatched ? COL_IS_WATCHED + "=1" : null;
        Cursor cursor = db.query(TABLE_MESSAGES, null, selection, null, null, null, COL_TIMESTAMP + " DESC", String.valueOf(limit));

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

    public String getMessagesByAddressJson(String address) {
        SQLiteDatabase db = this.getReadableDatabase();
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

    public long getMessageCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        return android.database.DatabaseUtils.queryNumEntries(db, TABLE_MESSAGES);
    }

    public long getProfileCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        return android.database.DatabaseUtils.queryNumEntries(db, TABLE_PROFILES);
    }
}
