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

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;

import in.rab.ordboken.NeClient.NeSearchResult;
import in.rab.ordboken.NeClient.NeWord;

public class NeSuggestionProvider extends ContentProvider {

    @Override
    public int delete(Uri arg0, String arg1, String[] arg2) {
        return 0;
    }

    @Override
    public String getType(Uri arg0) {
        return null;
    }

    @Override
    public Uri insert(Uri arg0, ContentValues arg1) {
        return null;
    }

    @Override
    public boolean onCreate() {
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        String[] columns = {BaseColumns._ID, SearchManager.SUGGEST_COLUMN_TEXT_1,
                SearchManager.SUGGEST_COLUMN_TEXT_2, SearchManager.SUGGEST_COLUMN_INTENT_DATA,
                SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA};
        MatrixCursor cursor = new MatrixCursor(columns);
        String q = uri.getLastPathSegment();

        if (q.equals(SearchManager.SUGGEST_URI_PATH_QUERY)) {
            NeWord lastWord = Ordboken.getInstance(getContext()).getCurrentWord();

            if (lastWord != null) {
                cursor.addRow(new Object[]{0, lastWord.mTitle, "", lastWord.mUrl, lastWord.mTitle});
            }
            return cursor;
        }

        try {
            NeSearchResult[] results = Ordboken.getInstance(getContext()).getNeClient()
                    .fetchSearchResults(q, 10);

            for (int i = 0; i < results.length; i++) {
                cursor.addRow(new Object[]{i, results[i].mTitle, results[i].mSummary,
                        results[i].mUrl, results[i].mTitle});
            }
        } catch (Exception e) {
            // Ignore all exceptions, this is just for the search suggestions.
        }

        return cursor;
    }

    @Override
    public int update(Uri arg0, ContentValues arg1, String arg2, String[] arg3) {
        return 0;
    }
}
