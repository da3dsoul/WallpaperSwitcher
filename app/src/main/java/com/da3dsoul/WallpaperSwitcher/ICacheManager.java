package com.da3dsoul.WallpaperSwitcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;

import java.io.File;

public interface ICacheManager {
    CacheProgress getProgress();

    double getMinAspect();

    double getMaxAspect();
    int getBucketSize();
    void setBucketSize(int size);

    int getCurrentIndex();
    File get(int index);
    String getCurrentPath();

    void switchWallpaper(Context c);

    boolean needsInitialized();

    void init(Context c);

    void reset(Context context);

    void cleanCache();

    void populateCache();

    void subscribe(INotifyWallpaperChanged listener);

    void unsubscribe(INotifyWallpaperChanged listener);

    void drawDebugStats(Canvas c);

    void onSharedPreferencesChanged(SharedPreferences sharedPreferences, Context context);
}