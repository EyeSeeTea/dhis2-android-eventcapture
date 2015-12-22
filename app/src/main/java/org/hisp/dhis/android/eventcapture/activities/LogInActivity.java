package org.hisp.dhis.android.eventcapture.activities;

import android.app.AlertDialog;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;

import org.hisp.dhis.android.eventcapture.R;

public class LogInActivity extends AppCompatActivity implements ILoginView {

    private ILoginPresenter loginPresenter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loginPresenter = new LoginPresenter(this);
        loginPresenter.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        loginPresenter.onDestroy();
    }

//    @Override
    protected void onLogInButtonClicked(Editable server, Editable username, Editable password) {
        loginPresenter.validateCredentials(username.toString(), password.toString());
    }

    @Override
    public void showProgress() {
        // onStartLoading();
    }

    @Override
    public void hideProgress() {
        // onFinishLoading();
    }

    @Override
    public void showServerError(String message) {
        showError(message);
    }

    @Override
    public void showInvalidCredentialsError() {
        showError(getString(R.string.invalid_credentials_error));
    }

    @Override
    public void showUnexpectedError(String message) {
        showError(message);
    }

    private void showError(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(LogInActivity.this);
        AlertDialog alertDialog = builder.setTitle(getString(R.string.error))
                .setMessage(message).show();
        alertDialog.show();
    }

    @Override
    public void navigateToHome() {
//        startActivity(new Intent(this, Main.class));
    }
}