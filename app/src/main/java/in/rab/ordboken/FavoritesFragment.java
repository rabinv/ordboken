package in.rab.ordboken;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import in.rab.ordboken.OrdbokenContract.FavoritesEntry;

public class FavoritesFragment extends CommonListFragment {
    public static FavoritesFragment newInstance() {
        return new FavoritesFragment();
    }

    protected Cursor getCursor(SQLiteDatabase db) {
        String[] projection = {
                FavoritesEntry._ID,
                FavoritesEntry.COLUMN_NAME_TITLE,
                FavoritesEntry.COLUMN_NAME_SUMMARY,
                FavoritesEntry.COLUMN_NAME_URL,
        };
        String sortOrder = FavoritesEntry.COLUMN_NAME_TITLE + " ASC";
        return db.query(FavoritesEntry.TABLE_NAME, projection,
                null, null, null, null, sortOrder);
    }
}
