package com.android.launcher3.folder;

import android.graphics.Point;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.model.data.FolderInfo;

/**
 * Organizes the grid layout within an enlarged folder.
 * Uses a fixed 3x3 grid to match Samsung OneUI enlarged folder style.
 */
public class EnlargedFolderGridOrganizer {

    private static final int GRID_X = 3;
    private static final int GRID_Y = 3;

    private final DeviceProfile mDeviceProfile;
    private int mCountX;
    private int mCountY;
    private int mMaxItemsPerPage;

    public EnlargedFolderGridOrganizer(DeviceProfile deviceProfile) {
        mDeviceProfile = deviceProfile;
        mCountX = GRID_X;
        mCountY = GRID_Y;
        mMaxItemsPerPage = mCountX * mCountY;
    }

    public EnlargedFolderGridOrganizer setFolderInfo(FolderInfo info) {
        return this;
    }

    public boolean updateRankAndPos(com.android.launcher3.model.data.ItemInfo item, int rank) {
        item.rank = rank;
        Point pos = getPosForRank(rank);
        item.cellX = pos.x;
        item.cellY = pos.y;
        return true;
    }

    public void setContentSize(int count) {
        // Always use the fixed 3x3 grid for enlarged folders
        mCountX = GRID_X;
        mCountY = GRID_Y;
        mMaxItemsPerPage = mCountX * mCountY;
    }

    public int getCountX() {
        return mCountX;
    }

    public int getCountY() {
        return mCountY;
    }

    public int getMaxItemsPerPage() {
        return mMaxItemsPerPage;
    }

    public DeviceProfile getDeviceProfile() {
        return mDeviceProfile;
    }

    /**
     * Translates a flat rank into a 2D position within a grid page.
     */
    public Point getPosForRank(int rank) {
        int pagePos = rank % mMaxItemsPerPage;
        int x = pagePos % mCountX;
        int y = pagePos / mCountX;
        return new Point(x, y);
    }
}
