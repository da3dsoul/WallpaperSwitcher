package ffts.android.WallpaperSwitcher;

import android.app.Activity;
import android.os.Bundle;

public class OpenCurrentWallpaper extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (WallpaperSwitcher.Instance == null) {
            finish();
            return;
        }
        WallpaperSwitcher.Instance.openCurrentWallpaper();
        finish();
    }
}