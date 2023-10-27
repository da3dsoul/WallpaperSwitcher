package com.da3dsoul.WallpaperSwitcher;

import java.util.HashMap;
import java.util.Locale;

public class CacheInstanceManager {
    public static int baseBucketSize = 100;
    public static int cacheReadAhead = 50;
    private static final HashMap<String, CacheManager> _instances = new HashMap<>();

    public static String getKey(double minAspect, double maxAspect)
    {
        return String.format(Locale.ENGLISH, "%.1f:%.1f", minAspect, maxAspect);
    }

    public static ICacheManager instance(double minAspect, double maxAspect)
    {
        String key = getKey(minAspect, maxAspect);
        if (!_instances.containsKey(key)) _instances.put(key, new CacheManager(minAspect, maxAspect));
        return _instances.get(key);
    }

    public static ICacheManager instanceForCanvas(double aspect)
    {
        for (ICacheManager cache : _instances.values()) {
            if (aspect >= cache.getMinAspect() && aspect <= cache.getMaxAspect()) return cache;
        }
        return null;
    }

    public static ICacheManager[] allInstances()
    {
        return _instances.values().toArray(new ICacheManager[0]);
    }
}
