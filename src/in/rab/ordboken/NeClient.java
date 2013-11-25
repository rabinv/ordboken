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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;

public class NeClient {
	private String mRefreshToken;
	private String mAccessToken;
	private String mUsername;
	private String mPassword;
	private long mAccessExpiry;

	public void setUsername(String username) {
		mUsername = username;
	}

	public void setPassword(String password) {
		mPassword = password;
	}

	public String getRefreshToken() {
		return mRefreshToken;
	}

	public void setRefreshToken(String token) {
		mRefreshToken = token;
	}

	public void setAccessToken(String token) {
		mAccessToken = token;
	}

	public class LoginException extends Exception {
		private static final long serialVersionUID = 5697171780819513115L;

		public LoginException(String message) {
			super(message);
		}
	}

	public class ParserException extends Exception {
		private static final long serialVersionUID = 5697171780819513115L;

		public ParserException(String message) {
			super(message);
		}
	}

	public class NeSearchResult {
		public String mTitle;
		public String mSummary;
		public String mUrl;

		public NeSearchResult(String title, String summary, String url) {
			mTitle = title;
			mSummary = summary;
			mUrl = url;
		}

		public NeSearchResult(JSONObject json) throws JSONException {
			mTitle = json.getString("title");
			mSummary = json.getString("summary").replace("\n", "");
			mUrl = "http://api.ne.se" + json.getString("url");
		}
	}

	public class NeWord {
		public final String mTitle;
		public final String mText;
		public final String mSlug;
		public final boolean mHasAudio;

		private String mAudioUrl;

		public String getAudioUrl() {
			return mAudioUrl;
		}

		public void setAudioUrl(String mAudioUrl) {
			this.mAudioUrl = mAudioUrl;
		}

		public NeWord(JSONObject json) throws JSONException {
			mTitle = json.getString("title");
			mSlug = json.getString("slug");
			mText = json.getString("text");

			// Audio is marked by an object data with an asset number we apparently can't
			// do anything with. To actually get the audio, we need to screen scrape the
			// full site. Since there is also no relation between the API urls and the full
			// site's urls, in order to find the full site url for a word with multiple meanings
			// (example, with a slug of "labb-(2)"), we would have to use the full site search
			// feature first which I'm not going to do. Let's just not support audio for those
			// words, and hope that this API is fixed soon.
			if (mText.indexOf("<object data") != -1 && mSlug.indexOf("-(") == -1) {
				mHasAudio = true;
			} else {
				mHasAudio = false;
			}
		}
	}

	public String getAudioUrl(NeWord word) throws LoginException, IOException, ParserException {
		if (word.getAudioUrl() != null) {
			return word.getAudioUrl();
		}

		Uri.Builder uriBuilder = Uri.parse("http://www.ne.se/sve/").buildUpon();
		uriBuilder.appendPath(word.mSlug);

		String page = fetchMainSitePage(uriBuilder.build().toString());
		Pattern regex = Pattern.compile("(neosound_mp3/.*?mp3)");
		Matcher matcher = regex.matcher(page);

		if (!matcher.find()) {
			throw new ParserException("Could not find audio url in page");
		}

		String url = "http://www.ne.se/" + matcher.group(1);
		word.setAudioUrl(url);

		return url;
	}

	private String inputStreamToString(InputStream input) throws IOException {
		Scanner scanner = new Scanner(input).useDelimiter("\\A");
		String str = scanner.hasNext() ? scanner.next() : "";

		input.close();

		return str;
	}

	private void loginMainSite() throws IOException, LoginException {
		ArrayList<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();

		data.add(new BasicNameValuePair("_save_loginForm", "true"));
		data.add(new BasicNameValuePair("redir", "/success"));
		data.add(new BasicNameValuePair("redirFail", "/fail"));
		data.add(new BasicNameValuePair("userName", mUsername));
		data.add(new BasicNameValuePair("passWord", mPassword));

		UrlEncodedFormEntity entity = new UrlEncodedFormEntity(data);

		URL url = new URL("https://www.ne.se/user/login.jsp");
		HttpsURLConnection https = (HttpsURLConnection) url.openConnection();
		https.setInstanceFollowRedirects(false);
		https.setFixedLengthStreamingMode((int) entity.getContentLength());
		https.setDoOutput(true);

		try {
			OutputStream output = https.getOutputStream();
			entity.writeTo(output);
			output.close();

			Integer response = https.getResponseCode();
			if (response != 302) {
				throw new LoginException("Unexpected response: " + response);
			}

			String location = https.getHeaderField("Location");
			if (location.indexOf("/success") == -1) {
				throw new LoginException("Failed to login");
			}
		} finally {
			https.disconnect();
		}
	}

	private String fetchMainSitePage(String pageUrl) throws IOException, LoginException,
			ParserException {
		URL url = new URL(pageUrl);
		HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
		urlConnection.setInstanceFollowRedirects(false);
		urlConnection.connect();

		int response;

		try {
			response = urlConnection.getResponseCode();
		} catch (IOException e) {
			urlConnection.disconnect();
			throw e;
		}

		if (response == 302) {
			urlConnection.disconnect();

			try {
				loginMainSite();
			} catch (EOFException e) {
				// Same EOFException as on token refreshes. Seems to be a POST thing.
				loginMainSite();
			}

			url = new URL(pageUrl);
			urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setInstanceFollowRedirects(false);
			urlConnection.connect();

			try {
				response = urlConnection.getResponseCode();
			} catch (IOException e) {
				urlConnection.disconnect();
				throw e;
			}
		}

		try {
			if (response != 200) {
				throw new ParserException("Unable to get page: " + response);
			}

			return inputStreamToString(urlConnection.getInputStream());
		} finally {
			urlConnection.disconnect();
		}
	}

