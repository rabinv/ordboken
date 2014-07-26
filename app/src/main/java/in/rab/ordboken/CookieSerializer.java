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

import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class CookieSerializer {
	private final CookieStore mCookieStore;
	private final String mDomain;

	public CookieSerializer(CookieStore cookieStore, String domain) {
		mCookieStore = cookieStore;
		mDomain = domain;
	}

	public void loadFromString(String json) {
		try {
			JSONObject obj = new JSONObject(json);
			JSONArray cookieStrings = obj.getJSONArray("cookies");
			URI uri = new URI("http://" + mDomain);

			for (int i = 0; i < cookieStrings.length(); i++) {
				String cookieString = cookieStrings.getString(i);
				HttpCookie cookie = HttpCookie.parse(cookieString).get(0);

				cookie.setPath("/");
				cookie.setDomain(mDomain);

				mCookieStore.add(uri, cookie);
			}

		} catch (Exception e) {
			return;
		}
	}

	public String saveToString() {
		try {
			URI uri = new URI("http://" + mDomain);
			List<HttpCookie> cookies = mCookieStore.get(uri);
			JSONObject obj = new JSONObject();
			JSONArray cookieStrings = new JSONArray();

			for (HttpCookie cookie : cookies) {
				cookieStrings.put(cookie.toString());
			}

			obj.put("cookies", cookieStrings);
			return obj.toString();
		} catch (Exception e) {
			return null;
		}
	}
}
