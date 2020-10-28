package com.da3dsoul.WallpaperSwitcher;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

public class CacheManager {
    // region variables and init
    private static CacheManager _instance = null;

    public static CacheManager instance()
    {
        if (_instance == null) _instance = new CacheManager();
        return _instance;
    }

    private final HashSet<INotifyWallpaperChanged> listeners = new HashSet<>();

    private boolean isInitialized;
    public String path;
    public int currentIndex = 0;
    public ArrayList<File> papers = new ArrayList<>();
    public int bucketSize = 100;
    public final ArrayList<File> cache = new ArrayList<>(bucketSize);
    public int cacheSize;
    private SharedPreferences sp;

    public boolean isInitialized() {
        return isInitialized;
    }

    public void init(Context c) {
        if (isInitialized) return;
        // load from persist/settings
        sp = c.getSharedPreferences("wall", Context.MODE_PRIVATE);

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

            cacheSize = cache.size();
            currentIndex = sp.getInt("currentIndex", 0);

            if (cacheSize == 0) populateCache();
            else queueCachePopulation(c);

            if (currentIndex < cacheSize) {
                path = cache.get(currentIndex).getAbsolutePath();
            }
        }
        isInitialized = true;
    }
    // endregion

    // region INotify
    public void subscribe(INotifyWallpaperChanged listener)
    {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void unsubscribe(INotifyWallpaperChanged listener)
    {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners()
    {
        synchronized (listeners) {
            for (INotifyWallpaperChanged item : listeners) {
                item.WallpaperChanged();
            }
        }
    }
    // endregion

    // region Cache Management
    public void populateCache() {
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
            cacheSize = 0;
            return;
        }

        synchronized (cache) {
            long seed = sp.getLong("seed", -1);
            Random rand;
            if (seed != -1) rand = new Random(seed);
            else rand = new Random();

            cache.addAll(pickNRandomElements(files, bucketSize, rand));
            cacheSize = cache.size();

            SharedPreferences.Editor edit = sp.edit();
            edit.putLong("seed", getSeed(rand));
            if (cacheSize > 0) {
                path = cache.get(currentIndex).getAbsolutePath();
                String[] paths = new String[cacheSize];
                for (int i = 0; i < cacheSize; i++) {
                    paths[i] = cache.get(i).getAbsolutePath();
                }
                edit.putString("cache", String.join("|", paths));
            } else {
                edit.putString("cache", "");
            }
            edit.apply();
        }
    }

    private void cleanCache() {
        synchronized (cache) {
            if (cacheSize <= bucketSize || currentIndex < bucketSize) return;
            cache.subList(0, Math.min(cacheSize, bucketSize)).clear();
            cacheSize = cache.size();
            cache.subList(bucketSize, cacheSize).clear();
            cacheSize = cache.size();
            currentIndex -= bucketSize;
        }
    }

    public File getCacheIndex(int i)
    {
        if (cacheSize == 0 || i < 0 || i >= cacheSize) return null;
        synchronized (cache)
        {
            return cache.get(i);
        }
    }
    // endregion

    public void switchWallpaper(Context c) {
        synchronized (cache) {
            currentIndex++;
            if (cacheSize > 0) {
                if (currentIndex >= bucketSize - 10) {
                    if (currentIndex >= cacheSize) {
                        populateCache();
                    }

                    if (cacheSize < bucketSize * 2)
                        queueCachePopulation(c);

                    if (currentIndex >= bucketSize + 10)
                        queueCacheCleanup(c);

                    path = cache.get(currentIndex).getAbsolutePath();
                } else {
                    path = cache.get(currentIndex).getAbsolutePath();
                    if (cacheSize > bucketSize)
                        queueCacheCleanup(c);
                }
            } else {
                queueCachePopulation(c);
            }
            sp.edit().putInt("currentIndex", currentIndex).apply();
        }
        notifyListeners();
    }

    // region Utility methods
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

    public static <E> List<E> pickNRandomElements(List<E> list, int n, Random r) {
        int length = list.size();

        if (length < n) return new ArrayList<>();

        //We don't need to shuffle the whole list
        for (int i = length - 1; i >= length - n; --i)
        {
            Collections.swap(list, i , r.nextInt(i + 1));
        }
        return list.subList(length - n, length);
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
    // endregion

    // region Worker Handling
    public void queueCachePopulation(Context c)
    {
        WorkManager.getInstance(c).beginUniqueWork("loadPapers", ExistingWorkPolicy.KEEP,
                getRequest(CachePopulationWorker.class)).enqueue();
    }

    public void queueCacheCleanup(Context c)
    {
        WorkManager.getInstance(c).beginUniqueWork("cleanCache", ExistingWorkPolicy.KEEP,
                getRequest(CleanCacheWorker.class)).enqueue();
    }

    private OneTimeWorkRequest getRequest(Class<? extends ListenableWorker> cls)
    {
        return OneTimeWorkRequest.from(cls);
    }

    public static class CachePopulationWorker extends Worker {
        public CachePopulationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @Override
        @NonNull
        public Result doWork() {
            instance().populateCache();

            // Indicate whether the work finished successfully with the Result
            return Result.success();
        }
    }

    public static class CleanCacheWorker extends Worker {
        public CleanCacheWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @Override
        @NonNull
        public Result doWork() {
            instance().cleanCache();

            // Indicate whether the work finished successfully with the Result
            return Result.success();
        }
    }
    // endregion
}
