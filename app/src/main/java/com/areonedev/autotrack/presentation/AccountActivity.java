package com.areonedev.autotrack.presentation;

import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import com.areonedev.autotrack.R;
import com.areonedev.autotrack.auth.AuthRuntime;
import com.areonedev.autotrack.application.Services;
import androidx.appcompat.app.AppCompatActivity;

/** Shared account gate and current-device sign-out action for private screens. */
public abstract class AccountActivity extends AppCompatActivity {
    @Override protected void onResume() {
        super.onResume();
        AuthRuntime.requireAccount(this);
    }
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, R.id.auth_sign_out, Menu.NONE, R.string.auth_sign_out)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() != R.id.auth_sign_out) return super.onOptionsItemSelected(item);
        signOutAccount();
        return true;
    }
    protected final void signOutAccount() {
        Services.closeDataAccess();
        AuthRuntime.execute(() -> {
            boolean failed = false;
            try { AuthRuntime.accounts(getApplicationContext()).logout(); }
            catch (Exception unavailable) { failed = true; }
            final boolean signOutFailed = failed;
            runOnUiThread(() -> {
                startActivity(new Intent(this, LoginActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .putExtra("signed_out", true).putExtra("sign_out_failed", signOutFailed));
                finish();
            });
        });
    }
}
