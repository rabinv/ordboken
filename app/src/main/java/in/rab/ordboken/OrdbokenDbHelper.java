package in.rab.ordboken;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class OrdbokenDbHelper extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 8;
    public static final String DATBASE_NAME = "Ordboken.db";
    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + OrdbokenContract.HistoryEntry.TABLE_NAME + " (" +
                    OrdbokenContract.HistoryEntry._ID + " INTEGER PRIMARY KEY," +
                    OrdbokenContract.HistoryEntry.COLUMN_NAME_TITLE + " TEXT," +
                    OrdbokenContract.HistoryEntry.COLUMN_NAME_SUMMARY + " TEXT," +
                    // FIXME XXX DISTINGUISH MULTIPLE TEXTS!
                    OrdbokenContract.HistoryEntry.COLUMN_NAME_URL + " TEXT," +
                    OrdbokenContract.HistoryEntry.COLUMN_NAME_DATE + " INTEGER," +
                    "UNIQUE (" +  OrdbokenContract.HistoryEntry.COLUMN_NAME_URL +
                    ") ON CONFLICT REPLACE" +
                    " )";
    private static final String SQL_CREATE_ENTRIES2 =
            "CREATE TABLE " + OrdbokenContract.FavoritesEntry.TABLE_NAME + "(" +
                    OrdbokenContract.FavoritesEntry._ID + " INTEGER PRIMARY KEY," +
                    OrdbokenContract.FavoritesEntry.COLUMN_NAME_TITLE + " TEXT," +
                    OrdbokenContract.FavoritesEntry.COLUMN_NAME_SUMMARY + " TEXT," +
                    OrdbokenContract.FavoritesEntry.COLUMN_NAME_URL + " TEXT UNIQUE" +
                    " )";
    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + OrdbokenContract.HistoryEntry.TABLE_NAME;
    private static final String SQL_DELETE_ENTRIES2 =
            "DROP TABLE IF EXISTS " + OrdbokenContract.FavoritesEntry.TABLE_NAME;

    public OrdbokenDbHelper(Context context) {
        super(context, DATBASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i("SQL", SQL_CREATE_ENTRIES);
        Log.i("SQL2", SQL_CREATE_ENTRIES2);
        db.execSQL(SQL_CREATE_ENTRIES);
        db.execSQL(SQL_CREATE_ENTRIES2);

        ContentValues values = new ContentValues();
        values.put(OrdbokenContract.HistoryEntry.COLUMN_NAME_TITLE, "ordbok");
        values.put(OrdbokenContract.HistoryEntry.COLUMN_NAME_URL, "http://api.ne.se/ordbok/svensk/ordbok");
        values.put(OrdbokenContract.HistoryEntry.COLUMN_NAME_SUMMARY, "");
        values.put(OrdbokenContract.HistoryEntry.COLUMN_NAME_DATE, 0);

        db.insertOrThrow(OrdbokenContract.HistoryEntry.TABLE_NAME, "null", values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_ENTRIES);
        db.execSQL(SQL_DELETE_ENTRIES2);
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
