package com.da3dsoul.WallpaperSwitcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.service.wallpaper.WallpaperService;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.util.Locale;


public class WallpaperSwitcher extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new WallpaperEngine();
    }

    public class WallpaperEngine extends Engine implements OnSharedPreferenceChangeListener, INotifyWallpaperChanged {
        private final SharedPreferences sp;

        private final BroadcastReceiver screenEventReceiver;
        private boolean noDraw = false;

        WallpaperEngine() {

            sp = WallpaperSwitcher.this.getSharedPreferences("wall", MODE_PRIVATE);
            sp.registerOnSharedPreferenceChangeListener(this);

            screenEventReceiver = new ScreenEventReceiver();
            registerReceiver(screenEventReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));

            String directorySetting = sp.getString("directories", null);
            if (directorySetting == null || directorySetting.equals("")) return;
            Gson gson = new Gson();
            DirectoryModel[] directories = gson.fromJson(directorySetting, DirectoryModel[].class);
            for (DirectoryModel model : directories) {
                ICacheManager cache = CacheInstanceManager.instance(model.MinAspect, model.MaxAspect);
                cache.init(getApplicationContext());
                cache.subscribe(this);
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            unregisterReceiver(screenEventReceiver);
            for (ICacheManager cache : CacheInstanceManager.allInstances()) {
                cache.unsubscribe(this);
            }

            noDraw = true;
        }

        public void drawFrame() {
            drawFrame(null, (SurfaceHolder) null);
        }

        private void drawFrame(ICacheManager cache, SurfaceHolder holder) {
            if (noDraw) return;
            if (holder == null) holder = getSurfaceHolder();
            if (!holder.getSurface().isValid()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                holder.getSurface().setFrameRate(0, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
            }

            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) {
                    drawFrame(cache, c);
                }
            } catch (Exception e) {
                // ignore
            } finally {
                if (c != null) holder.unlockCanvasAndPost(c);
            }
        }

        private void drawFrame(ICacheManager cache, Canvas c) {
            Bitmap paper = null;

            if (cache == null) cache = CacheInstanceManager.instanceForCanvas((double)c.getWidth() / c.getHeight());
            if (cache == null || cache.needsInitialized()) return;
            int height = c.getHeight();
            int width = c.getWidth();
            Paint black = new Paint();
            black.setARGB(255, 0, 0, 0);
            c.drawRect(0, 0, width, height, black);

            String path = cache.getCurrentPath();
            if (path != null && !path.equals("")) paper = BitmapFactory.decodeFile(path);
            if (paper != null) {
                double ar = (double) paper.getWidth() / paper.getHeight();
                Bitmap output = Bitmap.createScaledBitmap(paper, width, (int) Math.round(width / ar), true);
                c.drawBitmap(output, 0, (int) Math.floor(height / 2D - output.getHeight() / 2D), null);
            } else if (cache.getProgress().TotalFiles != 0 || cache.getProgress().AddedFiles != 0)
            {
                Paint white = new Paint();
                white.setARGB(255, 255, 255, 255);
                white.setShadowLayer(4, 2, 2, 0);
                white.setTextAlign(Paint.Align.CENTER);
                int lines = 3;
                int buffer = 24;
                int fontSize = 64;
                white.setTextSize(fontSize);
                float totalHeight = (white.ascent() + white.descent()) * lines + buffer * (lines - 1);
                int y = (int) Math.floor(c.getHeight() / 2D - totalHeight / 2D);
                int x = (int) Math.floor(c.getWidth() / 2D);
                c.drawText("Building Cache", x, y, white);
                y += fontSize + buffer;
                c.drawText(String.format(Locale.ENGLISH, "%.1f%%", cache.getProgress().PercentComplete * 100), x, y, white);
                y += fontSize + buffer;
                c.drawText(String.format(Locale.ENGLISH, "%s/%s", cache.getProgress().AddedFiles, cache.getProgress().TotalFiles), x, y, white);
            }

            if (sp.getBoolean("debug_stats", false)) {
                cache.drawDebugStats(c);
            }
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);

            drawFrame(null, surfaceHolder);
        }

        public void onVisibilityChanged(boolean visible) {
            if (visible) {
                drawFrame();
            }
        }

        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key == null || key.equals("directories")) {
                for (ICacheManager cache : CacheInstanceManager.allInstances()) {
                    cache.onSharedPreferencesChanged(sharedPreferences, getApplicationContext());
                }

                drawFrame();
            } else if (key.equals("debug_stats") || sharedPreferences.getBoolean("debug_stats", false))
                drawFrame();
            else if (key.equals("cache"))
                drawFrame();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            drawFrame(null, holder);
        }

        @Override
        public void onSurfaceRedrawNeeded(SurfaceHolder holder) {
            super.onSurfaceRedrawNeeded(holder);
            drawFrame(null, holder);
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            drawFrame(null, holder);
        }

        @Override
        public void WallpaperChanged(ICacheManager cache) {
            drawFrame(cache, (SurfaceHolder)null);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
        }
    }

    @Nullable
    public static DisplayMetrics getDisplayMetrics(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return getDisplayMetricsFallback(context);

        try {
            Display display = context.getDisplay();
            if (display != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                // deprecated because of multi-window, but we want the full display for a live wallpaper
                display.getRealMetrics(metrics);
                if (metrics.widthPixels != 0 && metrics.heightPixels != 0) return metrics;
                return getDisplayMetricsFallback(context);
            }
        } catch (UnsupportedOperationException e)
        {
            Log.e("WallpaperSwitcher", "Unable to get Display: " + e);
        }

        return getDisplayMetricsFallback(context);
    }

    @Nullable
    private static DisplayMetrics getDisplayMetricsFallback(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        if (metrics.widthPixels == 0 || metrics.heightPixels == 0) return null;
        return metrics;
    }
}