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
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class CacheManager {
    // region variables and init
    public static int baseBucketSize = 100;
    public static int cacheReadAhead = 50;
    private static CacheManager _instance = null;
    private final HashSet<INotifyWallpaperChanged> listeners = new HashSet<>();
    public String path;
    public int currentIndex = 0;
    public ArrayList<File> sourceDirectories = new ArrayList<>();
    public int bucketSize = baseBucketSize;
    public final ArrayList<File> cache = new ArrayList<>(bucketSize);
    public int cacheSize;
    private boolean isInitialized;
    private SharedPreferences sp;

    public static CacheManager instance()
    {
        if (_instance == null) _instance = new CacheManager();
        return _instance;
    }

    public boolean isInitialized() {
        return isInitialized;
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
            if (data != null) {
                String[] deserialized = data.split("\\|");
                for (String item : deserialized) {
                    File file = new File(item);
                    if (!file.isFile() || !file.exists()) continue;
                    cache.add(file);
                }

            }

            cacheSize = cache.size();
            currentIndex = Math.max(sp.getInt("currentIndex", 0), 0);
            baseBucketSize = Math.max(sp.getInt("bucketSize", baseBucketSize), 0);
            bucketSize = baseBucketSize;
            cacheReadAhead = Math.max(sp.getInt("readAhead", cacheReadAhead), 0);

            if (cacheSize == 0) populateCache();
            else queueCachePopulation(c);

            if (currentIndex < cacheSize) {
                path = cache.get(currentIndex).getAbsolutePath();
            }
        }
        isInitialized = true;
    }

    public boolean ParseSourceDirectories(SharedPreferences sharedPreferences) {
        if (sourceDirectories == null) sourceDirectories = new ArrayList<>();
        else sourceDirectories.clear();

        String directories = sharedPreferences.getString("dir", "");
        if (directories == null) {
            return true;
        }

        String[] parsedDirectories = directories.split("\\|");
        for (String dir : parsedDirectories) {
            File file = new File(dir);
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
                item.WallpaperChanged();
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
        else if (bucketSize < baseBucketSize && files.size() >= bucketSize) bucketSize = baseBucketSize;

        files.removeAll(temp);

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
                if (currentIndex < cacheSize)
                    path = cache.get(currentIndex).getAbsolutePath();
                else
                    path = cache.get(0).getAbsolutePath();

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
            int originalSize = cache.size();
            currentIndex -= bucketSize;
            cache.subList(0, originalSize - bucketSize).clear();
            cacheSize = cache.size();


            SharedPreferences.Editor edit = sp.edit();
            String[] paths = new String[cacheSize];
            for (int i = 0; i < cacheSize; i++) {
                paths[i] = cache.get(i).getAbsolutePath();
            }
            edit.putString("cache", String.join("|", paths));
            edit.putInt("currentIndex", currentIndex);
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
            currentIndex++;
            if (cacheSize > 0) {
                if (currentIndex >= cacheSize - cacheReadAhead) {
                    // it's not done, so do it now
                    if (currentIndex >= cacheSize) {
                        WorkManager.getInstance(c).cancelUniqueWork("WallpaperSwitcher.loadPapers");
                        populateCache();
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
                WorkManager.getInstance(c).cancelUniqueWork("WallpaperSwitcher.loadPapers");
                populateCache();
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
            if (!instance().isInitialized) instance().init(context);
            instance().populateCache();

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
            if (!instance().isInitialized) instance().init(context);
            instance().cleanCache();

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
            CacheManager i = instance();
            synchronized (i.cache) {
                if (!i.isInitialized) i.init(context);
                i.currentIndex = 0;
                i.cache.clear();
                i.cacheSize = 0;
                i.populateCache();
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
