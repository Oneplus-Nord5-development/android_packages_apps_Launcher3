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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.lineage.LineageUtils;
import com.android.launcher3.lineage.trust.db.TrustDatabaseHelper;
import com.android.launcher3.model.data.ItemInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Manages the hidden apps drawer page, including authentication state and item
 * filtering.
 */
public class HiddenAppsManager {

    public interface HiddenAppsListener {
        void onHiddenAppsAuthStateChanged(boolean isAuthenticated);
    }

    private final Context mContext;
    private final TrustDatabaseHelper mDbHelper;
    private boolean mIsAuthenticated = false;
    private HiddenAppsListener mListener;
    private Set<String> mHiddenPackages = new HashSet<>();

    public HiddenAppsManager(@NonNull Context context) {
        mContext = context;
        mDbHelper = TrustDatabaseHelper.getInstance(context);
        refreshHiddenPackages();
    }

    /**
     * Refresh the cached list of hidden packages from the database.
     */
    public void refreshHiddenPackages() {
        List<String> packages = mDbHelper.getHiddenPackages();
        mHiddenPackages.clear();
        mHiddenPackages.addAll(packages);
    }

    /**
     * Check if the user has authenticated to view hidden apps.
     */
    public boolean isAuthenticated() {
        return mIsAuthenticated;
    }

    /**
     * Check if there are any hidden apps.
     */
    public boolean hasHiddenApps() {
        return mDbHelper.hasHiddenPackages();
    }

    /**
     * Lock hidden apps (require re-authentication).
     */
    public void lock() {
        if (mIsAuthenticated) {
            mIsAuthenticated = false;
            if (mListener != null) {
                mListener.onHiddenAppsAuthStateChanged(false);
            }
        }
    }

    /**
     * Request authentication to view hidden apps.
     * 
     * @param onSuccess Runnable to execute on successful authentication
     */
    public void requestAuthentication(@NonNull Runnable onSuccess) {
        requestAuthentication(onSuccess, null);
    }

    /**
     * Request authentication to view hidden apps.
     * 
     * @param onSuccess Runnable to execute on successful authentication
     * @param onFailure Runnable to execute on failed/cancelled authentication
     */
    public void requestAuthentication(@NonNull Runnable onSuccess, @Nullable Runnable onFailure) {
        if (mIsAuthenticated) {
            onSuccess.run();
            return;
        }

        String title = mContext.getString(R.string.hidden_apps_auth_title);
        LineageUtils.showLockScreen(mContext, title, () -> {
            mIsAuthenticated = true;
            refreshHiddenPackages();
            if (mListener != null) {
                mListener.onHiddenAppsAuthStateChanged(true);
            }
            onSuccess.run();
        }, onFailure);
    }

    /**
     * Set listener for authentication state changes.
     */
    public void setListener(HiddenAppsListener listener) {
        mListener = listener;
    }

    /**
     * Get the item filter that matches only hidden apps.
     * This is used to filter the apps list to show only hidden apps.
     */
    public Predicate<ItemInfo> getItemInfoMatcher() {
        return itemInfo -> {
            if (itemInfo == null || itemInfo.getTargetComponent() == null) {
                return false;
            }
            String packageName = itemInfo.getTargetComponent().getPackageName();
            return mHiddenPackages.contains(packageName);
        };
    }

    /**
     * Get the item filter that matches only visible (non-hidden) apps.
     * This is the inverse of getItemInfoMatcher().
     */
    public Predicate<ItemInfo> getVisibleAppsFilter() {
        return itemInfo -> {
            if (itemInfo == null || itemInfo.getTargetComponent() == null) {
                return true;
            }
            String packageName = itemInfo.getTargetComponent().getPackageName();
            return !mHiddenPackages.contains(packageName);
        };
    }

    /**
     * Check if a package is hidden.
     */
    public boolean isPackageHidden(String packageName) {
        return mHiddenPackages.contains(packageName);
    }
}
