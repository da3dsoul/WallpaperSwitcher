package com.da3dsoul.WallpaperSwitcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.da3dsoul.WallpaperSwitcher.Activity.MainActivity;

public class TestScreenEventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_USER_PRESENT.equals(action)) return;

        Intent newIntent = new Intent(context, MainActivity.class);
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        context.startActivity(newIntent);
    }
}
