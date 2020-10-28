package com.da3dsoul.WallpaperSwitcher;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.io.File;

public class WallpaperSwitcher extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new WallpaperEngine();
    }

    public class WallpaperEngine extends Engine implements OnSharedPreferenceChangeListener, INotifyWallpaperChanged {
        private final SharedPreferences sp;

        private final ScreenEventReceiver screenEventReceiver;
        private boolean noDraw = false;

        WallpaperEngine() {

            sp = WallpaperSwitcher.this.getSharedPreferences("wall", MODE_PRIVATE);
            sp.registerOnSharedPreferenceChangeListener(this);

            screenEventReceiver = new ScreenEventReceiver();
            IntentFilter screenStateFilter = new IntentFilter();
            screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
            registerReceiver(screenEventReceiver, screenStateFilter);

            CacheManager.instance().init(getApplicationContext());
            CacheManager.instance().subscribe(this);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            unregisterReceiver(screenEventReceiver);
            CacheManager.instance().unsubscribe(this);
            noDraw = true;
        }

        public void drawFrame() {
            drawFrame((SurfaceHolder) null);
        }

        private void drawFrame(SurfaceHolder holder) {
            if (noDraw) return;
            if (holder == null) holder = getSurfaceHolder();

            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) {
                    drawFrame(c);
                }
            } catch (Exception e) {
                // ignore
            } finally {
                if (c != null) holder.unlockCanvasAndPost(c);
            }
        }

        private void drawFrame(Canvas c) {
            Bitmap paper = null;
            String path = CacheManager.instance().path;
            if (path != null) paper = BitmapFactory.decodeFile(path);
            if (paper == null) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 1;
                paper = BitmapFactory.decodeResource(getResources(), R.drawable.saber_lily, opts);
            }
            int height = Math.max(c.getWidth(), c.getHeight());
            int width = Math.min(c.getWidth(), c.getHeight());
            double ar = (double) paper.getWidth() / paper.getHeight();
            Paint black = new Paint();
            black.setARGB(255, 0, 0, 0);
            c.drawRect(0, 0, width, height, black);
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
                c.drawText("CurrentIndex: " + CacheManager.instance().currentIndex +
                        "   Cache Size: " + CacheManager.instance().cacheSize + "   bucketSize: " +
                        CacheManager.instance().bucketSize, 10, y, white);
                y += fontSize + 8;
                c.drawText("BucketSeed: " + sp.getLong("seed", 0), 10, y, white);

            }
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);

            drawFrame(surfaceHolder);
        }

        public void onVisibilityChanged(boolean visible) {
            if (visible) {
                drawFrame();
            }
        }

        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key == null || key.equals("dir")) {
                String directories = sharedPreferences.getString("dir", "");
                if (directories == null) {
                    drawFrame();
                    return;
                }
                CacheManager.instance().papers.clear();
                String[] parsedDirectories = directories.split("\\|");
                for (String dir : parsedDirectories) {
                    File file = new File(dir);
                    if (!file.exists() || !file.isDirectory()) continue;
                    CacheManager.instance().papers.add(file);
                }

                CacheManager.instance().populateCache();
                drawFrame();
            } else if (key.equals("debug_stats") || sharedPreferences.getBoolean("debug_stats", false))
                drawFrame();
        }

        @Override
        public void WallpaperChanged() {
            drawFrame();
        }
    }

}