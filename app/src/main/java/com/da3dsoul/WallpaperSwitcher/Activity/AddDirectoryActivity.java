package com.da3dsoul.WallpaperSwitcher.Activity;

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

import com.da3dsoul.WallpaperSwitcher.CacheManager;
import com.da3dsoul.WallpaperSwitcher.R;

public class AddDirectoryActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> resultLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_directory_activity);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        final SharedPreferences sp = getSharedPreferences("wall", MODE_PRIVATE);

        NumberPicker txtBucketSize = findViewById(R.id.txtMinAspect);
        txtBucketSize.
        txtBucketSize.setMinValue(0);
        txtBucketSize.setMaxValue(3);
        txtBucketSize.setOnValueChangedListener((l, previous, current) -> {
            if (current <= 0) return;
            if (CacheManager.instance().bucketSize != CacheManager.baseBucketSize) return;
            CacheManager.baseBucketSize = current;
            CacheManager.instance().bucketSize = current;
            sp.edit().putInt("bucketSize", current).apply();
        });

        Button select_button = findViewById(R.id.add_button);

        resultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_FIRST_USER) {
                    Intent data = result.getData();
                    if (data == null) return;
                    sp.edit().putString("dir", data.getStringExtra("dir")).apply();
                }
            });

        select_button.setOnClickListener(v -> {
            Intent it = new Intent(AddDirectoryActivity.this, SelectDirActivity.class);
            resultLauncher.launch(it);
        });
    }
}