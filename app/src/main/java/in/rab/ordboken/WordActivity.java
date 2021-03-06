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

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.ShareActionProvider;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;

import in.rab.ordboken.NeClient.NeWord;
import in.rab.ordboken.OrdbokenContract.FavoritesEntry;
import in.rab.ordboken.OrdbokenContract.HistoryEntry;

public class WordActivity extends Activity {
    private WebView mWebView;
    private Ordboken mOrdboken;
    private NeWord mWord;
    private String mUrl;
    private ProgressBar mProgressBar;
    private TextView mStatusText;
    private LinearLayout mStatusLayout;
    private Button mRetryButton;
    private ShareActionProvider mShareActionProvider;
    private SearchView mSearchView;
    private boolean mStarred;
    private boolean mGotStarred;
    private boolean mPageFinished;

    @Override
    @SuppressLint("AddJavascriptInterface")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mOrdboken = Ordboken.getInstance(this);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_word);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME
                | ActionBar.DISPLAY_HOME_AS_UP);
        actionBar.setCustomView(R.layout.actionbar);

        mWebView = (WebView) findViewById(R.id.webView);
        mWebView.setWebChromeClient(new WebChromeClient());
        WebSettings settings = mWebView.getSettings();

        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptEnabled(true);

        mWebView.setInitialScale(mOrdboken.mPrefs.getInt("scale", 0));
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.contains("/playAudio")) {
                    setProgressBarIndeterminateVisibility(true);
                    new AudioTask().execute();
                } else if (url.contains("/search/")) {
                    String word = url.substring(url.indexOf("ch/") + 3);

                    try {
                        mSearchView.setQuery(URLDecoder.decode(word, "UTF-8"), false);
                        mSearchView.setIconified(false);
                        mSearchView.requestFocusFromTouch();
                    } catch (UnsupportedEncodingException e) {
                        // Should not happen.
                    }

                    return true;
                } else {
                    Intent intent = new Intent(WordActivity.this, WordActivity.class);
                    intent.putExtra("url", url);
                    startActivity(intent);
                }

                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                mPageFinished = true;
                updateStar();
            }
        });

        class OrdbokenJsObject {
            @SuppressWarnings("unused")
            @JavascriptInterface
            public void toggleStar() {
                new StarToggleTask().execute();
            }

            @SuppressWarnings("unused")
            @JavascriptInterface
            public void loaded() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mStatusLayout.setVisibility(View.GONE);
                    }
                });
            }
        }
        mWebView.addJavascriptInterface(new OrdbokenJsObject(), "ordboken");

        mStarred = false;
        mProgressBar = (ProgressBar) findViewById(R.id.word_progress);
        mStatusText = (TextView) findViewById(R.id.word_status);
        mStatusLayout = (LinearLayout) findViewById(R.id.word_status_layout);
        mRetryButton = (Button) findViewById(R.id.word_retry);

        Intent intent = getIntent();

        String title = intent.getStringExtra("title");
        if (title != null) {
            setTitle(title);
        }

        String url = intent.getStringExtra("url");
        if (url == null) {
            return;
        }

        try {
            mUrl = new URI(intent.getStringExtra("url")).toASCIIString();
        } catch (URISyntaxException e) {
            finish();
            return;
        }

        fetchWord(null);
    }

    public void fetchWord(View view) {
        mProgressBar.setVisibility(View.VISIBLE);
        mStatusText.setText(R.string.loading);
        mRetryButton.setVisibility(View.GONE);
        new WordTask().execute(mUrl);
    }

    private void loadWebView(NeWord word) {
        String text = word.mText;
        final String head = "<head>"
                      + "<link rel='stylesheet' type='text/css' href='file:///android_asset/word.css'>"
                      + "<script src='file:///android_asset/jquery.min.js'></script>"
                      + "<script src='file:///android_asset/word.js'></script>";
        StringBuilder builder = new StringBuilder(head);

        if (word.mHasAudio) {
            text = text.replace("</object>", "</object><a class='sound' href='/playAudio'></a>");
        }

        builder.append("<title>").append(word.mTitle).append("</title>");
        builder.append("</head>");
        builder.append("<div id='bookmark'>✹</div>");
        builder.append(text);

        ArrayList<NeClient.NeSearchResult> relations = word.mRelations;
        if (relations.size() > 0) {
            builder.append("<p class='rel'><span class='neokap'>REL?:</span><span class='neopetit'> ");

            String seperator = "";
            for (NeClient.NeSearchResult relation : relations) {
                builder.append(seperator)
                        .append("<a href='")
                        .append(relation.mUrl)
                        .append("'>")
                        .append(relation.mTitle)
                        .append("</a>");
                seperator = ", ";
            }

            builder.append("</span></p>");
        }

        mWebView.loadDataWithBaseURL("http://api.ne.se/", builder.toString(),
                "text/html", "UTF-8", null);
    }

    private void updateStar() {
        Integer on = mStarred ? 1 : 0;

        if (!mGotStarred || !mPageFinished) {
            return;
        }

        mWebView.loadUrl("javascript:setStar(" + on.toString() + ")");
    }

    private class WordTask extends AsyncTask<String, Void, NeWord> {
        @Override
        protected NeWord doInBackground(String... params) {
            if (!mOrdboken.isOnline()) {
                return null;
            }

            try {
                return mOrdboken.getNeClient().fetchWord(params[0]);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(NeWord result) {
            mWord = result;
            mOrdboken.setCurrentWord(mWord);

            if (result == null) {
                mProgressBar.setVisibility(View.GONE);
                if (!mOrdboken.isOnline()) {
                    mStatusText.setText(R.string.error_offline);
                } else {
                    mStatusText.setText(R.string.error_word);
                }

                mRetryButton.setVisibility(View.VISIBLE);
                return;
            }

            loadWebView(result);
            setTitle(result.mTitle);
            updateShareIntent();

            new StarUpdateTask().execute();
            new HistorySaveTask().execute();
        }
    }

    private class AudioTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... params) {
            try {
                mOrdboken.initCookies();
                return mOrdboken.getNeClient().getAudioUrl(mWord);
            } catch (Exception e) {
                return null;
            }
        }

        private void showError() {
            setProgressBarIndeterminateVisibility(false);
            Toast.makeText(getApplicationContext(), R.string.error_audio, Toast.LENGTH_SHORT)
                    .show();
        }

        @Override
        protected void onPostExecute(String audioUrl) {
            if (audioUrl == null) {
                showError();
                return;
            }

            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

            try {
                mediaPlayer.setDataSource(audioUrl);
            } catch (Exception e) {
                showError();
                return;
            }

            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    setProgressBarIndeterminateVisibility(false);
                    mp.start();
                }
            });

            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    showError();
                    return false;
                }
            });

            mediaPlayer.prepareAsync();
        }
    }


    private class HistorySaveTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            OrdbokenDbHelper dbHelper = new OrdbokenDbHelper(WordActivity.this);
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();

            values.put(HistoryEntry.COLUMN_NAME_TITLE, mWord.mTitle);
            values.put(HistoryEntry.COLUMN_NAME_SUMMARY, mWord.getSummary());
            values.put(HistoryEntry.COLUMN_NAME_URL, mWord.mUrl);
            values.put(HistoryEntry.COLUMN_NAME_DATE, new Date().getTime());

            db.insert(HistoryEntry.TABLE_NAME, "null", values);
            db.close();

            return null;
        }
    }

    private abstract class StarTask extends AsyncTask<Void, Void, Boolean> {
        protected SQLiteDatabase getDb() {
            OrdbokenDbHelper dbHelper = new OrdbokenDbHelper(WordActivity.this);
            return dbHelper.getWritableDatabase();
        }

        protected boolean isStarred(SQLiteDatabase db) {
            Cursor cursor = db.query(FavoritesEntry.TABLE_NAME, null,
                    FavoritesEntry.COLUMN_NAME_URL + "=?",
                    new String[]{mWord.mUrl}, null, null, null, "1");
            int count = cursor.getCount();

            cursor.close();
            return count > 0;
        }

        @Override
        protected void onPostExecute(Boolean starred) {
            mStarred = starred;
            mGotStarred = true;
            updateStar();
            invalidateOptionsMenu();
        }
    }

    private class StarUpdateTask extends StarTask {
        @Override
        protected Boolean doInBackground(Void... params) {
            SQLiteDatabase db = getDb();
            boolean starred = isStarred(db);

            db.close();

            return starred;
        }
    }

    private class StarToggleTask extends StarTask {
        @Override
        protected Boolean doInBackground(Void... params) {
            SQLiteDatabase db = getDb();
            boolean starred = isStarred(db);

            if (starred) {
                db.delete(FavoritesEntry.TABLE_NAME,
                        FavoritesEntry.COLUMN_NAME_URL + "=?",
                        new String[]{mWord.mUrl});
            } else {
                ContentValues values = new ContentValues();

                values.put(FavoritesEntry.COLUMN_NAME_TITLE, mWord.mTitle);
                values.put(HistoryEntry.COLUMN_NAME_SUMMARY, mWord.getSummary());
                values.put(FavoritesEntry.COLUMN_NAME_URL, mWord.mUrl);

                db.insert(FavoritesEntry.TABLE_NAME, "null", values);
            }

            db.close();

            return !starred;
        }
    }

    private String guessPos(String text) {
        // https://spraakbanken.gu.se/swe/forskning/saldo/taggm%C3%A4ngd

        if (text.contains("subst.")) {
            return "nn";
        } else if (text.contains("adj.")) {
            return "av";
        } else if (text.contains("verb")) {
            return "vb";
        } else if (text.contains("adv.")) {
            return "ab";
        } else if (text.contains("prep.")) {
            return "pp";
        } else if (text.contains("interj.")) {
            return "in";
        } else if (text.contains("pron.")) {
            return "pn";
        } else if (text.contains("konj.")) {
            return "kn";
        } else {
            return null;
        }
    }

    private void updateShareIntent() {
        if (mShareActionProvider == null) {
            return;
        }

        if (mWord != null) {
            Intent shareIntent = new Intent();
            StringBuilder builder = new StringBuilder();

            shareIntent.setAction(Intent.ACTION_SEND);

            builder.append("<style>").append(mOrdboken.getCSS()).append("</style>");
            builder.append(mWord.mText);

            String plainText = Html.fromHtml(mWord.mText).toString();
            String pos = guessPos(plainText);

            shareIntent.putExtra(Intent.EXTRA_SUBJECT, mWord.mTitle);
            shareIntent.putExtra(Intent.EXTRA_TEXT, plainText);
            shareIntent.putExtra(Intent.EXTRA_HTML_TEXT, builder.toString());
            if (pos != null) {
                shareIntent.putExtra("in.rab.extra.pos", pos);
            }
            shareIntent.setType("text/plain");
            mShareActionProvider.setShareIntent(shareIntent);
        } else {
            mShareActionProvider.setShareIntent(null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mOrdboken.setCurrentWord(mWord);
        updateShareIntent();

        if (mWord != null) {
            new StarUpdateTask().execute();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        overridePendingTransition(0, 0);

        mOrdboken.setCurrentWord(null);
        if (mWord != null) {
            mOrdboken.setLastView(Ordboken.Where.WORD, mWord.mUrl);
        }

        SharedPreferences.Editor ed = mOrdboken.getPrefsEditor();

        // If the WebView was not made visible, getScale() does not
        // return the initalScale, but the default one.
        if (mWebView.getVisibility() == View.VISIBLE) {
            // getScale() is supposed to be deprecated, but its replacement
            // onScaleChanged() doesn't get called when zooming using pinch.
            @SuppressWarnings("deprecation")
            int scale = (int) (mWebView.getScale() * 100);

            ed.putInt("scale", scale);
        }

        ed.commit();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem star = menu.findItem(R.id.menu_star);

        if (mStarred) {
            star.setIcon(R.drawable.ic_action_important);
            star.setTitle(getString(R.string.remove_bookmark));
        } else {
            star.setIcon(R.drawable.ic_action_not_important);
            star.setTitle(getString(R.string.add_bookmark));
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.word, menu);

        MenuItem shareItem = menu.findItem(R.id.menu_share);
        shareItem.setVisible(true);
        mShareActionProvider = (ShareActionProvider) shareItem.getActionProvider();

        mSearchView = mOrdboken.initSearchView(this, menu, null, false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mOrdboken.onOptionsItemSelected(this, item)) {
            return true;
        }

        if (item.getItemId() == R.id.menu_resetzoom) {
            mWebView.setInitialScale(0);
            if (mWord != null) {
                loadWebView(mWord);
            }
        } else if (item.getItemId() == R.id.menu_star) {
            new StarToggleTask().execute();
        }

        return super.onOptionsItemSelected(item);
    }
}
