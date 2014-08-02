package in.rab.ordboken;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.Date;

import in.rab.ordboken.OrdbokenContract.FavoritesEntry;
import in.rab.ordboken.OrdbokenContract.HistoryEntry;

public class OrdbokenDbHelper extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 1;
    public static final String DATBASE_NAME = "Ordboken.db";
    private static final String SQL_CREATE_HISTORY =
            "CREATE TABLE " + HistoryEntry.TABLE_NAME + " (" +
                    HistoryEntry._ID + " INTEGER PRIMARY KEY," +
                    HistoryEntry.COLUMN_NAME_TITLE + " TEXT," +
                    HistoryEntry.COLUMN_NAME_SUMMARY + " TEXT," +
                    HistoryEntry.COLUMN_NAME_URL + " TEXT," +
                    HistoryEntry.COLUMN_NAME_DATE + " INTEGER," +
                    "UNIQUE (" + HistoryEntry.COLUMN_NAME_URL +
                    ") ON CONFLICT REPLACE" +
                    " )";
    private static final String SQL_CREATE_FAVORITES =
            "CREATE TABLE " + FavoritesEntry.TABLE_NAME + "(" +
                    FavoritesEntry._ID + " INTEGER PRIMARY KEY," +
                    FavoritesEntry.COLUMN_NAME_TITLE + " TEXT," +
                    FavoritesEntry.COLUMN_NAME_SUMMARY + " TEXT," +
                    FavoritesEntry.COLUMN_NAME_URL + " TEXT UNIQUE" +
                    " )";
    private static final String SQL_DELETE_HISTORY =
            "DROP TABLE IF EXISTS " + HistoryEntry.TABLE_NAME;
    private static final String SQL_DELETE_FAVORITES =
            "DROP TABLE IF EXISTS " + FavoritesEntry.TABLE_NAME;

    public OrdbokenDbHelper(Context context) {
        super(context, DATBASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_HISTORY);
        db.execSQL(SQL_CREATE_FAVORITES);

        ContentValues values = new ContentValues();
        values.put(HistoryEntry.COLUMN_NAME_TITLE, "ordbok");
        values.put(HistoryEntry.COLUMN_NAME_URL, "http://api.ne.se/ordbok/svensk/ordbok");
        values.put(HistoryEntry.COLUMN_NAME_SUMMARY, "");
        values.put(HistoryEntry.COLUMN_NAME_DATE, new Date().getTime());

        db.insert(HistoryEntry.TABLE_NAME, null, values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_HISTORY);
        db.execSQL(SQL_DELETE_FAVORITES);
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
