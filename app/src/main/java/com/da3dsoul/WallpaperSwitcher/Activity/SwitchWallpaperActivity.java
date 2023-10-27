package com.da3dsoul.WallpaperSwitcher.Activity;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Bundle;

import androidx.window.layout.WindowMetricsCalculator;

import com.da3dsoul.WallpaperSwitcher.CacheInstanceManager;
import com.da3dsoul.WallpaperSwitcher.ICacheManager;

public class SwitchWallpaperActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Rect bounds = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this).getBounds();
        int widthPixels = bounds.width();
        int heightPixels = bounds.height();

        if (widthPixels == 0 || heightPixels == 0) {
            finish();
            return;
        }

        double aspect = (double)widthPixels/heightPixels;
        CacheInstanceManager.instanceForCanvas(aspect);
        ICacheManager cache = CacheInstanceManager.instanceForCanvas(aspect);
        if (cache == null || cache.needsInitialized()) {
            finish();
            return;
        }
        cache.switchWallpaper(getApplicationContext());
        finish();
    }
}