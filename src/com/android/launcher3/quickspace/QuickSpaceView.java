package com.android.launcher3.quickspace;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.quickspace.QuickspaceController.OnDataListener;
import com.android.launcher3.quickspace.receivers.QuickSpaceActionReceiver;

public class QuickSpaceView extends FrameLayout implements OnDataListener {

    private ViewGroup mQuickspaceContent;
    private ViewGroup mWeatherContentSub;
    private ImageView mWeatherIconSub;
    private TextView mWeatherTempSub;
    private TextView mEventTitle;

    public boolean mWeatherAvailable;
    private boolean mFinishedInflate;
    private boolean mListenerRegistered;

    public QuickspaceController mController;

    public QuickSpaceView(Context context, AttributeSet set) {
        super(context, set);
        if (!LauncherPrefs.SHOW_QUICKSPACE.get(context)) return;
        mController = new QuickspaceController(context);
        setClipChildren(false);
    }

    @Override
    public void onDataUpdated() {
        if (mController == null) return;
        if (mEventTitle == null) {
            prepareLayout();
        }
        mWeatherAvailable = mController.isWeatherAvailable();
        loadDoubleLine();
    }

    private void loadDoubleLine() {
        String dateText = DateUtils.formatDateTime(getContext(), System.currentTimeMillis(), 
                DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
        mEventTitle.setText(dateText);
        mEventTitle.setOnClickListener(QuickSpaceActionReceiver.getCalendarAction());
        bindWeather(mWeatherContentSub, mWeatherTempSub, mWeatherIconSub);
    }

    private void bindWeather(View container, TextView title, ImageView icon) {
        if (!mWeatherAvailable) {
            container.setVisibility(View.GONE);
            return;
        }
        String weatherTemp = mController.getWeatherTemp();
        if (weatherTemp == null || weatherTemp.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);
        container.setOnClickListener(QuickSpaceActionReceiver.getWeatherAction());
        title.setText(weatherTemp);
        title.setOnClickListener(QuickSpaceActionReceiver.getWeatherAction());
        Drawable d = mController.getWeatherIcon();
        icon.setImageDrawable(d);
        icon.setOnClickListener(QuickSpaceActionReceiver.getWeatherAction());
        icon.setVisibility(d != null ? View.VISIBLE : View.GONE);
    }

    private void loadViews() {
        mEventTitle = findViewById(R.id.quick_event_title);
        mWeatherIconSub = findViewById(R.id.quick_event_weather_icon);
        mQuickspaceContent = findViewById(R.id.quickspace_content);
        mWeatherContentSub = findViewById(R.id.quick_event_weather_content);
        mWeatherTempSub = findViewById(R.id.quick_event_weather_temp);
        
        // Hide unused views
        View eventTitleSub = findViewById(R.id.quick_event_title_sub);
        if (eventTitleSub != null) eventTitleSub.setVisibility(View.GONE);
        View eventSubIcon = findViewById(R.id.quick_event_icon_sub);
        if (eventSubIcon != null) eventSubIcon.setVisibility(View.GONE);
    }

    private void prepareLayout() {
        int insertIndex = (mQuickspaceContent != null) ? indexOfChild(mQuickspaceContent) : -1;
        if (mQuickspaceContent != null) {
            removeView(mQuickspaceContent);
        }
        addView(LayoutInflater.from(getContext()).inflate(R.layout.quickspace_doubleline, this, false),
                insertIndex < 0 ? -1 : insertIndex);

        loadViews();
        
        if (mQuickspaceContent != null) {
            mQuickspaceContent.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mController != null && mFinishedInflate && !mListenerRegistered) {
            mListenerRegistered = true;
            mController.addListener(this);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        if (mController == null) return;
        super.onDetachedFromWindow();
        mController.onPause();
        mController.removeListener(this);
        mListenerRegistered = false;
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        if (mController == null) return;
        loadViews();
        mFinishedInflate = true;
        if (isAttachedToWindow() && !mListenerRegistered) {
            mController.addListener(this);
            mListenerRegistered = true;
        }
    }

    public void onPause() {
        if (mController != null) mController.onPause();
    }

    public void onResume() {
        if (mController != null && mListenerRegistered) mController.onResume();
    }

    public void onDestroy() {
        if (mController == null) return;
        mController.onDestroy();
        mController = null;
    }

    public void setPadding(int n, int n2, int n3, int n4) {
        super.setPadding(0, 0, 0, 0);
    }
}
