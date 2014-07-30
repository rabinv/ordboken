package in.rab.ordboken;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import in.rab.ordboken.OrdbokenContract.HistoryEntry;

public class HistoryFragment extends CommonListFragment {
    public static HistoryFragment newInstance() {
        return new HistoryFragment();
    }

    protected Cursor getCursor(SQLiteDatabase db) {
        String[] projection = {
                HistoryEntry._ID,
                HistoryEntry.COLUMN_NAME_TITLE,
                HistoryEntry.COLUMN_NAME_SUMMARY,
                HistoryEntry.COLUMN_NAME_URL,
                HistoryEntry.COLUMN_NAME_DATE,
        };
        String sortOrder = HistoryEntry.COLUMN_NAME_DATE + " DESC";
        return db.query(HistoryEntry.TABLE_NAME, projection,
                null, null, null, null, sortOrder);
    }
}
