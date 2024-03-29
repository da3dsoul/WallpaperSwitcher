package com.da3dsoul.WallpaperSwitcher;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.work.BackoffPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ForegroundInfo;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class CacheManager implements ICacheManager {
    // region variables and init
    private final CacheProgress progress = new CacheProgress();
    public CacheProgress getProgress() { return progress; }
    private final HashSet<INotifyWallpaperChanged> listeners = new HashSet<>();
    private final double minAspect;
    private final double maxAspect;
    public double getMinAspect() { return minAspect; }
    public double getMaxAspect() { return maxAspect; }
    public String path;
    public String getCurrentPath() { return path; }
    public int currentIndex = 0;
    public ArrayList<String> sourceDirectories = new ArrayList<>();
    public int bucketSize = CacheInstanceManager.baseBucketSize;
    public int getBucketSize() { return bucketSize; }
    public void setBucketSize(int size) { bucketSize = size; }
    public int cacheSize;
    private final ArrayList<String> cache = new ArrayList<>(bucketSize);
    private boolean isInitialized;
    private SharedPreferences sp;

    public CacheManager(double minAspect, double maxAspect) {
        this.minAspect = minAspect;
        this.maxAspect = maxAspect;
    }

    public boolean needsInitialized() {
        return !isInitialized;
    }

    public String getKey()
    {
        return CacheInstanceManager.getKey(minAspect, maxAspect);
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
        String data = sp.getString("cache", "");
        if (!data.equals("")) {
            ArrayList<String> toAdd = new ArrayList<>();
            try
            {
                Gson gson = new Gson();
                Type collectionType = new TypeToken<HashMap<String,String[]>>(){}.getType();
                HashMap<String,String[]> files = gson.fromJson(data, collectionType);
                if (files == null) files = new HashMap<>();
                String key = getKey();
                if (files.containsKey(key)) {
                    for (String item : Objects.requireNonNull(files.get(key))) {
                        File file = new File(item);
                        if (!file.isFile() || !file.exists()) continue;
                        toAdd.add(file.getAbsolutePath());
                    }
                }
            } catch (Exception e)
            {
                // ignore
            }

            synchronized (cache) {
                cache.addAll(toAdd);
                cacheSize = cache.size();
            }
        }

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

        CacheInstanceManager.baseBucketSize = Math.max(sp.getInt("bucketSize", CacheInstanceManager.baseBucketSize), 0);
        bucketSize = CacheInstanceManager.baseBucketSize;
        CacheInstanceManager.cacheReadAhead = Math.max(sp.getInt("readAhead", CacheInstanceManager.cacheReadAhead), 0);

        queueCachePopulation(c);

        if (currentIndex < cacheSize) {
            File file = get(currentIndex);
            path = file == null ? null : file.getAbsolutePath();
        }

        isInitialized = true;
    }

    public boolean ParseSourceDirectories(SharedPreferences sharedPreferences) {
        String directorySetting = sharedPreferences.getString("directories", null);
        if (directorySetting == null || directorySetting.equals("")) return true;
        Gson gson = new Gson();
        DirectoryModel[] directories = gson.fromJson(directorySetting, DirectoryModel[].class);
        if (directories == null) directories = new DirectoryModel[0];

        for (DirectoryModel dir : directories) {
            if (Math.abs(dir.MinAspect - minAspect) > 0.001D) continue;
            if (Math.abs(dir.MaxAspect - maxAspect) > 0.001D) continue;
            File file = new File(dir.Directory);
            if (!file.exists() || !file.isDirectory()) continue;
            sourceDirectories.add(file.getAbsolutePath());
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
        try {
            if (progress.TotalFiles != 0 || progress.AddedFiles != 0) return;
            String key = getKey();
            Gson gson = new Gson();
            resetProgress();

            if (cacheSize > bucketSize) return;
            if (cacheSize == bucketSize && currentIndex < cacheSize - CacheInstanceManager.cacheReadAhead) return;

            HashMap<String, Integer> totalMap;
            try {
                Type collectionType = new TypeToken<HashMap<String, Integer>>() {
                }.getType();
                totalMap = gson.fromJson(sp.getString("totalFiles", ""), collectionType);
                if (totalMap == null) totalMap = new HashMap<>();
            } catch (Exception e) {
                totalMap = new HashMap<>();
            }
            progress.TotalFiles = totalMap.getOrDefault(key, 0);

            List<String> temp;
            synchronized (cache) {
                temp = new ArrayList<>(cache);
            }

            ArrayList<String> files = new ArrayList<>();
            for (String dir : sourceDirectories) {
                if (dir == null || dir.equals("")) continue;
                File dirFile = new File(dir);
                if (!dirFile.exists()) continue;
                recursivelyAddWallpapers(files, dirFile);
                synchronized (progress) { progress.AddedDirectories++; }
            }
            if (files.size() < bucketSize) bucketSize = files.size();
            else if (bucketSize < CacheInstanceManager.baseBucketSize) {
                bucketSize = CacheInstanceManager.baseBucketSize;
            }

            files.removeAll(temp);

            if (files.size() == 0) {
                path = null;
                currentIndex = 0;
                cacheSize = 0;
                return;
            }

            HashMap<String, Long> seedMap;
            try {
                Type collectionType = new TypeToken<HashMap<String, Long>>() {
                }.getType();
                seedMap = gson.fromJson(sp.getString("seed", ""), collectionType);
                if (seedMap == null) seedMap = new HashMap<>();
            } catch (Exception e) {
                seedMap = new HashMap<>();
            }
            long seed = seedMap.getOrDefault(key, 0L);
            Random rand = seed != 0 ? new Random(seed) : new Random();
            List<String> filesToAdd = pickNRandomElements(files, bucketSize, rand);
            synchronized (cache) {
                cache.addAll(filesToAdd);
                cacheSize = cache.size();
            }

            seedMap.put(key, getSeed(rand));

            SharedPreferences.Editor edit = sp.edit();
            edit.putString("seed", gson.toJson(seedMap));

            progress.TotalFiles = files.size();
            totalMap.put(key, files.size());
            edit.putString("totalFiles", gson.toJson(totalMap));

            HashMap<String, String[]> filesMap;
            try {
                Type collectionType = new TypeToken<HashMap<String, String[]>>() {
                }.getType();
                filesMap = gson.fromJson(sp.getString("cache", ""), collectionType);
                if (filesMap == null) filesMap = new HashMap<>();
            } catch (Exception e) {
                filesMap = new HashMap<>();
            }

            // get(validIndex) should never be null here, as we just built the cache
            if (cacheSize > 0) {
                if (currentIndex < cacheSize)
                    path = get(currentIndex).getAbsolutePath();
                else
                    path = get(0).getAbsolutePath();

                String[] paths = new String[cacheSize];
                for (int i = 0; i < cacheSize; i++) {
                    paths[i] = get(i).getAbsolutePath();
                }

                filesMap.put(key, paths);
                edit.putString("cache", gson.toJson(filesMap));
            } else {
                filesMap.remove(key);
                edit.putString("cache", gson.toJson(filesMap));
            }
            edit.apply();

            if (temp.size() == 0) notifyListeners();
        } finally {
            resetProgress();
        }
    }

    private void resetProgress() {
        progress.TotalFiles = 0;
        progress.TotalDirectories = sourceDirectories.size();
        progress.PercentComplete = 0;
        progress.AddedDirectories = 0;
        progress.AddedFiles = 0;
    }

    public void cleanCache() {
        if (cacheSize <= bucketSize || currentIndex < bucketSize) return;
        synchronized (cache) {
            int originalSize = cache.size();
            currentIndex -= bucketSize;
            cache.subList(0, originalSize - bucketSize).clear();
            cacheSize = cache.size();
        }

        SharedPreferences.Editor edit = sp.edit();
        String[] paths = new String[cacheSize];
        for (int i = 0; i < cacheSize; i++) {
            paths[i] = get(i).getAbsolutePath();
        }

        HashMap<String,String[]> filesMap;
        HashMap<String,Integer> indexMap;
        String key = getKey();
        Gson gson = new Gson();
        try {
            Type collectionType = new TypeToken<HashMap<String, String[]>>() {}.getType();
            filesMap = gson.fromJson(sp.getString("cache", ""), collectionType);
            if (filesMap == null) filesMap = new HashMap<>();
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

    public int getCurrentIndex() { return currentIndex; }
    public File get(int i)
    {
        if (cacheSize == 0 || i < 0 || i >= cacheSize) return null;
        String path;
        synchronized (cache)
        {
            path = cache.get(i);
        }

        if (path == null || path.equals("")) return null;
        File currentFile = new File(path);
        if (currentFile.exists()) return currentFile;
        return null;
    }
    // endregion

    public void switchWallpaper(Context c) {
        File currentFile = null;
        do {
            currentIndex++;
            currentFile = get(currentIndex);
            if (currentFile != null && currentFile.exists()) break;
        } while (currentIndex < cacheSize);

        if (cacheSize > 0) {
            if (currentIndex >= cacheSize - CacheInstanceManager.cacheReadAhead) {
                // it's not done, so do it now
                if (currentIndex >= cacheSize) {
                    waitForPopulation();
                }

                // if cache is not read ahead
                if (cacheSize < bucketSize * 2)
                    queueCachePopulation(c);

                // cleanup cache
                if (currentIndex >= bucketSize + 5)
                    queueCacheCleanup(c);

                path = currentFile == null ? null : currentFile.getAbsolutePath();
            } else {
                path = currentFile == null ? null : currentFile.getAbsolutePath();
                if (cacheSize > bucketSize && currentIndex >= bucketSize)
                    queueCacheCleanup(c);
            }
        } else {
            // no cache, so do it now
            queueCachePopulation(c);
            waitForPopulation();
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
        notifyListeners();
    }

    private void waitForPopulation() {
        try {
            while (progress.AddedFiles != 0 || progress.TotalFiles != 0) {
                Thread.sleep(50);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    // region Utility methods
    private void recursivelyAddWallpapers(ArrayList<String> files, File source) {
        File[] items = source.listFiles();
        if (items == null) return;
        for (File item : items) {
            if (item.isDirectory()) {
                recursivelyAddWallpapers(files, item);
                continue;
            }

            if (!isImage(item.getName())) continue;
            files.add(item.getAbsolutePath());
            progress.AddedFiles++;
            if (progress.TotalFiles != 0) progress.PercentComplete = (double) progress.AddedFiles / progress.TotalFiles;
            if (progress.AddedFiles % 20 == 1) notifyListeners();
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

    public long getSeed() {
        String key = getKey();
        Gson gson = new Gson();
        HashMap<String, Long> seedMap;
        try {
            Type collectionType = new TypeToken<HashMap<String, Long>>() {
            }.getType();
            seedMap = gson.fromJson(sp.getString("seed", ""), collectionType);
            if (seedMap == null) seedMap = new HashMap<>();
        } catch (Exception e) {
            seedMap = new HashMap<>();
        }
        return seedMap.getOrDefault(key, 0L);
    }

    public void reset(Context context)
    {
        synchronized (cache) {
            if (needsInitialized()) init(context);
            currentIndex = 0;
            cache.clear();
            cacheSize = 0;
            populateCache();
        }
    }

    public void onSharedPreferencesChanged(SharedPreferences sharedPreferences, Context context)
    {
        sourceDirectories.clear();
        ParseSourceDirectories(sharedPreferences);
        if (sourceDirectories.size() > 0)
            queueCacheRebuild(context);
    }

    public void drawDebugStats(Canvas c)
    {
        Paint white = new Paint();
        white.setARGB(255, 255, 255, 255);
        white.setShadowLayer(4, 2, 2, 0);
        int fontSize = 24;
        int buffer = 8;
        white.setTextSize(fontSize);
        int y = 112;
        // debug box
        int lines = 6;
        int debugBoxHeight = lines * (fontSize + buffer) + y + 30;
        Paint gray = new Paint();
        gray.setARGB(191, 0, 0, 0);
        c.drawRect(0,0, c.getWidth(), debugBoxHeight, gray);
        // debug lines
        c.drawText("Path: " + path, 10, y, white);
        y += fontSize + buffer;
        c.drawText("CurrentIndex: " + currentIndex +
                "   Cache Size: " + cacheSize, 10, y, white);
        y += fontSize + buffer;
        c.drawText("bucketSize: " + bucketSize + "   baseBucketSize: "
                + CacheInstanceManager.baseBucketSize, 10, y, white);
        y += fontSize + buffer;
        c.drawText("ReadAhead: " + CacheInstanceManager.cacheReadAhead + "   BucketSeed: " + getSeed(), 10, y, white);
        y += fontSize + buffer;
        c.drawText("Canvas Width: " + c.getWidth() + "   Canvas Height: " + c.getHeight(), 10, y, white);
        y += fontSize + buffer;
        c.drawText("Cache minAspect: " + minAspect + "   Cache maxAspect: " + maxAspect + "   Aspect: " + (double)c.getWidth() / c.getHeight(), 10, y, white);
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
            double aspect = getInputData().getDouble("aspect", -1);
            if (aspect == -1) {
                for (ICacheManager cache : CacheInstanceManager.allInstances()) {
                    if (cache.needsInitialized()) cache.init(context);
                    cache.populateCache();
                }
            }

            // Indicate whether the work finished successfully with the Result
            return Result.success();
        }

        @NonNull
        @Override
        public ListenableFuture<ForegroundInfo> getForegroundInfoAsync() {
            int total = Arrays.stream(CacheInstanceManager.allInstances()).map(a -> a.getProgress().TotalFiles).reduce(0, Integer::sum);
            int added = Arrays.stream(CacheInstanceManager.allInstances()).map(a -> a.getProgress().AddedFiles).reduce(0, Integer::sum);
            Notification notification = new Notification.Builder(context, "WallpaperSwitcher")
                    .setSmallIcon(R.drawable.icon)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setLocalOnly(true)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setProgress(total, added, false)
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
            for (ICacheManager cache : CacheInstanceManager.allInstances()) {
                if (cache.needsInitialized()) cache.init(context);
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
            for (ICacheManager cache : CacheInstanceManager.allInstances()) {
                cache.reset(context);
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
