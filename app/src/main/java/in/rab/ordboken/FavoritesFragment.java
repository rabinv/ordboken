package in.rab.ordboken;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class FavoritesFragment extends CommonListFragment {
    public static FavoritesFragment newInstance() {
        return new FavoritesFragment();
    }

    protected Cursor getCursor(SQLiteDatabase db) {
        String[] projection = {
                OrdbokenContract.FavoritesEntry._ID,
                OrdbokenContract.FavoritesEntry.COLUMN_NAME_TITLE,
                OrdbokenContract.FavoritesEntry.COLUMN_NAME_URL,
        };
        String sortOrder = OrdbokenContract.FavoritesEntry.COLUMN_NAME_TITLE + " ASC";
        return db.query(OrdbokenContract.FavoritesEntry.TABLE_NAME, projection,
                null, null, null, null, sortOrder);
    }
}
