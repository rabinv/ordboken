package in.rab.ordboken;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class HistoryFragment extends CommonListFragment {
    public static HistoryFragment newInstance() {
        return new HistoryFragment();
    }

    protected Cursor getCursor(SQLiteDatabase db) {
        String[] projection = {
                OrdbokenContract.HistoryEntry._ID,
                OrdbokenContract.HistoryEntry.COLUMN_NAME_TITLE,
                OrdbokenContract.HistoryEntry.COLUMN_NAME_SUMMARY,
                OrdbokenContract.HistoryEntry.COLUMN_NAME_URL,
                OrdbokenContract.HistoryEntry.COLUMN_NAME_DATE,
        };
        String sortOrder = OrdbokenContract.HistoryEntry.COLUMN_NAME_DATE + " DESC";
        return db.query(OrdbokenContract.HistoryEntry.TABLE_NAME, projection,
                null, null, null, null, sortOrder);
    }
}
