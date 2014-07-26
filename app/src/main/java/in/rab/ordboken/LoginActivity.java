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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class LoginActivity extends Activity {
    private Ordboken mOrdboken;
    private UserLoginTask mAuthTask = null;
    private String mEmail;
    private String mPassword;
    private EditText mEmailView;
    private EditText mPasswordView;
    private View mLoginFormView;
    private View mLoginStatusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

        mOrdboken = Ordboken.getInstance(this);

        mEmailView = (EditText) findViewById(R.id.email);
        mEmailView.setText(mOrdboken.mPrefs.getString("username", ""));

        mPasswordView = (EditText) findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.login || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        mLoginFormView = findViewById(R.id.login_form);
        mLoginStatusView = findViewById(R.id.login_status);

        findViewById(R.id.sign_in_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });
    }

    public void attemptLogin() {
        if (mAuthTask != null) {
            return;
        }

        mEmail = mEmailView.getText().toString();
        mPassword = mPasswordView.getText().toString();

        showProgress(true);
        mAuthTask = new UserLoginTask();
        mAuthTask.execute((Void) null);
    }

    private void showProgress(final boolean show) {
        mLoginStatusView.setVisibility(show ? View.VISIBLE : View.GONE);
        mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    public class UserLoginTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                return mOrdboken.getNeClient().login(mEmail, mPassword);
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            mAuthTask = null;

            if (!success) {
                Toast.makeText(getApplicationContext(), R.string.error_login_failed,
                        Toast.LENGTH_SHORT).show();
                showProgress(false);
                mPasswordView.requestFocus();
                return;
            }

            mOrdboken.updateCreds(mEmail, mPassword);

            SharedPreferences.Editor ed = mOrdboken.getPrefsEditor();
            ed.putBoolean("loggedIn", true);
            ed.putString("username", mEmail);
            ed.putString("password", mPassword);
            ed.commit();

            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }
    }
}
