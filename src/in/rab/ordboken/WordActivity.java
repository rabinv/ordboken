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

import in.rab.ordboken.NeClient.LoginException;
import in.rab.ordboken.NeClient.NeWord;
import in.rab.ordboken.NeClient.ParserException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class WordActivity extends Activity {
	private WebView mWebView;
	private Ordboken mOrdboken;
	private NeWord mWord;
	private String mTitle;
	private ProgressBar mProgressBar;
	private TextView mStatusText;
	private LinearLayout mStatusLayout;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mOrdboken = Ordboken.getInstance(this);

		setContentView(R.layout.activity_word);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		mWebView = (WebView) findViewById(R.id.webView);
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

			if (result == null) {
				mProgressBar.setVisibility(View.GONE);
				if (!mOrdboken.isOnline()) {
					mStatusText.setText(R.string.error_offline);
				} else {
					mStatusText.setText(R.string.error_word);
				}
				return;
			}

			String header = "<style>" + "p.headword { display: none; }"
					+ "object { display: none; }" + ".neopetit { font-size: 90%; }"
					+ ".neokap { font-size: 90%; }" + "p { margin: 0; }" + "a.sound { "
					+ "	text-decoration: none;" + "	width: 16px; height: 16px;"
					+ "	display: inline-block;"
					+ "	background-image: url('file:///android_asset/audio.png');" + "}"
					+ "</style>";
			String text = result.mText;

			if (result.mHasAudio) {
				text = text
						.replace("</object>", "</object><a class='sound' href='/playAudio'></a>");
			}

			mWebView.loadDataWithBaseURL("http://api.ne.se/", header + text, "text/html", "UTF-8",
					null);
			setTitle(result.mTitle);
			mStatusLayout.setVisibility(View.GONE);
			mWebView.setVisibility(View.VISIBLE);
		}
	}

	private class AudioTask extends AsyncTask<Void, Void, String> {
		private String mError;

		@Override
		protected String doInBackground(Void... params) {
			try {
				mOrdboken.initCookies();
				return mOrdboken.getNeClient().getAudioUrl(mWord);
			} catch (LoginException e) {
				mError = e.getMessage();
			} catch (IOException e) {
				mError = e.getMessage();
			} catch (ParserException e) {
				mError = e.getMessage();
			}
			return null;
		}

		@Override
		protected void onPostExecute(String audioUrl) {
			if (audioUrl == null) {
				Toast.makeText(getApplicationContext(), mError, Toast.LENGTH_SHORT).show();
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

	@Override
	protected void onPause() {
		super.onPause();
		mOrdboken.getPrefsEditor().commit();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return mOrdboken.initSearchView(this, menu, mTitle, false);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (mOrdboken.onOptionsItemSelected(this, item)) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
