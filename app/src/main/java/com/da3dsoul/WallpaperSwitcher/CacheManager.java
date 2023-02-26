package com.da3dsoul.WallpaperSwitcher;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.work.BackoffPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ForegroundInfo;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class CacheManager {
    // region variables and init
    public static int baseBucketSize = 100;
    public static int cacheReadAhead = 50;
    private static final HashMap<String, CacheManager> _instances = new HashMap<>();
    private final HashSet<INotifyWallpaperChanged> listeners = new HashSet<>();
    public final double minAspect;
    public final double maxAspect;
    public String path;
    public int currentIndex = 0;
    public ArrayList<File> sourceDirectories = new ArrayList<>();
    public int bucketSize = baseBucketSize;
    public final ArrayList<File> cache = new ArrayList<>(bucketSize);
    public int cacheSize;
    private boolean isInitialized;
    private SharedPreferences sp;

    public CacheManager(double minAspect, double maxAspect) {
        this.minAspect = minAspect;
        this.maxAspect = maxAspect;
    }

    public String getKey()
    {
        return getKey(minAspect, maxAspect);
    }

    public static String getKey(double minAspect, double maxAspect)
    {
        return String.format(Locale.ENGLISH, "%.1f:%.1f", minAspect, maxAspect);
    }

    public static CacheManager instance(double minAspect, double maxAspect)
    {
        String key = getKey(minAspect, maxAspect);
        if (!_instances.containsKey(key)) _instances.put(key, new CacheManager(minAspect, maxAspect));
        return _instances.get(key);
    }

    public static CacheManager instanceForCanvas(double aspect)
    {
        for (CacheManager cache : _instances.values()) {
            if (aspect >= cache.minAspect && aspect <= cache.maxAspect) return cache;
        }
        return null;
    }

    public static CacheManager[] allInstances()
    {
        return _instances.values().toArray(new CacheManager[0]);
    }

    public boolean needsInitialized() {
        return !isInitialized;
    }

    public void init(Context c) {
        if (isInitialized) return;
        NotificationChannel channel = new NotificationChannel("WallpaperSwitcher", "WallpaperSwitcher", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("WallpaperSwitcher");
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        NotificationManager notificationManager = c.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
        // load from persist/settings
        sp = c.getSharedPreferences("wall", Context.MODE_PRIVATE);

        if (ParseSourceDirectories(sp)) return;

        synchronized (cache) {
            String data = sp.getString("cache", "");
            if (!data.equals("")) {
                try
                {
                    Gson gson = new Gson();
                    Type collectionType = new TypeToken<HashMap<String,String[]>>(){}.getType();
                    HashMap<String,String[]> files = gson.fromJson(data, collectionType);
                    String key = getKey();
                    if (files.containsKey(key)) {
                        for (String item : Objects.requireNonNull(files.get(key))) {
                            File file = new File(item);
                            if (!file.isFile() || !file.exists()) continue;
                            cache.add(file);
                        }
                    }
                } catch (Exception e)
                {
                    // ignore
                }
            }

            cacheSize = cache.size();
            String key = getKey();
            Gson gson = new Gson();
            try {
                Type collectionType = new TypeToken<HashMap<String, Integer>>() {}.getType();
                HashMap<String,Integer> indexMap = gson.fromJson(sp.getString("currentIndexes", null), collectionType);
                if (indexMap != null && indexMap.containsKey(key)) currentIndex = indexMap.get(key);
                else currentIndex = 0;
            } catch (Exception e)
            {
                currentIndex = 0;
            }

            baseBucketSize = Math.max(sp.getInt("bucketSize", baseBucketSize), 0);
            bucketSize = baseBucketSize;
            cacheReadAhead = Math.max(sp.getInt("readAhead", cacheReadAhead), 0);

            queueCachePopulation(c);

            if (currentIndex < cacheSize) {
                path = cache.get(currentIndex).getAbsolutePath();
            }
        }
        isInitialized = true;
    }

    public boolean ParseSourceDirectories(SharedPreferences sharedPreferences) {
        String directorySetting = sharedPreferences.getString("directories", null);
        if (directorySetting == null || directorySetting.equals("")) return true;
        Gson gson = new Gson();
        DirectoryModel[] directories = gson.fromJson(directorySetting, DirectoryModel[].class);

        for (DirectoryModel dir : directories) {
            if (Math.abs(dir.MinAspect - minAspect) > 0.001D) continue;
            if (Math.abs(dir.MaxAspect - maxAspect) > 0.001D) continue;
            File file = new File(dir.Directory);
            if (!file.exists() || !file.isDirectory()) continue;
            sourceDirectories.add(file);
        }
        return false;
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
                item.WallpaperChanged(this);
            }
        }
    }
    // endregion

    // region Cache Management
    public void populateCache() {
        List<File> temp = new ArrayList<>(cache);
        if (cacheSize > bucketSize) return;
        if (cacheSize == bucketSize && currentIndex < cacheSize - cacheReadAhead) return;

        ArrayList<File> files = new ArrayList<>();
        for (File dir : sourceDirectories) {
            recursivelyAddWallpapers(files, dir);
        }
        if (files.size() < bucketSize) bucketSize = files.size();
        else if (bucketSize < baseBucketSize) {
            files.size();
            bucketSize = baseBucketSize;
        }

        files.removeAll(temp);

        if (files.size() <= 0) {
            path = null;
            currentIndex = 0;
            cacheSize = 0;
            return;
        }

        synchronized (cache) {
            HashMap<String,Long> seedMap;
            String key = getKey();
            Gson gson = new Gson();
            try {
                Type collectionType = new TypeToken<HashMap<String,Long>>() {}.getType();
                seedMap = gson.fromJson(sp.getString("seed", ""), collectionType);
            } catch (Exception e)
            {
                seedMap = new HashMap<>();
            }
            long seed = seedMap.containsKey(key) ? seedMap.get(key) : -1;
            Random rand = seed != -1 ? new Random(seed) : new Random();

            cache.addAll(pickNRandomElements(files, bucketSize, rand));
            cacheSize = cache.size();

            seedMap.put(key, getSeed(rand));

            SharedPreferences.Editor edit = sp.edit();
            edit.putString("seed", gson.toJson(seedMap));

            HashMap<String,String[]> filesMap;
            try {
                Type collectionType = new TypeToken<HashMap<String, String[]>>() {}.getType();
                filesMap = gson.fromJson(sp.getString("cache", ""), collectionType);
            } catch (Exception e)
            {
                filesMap = new HashMap<>();
            }

            if (cacheSize > 0) {
                if (currentIndex < cacheSize)
                    path = cache.get(currentIndex).getAbsolutePath();
                else
                    path = cache.get(0).getAbsolutePath();

                String[] paths = new String[cacheSize];
                for (int i = 0; i < cacheSize; i++) {
                    paths[i] = cache.get(i).getAbsolutePath();
                }

                filesMap.put(key, paths);
                edit.putString("cache", gson.toJson(filesMap));
            } else {
                filesMap.remove(key);
                edit.putString("cache", gson.toJson(filesMap));
            }
            edit.apply();
        }

        if (temp.size() <= 0) notifyListeners();
    }

    private void cleanCache() {
        synchronized (cache) {
            if (cacheSize <= bucketSize || currentIndex < bucketSize) return;
            int originalSize = cache.size();
            currentIndex -= bucketSize;
            cache.subList(0, originalSize - bucketSize).clear();
            cacheSize = cache.size();


            SharedPreferences.Editor edit = sp.edit();
            String[] paths = new String[cacheSize];
            for (int i = 0; i < cacheSize; i++) {
                paths[i] = cache.get(i).getAbsolutePath();
            }

            HashMap<String,String[]> filesMap;
            HashMap<String,Integer> indexMap;
            String key = getKey();
            Gson gson = new Gson();
            try {
                Type collectionType = new TypeToken<HashMap<String, String[]>>() {}.getType();
                filesMap = gson.fromJson(sp.getString("cache", ""), collectionType);
            } catch (Exception e)
            {
                filesMap = new HashMap<>();
            }

            filesMap.put(key, paths);
            edit.putString("cache", gson.toJson(filesMap));

            try {
                Type collectionType = new TypeToken<HashMap<String, Integer>>() {}.getType();
                indexMap = gson.fromJson(sp.getString("currentIndexes", null), collectionType);
                if (indexMap == null) indexMap = new HashMap<>();
            } catch (Exception e)
            {
                indexMap = new HashMap<>();
            }

            indexMap.put(key, currentIndex);
            edit.putString("currentIndexes", gson.toJson(indexMap));

            edit.apply();
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
            File currentFile;
            do {
                currentIndex++;
            } while ((currentFile = getCacheIndex(currentIndex)) != null && currentFile.exists());

            if (cacheSize > 0) {
                if (currentIndex >= cacheSize - cacheReadAhead) {
                    // it's not done, so do it now
                    if (currentIndex >= cacheSize) {
                        waitForPopulation(c);
                    }

                    // if cache is not read ahead
                    if (cacheSize < bucketSize * 2)
                        queueCachePopulation(c);

                    // cleanup cache
                    if (currentIndex >= bucketSize + 5)
                        queueCacheCleanup(c);

                    path = cache.get(currentIndex).getAbsolutePath();
                } else {
                    path = cache.get(currentIndex).getAbsolutePath();
                    if (cacheSize > bucketSize && currentIndex >= bucketSize)
                        queueCacheCleanup(c);
                }
            } else {
                // no cache, so do it now
                waitForPopulation(c);
            }

            HashMap<String,Integer> indexMap;
            String key = getKey();
            Gson gson = new Gson();

            try {
                Type collectionType = new TypeToken<HashMap<String, Integer>>() {}.getType();
                indexMap = gson.fromJson(sp.getString("currentIndexes", null), collectionType);
                if (indexMap == null) indexMap = new HashMap<>();
            } catch (Exception e)
            {
                indexMap = new HashMap<>();
            }

            indexMap.put(key, currentIndex);
            sp.edit().putString("currentIndexes", gson.toJson(indexMap)).apply();
        }
        notifyListeners();
    }

    private void waitForPopulation(Context c) {
        ListenableFuture<List<WorkInfo>> workInfos = WorkManager.getInstance(c).getWorkInfosForUniqueWork("WallpaperSwitcher.loadPapers");
        try {
            List<WorkInfo> infos = workInfos.get();
            if (infos.size() > 0)
            {
                while (!infos.get(0).getState().isFinished()) {
                    Thread.sleep(50);
                }
            }
        } catch (Exception e) {
            // ignore
        }
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
        WorkManager.getInstance(c).enqueueUniqueWork("WallpaperSwitcher.loadPapers",
                ExistingWorkPolicy.KEEP, getRequest(CachePopulationWorker.class));
    }

    public void queueCacheCleanup(Context c)
    {
        WorkManager.getInstance(c).enqueueUniqueWork("WallpaperSwitcher.cleanCache",
                ExistingWorkPolicy.KEEP, getRequest(CleanCacheWorker.class));
    }

    public void queueCacheRebuild(Context c)
    {
        WorkManager.getInstance(c).enqueueUniqueWork("WallpaperSwitcher.rebuildCache",
                ExistingWorkPolicy.KEEP, getRequest(RebuildCacheWorker.class));
    }

    private OneTimeWorkRequest getRequest(Class<? extends ListenableWorker> cls)
    {
        return new OneTimeWorkRequest.Builder(cls)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.LINEAR, OneTimeWorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build();
    }

    public static class CachePopulationWorker extends Worker {
        private final Context context;
        public CachePopulationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
            this.context = context;
        }

        @Override
        @NonNull
        public Result doWork() {
            for (CacheManager cache : _instances.values()) {
                if (!cache.isInitialized) cache.init(context);
                cache.populateCache();
            }

            // Indicate whether the work finished successfully with the Result
            return Result.success();
        }

        @NonNull
        @Override
        public ListenableFuture<ForegroundInfo> getForegroundInfoAsync() {
            Notification notification = new Notification.Builder(context, "WallpaperSwitcher")
                    .setSmallIcon(R.drawable.icon)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setLocalOnly(true)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setContentText("Populating Cache")
                    .build();
            return CallbackToFutureAdapter.getFuture(completer -> {
                completer.set(new ForegroundInfo(1415642, notification));
                return "Populating Cache";
            });
        }
    }

    public static class CleanCacheWorker extends Worker {
        private final Context context;
        public CleanCacheWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
            this.context = context;
        }

        @Override
        @NonNull
        public Result doWork() {
            for (CacheManager cache : _instances.values()) {
                if (!cache.isInitialized) cache.init(context);
                cache.cleanCache();
            }

            // Indicate whether the work finished successfully with the Result
            return Result.success();
        }

        @NonNull
        @Override
        public ListenableFuture<ForegroundInfo> getForegroundInfoAsync() {
            Notification notification = new Notification.Builder(context, "WallpaperSwitcher")
                    .setSmallIcon(R.drawable.icon)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setLocalOnly(true)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setContentText("Cleaning Cache")
                    .build();
            return CallbackToFutureAdapter.getFuture(completer -> {
                completer.set(new ForegroundInfo(1415641, notification));
                return "Cleaning Cache";
            });
        }
    }

    public static class RebuildCacheWorker extends Worker {
        private final Context context;
        public RebuildCacheWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
            this.context = context;
        }

        @Override
        @NonNull
        public Result doWork() {
            for (CacheManager cache : _instances.values()) {
                synchronized (cache.cache) {
                    if (!cache.isInitialized) cache.init(context);
                    cache.currentIndex = 0;
                    cache.cache.clear();
                    cache.cacheSize = 0;
                    cache.populateCache();
                }
            }

            // Indicate whether the work finished successfully with the Result
            return Result.success();
        }

        @NonNull
        @Override
        public ListenableFuture<ForegroundInfo> getForegroundInfoAsync() {
            Notification notification = new Notification.Builder(context, "WallpaperSwitcher")
                    .setSmallIcon(R.drawable.icon)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setLocalOnly(true)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setContentText("Rebuilding Cache")
                    .build();
            return CallbackToFutureAdapter.getFuture(completer -> {
                completer.set(new ForegroundInfo(1415641, notification));
                return "Rebuilding Cache";
            });
        }
    }
    // endregion
}
