package com.da3dsoul.WallpaperSwitcher;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Environment;
import android.widget.Button;

public class SelectDir extends Activity {

    public final List<InputFileListItem> directoryEntries = new ArrayList<>();
    private File currentDirectory = new File(Environment.getExternalStorageDirectory().getPath());

    RecyclerView fileList;
    InputFileListAdapter listAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.input_file_list);
        fileList = findViewById(R.id.listview);
		listAdapter = new InputFileListAdapter(this, this.directoryEntries);
		fileList.setAdapter(listAdapter);

        Button select = findViewById(R.id.select);
        select.setOnClickListener(v -> {
            Intent it = new Intent();
            it.putExtra("dir", currentDirectory.getAbsolutePath());
            it.setClass(SelectDir.this, SettingsActivity.class);
            SelectDir.this.setResult(RESULT_FIRST_USER, it);
            SelectDir.this.finish();
        });
        browseToRoot();
    }

    public void browseToRoot() {
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 138);
        this.setTitle("Storage");
        File[] storages = ContextCompat.getExternalFilesDirs(getApplicationContext(), null);
        if (storages.length <= 0) browseTo(new File(Environment.getExternalStorageDirectory().getPath()));
        ArrayList<File> files = new ArrayList<>();
        for (File item : storages) {
            String path = item.getAbsolutePath().replace("/Android/data/" + getApplicationContext().getPackageName() + "/files", "");
            File file = new File(path);
            if (!file.exists()) continue;
            if (!file.isDirectory()) continue;
            String[] children = file.list();
            if (children == null || children.length <= 0) continue;
            files.add(file);
        }

        File[] array = files.toArray(new File[0]);
        fill(array);
    }

    public void upOneLevel() {
        if (this.currentDirectory.getParent() != null)
            this.browseTo(this.currentDirectory.getParentFile());
        else
            browseToRoot();
    }

    public void browseTo(final File file) {
        if (file == null) return;
        this.setTitle(file.getAbsolutePath());
        if (file.isDirectory()) {
            this.currentDirectory = file;
            File[] files = file.listFiles();
            if (files == null) return;
            fill(files);
        }
    }


    private void fill(File[] files) {
        this.directoryEntries.clear();

        for (File currentFile : files) {
            String fileName = currentFile.getName();

            if (currentFile.isDirectory()) {
                this.directoryEntries.add(new InputFileListItem(fileName, currentFile.getAbsolutePath(), R.drawable.folder, true));
            } else if (checkEndsWithInStringArray(fileName, getResources().getStringArray(R.array.fileEndingImage))) {
                this.directoryEntries.add(new InputFileListItem(fileName, currentFile.getAbsolutePath(), R.drawable.image, false));
            }
        }
        this.directoryEntries.sort((a, b) -> a.getDir().compareToIgnoreCase(b.getDir()));
        if (this.currentDirectory.getParent() != null) {
            this.directoryEntries.add(0, new InputFileListItem(getString(R.string.up_one_level), getString(R.string.none), R.drawable.uponelevel, true));
        }

        listAdapter.notifyDataSetChanged();
    }

    private boolean checkEndsWithInStringArray(String checkItsEnd, String[] fileEndings) {
        for (String aEnd : fileEndings) {
            if (checkItsEnd.endsWith(aEnd))
                return true;
        }
        return false;
    }
}
