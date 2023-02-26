package com.da3dsoul.WallpaperSwitcher.Activity;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.da3dsoul.WallpaperSwitcher.CacheManager;
import com.da3dsoul.WallpaperSwitcher.HistoryRecyclerViewAdapter;
import com.da3dsoul.WallpaperSwitcher.R;

import java.io.File;
import java.util.ArrayList;

public class ViewHistoryActivity  extends Activity {

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

        try {
            setTitle("");
            setContentView(R.layout.history_listing_activity);
            RecyclerView recyclerView = findViewById(R.id.history_list);

            ArrayList<String> matches = new ArrayList<>();
            int index = cache.currentIndex;
            for (int i = index; i >= 0; i--)
            {
                File file = cache.cache.get(i);
                if (!file.exists()) continue;
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