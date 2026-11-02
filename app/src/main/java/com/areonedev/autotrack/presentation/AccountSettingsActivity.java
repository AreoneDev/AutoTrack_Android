package com.areonedev.autotrack.presentation;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import com.areonedev.autotrack.R;
import com.areonedev.autotrack.auth.AuthRuntime;
import com.areonedev.autotrack.auth.AccountSession;

/** Shows only the public identity from the active session, never its credentials. */
public final class AccountSettingsActivity extends AccountActivity {
    private AccountSession.Profile profile;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthRuntime.requireAccount(this)) return;
        setContentView(R.layout.activity_account_settings);
        setupAccountNavigation(R.id.nav_account);
        findViewById(R.id.account_edit).setOnClickListener(view -> editProfile());
        findViewById(R.id.account_retry).setOnClickListener(view -> loadProfile());
        findViewById(R.id.account_appearance).setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.account_appearance).setItems(new String[]{getString(R.string.account_system), getString(R.string.account_light), getString(R.string.account_dark)}, (dialog, item) -> {
                    int mode = item == 1 ? AppCompatDelegate.MODE_NIGHT_NO : item == 2 ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                    getSharedPreferences("appearance", MODE_PRIVATE).edit().putInt("mode", mode).apply();
                    AppCompatDelegate.setDefaultNightMode(mode);
                }).show());
        findViewById(R.id.account_language).setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.account_language).setItems(new String[]{"English", "Français", "简体中文"}, (dialog, item) ->
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(new String[]{"en", "fr", "zh-CN"}[item]))).show());
        findViewById(R.id.account_support).setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.account_support).setMessage(R.string.account_support_unavailable).setPositiveButton(android.R.string.ok, null).show());
        findViewById(R.id.auth_sign_out).setOnClickListener(view -> {
            view.setEnabled(false);
            signOutAccount();
        });
        findViewById(R.id.sync_review).setOnClickListener(view -> startActivity(new Intent(this, SyncActivity.class)));
        findViewById(R.id.account_notifications).setOnClickListener(view -> {
            try {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            } catch (ActivityNotFoundException unavailable) {
                Toast.makeText(this, R.string.account_settings_unavailable, Toast.LENGTH_LONG).show();
            }
        });
        loadProfile();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!AuthRuntime.hasAccount()) return;
        ((TextView) findViewById(R.id.account_identity)).setText(AuthRuntime.accounts(this).current().userId);
    }
    private void loadProfile() {
        findViewById(R.id.account_retry).setVisibility(android.view.View.GONE);
        ((TextView) findViewById(R.id.account_profile)).setText(R.string.account_loading);
        AccountSession accounts = AuthRuntime.accounts(this);
        String owner = accounts.current().userId;
        long generation = accounts.generation();
        AuthRuntime.execute(() -> {
            try {
                AccountSession.Profile result = accounts.profile();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || !accounts.isCurrent(owner, generation)) return;
                    showProfile(result);
                });
            } catch (Exception unavailable) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed() && accounts.isCurrent(owner, generation)) {
                        ((TextView) findViewById(R.id.account_profile)).setText(R.string.account_profile_failed);
                        findViewById(R.id.account_retry).setVisibility(android.view.View.VISIBLE);
                    }
                });
            }
        });
    }
    private void showProfile(AccountSession.Profile result) {
        profile = result;
        ((TextView) findViewById(R.id.account_profile)).setText(getString(R.string.account_profile_format,
                result.firstName, result.lastName, result.phone, result.email, getString(R.string.account_role_unavailable)));
        findViewById(R.id.account_edit).setEnabled(true);
    }
    private void editProfile() {
        if (profile == null) return;
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        fields.setPadding(padding, 0, padding, 0);
        EditText first = field(fields, R.string.auth_first_name, profile.firstName);
        EditText last = field(fields, R.string.auth_last_name, profile.lastName);
        EditText phone = field(fields, R.string.auth_phone, profile.phone);
        phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(fields);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.account_edit).setView(scroll)
                .setNegativeButton(R.string.account_cancel, null).setPositiveButton(R.string.account_save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String a = first.getText().toString().trim(), b = last.getText().toString().trim(), c = phone.getText().toString().trim();
            if (a.isEmpty() || b.isEmpty() || c.isEmpty()) { Toast.makeText(this, R.string.auth_profile_required, Toast.LENGTH_LONG).show(); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            AccountSession accounts = AuthRuntime.accounts(this);
            String owner = accounts.current().userId; long generation = accounts.generation();
            AuthRuntime.execute(() -> {
                try {
                    AccountSession.Profile updated = accounts.updateProfile(a, b, c);
                    runOnUiThread(() -> { if (!isFinishing() && !isDestroyed() && accounts.isCurrent(owner, generation)) {
                        dialog.dismiss(); showProfile(updated); Toast.makeText(this, R.string.account_saved, Toast.LENGTH_SHORT).show();
                    }});
                } catch (Exception failure) {
                    runOnUiThread(() -> { if (!isFinishing() && !isDestroyed() && accounts.isCurrent(owner, generation)) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        Toast.makeText(this, R.string.account_profile_failed, Toast.LENGTH_LONG).show();
                    }});
                }
            });
        }));
        dialog.show();
    }
    private EditText field(LinearLayout parent, int label, String value) {
        TextView description = new TextView(this);
        description.setText(label);
        parent.addView(description, new LinearLayout.LayoutParams(-1, -2));
        EditText input = new EditText(this);
        input.setHint(label); input.setText(value); input.setSingleLine(true);
        parent.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return input;
    }
}
