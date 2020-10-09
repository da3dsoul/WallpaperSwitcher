package ffts.android.WallpaperSwitcher;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ComponentName;
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

import androidx.core.content.FileProvider;

public class WallpaperSwitcher extends WallpaperService {

    public static WallpaperEngine Instance;

    private ScreenEventReceiver screenEventReceiver;

    @Override
    public Engine onCreateEngine() {
        return (Instance = new WallpaperEngine());
    }

    class WallpaperEngine extends Engine implements OnSharedPreferenceChangeListener {
        private SharedPreferences sp;

        String path;
        int currentIndex = 0;
        String previousPath;
        ArrayList<File> papers = null;
        ArrayList<File> cache = new ArrayList<File>(100);

        WallpaperEngine() {
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
        }

        private void drawFrame()
        {
            drawFrame(null);
        }

        private void drawFrame(SurfaceHolder holder) {
            if (path == null ? previousPath == null : path.equals(previousPath)) return;
            if (holder == null) holder = getSurfaceHolder();

            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) {
                    drawPaper(c);
                }
            } catch (Exception e)
            {
                Log.e("Drawing Wallpaper", e.getMessage(), e);
            } finally {
                if (c != null) holder.unlockCanvasAndPost(c);
            }
        }

        private void init() {
            String directories = sp.getString("dir", "");
            if (directories == null) {
                return;
            }
            String[] parsedDirectories = directories.split("\\|");
            for (String dir : parsedDirectories) {
                if (papers == null) papers = new ArrayList<File>();
                File file = new File(dir);
                if (!file.exists() || !file.isDirectory()) continue;
                papers.add(file);
            }

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
            if (currentIndex < cache.size())
            {
                path = cache.get(currentIndex).getAbsolutePath();
                if (currentIndex > 0) previousPath = cache.get(currentIndex - 1).getAbsolutePath();
            }

            loadPapers();
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
            c.drawBitmap(output, 0, (int) Math.floor(height/2D - output.getHeight()/2D), null);
            previousPath = path;
        }

        public void switchWallpaper() {
            currentIndex++;
            if (currentIndex >= cache.size()) {
                loadPapers();
            } else {
                path = cache.get(currentIndex).getAbsolutePath();
            }
            sp.edit().putInt("currentIndex", currentIndex).apply();
            drawFrame();
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);

            drawFrame(surfaceHolder);
        }

        //加载指定路径的图片
        private void loadPapers() {
            // TODO Populate the cache and select the next x wallpapers, if needed
            if (!cache.isEmpty() && currentIndex < cache.size()) return;
            ArrayList<File> files = new ArrayList<File>();
            for (File dir : papers) {
                recursivelyAddWallpapers(files, dir);
            }

            files.removeAll(cache);
            cache.clear();

            if (files.size() <= 0) {
                path = null;
                currentIndex = 0;
                return;
            }

            Random rand = new Random();
            for (int i = 0; i < 100; i++) {
                if (files.size() <= 0) break;
                int index = rand.nextInt(files.size());
                cache.add(files.get(index));
                files.remove(index);
            }
            path = cache.get(0).getAbsolutePath();
            currentIndex = 0;
            if (cache.size() > 0) {
                String[] paths = new String[cache.size()];
                for (int i = 0; i < cache.size(); i++) {
                    paths[i] = cache.get(i).getAbsolutePath();
                }
                sp.edit().putString("cache", String.join("|", paths)).apply();
            }
            else {
                sp.edit().putString("cache", "").apply();
            }
        }

        private void recursivelyAddWallpapers(ArrayList<File> files, File source) {
            File[] items = source.listFiles();
            if (items == null) return;
            for(File item : items) {
                if (item.isDirectory()) {
                    recursivelyAddWallpapers(files, item);
                    continue;
                }
                if (!isImage(item.getName())) continue;
                files.add(item);
            }
        }

        private boolean isImage(String filename)
        {
            String name = filename.toLowerCase();
            if (name.endsWith(".jpg")) return true;
            if (name.endsWith(".jpeg")) return true;
            if (name.endsWith(".png")) return true;
            if (name.endsWith(".bmp")) return true;
            if (name.endsWith(".tiff")) return true;
            if (name.endsWith(".svg")) return true;
            if (name.endsWith(".webp")) return true;
            return false;
        }

        public void onVisibilityChanged(boolean visible) {
            if (visible) {
                drawFrame();
            }
        }

        public void shareCurrentWallpaper()
        {
            File file = new File(path);
            if (!file.exists()) return;
            try {
                Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                sharingIntent.setType("image/*");

                sharingIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getApplicationContext(), "ffts.android.WallpaperSwitcher.fileprovider", file));
                Intent chooser = Intent.createChooser(sharingIntent, "Share " + path.substring(path.lastIndexOf('/') + 1));
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooser);
            } catch (Exception e)
            {
                Log.e("shareCurrentWallpaper", e.toString());
            }
        }

        public void openCurrentWallpaperOnPixiv()
        {
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
            } catch (Exception e)
            {
                Log.e("openCurrentWallpaper", e.toString());
            }
        }

        public void openCurrentWallpaper()
        {
            File file = new File(path);
            if (!file.exists()) return;
            try {
                Intent openIntent = new Intent(Intent.ACTION_VIEW);
                openIntent.setDataAndType(Uri.parse(file.getAbsolutePath()), "image/*");
                openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(openIntent);
            } catch (Exception e)
            {
                Log.e("openCurrentWallpaper", e.toString());
            }
        }

        public void openPreviousWallpaper()
        {
            if (currentIndex <= 0 || currentIndex - 1 >= cache.size()) return;

            String prev = cache.get(currentIndex - 1).getAbsolutePath();
            File file = new File(prev);
            if (!file.exists()) return;
            try {
                Intent openIntent = new Intent(Intent.ACTION_VIEW);
                openIntent.setDataAndType(Uri.parse(file.getAbsolutePath()), "image/*");
                openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(openIntent);
            } catch (Exception e)
            {
                Log.e("openPreviousWallpaper", e.toString());
            }
        }

        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // TODO Auto-generated method stub
            if (key == null || key.equals("dir")) {
                String directories = sp.getString("dir", "");
                if (directories == null) {
                    drawFrame();
                    return;
                }
                String[] parsedDirectories = directories.split("\\|");
                for (String dir : parsedDirectories) {
                    if (papers == null) papers = new ArrayList<File>();
                    File file = new File(dir);
                    if (!file.exists() || !file.isDirectory()) continue;
                    papers.add(file);
                }
                loadPapers();
                drawFrame();
//        	Log.i("wall", "change dirs:"+sp.getString("dir", ""));
            }
        }
    }

}