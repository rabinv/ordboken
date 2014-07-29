package in.rab.ordboken;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public abstract class CommonListFragment extends ListFragment {
    private Ordboken mOrdboken;

    public CommonListFragment() {
        mOrdboken = Ordboken.getInstance(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        new HistoryTask().execute();
    }

    protected abstract Cursor getCursor(SQLiteDatabase db);

    private class HistoryTask extends AsyncTask<Void, Void, ListAdapter> {
        @Override
        protected ListAdapter doInBackground(Void... params) {
            OrdbokenDbHelper dbHelper = new OrdbokenDbHelper(getActivity());
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = getCursor(db);

            return new SimpleCursorAdapter(getActivity(),
                    android.R.layout.simple_list_item_2, cursor,
                    new String[]{OrdbokenContract.HistoryEntry.COLUMN_NAME_TITLE,
                            OrdbokenContract.HistoryEntry.COLUMN_NAME_SUMMARY},
                    new int[]{android.R.id.text1, android.R.id.text2}, 0);
        }

        @Override
        protected void onPostExecute(ListAdapter adapter) {
            setListAdapter(adapter);
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        Cursor c = (Cursor) l.getItemAtPosition(position);
        String title = c
                .getString(c.getColumnIndex(OrdbokenContract.HistoryEntry.COLUMN_NAME_TITLE));
        String url = c.getString(c.getColumnIndex(OrdbokenContract.HistoryEntry.COLUMN_NAME_URL));

        mOrdboken.startWordActivity(getActivity(), title, url);
    }
}
