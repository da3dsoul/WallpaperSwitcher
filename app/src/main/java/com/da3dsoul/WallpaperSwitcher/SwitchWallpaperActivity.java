package com.da3dsoul.WallpaperSwitcher;

import android.app.Activity;
import android.os.Bundle;

import java.util.Map;
import java.util.UUID;

public class SwitchWallpaperActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (CacheManager.instance().isInitialized())
        {
            CacheManager.instance().switchWallpaper(getApplicationContext());
        }
        finish();
    }
}