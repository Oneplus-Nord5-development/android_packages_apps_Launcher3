package com.android.launcher3.icons.pack;

import android.graphics.drawable.Drawable;

import com.android.launcher3.icons.clock.CustomClock;

public interface IconResolver {
    boolean isCalendar();

    boolean isClock();

    CustomClock.Metadata clockData();

    Drawable getIcon(int iconDpi, DefaultDrawableProvider fallback);

    interface DefaultDrawableProvider {
        Drawable get();
    }
}
