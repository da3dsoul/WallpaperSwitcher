package com.da3dsoul.WallpaperSwitcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class WallpaperSwitcher extends WallpaperService {

    public static final ConcurrentHashMap<UUID, WallpaperEngine> Instances = new ConcurrentHashMap<>();

    @Override
    public Engine onCreateEngine() {
        return new WallpaperEngine();
    }

    public class WallpaperEngine extends Engine implements OnSharedPreferenceChangeListener {
        private final UUID id;
        private final SharedPreferences sp;

        private final ScreenEventReceiver screenEventReceiver;
        private String path;
        private int currentIndex = 0;
        private String previousPath;
        private ArrayList<File> papers = null;
        private int bucketSize = 100;
        public final ArrayList<File> cache = new ArrayList<>(bucketSize);

        WallpaperEngine() {
            id = UUID.randomUUID();
            Instances.put(id, this);

            sp = WallpaperSwitcher.this.getSharedPreferences("wall", MODE_PRIVATE);
            sp.registerOnSharedPreferenceChangeListener(this);

            screenEventReceiver = new ScreenEventReceiver();
            IntentFilter screenStateFilter = new IntentFilter();
            screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
            registerReceiver(screenEventReceiver, screenStateFilter);

            init();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            unregisterReceiver(screenEventReceiver);
            Instances.remove(id);
        }

        private void drawFrame() {
            drawFrame(null);
        }

        private void drawFrame(SurfaceHolder holder) {
            if (Objects.equals(path, previousPath)) return;
            if (holder == null) holder = getSurfaceHolder();

            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) {
                    drawPaper(c);
                }
            } catch (Exception e) {
                // ignore
            } finally {
                if (c != null) holder.unlockCanvasAndPost(c);
            }
        }

        private void init() {
            // load from persist/settings
            String directories = sp.getString("dir", "");
            if (directories == null) {
                return;
            }
            String[] parsedDirectories = directories.split("\\|");
            for (String dir : parsedDirectories) {
                if (papers == null) papers = new ArrayList<>();
                File file = new File(dir);
                if (!file.exists() || !file.isDirectory()) continue;
                papers.add(file);
            }

            synchronized (cache) {
                String data = sp.getString("cache", "");
                if (data != null) {
                    String[] deserialized = data.split("\\|");
                    for (String item : deserialized) {
                        File file = new File(item);
                        if (!file.isFile() || !file.exists()) continue;
                        cache.add(file);
                    }
                }
                currentIndex = sp.getInt("currentIndex", 0);

                if (currentIndex < cache.size()) {
                    path = cache.get(currentIndex).getAbsolutePath();
                    if (currentIndex > 0)
                        previousPath = cache.get(currentIndex - 1).getAbsolutePath();
                }

                if (cache.isEmpty()) loadPapers();
                else WorkManager.getInstance(getApplicationContext())
                        .beginUniqueWork("loadPapers", ExistingWorkPolicy.KEEP,
                                getRequest(LoadPapersWorker.class)).enqueue();
            }
        }

        private void drawPaper(Canvas c) {
            Bitmap paper = null;
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
                c.drawText("CurrentIndex: " + currentIndex + "   Cache Size: " + cache.size(), 10, y, white);
                y += fontSize + 8;
                c.drawText("bucketSize: " + bucketSize + "   Instances: " + Instances.size(), 10, y, white);
                y += fontSize + 8;
                c.drawText("BucketSeed: " + sp.getLong("seed", 0), 10, y, white);

            }
            previousPath = path;
        }

        private OneTimeWorkRequest getRequest(Class<? extends ListenableWorker> cls)
        {
            OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(cls);
            Data.Builder data = new Data.Builder();
            data.putString("id", id.toString());
            builder.setInputData(data.build());
            return builder.build();
        }

        public void switchWallpaper() {
            synchronized (cache) {
                currentIndex++;
                if (cache.size() > 0) {
                    if (currentIndex >= bucketSize - 10) {
                        if (cache.size() < bucketSize * 2)
                            WorkManager.getInstance(getApplicationContext()).
                                    beginUniqueWork("loadPapers", ExistingWorkPolicy.KEEP,
                                            getRequest(LoadPapersWorker.class)).enqueue();

                        if (currentIndex >= bucketSize + 10)
                            WorkManager.getInstance(getApplicationContext())
                                    .beginUniqueWork("cleanCache", ExistingWorkPolicy.KEEP,
                                            getRequest(CleanCacheWorker.class)).enqueue();

                        if (currentIndex >= cache.size()) currentIndex = cache.size() - 1;
                        path = cache.get(currentIndex).getAbsolutePath();
                    } else {
                        path = cache.get(currentIndex).getAbsolutePath();
                        if (cache.size() > bucketSize)
                            WorkManager.getInstance(getApplicationContext())
                                    .beginUniqueWork("cleanCache", ExistingWorkPolicy.KEEP,
                                            getRequest(CleanCacheWorker.class)).enqueue();
                    }
                } else {
                    WorkManager.getInstance(getApplicationContext())
                            .beginUniqueWork("loadPapers", ExistingWorkPolicy.KEEP,
                                    getRequest(LoadPapersWorker.class)).enqueue();
                }
                sp.edit().putInt("currentIndex", currentIndex).apply();
                drawFrame();
            }
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);

            drawFrame(surfaceHolder);
        }

        private void loadPapers() {
            synchronized (cache) {
                if (!cache.isEmpty() && currentIndex < bucketSize - 10) return;
            }

            ArrayList<File> files = new ArrayList<>();
            for (File dir : papers) {
                recursivelyAddWallpapers(files, dir);
            }
            if (files.size() < bucketSize) bucketSize = files.size();

            files.removeAll(cache);

            if (files.size() <= 0) {
                path = null;
                currentIndex = 0;
                return;
            }

            synchronized (cache) {
                long seed = sp.getLong("seed", -1);
                Random rand;
                if (seed != -1) rand = new Random(seed);
                else rand = new Random();

                for (int i = 0; i < 100; i++) {
                    if (files.size() <= 0) break;
                    int index = rand.nextInt(files.size());
                    cache.add(files.get(index));
                    files.remove(index);
                }
                SharedPreferences.Editor edit = sp.edit();
                edit.putLong("seed", getSeed(rand));
                if (cache.size() > 0) {
                    path = cache.get(currentIndex).getAbsolutePath();
                    String[] paths = new String[cache.size()];
                    for (int i = 0; i < cache.size(); i++) {
                        paths[i] = cache.get(i).getAbsolutePath();
                    }
                    edit.putString("cache", String.join("|", paths));
                } else {
                    edit.putString("cache", "");
                }
                edit.apply();
            }
        }

        private long getSeed(Random random) {
            byte[] ba0, ba1, bar;
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(new Random(0));
                ba0 = baos.toByteArray();
                baos = new ByteArrayOutputStream(128);
                oos = new ObjectOutputStream(baos);
                oos.writeObject(new Random(-1));
                ba1 = baos.toByteArray();
                baos = new ByteArrayOutputStream(128);
                oos = new ObjectOutputStream(baos);
                oos.writeObject(random);
                bar = baos.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException("IOException: " + e);
            }
            if (ba0.length != ba1.length || ba0.length != bar.length)
                throw new RuntimeException("bad serialized length");
            int i = 0;
            while (i < ba0.length && ba0[i] == ba1[i]) {
                i++;
            }
            int j = ba0.length;
            while (j > 0 && ba0[j - 1] == ba1[j - 1]) {
                j--;
            }
            if (j - i != 6)
                throw new RuntimeException("6 differing bytes not found");
            // The constant 0x5DEECE66DL is from
            // http://download.oracle.com/javase/6/docs/api/java/util/Random.html .
            return ((bar[i] & 255L) << 40 | (bar[i + 1] & 255L) << 32 |
                    (bar[i + 2] & 255L) << 24 | (bar[i + 3] & 255L) << 16 |
                    (bar[i + 4] & 255L) << 8 | (bar[i + 5] & 255L)) ^ 0x5DEECE66DL;
        }

        private void cleanCache() {
            synchronized (cache) {
                if (cache.size() <= bucketSize || currentIndex < bucketSize) return;
                cache.subList(0, Math.min(cache.size(), bucketSize)).clear();
                cache.subList(bucketSize, cache.size()).clear();
                currentIndex -= bucketSize;
            }
        }

        private void recursivelyAddWallpapers(ArrayList<File> files, File source) {
            File[] items = source.listFiles();
            if (items == null) return;
            for (File item : items) {
                if (item.isDirectory()) {
                    recursivelyAddWallpapers(files, item);
                    continue;
                }
                if (!isImage(item.getName())) continue;
                files.add(item);
            }
        }

        private boolean isImage(String filename) {
            String name = filename.toLowerCase();
            if (name.endsWith(".jpg")) return true;
            if (name.endsWith(".jpeg")) return true;
            if (name.endsWith(".png")) return true;
            if (name.endsWith(".bmp")) return true;
            if (name.endsWith(".tiff")) return true;
            if (name.endsWith(".svg")) return true;
            return name.endsWith(".webp");
        }

        public void onVisibilityChanged(boolean visible) {
            if (visible) {
                drawFrame();
            }
        }

        public void shareCurrentWallpaper() {
            File file = new File(path);
            if (!file.exists()) return;
            try {
                Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                sharingIntent.setType("image/*");

                sharingIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getApplicationContext(), "com.da3dsoul.WallpaperSwitcher.fileprovider", file));
                Intent chooser = Intent.createChooser(sharingIntent, "Share " + path.substring(path.lastIndexOf('/') + 1));
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooser);
            } catch (Exception e) {
                Log.e("shareCurrentWallpaper", e.toString());
            }
        }

        public void openCurrentWallpaperOnPixiv() {
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
        }

        public void openCurrentWallpaper() {
            File file = new File(path);
            if (!file.exists()) return;
            try {
                Intent openIntent = new Intent(Intent.ACTION_VIEW);
                openIntent.setDataAndType(Uri.parse(file.getAbsolutePath()), "image/*");
                openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(openIntent);
            } catch (Exception e) {
                Log.e("openCurrentWallpaper", e.toString());
            }
        }

        public void openPreviousWallpaper() {
            if (currentIndex <= 0 || currentIndex - 1 >= cache.size()) return;

            String prev = cache.get(currentIndex - 1).getAbsolutePath();
            File file = new File(prev);
            if (!file.exists()) return;
            try {
                Intent openIntent = new Intent(Intent.ACTION_VIEW);
                openIntent.setDataAndType(Uri.parse(file.getAbsolutePath()), "image/*");
                openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(openIntent);
            } catch (Exception e) {
                Log.e("openPreviousWallpaper", e.toString());
            }
        }

        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key == null || key.equals("dir")) {
                String directories = sp.getString("dir", "");
                if (directories == null) {
                    drawFrame();
                    return;
                }
                String[] parsedDirectories = directories.split("\\|");
                for (String dir : parsedDirectories) {
                    if (papers == null) papers = new ArrayList<>();
                    File file = new File(dir);
                    if (!file.exists() || !file.isDirectory()) continue;
                    papers.add(file);
                }
                loadPapers();
                drawFrame();
            } else if (key.equals("debug_stats"))
                drawFrame();
        }
    }

    public static class LoadPapersWorker extends Worker {
        WallpaperEngine engine;
        public LoadPapersWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
            UUID uuid = UUID.fromString(getInputData().getString("id"));
            engine = WallpaperSwitcher.Instances.get(uuid);
        }

        @Override
        @NonNull
        public Result doWork() {
            if (engine == null) return Result.failure();
            ArrayList<File> cache = engine.cache;
            int currentIndex = engine.currentIndex;
            int bucketSize = engine.bucketSize;
            if (cache.size() > 0) {
                if (currentIndex >= bucketSize - 10) {
                    if (cache.size() < bucketSize * 2)
                        engine.loadPapers();
                }
            } else {
                engine.loadPapers();
            }

            // Indicate whether the work finished successfully with the Result
            return Result.success();
        }
    }

    public static class CleanCacheWorker extends Worker {
        WallpaperEngine engine;
        public CleanCacheWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
            UUID uuid = UUID.fromString(getInputData().getString("id"));
            engine = WallpaperSwitcher.Instances.get(uuid);
        }

        @Override
        @NonNull
        public Result doWork() {
            if (engine == null) return Result.failure();
            ArrayList<File> cache = engine.cache;
            int currentIndex = engine.currentIndex;
            int bucketSize = engine.bucketSize;

            if (cache.size() > 0) {
                if (currentIndex >= bucketSize - 10 && currentIndex >= bucketSize + 10)
                    engine.cleanCache();
                else if (cache.size() > bucketSize) engine.cleanCache();
            }

            // Indicate whether the work finished successfully with the Result
            return Result.success();
        }
    }

}