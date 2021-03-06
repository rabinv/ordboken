/*
 * Copyright (C) 2013 Rabin Vincent <rabin@rab.in>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package in.rab.ordboken;

import android.app.ActionBar;
import android.app.ListActivity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import in.rab.ordboken.NeClient.NeSearchResult;

public class MainActivity extends ListActivity {
    private Button mRetryButton;
    private ProgressBar mProgressBar;
    private TextView mStatusText;
    private String mLastQuery;
    private Ordboken mOrdboken;
    private boolean mSeenResults;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME
                | ActionBar.DISPLAY_HOME_AS_UP);
        actionBar.setCustomView(R.layout.actionbar);

        mOrdboken = Ordboken.getInstance(this);

        if (!mOrdboken.mPrefs.getBoolean("loggedIn", false)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        mProgressBar = (ProgressBar) findViewById(R.id.main_progress);
        mStatusText = (TextView) findViewById(R.id.main_status);
        mRetryButton = (Button) findViewById(R.id.main_retry);

        Intent intent = getIntent();
        if (Intent.ACTION_SEARCH.equals(intent.getAction())
                || Intent.ACTION_VIEW.equals(intent.getAction())) {
            onNewIntent(intent);
        } else {
            restoreLastView();
        }
    }

    private void restoreLastView() {
        Ordboken.Where where = mOrdboken.getLastWhere();
        String what = mOrdboken.getLastWhat();

        if (where == Ordboken.Where.MAIN) {
            doSearch(what);
        } else if (where == Ordboken.Where.WORD) {
            Ordboken.startWordActivity(this, null, what);
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        mOrdboken.setLastView(Ordboken.Where.MAIN, mLastQuery);
        mOrdboken.getPrefsEditor().commit();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        NeSearchResult searchResult = (NeSearchResult) l.getItemAtPosition(position);
        Ordboken.startWordActivity(this, searchResult.mTitle, searchResult.mUrl);
    }

    private class SearchResultAdapter extends ArrayAdapter<NeSearchResult> {
        public SearchResultAdapter(Context context, NeSearchResult[] results) {
            super(context, android.R.layout.simple_list_item_2, android.R.id.text1, results);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            TextView text1 = (TextView) view.findViewById(android.R.id.text1);
            TextView text2 = (TextView) view.findViewById(android.R.id.text2);
            NeSearchResult searchResult = getItem(position);

            text1.setText(searchResult.mTitle);
            text2.setText(searchResult.mSummary);

            return view;
        }
    }

    private class SearchTask extends AsyncTask<String, Void, NeSearchResult[]> {
        @Override
        protected NeSearchResult[] doInBackground(String... params) {
            if (!mOrdboken.isOnline()) {
                return null;
            }

            try {
                return mOrdboken.getNeClient().fetchSearchResults(params[0], 50);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(NeSearchResult[] results) {
            mProgressBar.setVisibility(View.GONE);

            if (results == null) {
                if (!mOrdboken.isOnline()) {
                    mStatusText.setText(R.string.error_offline);
                } else {
                    mStatusText.setText(R.string.error_results);
                }
                mRetryButton.setVisibility(View.VISIBLE);
                return;
            }

            mSeenResults = true;
            mStatusText.setText(R.string.no_results);
            setListAdapter(new SearchResultAdapter(MainActivity.this, results));
        }
    }

    private void doSearch(String query) {
        mProgressBar.setVisibility(View.VISIBLE);
        mStatusText.setText(R.string.loading);
        mRetryButton.setVisibility(View.GONE);
        mLastQuery = query;
        new SearchTask().execute(query);
    }

    public void doSearchAgain(View view) {
        doSearch(mLastQuery);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            doSearch(query);
        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            String url = intent.getDataString();
            String word = intent.getStringExtra(SearchManager.EXTRA_DATA_KEY);
            Ordboken.startWordActivity(this, word, url);

            if (!mSeenResults) {
                finish();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        mOrdboken.initSearchView(this, menu, mLastQuery, false);
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
