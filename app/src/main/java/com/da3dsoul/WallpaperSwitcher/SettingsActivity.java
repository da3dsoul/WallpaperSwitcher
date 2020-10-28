package com.da3dsoul.WallpaperSwitcher;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        final SharedPreferences sp = getSharedPreferences("wall", MODE_PRIVATE);

        CheckBox debug_checkBox = findViewById(R.id.debug_checkBox);
        debug_checkBox.setChecked(sp.getBoolean("debug_stats", false));
        debug_checkBox.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sp.edit().putBoolean("debug_stats", ((CheckBox) v).isChecked()).apply();
            }
        });

        Button select_button = findViewById(R.id.select_folder_button);
        select_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent it = new Intent();
                it.setClass(SettingsActivity.this, SelectDir.class);
                startActivityForResult(it, RESULT_FIRST_USER);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_FIRST_USER) {
            SharedPreferences sp = getSharedPreferences("wall", MODE_PRIVATE);
            sp.edit().putString("dir", data.getStringExtra("dir")).apply();
        }

    }
}