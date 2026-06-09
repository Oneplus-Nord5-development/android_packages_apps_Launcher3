package com.android.launcher3.icons.calendar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.AppReloader;

import java.util.HashSet;
import java.util.Set;

public class DateChangeReceiver extends BroadcastReceiver {
    private final Set<ComponentKey> mDynamicCalendars = new HashSet<>();

    private static DateChangeReceiver sInstance;

    public static synchronized DateChangeReceiver get(Context context) {
        if (sInstance == null) {
            sInstance = new DateChangeReceiver(context.getApplicationContext());
        }
        return sInstance;
    }

    private DateChangeReceiver(Context context) {
        super();

        IntentFilter filter = new IntentFilter(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);

        Handler handler = MODEL_EXECUTOR.getHandler();
        context.registerReceiver(this, filter, null, handler);
    }

    public void setIsDynamic(ComponentKey key, boolean calendar) {
        if (calendar) {
            mDynamicCalendars.add(key);
        } else {
            mDynamicCalendars.remove(key);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        AppReloader.get(context).reload(mDynamicCalendars);
    }
}
