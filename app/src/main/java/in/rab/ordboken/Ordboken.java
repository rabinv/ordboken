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
import android.app.Activity;
import android.app.SearchManager;
import android.app.TaskStackBuilder;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.http.HttpResponseCache;
import android.support.v4.app.NavUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SearchView;

import java.io.File;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;

import in.rab.ordboken.NeClient.NeWord;

public class Ordboken {
    private static Ordboken sInstance = null;
    private CookieSerializer mCookieSerializer;
    private final NeClient mNeClient;
    private final Context mContext;
    private final ConnectivityManager mConnMgr;
    public final SharedPreferences mPrefs;
    private NeWord mCurrentWord;
    private Where mLastWhere;
    private String mLastWhat;

    public enum Where {
        MAIN, WORD
    }

    private Ordboken(Context context) {
        mContext = context;

        try {
            // Ends up only fully caching the search results. Documents use ETag
            // and are conditionally cached.
            HttpResponseCache.install(new File(context.getCacheDir(), "ordboken"), 1024 * 1024);
        } catch (IOException e) {
            // Oh well...
        }

        mPrefs = context.getSharedPreferences("ordboken", Context.MODE_PRIVATE);
        mLastWhere = Where.valueOf(mPrefs.getString("lastWhere", Where.MAIN.toString()));
        mLastWhat = mPrefs.getString("lastWhat", "ordbok");

        mConnMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        mNeClient = new NeClient(NeClient.Auth.BASIC);
        mNeClient.setPersistentAuthData(mPrefs.getString("persistentAuthData", null));
        mNeClient.setUsername(mPrefs.getString("username", null));
        mNeClient.setPassword(mPrefs.getString("password", null));
    }

    public boolean isOnline() {
        NetworkInfo networkInfo = mConnMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    public NeClient getNeClient() {
        return mNeClient;
    }

    public static Ordboken getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new Ordboken(context);
        }

        return sInstance;
    }

    public void initCookies() {
        if (mCookieSerializer != null) {
            return;
        }

        CookieManager cookieManager = new CookieManager();
        CookieHandler.setDefault(cookieManager);

        mCookieSerializer = new CookieSerializer(cookieManager.getCookieStore(), "www.ne.se");
        mCookieSerializer.loadFromString(mPrefs.getString("cookies", null));
    }

    public void updateCreds(String username, String password) {
        mNeClient.setUsername(username);
        mNeClient.setPassword(password);
    }

    // Caller does the commit
    @SuppressLint("CommitPrefEdits")
    public SharedPreferences.Editor getPrefsEditor() {
        SharedPreferences.Editor ed = mPrefs.edit();

        ed.putString("lastWhere", mLastWhere.toString());
        ed.putString("lastWhat", mLastWhat);
        ed.putString("persistentAuthData", mNeClient.getPersistentAuthData());
        if (mCookieSerializer != null) {
            ed.putString("cookies", mCookieSerializer.saveToString());
        }

        return ed;
    }

    public SearchView initSearchView(Activity activity, Menu menu, String query, Boolean focus) {
        SearchManager searchManager = (SearchManager) activity
                .getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) activity.findViewById(R.id.mySearchView);

        searchView.setSearchableInfo(searchManager.getSearchableInfo(new ComponentName(activity,
                MainActivity.class)));

        // Hack to get the magnifying glass icon inside the EditText
        searchView.setIconifiedByDefault(true);
        searchView.setIconified(false);

        // Hack to get rid of the collapse button
        searchView.onActionViewExpanded();

        if (!focus) {
            searchView.clearFocus();
        }

        // searchView.setSubmitButtonEnabled(true);
        searchView.setQueryRefinementEnabled(true);

        if (query != null) {
            searchView.setQuery(query, false);
        }

        return searchView;
    }

    public boolean onOptionsItemSelected(Activity activity, MenuItem item) {
        if (item.getItemId() == R.id.menu_logout) {
            updateCreds("", "");
            mNeClient.logout();

            SharedPreferences.Editor ed = mPrefs.edit();
            ed.putBoolean("loggedIn", false);
            ed.putString("username", "");
            ed.putString("password", "");
            ed.commit();

            Intent intent = new Intent(mContext, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);

            activity.finish();

            return true;
        } else if (item.getItemId() == android.R.id.home) {
            Intent upIntent = NavUtils.getParentActivityIntent(activity);
            if (NavUtils.shouldUpRecreateTask(activity, upIntent)) {
                TaskStackBuilder.create(activity)
                        .addNextIntentWithParentStack(upIntent)
                        .startActivities();
            } else {
                NavUtils.navigateUpFromSameTask(activity);
            }
            return true;
        }

        return false;
    }

    static void startWordActivity(Activity activity, String word, String url) {
        Intent intent = new Intent(activity, WordActivity.class);

        intent.putExtra("title", word);
        intent.putExtra("url", url);

        intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        activity.startActivity(intent);
    }

    public NeWord getCurrentWord() {
        return mCurrentWord;
    }

    public void setCurrentWord(NeWord word) {
        mCurrentWord = word;
    }

    public void setLastView(Where where, String what) {
        mLastWhere = where;
        mLastWhat = what;
    }

    public Where getLastWhere() {
        return mLastWhere;
    }

    public String getLastWhat() {
        return mLastWhat;
    }
}
