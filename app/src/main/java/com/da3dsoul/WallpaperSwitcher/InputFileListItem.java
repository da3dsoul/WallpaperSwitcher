package com.da3dsoul.WallpaperSwitcher;

public class InputFileListItem implements Comparable<InputFileListItem> {
    private final String mName;
    private final String mDir;
    private final int mIcon;
    private final boolean isFolder;

    public InputFileListItem(String name, String dir, int icon, boolean folder) {
        mName = name;
        mIcon = icon;
        isFolder = folder;
        mDir = dir;
    }

    public String getName() {
        return mName;
    }

    public String getDir() {
        return mDir;
    }

    public int getIcon() {
        return mIcon;
    }

    public boolean isFolder() {
        return isFolder;
    }

    public int compareTo(InputFileListItem another) {
        if (this.mName != null)
            return this.mName.compareTo(another.getName());
        else
            throw new IllegalArgumentException();
    }


}