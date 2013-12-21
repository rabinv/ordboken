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

import in.rab.ordboken.NeClient.NeWord;

import java.net.URI;
import java.net.URISyntaxException;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ShareActionProvider;
import android.widget.TextView;
import android.widget.Toast;

public class WordActivity extends Activity {
	private static final String FLASHCARD_ACTION = "org.openintents.action.CREATE_FLASHCARD";
	private static final String CSS = "<style>" // formatter hack
			+ "p.headword { display: none; }"
			+ "object { display: none; }"
			+ ".neopetit { font-size: 90%; }" //
			+ ".neokap { font-size: 90%; }" //
			+ "p { margin: 0; }"
			+ "a.sound { "
			+ "	text-decoration: none;"
			+ "	width: 16px; height: 16px;" //
			+ "	display: inline-block;"
			+ "	background-image: url('file:///android_asset/audio.png');" //
			+ "}" + "</style>";
	private WebView mWebView;
	private Ordboken mOrdboken;
	private NeWord mWord;
	private String mTitle;
	private ProgressBar mProgressBar;
	private TextView mStatusText;
	private LinearLayout mStatusLayout;
	private ShareActionProvider mShareActionProvider;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mOrdboken = Ordboken.getInstance(this);

		setContentView(R.layout.activity_word);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		mWebView = (WebView) findViewById(R.id.webView);
		WebSettings settings = mWebView.getSettings();

		settings.setBuiltInZoomControls(true);
		settings.setDisplayZoomControls(false);

		mWebView.setInitialScale(mOrdboken.mPrefs.getInt("scale", 100));
		mWebView.setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				if (url.indexOf("/playAudio") != -1) {
					new AudioTask().execute();
				} else {
					Intent intent = new Intent(WordActivity.this, WordActivity.class);
					intent.putExtra("url", url);
					startActivity(intent);
				}

				return true;
			}
		});

		mProgressBar = (ProgressBar) findViewById(R.id.word_progress);
		mStatusText = (TextView) findViewById(R.id.word_status);
		mStatusLayout = (LinearLayout) findViewById(R.id.word_status_layout);

		Intent intent = getIntent();

		mTitle = intent.getStringExtra("title");
		if (mTitle != null) {
			setTitle(mTitle);
		}

		try {
			String url = new URI(intent.getStringExtra("url")).toASCIIString();
			new WordTask().execute(url);
		} catch (URISyntaxException e) {
			finish();
		}
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
			mOrdboken.setLastWord(mWord);

			if (result == null) {
				mProgressBar.setVisibility(View.GONE);
				if (!mOrdboken.isOnline()) {
					mStatusText.setText(R.string.error_offline);
				} else {
					mStatusText.setText(R.string.error_word);
				}
				return;
			}

			String text = result.mText;

			if (result.mHasAudio) {
				text = text
						.replace("</object>", "</object><a class='sound' href='/playAudio'></a>");
			}

			mWebView.loadDataWithBaseURL("http://api.ne.se/", CSS + text, "text/html", "UTF-8",
					null);
			setTitle(result.mTitle);
			mStatusLayout.setVisibility(View.GONE);
			mWebView.setVisibility(View.VISIBLE);
			updateShareIntent();
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

		@Override
		protected void onPostExecute(String audioUrl) {
			if (audioUrl == null) {
				Toast.makeText(getApplicationContext(), R.string.error_audio, Toast.LENGTH_SHORT)
						.show();
				return;
			}

			MediaPlayer mediaPlayer = new MediaPlayer();
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

			try {
				mediaPlayer.setDataSource(audioUrl);
			} catch (Exception e) {
				Toast.makeText(getApplicationContext(), R.string.error_audio, Toast.LENGTH_SHORT)
						.show();
				return;
			}

			mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
				@Override
				public void onPrepared(MediaPlayer mp) {
					mp.start();
				}
			});

			mediaPlayer.prepareAsync();
		}
	}

	private void updateShareIntent() {
		if (mShareActionProvider == null) {
			return;
		}

		if (mWord != null) {
			Intent shareIntent = new Intent(FLASHCARD_ACTION);
			shareIntent.putExtra("SOURCE_TEXT", mWord.mTitle);
			shareIntent.putExtra("TARGET_TEXT", CSS + mWord.mText);
			mShareActionProvider.setShareIntent(shareIntent);
		} else {
			mShareActionProvider.setShareIntent(null);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		mOrdboken.setLastWord(mWord);
		updateShareIntent();

	}

	@Override
	protected void onPause() {
		super.onPause();
		overridePendingTransition(0, 0);

		mOrdboken.setLastWord(null);
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
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);

		if (getPackageManager().queryIntentActivities(new Intent(FLASHCARD_ACTION), 0).size() > 0) {
			MenuItem shareItem = menu.findItem(R.id.menu_share);
			shareItem.setVisible(true);
			mShareActionProvider = (ShareActionProvider) shareItem.getActionProvider();
		}

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
