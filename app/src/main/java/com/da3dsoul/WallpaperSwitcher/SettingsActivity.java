package com.da3dsoul.WallpaperSwitcher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.NumberPicker;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> resultLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        final SharedPreferences sp = getSharedPreferences("wall", MODE_PRIVATE);
        int bucketSize = sp.getInt("bucketSize", CacheManager.baseBucketSize);
        int readAhead = sp.getInt("readAhead", CacheManager.cacheReadAhead);
        if (bucketSize == 0 || readAhead == 0) {
            SharedPreferences.Editor edit = sp.edit();
            edit.putInt("bucketSize", CacheManager.baseBucketSize);
            edit.putInt("readAhead", CacheManager.cacheReadAhead);
            edit.apply();
        }

        CheckBox debug_checkBox = findViewById(R.id.debug_checkBox);
        debug_checkBox.setChecked(sp.getBoolean("debug_stats", false));
        debug_checkBox.setOnClickListener(v -> sp.edit().putBoolean("debug_stats", ((CheckBox) v).isChecked()).apply());
        NumberPicker txtBucketSize = findViewById(R.id.txtBucketSize);
        txtBucketSize.setMinValue(0);
        txtBucketSize.setMaxValue(1000);
        txtBucketSize.setValue(bucketSize);
        txtBucketSize.setOnValueChangedListener((l, previous, current) -> {
            if (current <= 0) return;
            if (CacheManager.instance().bucketSize != CacheManager.baseBucketSize) return;
            CacheManager.baseBucketSize = current;
            CacheManager.instance().bucketSize = current;
            sp.edit().putInt("bucketSize", current).apply();
        });
        NumberPicker txtReadAhead = findViewById(R.id.txtReadAhead);
        txtReadAhead.setMinValue(0);
        txtReadAhead.setMaxValue(1000);
        txtReadAhead.setValue(readAhead);
        txtReadAhead.setOnValueChangedListener((l, previous, current) -> {
            if (current <= 0) return;
            CacheManager.cacheReadAhead = current;
            sp.edit().putInt("readAhead", current).apply();
        });

        Button select_button = findViewById(R.id.select_folder_button);

        resultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_FIRST_USER) {
                    Intent data = result.getData();
                    if (data == null) return;
                    sp.edit().putString("dir", data.getStringExtra("dir")).apply();
                }
            });

        select_button.setOnClickListener(v -> {
            Intent it = new Intent(SettingsActivity.this, SelectDir.class);
            resultLauncher.launch(it);
        });
    }
}