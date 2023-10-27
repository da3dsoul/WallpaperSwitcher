package com.da3dsoul.WallpaperSwitcher.Activity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.da3dsoul.WallpaperSwitcher.InputFileListAdapter;
import com.da3dsoul.WallpaperSwitcher.InputFileListItem;
import com.da3dsoul.WallpaperSwitcher.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SelectDirActivity extends Activity {

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
            it.setClass(SelectDirActivity.this, AddDirectoryActivity.class);
            SelectDirActivity.this.setResult(RESULT_FIRST_USER, it);
            SelectDirActivity.this.finish();
        });
        browseToRoot();
    }

    public void browseToRoot() {
        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 138);
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
        int prevCount = this.directoryEntries.size();
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
        File parent = this.currentDirectory.getParentFile();
        if (parent != null) {
            this.directoryEntries.add(0, new InputFileListItem(getString(R.string.up_one_level), parent.getAbsolutePath(), R.drawable.uponelevel, true));
        }

        listAdapter.notifyItemRangeRemoved(0, prevCount);
        listAdapter.notifyItemRangeInserted(0, this.directoryEntries.size());
    }

    private boolean checkEndsWithInStringArray(String checkItsEnd, String[] fileEndings) {
        for (String aEnd : fileEndings) {
            if (checkItsEnd.endsWith(aEnd))
                return true;
        }
        return false;
    }
}
