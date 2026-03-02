package com.android.launcher3.folder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pageindicators.PageIndicatorDots;
import com.android.launcher3.util.ViewCache;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Paged view for the enlarged folder. Displays a scrolling grid of items.
 * Grid dimensions are managed by {@link EnlargedFolderGridOrganizer} (3x3).
 */
public class EnlargedFolderPagedView extends PagedView<PageIndicatorDots> {

    private final EnlargedFolderGridOrganizer mOrganizer;
    private final ViewCache mViewCache;
    private EnlargedFolderView mFolderView;

    private int mAllocatedContentSize;
    private int mGridCountX;
    private int mGridCountY;
    private boolean mViewsBound = false;

    public EnlargedFolderPagedView(Context context, AttributeSet attrs) {
        super(context, attrs);
        ActivityContext activityContext = ActivityContext.lookupContext(context);
        mOrganizer = new EnlargedFolderGridOrganizer(activityContext.getDeviceProfile());
        mViewCache = activityContext.getViewCache();
        // Initialize grid from organizer (single source of truth)
        mGridCountX = mOrganizer.getCountX();
        mGridCountY = mOrganizer.getCountY();
    }

    public void setFolderView(EnlargedFolderView folderView) {
        mFolderView = folderView;
        mPageIndicator = folderView.findViewById(R.id.enlarged_folder_page_indicator);
    }

    private void setupContentDimensions(int count) {
        mAllocatedContentSize = count;
        mOrganizer.setContentSize(count);
        mGridCountX = mOrganizer.getCountX();
        mGridCountY = mOrganizer.getCountY();

        for (int i = getPageCount() - 1; i >= 0; i--) {
            ((CellLayout) getChildAt(i)).setGridSize(mGridCountX, mGridCountY);
        }
    }

    public void bindItems(List<ItemInfo> items) {
        if (mViewsBound) {
            unbindItems();
        }
        arrangeChildren(items.stream().map(this::createNewView).collect(Collectors.toList()));
        mViewsBound = true;
    }

    public void unbindItems() {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            CellLayout page = (CellLayout) getChildAt(i);
            for (int j = page.getShortcutsAndWidgets().getChildCount() - 1; j >= 0; j--) {
                View iconView = page.getShortcutsAndWidgets().getChildAt(j);
                if (iconView instanceof BubbleTextView) {
                    mViewCache.recycleView(R.layout.folder_application, iconView);
                }
            }
            page.removeAllViews();
            mViewCache.recycleView(R.layout.folder_page, page);
        }
        removeAllViews();
        mViewsBound = false;
    }

    @SuppressLint("InflateParams")
    public View createNewView(ItemInfo item) {
        if (item == null) {
            return null;
        }

        final View icon;
        ActivityContext ac = ActivityContext.lookupContext(getContext());
        if (item instanceof AppPairInfo) {
            icon = AppPairIcon.inflateIcon(R.layout.folder_app_pair, ac, null, (AppPairInfo) item,
                    BubbleTextView.DISPLAY_FOLDER);
        } else {
            icon = mViewCache.getView(R.layout.folder_application, getContext(), null);
            ((BubbleTextView) icon).applyFromWorkspaceItem((WorkspaceItemInfo) item);
        }

        // Each icon gets its own click listener to launch the app directly
        icon.setOnClickListener(ac.getItemOnClickListener());

        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) icon.getLayoutParams();
        Point pos = mOrganizer.getPosForRank(item.rank);
        if (lp == null) {
            icon.setLayoutParams(new CellLayoutLayoutParams(pos.x, pos.y, 1, 1));
        } else {
            lp.setCellXY(pos);
            lp.cellHSpan = lp.cellVSpan = 1;
        }

        return icon;
    }

    private CellLayout createAndAddNewPage() {
        CellLayout page = mViewCache.getView(R.layout.folder_page, getContext(), this);
        // Do NOT call setCellDimensions — let CellLayout.onMeasure() dynamically
        // compute cell sizes based on available space (mFixedCellWidth defaults to -1).
        page.getShortcutsAndWidgets().setMotionEventSplittingEnabled(false);
        page.setGridSize(mGridCountX, mGridCountY);
        addView(page, -1, generateDefaultLayoutParams());
        return page;
    }

    @SuppressLint("RtlHardcoded")
    public void arrangeChildren(List<View> list) {
        int itemCount = list.size();
        ArrayList<CellLayout> pages = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout page = (CellLayout) getChildAt(i);
            page.removeAllViews();
            pages.add(page);
        }
        setupContentDimensions(itemCount);

        Iterator<CellLayout> pageItr = pages.iterator();
        CellLayout currentPage = null;

        int position = 0;
        int rank = 0;

        for (int i = 0; i < itemCount; i++) {
            View v = list.size() > i ? list.get(i) : null;
            if (currentPage == null || position >= mOrganizer.getMaxItemsPerPage()) {
                if (pageItr.hasNext()) {
                    currentPage = pageItr.next();
                } else {
                    currentPage = createAndAddNewPage();
                }
                position = 0;
            }

            if (v != null) {
                CellLayoutLayoutParams lp = (CellLayoutLayoutParams) v.getLayoutParams();
                ItemInfo info = (ItemInfo) v.getTag();
                lp.setCellXY(mOrganizer.getPosForRank(rank));
                currentPage.addViewToCellLayout(v, -1, info.getViewId(), lp, true);
            }

            rank++;
            position++;
        }

        boolean removed = false;
        while (pageItr.hasNext()) {
            removeView(pageItr.next());
            removed = true;
        }
        if (removed) {
            setCurrentPage(0);
        }

        setEnableOverscroll(getPageCount() > 1);
        if (mPageIndicator != null) {
            mPageIndicator.setVisibility(getPageCount() > 1 ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected int getChildGap(int fromIndex, int toIndex) {
        return 0; // Padding handled by parent
    }
}
