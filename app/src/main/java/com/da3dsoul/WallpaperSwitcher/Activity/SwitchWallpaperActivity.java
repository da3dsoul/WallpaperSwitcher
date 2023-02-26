package com.da3dsoul.WallpaperSwitcher.Activity;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import androidx.window.layout.WindowMetricsCalculator;

import com.da3dsoul.WallpaperSwitcher.CacheManager;

public class SwitchWallpaperActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Rect bounds = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this).getBounds();
        int widthPixels = bounds.width();
        int heightPixels = bounds.height();
        if (false && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            DisplayMetrics metrics = new DisplayMetrics();
            getDisplay().getRealMetrics(metrics);
            widthPixels = metrics.widthPixels;
            heightPixels = metrics.heightPixels;
        }

        if (widthPixels == 0 || heightPixels == 0) {
            finish();
            return;
        }

        double aspect = (double)widthPixels/heightPixels;
        CacheManager cache = CacheManager.instanceForCanvas(aspect);
        if (cache == null || cache.needsInitialized()) {
            finish();
            return;
        }
        cache.switchWallpaper(getApplicationContext());
        finish();
    }
}