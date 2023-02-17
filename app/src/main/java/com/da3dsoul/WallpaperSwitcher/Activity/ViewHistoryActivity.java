package com.da3dsoul.WallpaperSwitcher.Activity;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
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
        if (!CacheManager.instance().isInitialized()) {
            finish();
            return;
        }

        try {
            setTitle("");
            setContentView(R.layout.history_listing_activity);
            RecyclerView recyclerView = findViewById(R.id.history_list);

            ArrayList<String> matches = new ArrayList<>();
            int index = CacheManager.instance().currentIndex;
            for (int i = index; i >= 0; i--)
            {
                matches.add(CacheManager.instance().cache.get(i).getAbsolutePath());
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