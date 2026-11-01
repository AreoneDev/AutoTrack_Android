package com.areonedev.autotrack.presentation;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.areonedev.autotrack.R;
import com.areonedev.autotrack.auth.*;
import com.areonedev.autotrack.application.Services;

public final class LoginActivity extends AppCompatActivity {
    private EditText email, password, firstName, lastName, phone;
    private TextView status;
    private Button signIn, retryLogout, switchMode;
    private AccountSession accounts;
    private boolean busy;
    private boolean registering;
    private boolean emailEntered;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.areonedev.autotrack.BuildConfig.DEBUG)
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_login);
        email = findViewById(R.id.auth_email);
        password = findViewById(R.id.auth_password);
        firstName = findViewById(R.id.auth_first_name);
        lastName = findViewById(R.id.auth_last_name);
        phone = findViewById(R.id.auth_phone);
        status = findViewById(R.id.auth_status);
        signIn = findViewById(R.id.auth_sign_in);
        retryLogout = findViewById(R.id.auth_retry_logout);
        switchMode = findViewById(R.id.auth_switch_mode);
        switchMode.setOnClickListener(view -> {
            registering = !registering;
            int visibility = registering ? android.view.View.VISIBLE : android.view.View.GONE;
            firstName.setVisibility(visibility); lastName.setVisibility(visibility); phone.setVisibility(visibility);
            signIn.setText(registering ? R.string.auth_register : R.string.auth_sign_in);
            switchMode.setText(registering ? R.string.auth_back_to_login : R.string.auth_create_account);
            status.setText(registering ? R.string.auth_registration_hint : R.string.auth_password_hint);
        });
        retryLogout.setOnClickListener(view -> retryLogout());
        signIn.setOnClickListener(view -> login());
        try {
            accounts = AuthRuntime.accounts(this);
            if (accounts.isLogoutPending()) showLogoutPending();
            else if (getIntent().getBooleanExtra("signed_out", false)) {
                setBusy(false);
                if (getIntent().getBooleanExtra("sign_out_failed", false)) status.setText(R.string.auth_remote_logout_failed);
            }
            else restore();
        } catch (IllegalArgumentException missingConfiguration) {
            status.setText(R.string.auth_configuration_missing);
            signIn.setEnabled(false);
        }
    }
    private void showLogoutPending() {
        setBusy(true);
        status.setText(R.string.auth_logout_pending);
        retryLogout.setVisibility(android.view.View.VISIBLE);
        retryLogout.setEnabled(true);
    }
    private void retryLogout() {
        retryLogout.setEnabled(false);
        AuthRuntime.execute(() -> {
            boolean failed = false;
            try { accounts.logout(); } catch (Exception error) { failed = true; }
            final boolean remoteFailed = failed;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (accounts.isLogoutPending()) showLogoutPending();
                else {
                    retryLogout.setVisibility(android.view.View.GONE);
                    setBusy(false);
                    if (remoteFailed) status.setText(R.string.auth_remote_logout_failed);
                }
            });
        });
    }
    private void restore() {
        setBusy(true);
        AuthRuntime.execute(() -> {
            try {
                AccountSession.Session restored = accounts.restore();
                runOnUiThread(() -> { if (isFinishing() || isDestroyed()) return;
                    if (restored != null) enterAccount(restored);
                    else setBusy(false);
                });
            } catch (Exception failure) {
                if (accounts.isLogoutPending()) runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) showLogoutPending();
                });
                else showError(getString(R.string.auth_restore_failed));
            }
        });
    }
    private void login() {
        if (busy) return;
        String address = email.getText().toString().trim();
        if (!emailEntered) {
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) { status.setText(R.string.auth_credentials_required); return; }
            emailEntered = true;
            password.setVisibility(android.view.View.VISIBLE);
            switchMode.setVisibility(android.view.View.VISIBLE);
            signIn.setText(R.string.auth_sign_in);
            status.setText(R.string.auth_password_hint);
            return;
        }
        String secret = password.getText().toString();
        if (address.isEmpty() || secret.isEmpty()) { status.setText(R.string.auth_credentials_required); return; }
        String first = firstName.getText().toString().trim(), last = lastName.getText().toString().trim(), number = phone.getText().toString().trim();
        if (registering && (first.isEmpty() || last.isEmpty() || number.isEmpty())) { status.setText(R.string.auth_profile_required); return; }
        setBusy(true);
        Services.closeDataAccess();
        password.getText().clear();
        AuthRuntime.execute(() -> {
            try {
                AccountSession.Session session = registering ? accounts.register(address, secret, first, last, number) : accounts.login(address, secret);
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) {
                    if (session == null) { setBusy(false); status.setText(R.string.auth_confirmation_required); }
                    else enterAccount(session);
                }});
            } catch (AccountSession.RejectedSessionException rejected) {
                showError(getString(registering ? R.string.auth_registration_failed : R.string.auth_credentials_rejected));
            } catch (Exception failure) { showError(getString(R.string.auth_login_failed)); }
        });
    }
    private void enterAccount(AccountSession.Session session) {
        if (accounts.current() != session) { setBusy(false); return; }
        try {
            Services.initialize(this);
            Services.openAccount(session.userId);
            startActivity(new Intent(this, LeadsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        } catch (RuntimeException unavailable) {
            Services.closeDataAccess();
            showError(getString(R.string.auth_storage_failed));
        }
    }
    private void setBusy(boolean value) {
        busy = value; signIn.setEnabled(!value); email.setEnabled(!value); password.setEnabled(!value);
        switchMode.setEnabled(!value);
        status.setText(value ? R.string.auth_checking : R.string.auth_password_hint);
    }
    private void showError(String message) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            setBusy(false); status.setText(message);
        });
    }
}
