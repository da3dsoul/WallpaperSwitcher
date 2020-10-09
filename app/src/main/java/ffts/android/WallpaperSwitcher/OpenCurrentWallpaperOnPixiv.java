package ffts.android.WallpaperSwitcher;

import android.app.Activity;
import android.os.Bundle;

public class OpenCurrentWallpaperOnPixiv extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (WallpaperSwitcher.Instance == null) {
            finish();
            return;
        }
        WallpaperSwitcher.Instance.openCurrentWallpaperOnPixiv();
        finish();
    }
}