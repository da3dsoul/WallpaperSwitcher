package com.da3dsoul.WallpaperSwitcher;

import android.app.Activity;
import android.os.Bundle;

import java.util.Map;
import java.util.UUID;

public class OpenPreviousWallpaper extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (WallpaperSwitcher.Instances.isEmpty()) {
            finish();
            return;
        }
        for (Map.Entry<UUID, WallpaperSwitcher.WallpaperEngine> entry : WallpaperSwitcher.Instances.entrySet())
        {
            entry.getValue().openPreviousWallpaper();
            break;
        }
        finish();
    }
}