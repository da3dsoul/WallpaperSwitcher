package com.da3dsoul.WallpaperSwitcher.Activity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.NumberPicker;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.da3dsoul.WallpaperSwitcher.AddDirectoryRecyclerViewAdapter;
import com.da3dsoul.WallpaperSwitcher.CacheManager;
import com.da3dsoul.WallpaperSwitcher.DirectoryModel;
import com.da3dsoul.WallpaperSwitcher.R;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;

public class SettingsActivity extends AppCompatActivity {
    public final ArrayList<DirectoryModel> directories = new ArrayList<>();

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
            CacheManager.baseBucketSize = current;
            for (CacheManager cache : CacheManager.allInstances()) {
                if (cache.bucketSize != CacheManager.baseBucketSize) continue;
                cache.bucketSize = current;
            }

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

        try {
            // get directories
            String directorySettings = sp.getString("directories", null);
            if (directorySettings != null && !directorySettings.equals("")) {
                Gson gson = new Gson();
                DirectoryModel[] model = gson.fromJson(directorySettings, DirectoryModel[].class);
                if (model != null) {
                    directories.addAll(Arrays.asList(model));
                }
            }

            RecyclerView recyclerView = findViewById(R.id.folders);
            ActivityResultLauncher<Intent> directoryResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_FIRST_USER) return;
                        Intent data = result.getData();
                        if (data == null) return;
                        // set directory
                        int index = data.getIntExtra("index", -1);
                        if (index == -1) return;
                        AddDirectoryRecyclerViewAdapter adapter = (AddDirectoryRecyclerViewAdapter) recyclerView.getAdapter();
                        if (adapter == null) return;
                        if (data.getBooleanExtra("delete", false)) {
                            directories.remove(index);
                            adapter.notifyItemRemoved(index);
                            String newSettings = new Gson().toJson(directories);
                            sp.edit().putString("directories", newSettings).apply();
                        } else
                        {
                            adapter.notifyItemChanged(index);
                            SaveDirectory(sp, directories, data, index);
                        }
                    });

            recyclerView.setAdapter(new AddDirectoryRecyclerViewAdapter(directories, directoryResult));

            Button select_button = findViewById(R.id.add_button);

            ActivityResultLauncher<Intent> resultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_FIRST_USER) {
                            Intent data = result.getData();
                            if (data == null) return;
                            AddDirectoryRecyclerViewAdapter adapter = (AddDirectoryRecyclerViewAdapter) recyclerView.getAdapter();
                            if (adapter == null) return;
                            adapter.notifyItemInserted(directories.size() - 1);
                            SaveDirectory(sp, directories, data, -1);
                        }
                    });

            select_button.setOnClickListener(v -> {
                Intent it = new Intent(SettingsActivity.this, AddDirectoryActivity.class);
                resultLauncher.launch(it);
            });
        } catch (Exception e) {
            Log.e("settings", e.toString());
        }
    }

    private void SaveDirectory(SharedPreferences sp, ArrayList<DirectoryModel> matches, Intent data, int index) {
        DirectoryModel directoryModel = new DirectoryModel();
        String directory = data.getStringExtra("directory");
        if (directory == null) return;
        double minAspect = data.getDoubleExtra("minAspect", -1);
        if (minAspect == -1) return;
        double maxAspect = data.getDoubleExtra("maxAspect", -1);
        if (maxAspect == -1) return;
        directoryModel.Directory = directory;
        directoryModel.MinAspect = minAspect;
        directoryModel.MaxAspect = maxAspect;

        if (index == -1) matches.add(directoryModel);
        else matches.set(index, directoryModel);

        String newSettings = new Gson().toJson(matches);
        sp.edit().putString("directories", newSettings).apply();
    }
}