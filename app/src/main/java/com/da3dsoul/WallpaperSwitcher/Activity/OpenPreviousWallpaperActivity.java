package com.da3dsoul.WallpaperSwitcher.Activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;

import com.da3dsoul.WallpaperSwitcher.CacheInstanceManager;
import com.da3dsoul.WallpaperSwitcher.ICacheManager;

import java.io.File;

public class OpenPreviousWallpaperActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DisplayMetrics metrics = new DisplayMetrics();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getDisplay().getRealMetrics(metrics);
        }

        if (metrics.widthPixels == 0 || metrics.heightPixels == 0) {
            finish();
            return;
        }

        double aspect = (double)metrics.widthPixels/metrics.heightPixels;
        ICacheManager cache = CacheInstanceManager.instanceForCanvas(aspect);
        if (cache == null || cache.needsInitialized()) {
            finish();
            return;
        }

        int currentIndex = cache.getCurrentIndex();
        File previousFile = cache.get(currentIndex - 1);
        if (previousFile == null || !previousFile.exists()) return;

        try {
            Intent openIntent = new Intent(Intent.ACTION_VIEW);
            openIntent.setDataAndType(Uri.parse(previousFile.getAbsolutePath()), "image/*");
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(openIntent);
        } catch (Exception e) {
            Log.e("openPreviousWallpaper", e.toString());
        }

        finish();
    }
}