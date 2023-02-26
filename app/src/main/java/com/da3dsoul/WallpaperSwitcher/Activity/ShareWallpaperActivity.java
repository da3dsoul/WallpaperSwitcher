package com.da3dsoul.WallpaperSwitcher.Activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.da3dsoul.WallpaperSwitcher.BuildConfig;
import com.da3dsoul.WallpaperSwitcher.CacheManager;

import java.io.File;

public class ShareWallpaperActivity extends Activity {

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
        CacheManager cache = CacheManager.instanceForCanvas(aspect);
        if (cache == null || cache.needsInitialized()) {
            finish();
            return;
        }

        String path = cache.path;
        File file = new File(path);
        if (!file.exists()) return;
        try {
            Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID, file);
            Intent sharingIntent = new Intent(Intent.ACTION_SEND);
            sharingIntent.setDataAndType(uri, getContentResolver().getType(uri));
            sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);
            sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            Intent chooser = Intent.createChooser(sharingIntent, "Share " + path.substring(path.lastIndexOf('/') + 1));
            startActivity(chooser);
        } catch (Exception e) {
            Log.e("shareCurrentWallpaper", e.toString());
        }

        finish();
    }
}