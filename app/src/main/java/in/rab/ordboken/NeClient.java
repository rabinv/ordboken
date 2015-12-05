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

import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;
import android.text.Html;
import android.util.Base64;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

public class NeClient {
    private String mUsername;
    private String mPassword;
    private final Authenticator authenticator;

    public enum Auth {
        OAUTH2, BASIC
    }

    public NeClient(Auth auth) {
        if (auth == Auth.OAUTH2) {
            authenticator = new Oauth2Authenticator();
        } else {
            authenticator = new BasicAuthenticator();
        }
    }

    public void setUsername(String username) {
        mUsername = username;
    }

    public void setPassword(String password) {
        mPassword = password;
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

        public NeSearchResult(String title, String url) {
            mTitle = title;
            mSummary = "";
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
        public final String mUrl;
        public final ArrayList<NeSearchResult> mRelations;
        public final boolean mHasAudio;

        private String mAudioUrl;

        public String getAudioUrl() {
            return mAudioUrl;
        }

        public void setAudioUrl(String mAudioUrl) {
            this.mAudioUrl = mAudioUrl;
        }

        public String getSummary() {
            String clean = Html.fromHtml((mText))
                    .toString()
                    .replace(mTitle, "")
                    .trim();

            return clean.substring(0, Math.min(100, clean.length()))
                    .split("\n", 2)[0]
                    .trim();
        }

        private String getSelfUrl(JSONObject json, String url) {
            try {
                JSONArray relations = json.getJSONArray("relations");
                int num = relations.length();

                for (int i = 0; i < num; i++) {
                    JSONObject rel = relations.getJSONObject(i);

                    if (rel.getString("rel").equals("self")) {
                        return Uri.parse(url)
                                .buildUpon()
                                .path(rel.getString("url"))
                                .toString();
                    }
                }
            } catch (JSONException e) {
                // Oh well
            }

            return url;
        }

        private ArrayList<NeSearchResult> getRelatedWords(JSONObject json) {
            ArrayList<NeSearchResult> results = new ArrayList<NeSearchResult>();

            try {
                JSONArray relations = json.getJSONArray("relations");
                int num = relations.length();

                for (int i = 0; i < num; i++) {
                    JSONObject rel = relations.getJSONObject(i);

                    if (!rel.getString("rel").equals("related") ||
                            !rel.getString("type").equals("ordbok")) {
                        continue;
                    }

                    results.add(new NeSearchResult(rel.getString("value"),
                            Uri.parse(mUrl)
                                    .buildUpon()
                                    .path(rel.getString("url"))
                                    .toString()));
                }
            } catch (JSONException e) {
                // Oh well
            }

            return results;
        }

        public NeWord(String url, JSONObject json) throws JSONException {
            mTitle = json.getString("title");
            mSlug = json.getString("slug");
            mText = json.getString("text");

            // Try to get the canonical URL to prevent duplicates in history
            mUrl = getSelfUrl(json, url);
            mRelations = getRelatedWords(json);

            // Audio is marked by an object data with an asset number we apparently can't
            // do anything with. To actually get the audio, we need to screen scrape the
            // full site.
            mHasAudio = mText.contains("<object data");
        }
    }

    public String getAudioUrl(NeWord word) throws LoginException, IOException, ParserException {
        if (word.getAudioUrl() != null) {
            return word.getAudioUrl();
        }

        Uri.Builder uriBuilder = Uri.parse("https://www.ne.se/uppslagsverk/ordbok/svensk/").buildUpon();
        uriBuilder.appendPath(word.mSlug);

        String page = fetchMainSitePage(uriBuilder.build().toString());
        Pattern regex = Pattern.compile("(http://assets.*?mp3)");
        Matcher matcher = regex.matcher(page);

        if (!matcher.find()) {
            throw new ParserException("Could not find audio url in page");
        }

        String url = matcher.group(1);
        word.setAudioUrl(url);

        return url;
    }

    private void loginMainSite() throws IOException, LoginException {
        ArrayList<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();

        data.add(new BasicNameValuePair("remember_me", "true"));
        data.add(new BasicNameValuePair("username", mUsername));
        data.add(new BasicNameValuePair("password", mPassword));

        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(data);

        URL url = new URL("https://auth.ne.se/login");
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

            // http://auth.ne.se/login?failed=true on failure
            // http://auth.ne.se/ on success
            String location = https.getHeaderField("Location");
            if (location.contains("fail")) {
                throw new LoginException("Failed to login");
            }
        } finally {
            https.disconnect();
        }
    }