	public NeSearchResult[] fetchSearchResults(String query) throws ParserException, IOException {
		Uri.Builder uriBuilder = Uri.parse("http://api.ne.se/search").buildUpon();

		uriBuilder.appendQueryParameter("fq", "type:ordbok");
		uriBuilder.appendQueryParameter("q", query);
		uriBuilder.appendQueryParameter("rows", "30");

		try {
			JSONObject json = publicApiRequest(uriBuilder.build().toString());
			JSONObject result = json.getJSONObject("result");

			JSONArray documents = result.getJSONArray("document");
			NeSearchResult[] results = new NeSearchResult[documents.length()];

			for (int i = 0; i < documents.length(); i++) {
				results[i] = new NeSearchResult(documents.getJSONObject(i));
			}

			return results;
		} catch (JSONException e) {
			throw new ParserException(e.getMessage());
		}
	}

	public NeWord fetchWord(String wordUrl) throws LoginException, ParserException, IOException {
		try {
			return new NeWord(privateApiRequest(wordUrl));
		} catch (JSONException e) {
			throw new ParserException(e.getMessage());
		}
	}

	private JSONObject publicApiRequest(String requestUrl) throws IOException, JSONException,
			ParserException {
		URL url = new URL(requestUrl);
		HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
		urlConnection.addRequestProperty("Accept", "application/json");
		urlConnection.connect();

		try {
			if (urlConnection.getResponseCode() != 200) {
				throw new ParserException("Unexpected response: " + urlConnection.getResponseCode());
			}

			return new JSONObject(inputStreamToString(urlConnection.getInputStream()));
		} finally {
			urlConnection.disconnect();
		}
	}

	private JSONObject privateApiRequest(String requestUrl) throws IOException, LoginException,
			JSONException {
		if (System.currentTimeMillis() + 10000 > mAccessExpiry && !authenticate()) {
			throw new LoginException("Login required");
		}

		URL url = new URL(requestUrl);
		HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
		urlConnection.addRequestProperty("Authorization", "Bearer " + mAccessToken);
		urlConnection.addRequestProperty("Accept", "application/json");
		urlConnection.connect();

		int resp;

		try {
			resp = urlConnection.getResponseCode();
		} catch (IOException e) {
			urlConnection.disconnect();
			throw e;
		}

		if (resp == 401) {
			urlConnection.disconnect();

			if (!authenticate()) {
				throw new LoginException("Login required");
			}

			urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.addRequestProperty("Authorization", "Bearer " + mAccessToken);
			urlConnection.addRequestProperty("Accept", "application/json");
			urlConnection.connect();
		}

		try {
			if (urlConnection.getResponseCode() == 401) {
				throw new LoginException("Login required");
			}

			return new JSONObject(inputStreamToString(urlConnection.getInputStream()));
		} finally {
			urlConnection.disconnect();
		}
	}

	private boolean requestToken(ArrayList<BasicNameValuePair> data) throws IOException {
		String page;

		URL url = new URL("https://www.ne.se/oauth/token");
		UrlEncodedFormEntity entity = new UrlEncodedFormEntity(data);

		HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
		urlConnection.addRequestProperty("Authorization", "Basic bWVkaWEtc2VydmljZTo=");
		urlConnection.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		urlConnection.setFixedLengthStreamingMode((int) entity.getContentLength());
		urlConnection.setDoOutput(true);

		try {
			OutputStream output = urlConnection.getOutputStream();
			entity.writeTo(output);
			output.close();

			int response = urlConnection.getResponseCode();
			if (response != 200) {
				return false;
			}

			page = inputStreamToString(urlConnection.getInputStream());
		} finally {
			urlConnection.disconnect();
		}

		try {
			JSONObject json = new JSONObject(page);
			mAccessToken = json.getString("access_token");
			mRefreshToken = json.getString("refresh_token");
			mAccessExpiry = System.currentTimeMillis() + json.getInt("expires_in") * 1000;
			return true;
		} catch (JSONException e) {
			return false;
		}
	}

	private boolean authenticate() throws IOException {
		if (refreshToken()) {
			return true;
		}

		return login(mUsername, mPassword);
	}

	private boolean refreshToken() throws IOException {
		if (mRefreshToken == null) {
			return false;
		}

		ArrayList<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();

		data.add(new BasicNameValuePair("grant_type", "refresh_token"));
		data.add(new BasicNameValuePair("refresh_token", mRefreshToken));

		try {
			return requestToken(data);
		} catch (EOFException e) {
			// For some reason, EOFException is raised on some token refreshes.
			// Needs to be investigated further.
			return requestToken(data);
		}
	}

	public boolean login(String userName, String password) throws IOException {
		ArrayList<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();

		data.add(new BasicNameValuePair("grant_type", "password"));
		data.add(new BasicNameValuePair("scope", "read"));
		data.add(new BasicNameValuePair("username", userName));
		data.add(new BasicNameValuePair("password", password));

		try {
			return requestToken(data);
		} catch (EOFException e) {
			// Never seen it here but doesn't hurt.
			return requestToken(data);
		}
	}
}
