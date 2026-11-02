package com.areonedev.autotrack.presentation;

import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import com.areonedev.autotrack.R;
import com.areonedev.autotrack.auth.AuthRuntime;
import com.areonedev.autotrack.application.Services;
import androidx.appcompat.app.AppCompatActivity;

/** Shared account gate, tab navigation, and current-device sign-out for private screens. */
public abstract class AccountActivity extends AppCompatActivity {
    private int selectedTab;
    @Override protected void onResume() {
        super.onResume();
        if(AuthRuntime.requireAccount(this)) {
            if (selectedTab != 0) {
                com.google.android.material.bottomnavigation.BottomNavigationView navigation = findViewById(R.id.bottom_navigation);
                navigation.getMenu().findItem(selectedTab).setChecked(true);
            }
            com.areonedev.autotrack.sync.SyncRuntime.request(getApplicationContext());
        }
    }
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE,R.id.sync_review,Menu.NONE,R.string.sync_review);
        return true;
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId()==R.id.sync_review) {startActivity(new Intent(this,SyncActivity.class));return true;}
        return super.onOptionsItemSelected(item);
    }
    protected final void setupAccountNavigation(int tab) {
        selectedTab = tab;
        com.google.android.material.bottomnavigation.BottomNavigationView navigation = findViewById(R.id.bottom_navigation);
        android.content.res.Configuration configuration = getResources().getConfiguration();
        boolean compact = configuration.screenWidthDp < 360 || configuration.fontScale >= 1.3f;
        navigation.setLabelVisibilityMode(compact
                ? com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_SELECTED
                : com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED);
        navigation.setSelectedItemId(tab);
        navigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selectedTab) return true;
            Class<?> destination;
            if (id == R.id.nav_leads) destination = LeadsActivity.class;
            else if (id == R.id.nav_calendar) destination = CalendarActivity.class;
            else if (id == R.id.nav_notifications) destination = NotificationsActivity.class;
            else if (id == R.id.nav_account) destination = AccountSettingsActivity.class;
            else return false;
            startActivity(new Intent(this, destination).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
            return false;
        });
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
