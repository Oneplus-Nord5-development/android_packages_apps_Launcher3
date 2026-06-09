package com.android.launcher3.icons;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import com.android.launcher3.icons.cache.BaseIconCache;
import com.android.launcher3.icons.cache.CachingLogic;
import com.android.launcher3.icons.cache.LauncherActivityCachingLogic;

public class CustomLauncherActivityCachingLogic implements CachingLogic<LauncherActivityInfo> {
    public static final CustomLauncherActivityCachingLogic INSTANCE = new CustomLauncherActivityCachingLogic();

    private CustomLauncherActivityCachingLogic() {}

    @Override
    public ComponentName getComponent(LauncherActivityInfo info) {
        return LauncherActivityCachingLogic.INSTANCE.getComponent(info);
    }

    @Override
    public UserHandle getUser(LauncherActivityInfo info) {
        return LauncherActivityCachingLogic.INSTANCE.getUser(info);
    }

    @Override
    public CharSequence getLabel(LauncherActivityInfo info) {
        return LauncherActivityCachingLogic.INSTANCE.getLabel(info);
    }

    @Override
    public ApplicationInfo getApplicationInfo(LauncherActivityInfo info) {
        return LauncherActivityCachingLogic.INSTANCE.getApplicationInfo(info);
    }

    @Override
    public BitmapInfo loadIcon(Context context, BaseIconCache cache, LauncherActivityInfo info) {
        try (LauncherIcons li = LauncherIcons.obtain(context)) {
            Drawable iconDrawable = cache.getIconProvider().getIcon(info.getActivityInfo(), li.fullResIconDpi);
            if (context.getPackageManager().isDefaultApplicationIcon(iconDrawable)) {
                return cache.getDefaultIcon(info.getUser());
            }
            BaseIconFactory.IconOptions iconOptions = new BaseIconFactory.IconOptions();
            if ((iconDrawable.getChangingConfigurations() & ThirdPartyIconProvider.CONFIG_HINT_NO_WRAP) != 0) {
                iconOptions.setWrapNonAdaptiveIcon(false);
                iconOptions.setDrawFullBleed(false);
            }
            return li.createBadgedIconBitmap(iconDrawable, iconOptions);
        }
    }

    @Override
    public String getFreshnessIdentifier(LauncherActivityInfo item, IconProvider provider) {
        return LauncherActivityCachingLogic.INSTANCE.getFreshnessIdentifier(item, provider);
    }
}
