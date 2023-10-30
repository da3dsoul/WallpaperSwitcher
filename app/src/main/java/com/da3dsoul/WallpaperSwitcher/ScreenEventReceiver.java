package com.da3dsoul.WallpaperSwitcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.DisplayMetrics;
import android.util.Log;

public class ScreenEventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_USER_PRESENT.equals(action)) return;

        try {
            //The screen on position
            DisplayMetrics metrics = WallpaperSwitcher.getDisplayMetrics(context);
            if (metrics == null) {
                Log.e("WallpaperSwitcher", "Could could not get display");
                return;
            }

            double aspect = (double) metrics.widthPixels / metrics.heightPixels;
            ICacheManager cache = CacheInstanceManager.instanceForCanvas(aspect);
            if (cache == null || cache.needsInitialized()) {
                Log.e("WallpaperSwitcher", String.format("Could not get cache for active display: %dx%d, aspect: %.3f", metrics.widthPixels, metrics.heightPixels, aspect));
                return;
            }

            cache.switchWallpaper(context);
        } catch (Exception e) {
            Log.e("WallpaperSwitcher", e.toString());
        }
    }
}
