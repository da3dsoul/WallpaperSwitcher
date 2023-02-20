package com.da3dsoul.WallpaperSwitcher.Activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;

import com.da3dsoul.WallpaperSwitcher.CacheManager;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenCurrentWallpaperOnDa3dsoulActivity extends Activity {

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
        if (cache == null || !cache.isInitialized()) {
            finish();
            return;
        }

        String path = cache.path;
        File file = new File(path);
        if (!file.exists()) return;
        try {
            Pattern regex = Pattern.compile("(\\d+)(_p\\d+)", Pattern.CASE_INSENSITIVE);
            Pattern regex2 = Pattern.compile("(illust_)(\\d+)(_\\d+_\\d+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = regex.matcher(file.getName());
            Matcher matcher2 = regex2.matcher(file.getName());
            String id = null;
            if (matcher.find()) {
                id = matcher.group(1);
            } else if (matcher2.find()) {
                id = matcher2.group(2);
            }
            if (id == null) return;
            Intent openIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://da3dsoul.dev/Search?pixivid=" + id));
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(openIntent);
        } catch (Exception e) {
            Log.e("openCurrentWallpaper", e.toString());
        }

        finish();
    }
}