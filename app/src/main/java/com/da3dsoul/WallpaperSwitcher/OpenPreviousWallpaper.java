package com.da3dsoul.WallpaperSwitcher;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.util.Map;
import java.util.UUID;

public class OpenPreviousWallpaper extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!CacheManager.instance().isInitialized()) {
            finish();
            return;
        }

        int currentIndex = CacheManager.instance().currentIndex;
        if (currentIndex <= 0 || currentIndex - 1 >= CacheManager.instance().cacheSize) return;

        String prev = CacheManager.instance().getCacheIndex(currentIndex - 1).getAbsolutePath();
        File file = new File(prev);
        if (!file.exists()) return;
        try {
            Intent openIntent = new Intent(Intent.ACTION_VIEW);
            openIntent.setDataAndType(Uri.parse(file.getAbsolutePath()), "image/*");
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(openIntent);
        } catch (Exception e) {
            Log.e("openPreviousWallpaper", e.toString());
        }

        finish();
    }
}