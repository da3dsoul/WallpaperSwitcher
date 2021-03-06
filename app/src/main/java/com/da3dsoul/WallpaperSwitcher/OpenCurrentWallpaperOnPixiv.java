package com.da3dsoul.WallpaperSwitcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenCurrentWallpaperOnPixiv extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!CacheManager.instance().isInitialized()) {
            finish();
            return;
        }

        String path = CacheManager.instance().path;
        File file = new File(path);
        if (!file.exists()) return;
        try {
            Pattern regex = Pattern.compile("([0-9]+)(_p[0-9]+)", Pattern.CASE_INSENSITIVE);
            Pattern regex2 = Pattern.compile("(illust_)([0-9]+)(_[0-9]+_[0-9]+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = regex.matcher(file.getName());
            Matcher matcher2 = regex2.matcher(file.getName());
            String id = null;
            if (matcher.find()) {
                id = matcher.group(1);
            } else if (matcher2.find()) {
                id = matcher2.group(2);
            }
            if (id == null) return;
            Intent openIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://pixiv.net/en/artworks/" + id));
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            openIntent.setComponent(new ComponentName("jp.pxv.android", "jp.pxv.android.activity.IntentFilterActivity"));
            startActivity(openIntent);
        } catch (Exception e) {
            Log.e("openCurrentWallpaper", e.toString());
        }

        finish();
    }
}