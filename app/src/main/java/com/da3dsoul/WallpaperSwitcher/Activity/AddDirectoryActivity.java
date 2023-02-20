package com.da3dsoul.WallpaperSwitcher.Activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.da3dsoul.WallpaperSwitcher.DecimalPicker;
import com.da3dsoul.WallpaperSwitcher.DirectoryModel;
import com.da3dsoul.WallpaperSwitcher.R;

import java.io.Serializable;

public class AddDirectoryActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> resultLauncher;

    private int index;
    private double minAspect = 0.1;
    private double maxAspect = 10.0;
    private String directory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_directory_activity);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Serializable s = getIntent().getSerializableExtra("model");
        if (s != null) {
            DirectoryModel model = (DirectoryModel)s;
            minAspect = model.MinAspect;
            maxAspect = model.MaxAspect;
            directory = model.Directory;
        }

        index = getIntent().getIntExtra("index", -1);

        DecimalPicker minAspectPicker = findViewById(R.id.txtMinAspect);
        minAspectPicker.setFormat("%.1f");
        minAspectPicker.setRange(0.1, 10.0);

        minAspectPicker.setOnValueChangeListener((l, previous, current) -> {
            if (current <= 0) return;
            minAspect = current;
        });

        DecimalPicker maxAspectPicker = findViewById(R.id.txtMaxAspect);
        maxAspectPicker.setFormat("%.1f");
        maxAspectPicker.setRange(0.1, 10.0);
        maxAspectPicker.setOnValueChangeListener((l, previous, current) -> {
            if (current <= 0) return;
            maxAspect = current;
        });

        Button select_button = findViewById(R.id.select_folder_button);

        resultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_FIRST_USER) {
                    Intent data = result.getData();
                    if (data == null) return;
                    directory = data.getStringExtra("dir");
                }
            });

        select_button.setOnClickListener(v -> {
            Intent it = new Intent(AddDirectoryActivity.this, SelectDirActivity.class);
            resultLauncher.launch(it);
        });

        Button save = findViewById(R.id.save_button);
        save.setOnClickListener(v -> {
            Intent it = new Intent();
            it.putExtra("minAspect", minAspect);
            it.putExtra("maxAspect", maxAspect);
            it.putExtra("directory", directory);
            it.putExtra("delete", false);
            it.putExtra("index", index);
            it.setClass(AddDirectoryActivity.this, SettingsActivity.class);
            AddDirectoryActivity.this.setResult(RESULT_FIRST_USER, it);
            AddDirectoryActivity.this.finish();
        });

        Button deleteBtn = findViewById(R.id.delete_button);
        deleteBtn.setOnClickListener(v -> {
            Intent it = new Intent();
            it.putExtra("delete", true);
            it.putExtra("index", index);
            it.setClass(AddDirectoryActivity.this, SettingsActivity.class);
            AddDirectoryActivity.this.setResult(RESULT_FIRST_USER, it);
            AddDirectoryActivity.this.finish();
        });
    }
}