/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.allapps;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_SWIPE_TO_PERSONAL_TAB;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_SWIPE_TO_WORK_TAB;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.android.launcher3.PagedView;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.workprofile.PersonalWorkPagedView;

/**
 * A {@link PagedView} for showing different views for the personal and work
 * profile respectively
 * in the {@link BaseAllAppsContainerView}.
 */
public class AllAppsPagedView extends PersonalWorkPagedView {

    /** Listener for hidden apps swipe gesture */
    public interface OnHiddenAppsSwipeListener {
        void onHiddenAppsSwipeStarted(float startX);

        void onHiddenAppsSwipeUpdate(float currentX);

        void onHiddenAppsSwipeEnded();

        void onHiddenAppsSwipeCancelled();
    }

    private OnHiddenAppsSwipeListener mHiddenAppsSwipeListener;
    private float mDownX;
    private float mDownY;
    private boolean mIsHiddenAppsSwipe = false;
    private static final int SWIPE_START_THRESHOLD = 50;
    private static final int EDGE_ZONE_WIDTH = 100;

    public AllAppsPagedView(Context context) {
        this(context, null);
    }

    public AllAppsPagedView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsPagedView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setOnLeftEdgeSwipeListener(OnHiddenAppsSwipeListener listener) {
        mHiddenAppsSwipeListener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getX();
                mDownY = ev.getY();
                mIsHiddenAppsSwipe = false;
                break;
            case MotionEvent.ACTION_MOVE:
                // Check for hidden apps swipe on page 0 (Personal tab)
                if (mCurrentPage == 0 && mHiddenAppsSwipeListener != null && !mIsHiddenAppsSwipe) {
                    float deltaX = ev.getX() - mDownX;
                    float deltaY = Math.abs(ev.getY() - mDownY);
                    // Right swipe (finger moving right) from left edge opens hidden apps
                    boolean isFromLeftEdge = mDownX < EDGE_ZONE_WIDTH;
                    if (deltaX > SWIPE_START_THRESHOLD && deltaX > deltaY * 2 && isFromLeftEdge) {
                        mIsHiddenAppsSwipe = true;
                        mHiddenAppsSwipeListener.onHiddenAppsSwipeStarted(mDownX);
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mIsHiddenAppsSwipe) {
                    mIsHiddenAppsSwipe = false;
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mIsHiddenAppsSwipe && mHiddenAppsSwipeListener != null) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    mHiddenAppsSwipeListener.onHiddenAppsSwipeUpdate(ev.getX());
                    return true;
                case MotionEvent.ACTION_UP:
                    mHiddenAppsSwipeListener.onHiddenAppsSwipeEnded();
                    mIsHiddenAppsSwipe = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    mHiddenAppsSwipeListener.onHiddenAppsSwipeCancelled();
                    mIsHiddenAppsSwipe = false;
                    return true;
            }
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected boolean snapToPageWithVelocity(int whichPage, int velocity) {
        boolean resp = super.snapToPageWithVelocity(whichPage, velocity);
        if (resp && whichPage != mCurrentPage) {
            ActivityContext.lookupContext(getContext()).getStatsLogManager().logger()
                    .log(mCurrentPage < whichPage
                            ? LAUNCHER_ALLAPPS_SWIPE_TO_WORK_TAB
                            : LAUNCHER_ALLAPPS_SWIPE_TO_PERSONAL_TAB);
        }
        return resp;
    }
}
