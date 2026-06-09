package com.android.launcher3.icons;

import android.content.Context;
import android.content.pm.ComponentInfo;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.pack.IconResolver;
import com.android.launcher3.util.ComponentKey;
public class ThirdPartyIconProvider extends LauncherIconProvider {
    public static final int CONFIG_HINT_NO_WRAP = 1 << 30;


    public ThirdPartyIconProvider(Context context, ThemeManager themeManager) {
        super(context, themeManager);
    }

    @Override
    public Drawable getIcon(ComponentInfo info, int iconDpi) {
        ComponentKey key = new ComponentKey(
                info.getComponentName(), UserHandle.getUserHandleForUid(info.applicationInfo.uid));

        IconResolver.DefaultDrawableProvider fallback =
                () -> super.getIcon(info, iconDpi);
        Drawable icon = ThirdPartyIconUtils.getByKey(mContext, key, iconDpi, fallback);

        if (icon == null) {
            return fallback.get();
        }
        icon.setChangingConfigurations(icon.getChangingConfigurations() | CONFIG_HINT_NO_WRAP);
        return icon;
    }
}
