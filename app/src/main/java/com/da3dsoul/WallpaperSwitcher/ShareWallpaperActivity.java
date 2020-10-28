package com.da3dsoul.WallpaperSwitcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.Map;
import java.util.UUID;

public class ShareWallpaperActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!CacheManager.instance().isInitialized()) {
            finish();
            return;
        }

        String path = CacheManager.instance().path;
        File file = new File(path);
        if (!file.exists()) return;
        try {
            Intent sharingIntent = new Intent(Intent.ACTION_SEND);
            sharingIntent.setType("image/*");

            sharingIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getApplicationContext(), "com.da3dsoul.WallpaperSwitcher.fileprovider", file));
            Intent chooser = Intent.createChooser(sharingIntent, "Share " + path.substring(path.lastIndexOf('/') + 1));
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(chooser);
        } catch (Exception e) {
            Log.e("shareCurrentWallpaper", e.toString());
        }

        finish();
    }
}