    private String fetchMainSitePage(String pageUrl) throws IOException, LoginException,
            ParserException {
        URL url = new URL(pageUrl);
        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
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
            urlConnection = (HttpsURLConnection) url.openConnection();
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

            return Utils.inputStreamToString(urlConnection.getInputStream());
        } finally {
            urlConnection.disconnect();
        }
    }

    public NeSearchResult[] fetchSearchResults(String query, int count) throws ParserException,
            IOException {
        Uri.Builder uriBuilder = Uri.parse("http://api.ne.se/search").buildUpon();

        uriBuilder.appendQueryParameter("fq", "type:ordbok");
        uriBuilder.appendQueryParameter("q", query);
        uriBuilder.appendQueryParameter("rows", Integer.toString(count));

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
            return new NeWord(wordUrl, privateApiRequest(wordUrl));
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
                throw new ParserException(
                        "Unexpected response: " + urlConnection.getResponseCode());
            }

            return new JSONObject(Utils.inputStreamToString(urlConnection.getInputStream()));
        } finally {
            urlConnection.disconnect();
        }
    }

    private JSONObject privateApiRequest(String requestUrl) throws IOException, LoginException,
            JSONException {
        return authenticator.fetch(requestUrl);
    }

    public boolean login(String userName, String password) throws IOException {
        return authenticator.login(userName, password);
    }

    public void logout() {
        authenticator.logout();
    }

    public String getPersistentAuthData() {
        return authenticator.getPersistentAuthData();
    }

    public void setPersistentAuthData(String data) {
        authenticator.setPersistentAuthData(data);
    }

    private abstract class Authenticator {
        public String getPersistentAuthData() {
            return null;
        }

        public void setPersistentAuthData(String data) {
        }

        abstract boolean login(String userName, String password) throws IOException;

        abstract void logout();

        abstract JSONObject fetch(String requestUrl) throws IOException, LoginException,
                JSONException;
    }

    private class BasicAuthenticator extends Authenticator {
        private String mAuthorization;

        private String makeAuthorization(String userName, String password) {
            String cred = userName + ':' + password;
            String hash = Base64.encodeToString(cred.getBytes(), Base64.DEFAULT);
            return "Basic " + hash;
        }

        @Override
        boolean login(String userName, String password) throws IOException {
            mAuthorization = makeAuthorization(userName, password);

            try {
                fetch("http://api.ne.se/ordbok/svensk/ordbok");
                return true;
            } catch (LoginException e) {
                return false;
            } catch (JSONException e) {
                return false;
            }
        }

        @Override
        void logout() {
            mAuthorization = null;
        }

        @Override
        JSONObject fetch(String requestUrl) throws IOException, LoginException, JSONException {
            if (mAuthorization == null) {
                if (mUsername != null && mPassword != null) {
                    mAuthorization = makeAuthorization(mUsername, mPassword);
                } else {
                    throw new LoginException("Login required");
                }
            }

            URL url = new URL(requestUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.addRequestProperty("Authorization", mAuthorization);
            urlConnection.addRequestProperty("Accept", "application/json");
            urlConnection.connect();

            try {
                if (urlConnection.getResponseCode() == 401) {
                    throw new LoginException("Login failed");
                }

                return new JSONObject(Utils.inputStreamToString(urlConnection.getInputStream()));
            } finally {
                urlConnection.disconnect();
            }
        }
    }

    private class Oauth2Authenticator extends Authenticator {
        private long mAccessExpiry;
        private String mAccessToken;
        private String mRefreshToken;

        @Override
        public String getPersistentAuthData() {
            return mRefreshToken;
        }

        @Override
        public void setPersistentAuthData(String data) {
            mRefreshToken = data;
        }

        @Override
        public JSONObject fetch(String requestUrl) throws IOException, LoginException,
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

                return new JSONObject(Utils.inputStreamToString(urlConnection.getInputStream()));
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

                page = Utils.inputStreamToString(urlConnection.getInputStream());
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

        @Override
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

        @Override
        public void logout() {
            mRefreshToken = null;
            mAccessToken = null;
        }
    }
}
