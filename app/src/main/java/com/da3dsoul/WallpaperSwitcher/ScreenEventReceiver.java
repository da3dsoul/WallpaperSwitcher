package com.da3dsoul.WallpaperSwitcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ScreenEventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (intent.getAction() == null) return;
        if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            //The screen on position
            if (!CacheManager.instance().isInitialized()) return;
            CacheManager.instance().switchWallpaper(context);
        }
    }
}

