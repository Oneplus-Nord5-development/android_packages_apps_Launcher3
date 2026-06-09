package com.android.launcher3.icons;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.icons.pack.IconResolver;
import com.android.launcher3.util.ComponentKey;

import com.android.launcher3.icons.calendar.DateChangeReceiver;

class ThirdPartyIconUtils {
    static Drawable getByKey(Context context, ComponentKey key, int iconDpi,
                             IconResolver.DefaultDrawableProvider fallback) {
        IconResolver resolver = IconPackManager.get(context).resolve(key);
        if (resolver == null) {
            DateChangeReceiver.get(context).setIsDynamic(key, false);
            return null;
        }
        DateChangeReceiver.get(context).setIsDynamic(key, resolver.isCalendar());
        return resolver.getIcon(iconDpi, fallback);
    }
}
