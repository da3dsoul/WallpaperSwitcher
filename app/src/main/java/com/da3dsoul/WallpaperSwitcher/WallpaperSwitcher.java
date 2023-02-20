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
import android.view.SurfaceHolder;

import com.google.gson.Gson;


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

            screenEventReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction() == null) return;
                    if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                        try {
                            //The screen on position
                            DisplayMetrics metrics = new DisplayMetrics();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                getDisplayContext().getDisplay().getRealMetrics(metrics);
                            }

                            if (metrics.widthPixels == 0 || metrics.heightPixels == 0) return;
                            double aspect = (double) metrics.widthPixels / metrics.heightPixels;
                            CacheManager cache = CacheManager.instanceForCanvas(aspect);
                            if (cache == null || !cache.isInitialized()) return;
                            cache.switchWallpaper(context);
                        } catch (Exception e)
                        {
                            Log.e("WallpaperSwitcher", e.toString());
                        }
                    }
                }
            };
            IntentFilter screenStateFilter = new IntentFilter();
            screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
            registerReceiver(screenEventReceiver, screenStateFilter);

            String directorySetting = sp.getString("directories", null);
            if (directorySetting == null || directorySetting.equals("")) return;
            Gson gson = new Gson();
            DirectoryModel[] directories = gson.fromJson(directorySetting, DirectoryModel[].class);
            for (DirectoryModel model : directories) {
                CacheManager cache = CacheManager.instance(model.MinAspect, model.MaxAspect);
                cache.init(getApplicationContext());
                cache.subscribe(this);
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            unregisterReceiver(screenEventReceiver);
            for (CacheManager cache : CacheManager.allInstances()) {
                cache.unsubscribe(this);
            }

            noDraw = true;
        }

        public void drawFrame() {
            drawFrame(null, (SurfaceHolder) null);
        }

        private void drawFrame(CacheManager cache, SurfaceHolder holder) {
            if (noDraw) return;
            if (holder == null) holder = getSurfaceHolder();

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

        private void drawFrame(CacheManager cache, Canvas c) {
            Bitmap paper = null;

            if (cache == null) cache = CacheManager.instanceForCanvas((double)c.getWidth() / c.getHeight());
            if (cache == null || !cache.isInitialized()) return;
            String path = cache.path;
            if (path != null && !path.equals("")) paper = BitmapFactory.decodeFile(path);
            if (paper == null) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 1;
                paper = BitmapFactory.decodeResource(getResources(), R.drawable.saber_lily, opts);
            }
            int height = Math.max(c.getWidth(), c.getHeight());
            int width = Math.min(c.getWidth(), c.getHeight());
            Paint black = new Paint();
            black.setARGB(255, 0, 0, 0);
            c.drawRect(0, 0, width, height, black);
            double ar = (double) paper.getWidth() / paper.getHeight();
            Bitmap output = Bitmap.createScaledBitmap(paper, width, (int) Math.round(width / ar), true);
            c.drawBitmap(output, 0, (int) Math.floor(height / 2D - output.getHeight() / 2D), null);
            if (sp.getBoolean("debug_stats", false)) {
                Paint white = new Paint();
                white.setARGB(255, 255, 255, 255);
                white.setShadowLayer(4, 2, 2, 0);
                int fontSize = 24;
                white.setTextSize(fontSize);
                int y = 100;
                c.drawText("Path: " + path, 10, y, white);
                y += fontSize + 8;
                c.drawText("CurrentIndex: " + cache.currentIndex +
                        "   Cache Size: " + cache.cacheSize, 10, y, white);
                y += fontSize + 8;
                c.drawText("bucketSize: " + cache.bucketSize + "   baseBucketSize: "
                        + CacheManager.baseBucketSize, 10, y, white);
                y += fontSize + 8;
                c.drawText("ReadAhead: " + CacheManager.cacheReadAhead + "   BucketSeed: " + sp.getLong("seed", 0), 10, y, white);
                y += fontSize + 8;
                c.drawText("Canvas Width: " + c.getWidth() + "   Canvas Height: " + c.getHeight(), 10, y, white);
                y += fontSize + 8;
                c.drawText("Cache minAspect: " + cache.minAspect + "   Cache maxAspect: " + cache.maxAspect + "   Aspect: " + (double)c.getWidth() / c.getHeight(), 10, y, white);
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
                for (CacheManager cache : CacheManager.allInstances()) {
                    cache.sourceDirectories.clear();
                    cache.ParseSourceDirectories(sharedPreferences);
                    if (cache.sourceDirectories.size() > 0)
                        cache.queueCacheRebuild(getApplicationContext());
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
        public void WallpaperChanged(CacheManager cache) {
            drawFrame(cache, (SurfaceHolder)null);
        }
    }

}