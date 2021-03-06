/*
 * Copyright 2014 Uwe Trottmann
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

package com.battlelancer.seriesguide.getglueapi;

import android.app.ActionBar;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.text.format.DateUtils;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import com.battlelancer.seriesguide.BuildConfig;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.settings.GetGlueSettings;
import com.battlelancer.seriesguide.ui.BaseActivity;
import com.uwetrottmann.getglue.GetGlue;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthAccessTokenResponse;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import timber.log.Timber;

/**
 * Starts an OAuth 2.0 authentication flow via an {@link android.webkit.WebView}.
 */
public class GetGlueAuthActivity extends BaseActivity {

    static final String TAG = "GetGlueAuthActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // webview uses a progress bar
        requestWindowFeature(Window.FEATURE_PROGRESS);

        super.onCreate(savedInstanceState);

        WebView webview = new WebView(this);
        setContentView(webview);

        final ActionBar actionBar = getActionBar();
        actionBar.setTitle(getString(R.string.getglue_authentication));
        actionBar.setDisplayShowTitleEnabled(true);

        setProgressBarVisibility(true);

        final FragmentActivity activity = this;
        webview.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
                /*
                 * Activities and WebViews measure progress with different
                 * scales. The progress meter will automatically disappear when
                 * we reach 100%.
                 */
                activity.setProgress(progress * 1000);
            }
        });
        webview.setWebViewClient(new WebViewClient() {
            public void onReceivedError(WebView view, int errorCode, String description,
                    String failingUrl) {
                Toast.makeText(activity,
                        getString(R.string.getglue_authfailed) + " " + description,
                        Toast.LENGTH_LONG).show();

                finish();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith(GetGlueCheckin.OAUTH_CALLBACK_URL)) {
                    Uri uri = Uri.parse(url);

                    new RetrieveAccessTokenTask(getApplicationContext()).execute(uri);

                    finish();
                    return true;
                }
                return false;
            }
        });

        // mWebview.getSettings().setJavaScriptEnabled(true);

        // make sure we start fresh
        webview.clearCache(true);

        Timber.d("Initiating authorization request...");
        Resources res = getResources();
        try {
            OAuthClientRequest request = com.uwetrottmann.getglue.GetGlue
                    .getAuthorizationRequest(BuildConfig.TVTAG_CLIENT_ID,
                            GetGlueCheckin.OAUTH_CALLBACK_URL);
            webview.loadUrl(request.getLocationUri());
        } catch (OAuthSystemException e) {
            Timber.e(e, "Auth request failed");
        }
    }

    public class RetrieveAccessTokenTask extends AsyncTask<Uri, Void, Integer> {

        private static final int AUTH_FAILED = 0;

        private static final int AUTH_SUCCESS = 1;

        private Context mContext;

        public RetrieveAccessTokenTask(Context context) {
            mContext = context;
        }

        /**
         * Retrieve the oauth_verifier, and store the oauth and oauth_token_secret for future API
         * calls.
         */
        @Override
        protected Integer doInBackground(Uri... params) {
            final Uri uri = params[0];
            final String authCode = uri.getQueryParameter("code");

            return fetchAndStoreTokens(mContext, authCode) ? AUTH_SUCCESS : AUTH_FAILED;
        }

        @Override
        protected void onPostExecute(Integer result) {
            switch (result) {
                case AUTH_SUCCESS:
                    break;
                case AUTH_FAILED:
                    Toast.makeText(getApplicationContext(), getString(R.string.getglue_authfailed),
                            Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }

    /**
     * Tries to obtain new tokens from GetGlue using the given auth code. Returns true if
     * successful, returns false and clears any existing tokens if failing.
     */
    public static boolean fetchAndStoreTokens(Context context, String authCode) {
        Resources resources = context.getResources();
        OAuthAccessTokenResponse response = null;

        Timber.d("Building OAuth 2.0 token request...");
        try {
            response = GetGlue.getAccessTokenResponse(
                    BuildConfig.TVTAG_CLIENT_ID,
                    BuildConfig.TVTAG_CLIENT_SECRET,
                    GetGlueCheckin.OAUTH_CALLBACK_URL,
                    authCode
            );
        } catch (OAuthSystemException | OAuthProblemException e) {
            response = null;
            Timber.e(e, "Getting access token failed");
        }

        if (response != null) {
            Timber.d("Storing received OAuth 2.0 tokens...");
            long expiresIn = response.getExpiresIn();
            long expirationDate = System.currentTimeMillis()
                    + expiresIn * DateUtils.SECOND_IN_MILLIS;
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putString(GetGlueSettings.KEY_AUTH_TOKEN, response.getAccessToken())
                    .putLong(GetGlueSettings.KEY_AUTH_EXPIRATION, expirationDate)
                    .putString(GetGlueSettings.KEY_REFRESH_TOKEN, response.getRefreshToken())
                    .commit();
            return true;
        } else {
            Timber.d("Failed, purging OAuth 2.0 tokens...");
            GetGlueSettings.clearTokens(context);
            return false;
        }
    }

}
