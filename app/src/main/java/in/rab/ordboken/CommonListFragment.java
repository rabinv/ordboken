package in.rab.ordboken;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

import in.rab.ordboken.OrdbokenContract.HistoryEntry;

public abstract class CommonListFragment extends ListFragment {
    private String mTable;

    public CommonListFragment(String table) {
        mTable = table;
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
                    new String[]{HistoryEntry.COLUMN_NAME_TITLE,
                            HistoryEntry.COLUMN_NAME_SUMMARY},
                    new int[]{android.R.id.text1, android.R.id.text2}, 0
            );
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
                .getString(c.getColumnIndex(HistoryEntry.COLUMN_NAME_TITLE));
        String url = c.getString(c.getColumnIndex(HistoryEntry.COLUMN_NAME_URL));

        Ordboken.startWordActivity(getActivity(), title, url);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        registerForContextMenu(getListView());
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.list, menu);
    }

    private class DeleteTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
            String url = params[0];
            OrdbokenDbHelper dbHelper = new OrdbokenDbHelper(getActivity());
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            if (url == null) {
                db.delete(mTable, null, null);
            } else {
                db.delete(mTable, HistoryEntry.COLUMN_NAME_URL + "=?", new String[]{url});
            }

            db.close();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            new HistoryTask().execute();
        }
    }

    private boolean deleteItem(Cursor cursor) {
        String url = cursor.getString(cursor.getColumnIndex(HistoryEntry.COLUMN_NAME_URL));
        new DeleteTask().execute(url);
        return true;
    }

    private boolean deleteAll() {
        new DeleteTask().execute((String) null);
        return true;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // This gets called on all fragments; this hack(?) makes us operate
        // on the correct one only.
        if (!getUserVisibleHint()) {
            return false;
        }

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        Cursor c = (Cursor) getListAdapter().getItem(info.position);

        switch (item.getItemId()) {
            case R.id.delete:
                return deleteItem(c);
            case R.id.delete_all:
                return deleteAll();
            default:
                return super.onContextItemSelected(item);
        }
    }
}
