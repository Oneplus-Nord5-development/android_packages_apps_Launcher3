/*
 * Copyright (C) 2024 The LineageOS Project
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.R;
import com.android.launcher3.model.data.AppInfo;

import java.util.List;

/**
 * A sliding container view for hidden apps that appears when swiping left from
 * the Personal tab in the all apps drawer. This mimics OxygenOS 11's hidden
 * apps drawer behavior.
 */
public class HiddenAppsContainerView extends FrameLayout {

    private static final int SLIDE_DURATION_MS = 250;
    private static final float SWIPE_VELOCITY_THRESHOLD = 500f;

    private AllAppsRecyclerView mRecyclerView;
    private View mEmptyView;
    private TextView mTitleView;
    private View mBackButton;
    private View mContentContainer;
    private boolean mIsOpen = false;
    private boolean mIsDragging = false;
    private Runnable mOnCloseListener;
    private Runnable mOnOpenListener;
    private float mDragStartX;
    private float mDragStartTranslationX;
    private long mLastMoveTime;
    private float mLastMoveX;
    private float mVelocityX;

    public HiddenAppsContainerView(@NonNull Context context) {
        this(context, null);
    }

    public HiddenAppsContainerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HiddenAppsContainerView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContentContainer = findViewById(R.id.hidden_apps_content);
        mRecyclerView = findViewById(R.id.hidden_apps_list);
        mEmptyView = findViewById(R.id.hidden_apps_empty);
        mTitleView = findViewById(R.id.hidden_apps_title);
        mBackButton = findViewById(R.id.hidden_apps_back_button);

        if (mBackButton != null) {
            mBackButton.setOnClickListener(v -> close());
        }

        // Initially hidden and positioned off-screen to the left
        setVisibility(GONE);
    }

    /**
     * Start dragging to preview the drawer opening.
     * Called when user starts swiping from the left edge.
     */
    public void startDrag(float startX) {
        if (mIsOpen)
            return;

        mIsDragging = true;
        mDragStartX = startX;
        mDragStartTranslationX = -getWidth();
        mLastMoveX = startX;
        mLastMoveTime = System.currentTimeMillis();
        mVelocityX = 0;

        setVisibility(VISIBLE);
        setTranslationX(mDragStartTranslationX);
    }

    /**
     * Update the drag position as user swipes.
     * 
     * @param currentX The current X position of the touch
     * @return true if the drag is being handled
     */
    public boolean updateDrag(float currentX) {
        if (!mIsDragging)
            return false;

        // Calculate velocity
        long currentTime = System.currentTimeMillis();
        long deltaTime = currentTime - mLastMoveTime;
        if (deltaTime > 0) {
            mVelocityX = (currentX - mLastMoveX) / deltaTime * 1000;
        }
        mLastMoveX = currentX;
        mLastMoveTime = currentTime;

        float deltaX = currentX - mDragStartX;
        float newTranslation = mDragStartTranslationX + deltaX;

        // Clamp translation between -width and 0
        newTranslation = Math.max(-getWidth(), Math.min(0, newTranslation));
        setTranslationX(newTranslation);

        return true;
    }

    /**
     * End the drag and decide whether to open or close.
     */
    /**
     * End the drag and decide whether to open or close.
     * Note: This keeps the drawer in its current position until a decision is made
     * via callbacks to completeOpen() or swipe close.
     */
    public void endDrag() {
        // Drag end is handled by the caller checking shouldOpenOnRelease()
        mIsDragging = false;
    }

    /**
     * Check if the drawer should open based on current drag position and velocity.
     */
    public boolean shouldOpenOnRelease() {
        float currentTranslation = getTranslationX();
        float threshold = -getWidth() * 0.5f;

        // Check velocity and position to decide
        return (mVelocityX > SWIPE_VELOCITY_THRESHOLD)
                || (currentTranslation > threshold && mVelocityX > -SWIPE_VELOCITY_THRESHOLD);
    }

    /**
     * Complete the open animation after authentication success.
     */
    public void completeOpen() {
        animateOpen();
    }

    /**
     * Cancel the drag and close the drawer.
     */
    public void cancelDrag() {
        if (!mIsDragging)
            return;
        mIsDragging = false;
        animateClose();
    }

    /**
     * Check if currently being dragged.
     */
    public boolean isDragging() {
        return mIsDragging;
    }

    /**
     * Open the hidden apps drawer with a slide-in animation.
     */
    public void open() {
        if (mIsOpen)
            return;

        setVisibility(VISIBLE);
        setTranslationX(-getWidth());
        animateOpen();
    }

    private void animateOpen() {
        mIsOpen = true;

        ObjectAnimator animator = ObjectAnimator.ofFloat(this, TRANSLATION_X, 0f);
        animator.setDuration(SLIDE_DURATION_MS);
        animator.setInterpolator(Interpolators.DECELERATE);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mOnOpenListener != null) {
                    mOnOpenListener.run();
                }
            }
        });
        animator.start();
    }

    /**
     * Close the hidden apps drawer with a slide-out animation.
     */
    public void close() {
        if (!mIsOpen && !mIsDragging)
            return;
        mIsDragging = false;
        animateClose();
    }

    private void animateClose() {
        mIsOpen = false;

        ObjectAnimator animator = ObjectAnimator.ofFloat(this, TRANSLATION_X, -getWidth());
        animator.setDuration(SLIDE_DURATION_MS);
        animator.setInterpolator(Interpolators.ACCELERATE);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setVisibility(GONE);
                if (mOnCloseListener != null) {
                    mOnCloseListener.run();
                }
            }
        });
        animator.start();
    }

    /**
     * Check if the drawer is open.
     */
    public boolean isOpen() {
        return mIsOpen;
    }

    /**
     * Set listener for when the drawer closes.
     */
    public void setOnCloseListener(Runnable listener) {
        mOnCloseListener = listener;
    }

    /**
     * Set listener for when the drawer opens.
     */
    public void setOnOpenListener(Runnable listener) {
        mOnOpenListener = listener;
    }

    /**
     * Update the visibility of hidden apps list vs empty state.
     */
    public void updateHiddenApps(List<AppInfo> hiddenApps) {
        if (hiddenApps == null || hiddenApps.isEmpty()) {
            if (mRecyclerView != null)
                mRecyclerView.setVisibility(GONE);
            if (mEmptyView != null)
                mEmptyView.setVisibility(VISIBLE);
        } else {
            if (mRecyclerView != null)
                mRecyclerView.setVisibility(VISIBLE);
            if (mEmptyView != null)
                mEmptyView.setVisibility(GONE);
        }
    }

    /**
     * Get the RecyclerView for setting up the adapter.
     */
    public AllAppsRecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    /**
     * Handle back press. Returns true if consumed.
     */
    public boolean onBackPressed() {
        if (mIsOpen) {
            close();
            return true;
        }
        return false;
    }

    /**
     * Apply window insets padding.
     */
    public void setInsets(Rect insets) {
        if (mContentContainer != null) {
            mContentContainer.setPadding(
                    mContentContainer.getPaddingLeft(),
                    insets.top,
                    mContentContainer.getPaddingRight(),
                    insets.bottom);
        }
    }
}
