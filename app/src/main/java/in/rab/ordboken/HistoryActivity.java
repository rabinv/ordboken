package in.rab.ordboken;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class HistoryActivity extends ListActivity {
    private Ordboken mOrdboken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        mOrdboken = Ordboken.getInstance(this);
        if (mOrdboken.mPrefs.getBoolean("loggedIn", false) == false) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        new HistoryTask().execute();
    }

    @Override
    protected void onResume() {
        super.onResume();
        new HistoryTask().execute();
    }

    private class HistoryTask extends AsyncTask<Void, Void, ListAdapter> {
        @Override
        protected ListAdapter doInBackground(Void... params) {
            OrdbokenDbHelper dbHelper = new OrdbokenDbHelper(HistoryActivity.this);
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            String[] projection = {
                    OrdbokenContract.HistoryEntry._ID,
                    OrdbokenContract.HistoryEntry.COLUMN_NAME_TITLE,
                    OrdbokenContract.HistoryEntry.COLUMN_NAME_URL,
                    OrdbokenContract.HistoryEntry.COLUMN_NAME_DATE,
            };
            String sortOrder = OrdbokenContract.HistoryEntry.COLUMN_NAME_DATE + " DESC";
            Cursor cursor = db.query(OrdbokenContract.HistoryEntry.TABLE_NAME, projection,
                                null, null, null, null, sortOrder);

            return new SimpleCursorAdapter(HistoryActivity.this,
                    android.R.layout.simple_list_item_1, cursor,
                    new String[] {OrdbokenContract.HistoryEntry.COLUMN_NAME_TITLE},
                    new int [] {android.R.id.text1}, 0);
        }

        @Override
        protected void onPostExecute(ListAdapter adapter) {
            setListAdapter(adapter);
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        Cursor c = (Cursor) l.getItemAtPosition(position);
        String title = c.getString(c.getColumnIndex(OrdbokenContract.HistoryEntry.COLUMN_NAME_TITLE));
        String url = c.getString(c.getColumnIndex(OrdbokenContract.HistoryEntry.COLUMN_NAME_URL));

        mOrdboken.startWordActivity(this, title, url);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        mOrdboken.initSearchView(this, menu, null, false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mOrdboken.onOptionsItemSelected(this, item)) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
