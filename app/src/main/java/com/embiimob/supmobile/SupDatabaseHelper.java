package com.embiimob.supmobile;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class SupDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "sup_data.db";
    private static final int DATABASE_VERSION = 1;

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

    public void addMessage(String txid, String sender, String content) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TXID, txid);
        values.put(COL_SENDER, sender);
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
}
