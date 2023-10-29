package com.da3dsoul.WallpaperSwitcher.Activity;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.da3dsoul.WallpaperSwitcher.CacheInstanceManager;
import com.da3dsoul.WallpaperSwitcher.HistoryRecyclerViewAdapter;
import com.da3dsoul.WallpaperSwitcher.ICacheManager;
import com.da3dsoul.WallpaperSwitcher.MainActivityListRecyclerViewAdapter;
import com.da3dsoul.WallpaperSwitcher.R;

import java.io.File;
import java.util.ArrayList;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setTitle("");
            setContentView(R.layout.main_activity);
            RecyclerView recyclerView = findViewById(R.id.main_activity_list);

            ArrayList<Object[]> matches = new ArrayList<>();
            matches.add(new Object[]{ "Settings", SettingsActivity.class });
            matches.add(new Object[]{ "Switch Wallpaper", SwitchWallpaperActivity.class });
            matches.add(new Object[]{ "Share Wallpaper", ShareWallpaperActivity.class });
            matches.add(new Object[]{ "Open Current Wallpaper", OpenCurrentWallpaperActivity.class });
            matches.add(new Object[]{ "Open Current Wallpaper on da3dsoul.dev", OpenCurrentWallpaperOnDa3dsoulActivity.class });
            matches.add(new Object[]{ "Open Current Wallpaper on Pixiv", OpenCurrentWallpaperOnPixivActivity.class });
            matches.add(new Object[]{ "Open Previous Wallpaper", OpenPreviousWallpaperActivity.class });
            matches.add(new Object[]{ "View History", ViewHistoryActivity.class });

            recyclerView.setAdapter(new MainActivityListRecyclerViewAdapter(matches));
        } catch (Exception e) {
            Log.e("WallpaperSwitcher.main", e.toString());
        }
    }


}