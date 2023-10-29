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
import com.da3dsoul.WallpaperSwitcher.R;
import com.da3dsoul.WallpaperSwitcher.WallpaperSwitcher;

import java.io.File;
import java.util.ArrayList;

public class ViewHistoryActivity  extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DisplayMetrics metrics = WallpaperSwitcher.getDisplayMetrics(this);

        if (metrics == null) {
            finish();
            return;
        }

        double aspect = (double)metrics.widthPixels/metrics.heightPixels;
        ICacheManager cache = CacheInstanceManager.instanceForCanvas(aspect);
        if (cache == null || cache.needsInitialized()) {
            finish();
            return;
        }

        try {
            setTitle("");
            setContentView(R.layout.history_listing_activity);
            RecyclerView recyclerView = findViewById(R.id.history_list);

            ArrayList<String> matches = new ArrayList<>();
            int index = cache.getCurrentIndex();
            for (int i = index; i >= 0; i--)
            {
                File file = cache.get(i);
                if (file == null || !file.exists()) continue;
                matches.add(file.getAbsolutePath());
            }

            recyclerView.setAdapter(new HistoryRecyclerViewAdapter(matches));
            if (matches.isEmpty()) {
                recyclerView.setVisibility(View.INVISIBLE);
                TextView noResults = findViewById(R.id.no_results);
                noResults.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.e("viewHistory", e.toString());
        }
    }
}