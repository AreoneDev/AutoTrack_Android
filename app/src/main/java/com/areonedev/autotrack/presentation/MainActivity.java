package com.areonedev.autotrack.presentation;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** Starts authentication before any customer database can be opened. */
public class MainActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(getSharedPreferences("appearance", MODE_PRIVATE)
                .getInt("mode", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
