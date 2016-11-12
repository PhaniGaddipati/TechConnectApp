package org.techconnect.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.centum.techconnect.R;
import org.techconnect.misc.auth.AuthManager;
import org.techconnect.model.User;
import org.techconnect.model.UserAuth;
import org.techconnect.network.TCNetworkHelper;
import org.techconnect.sql.TCDatabaseHelper;

import java.io.IOException;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends AppCompatActivity {
    private static final int REGISTER_REQUEST = 1;
    private static final String SHOW_SKIP_ALERT = "org.techconnect.login.skipalert";
    // UI references.
    @Bind(R.id.email)
    TextView mEmailView;
    @Bind(R.id.password)
    EditText mPasswordView;
    @Bind(R.id.login_progress)
    View mProgressView;
    @Bind(R.id.login_form)
    View mLoginFormView;
    @Bind(R.id.email_sign_in_button)
    Button mEmailSignInButton;
    @Bind(R.id.register_button)
    Button registerButton;
    @Bind(R.id.skip_signin_button)
    Button mSkipSigninButton;
    @Bind(R.id.coordinatorLayout)
    CoordinatorLayout coordinatorLayout;

    FirebaseAnalytics firebaseAnalytics;

    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private UserLoginTask mAuthTask = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        ButterKnife.bind(this);
        firebaseAnalytics = FirebaseAnalytics.getInstance(this);

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
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REGISTER_REQUEST && resultCode == Activity.RESULT_OK) {
            mEmailView.setText(data.getStringExtra(RegisterActivity.RESULT_REGISTERED_EMAIL));
            Snackbar.make(coordinatorLayout, R.string.user_registered, Snackbar.LENGTH_SHORT).show();
        }
    }

    @OnClick(R.id.register_button)
    public void onRegister() {
        Intent intent = new Intent(this, RegisterActivity.class);
        if (!TextUtils.isEmpty(mEmailView.getText())) {
            intent.putExtra(RegisterActivity.EXTRA_EMAIL, mEmailView.getText());
        }
        if (!TextUtils.isEmpty(mPasswordView.getText())) {
            intent.putExtra(RegisterActivity.EXTRA_PASSWORD, mPasswordView.getText());
        }
        startActivityForResult(intent, REGISTER_REQUEST);
    }

    @OnClick(R.id.skip_signin_button)
    public void onSkipSignin() {
        final SharedPreferences prefs = getSharedPreferences(LoginActivity.class.getName(), MODE_PRIVATE);
        if (prefs.getBoolean(SHOW_SKIP_ALERT, true)) {
            final CheckBox checkBox = new CheckBox(this);
            checkBox.setText(R.string.dont_show_again);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.are_you_sure)
                    .setMessage(R.string.skip_sign_in_msg)
                    .setView(checkBox)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                            if (checkBox.isChecked()) {
                                prefs.edit().putBoolean(SHOW_SKIP_ALERT, false).apply();
                            }
                            LoginActivity.this.finish();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                        }
                    }).show();
        } else {
            LoginActivity.this.finish();
        }
    }

    @OnClick(R.id.email_sign_in_button)
    public void attemptLogin() {
        if (mAuthTask != null) {
            return;
        }

        // Hide keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mPasswordView.getWindowToken(), 0);

        // Reset errors.
        mEmailView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String email = mEmailView.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid email address & password.
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            mEmailView.setError(getString(R.string.error_signin_field_required));
            focusView = mEmailView;
            cancel = true;
        }
        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress_spinner spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true);
            mAuthTask = new UserLoginTask(email, password);
            mAuthTask.execute((Void) null);
        }
    }

    /**
     * Shows the progress_spinner UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress_spinner spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    class UserLoginTask extends AsyncTask<Void, Void, Object[]> {

        private final String mEmail;
        private final String mPassword;

        UserLoginTask(String email, String password) {
            mEmail = email;
            mPassword = password;
        }

        @Override
        protected Object[] doInBackground(Void... params) {
            try {
                // Login and get user
                TCNetworkHelper helper = new TCNetworkHelper();
                UserAuth auth = helper.login(mEmail, mPassword);
                User user = null;
                if (auth != null) {
                    user = helper.getUser(auth.getUserId());
                }
                return new Object[]{user, auth};
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(final Object[] objs) {
            mAuthTask = null;
            showProgress(false);
            if (objs[0] == null) {
                Snackbar.make(coordinatorLayout, R.string.couldnt_login, Snackbar.LENGTH_LONG).show();
                mPasswordView.requestFocus();
            } else {
                // Store user
                User user = (User) objs[0];
                UserAuth auth = (UserAuth) objs[1];
                TCDatabaseHelper.get(LoginActivity.this).upsertUser(user);
                AuthManager.get(LoginActivity.this).setAuth(auth);
                Bundle bundle = new Bundle();
                firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN, null);
                finish();
            }
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }
    }
}

