package com.android.launcher3.folder;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.views.ActivityContext;

/**
 * A view that represents a folder in an enlarged state directly on the
 * workspace. It displays a grid of app icons that can be directly interacted
 * with, without needing to open the standard folder popup.
 *
 * The view keeps spacing/layout stable across device profiles and supports the
 * same workspace long-press behavior as a regular FolderIcon.
 */
public class EnlargedFolderView extends LinearLayout implements DragSource {

    private FolderInfo mInfo;
    private EnlargedFolderPagedView mContent;
    private TextView mFolderName;

    public EnlargedFolderView(Context context) {
        this(context, null);
    }

    public EnlargedFolderView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EnlargedFolderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.enlarged_folder_content);
        mFolderName = findViewById(R.id.enlarged_folder_name);

        if (mContent != null) {
            mContent.setFolderView(this);
        }

        // Square outline for the background clipping
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                int radius = getResources().getDimensionPixelSize(R.dimen.bg_round_rect_radius);
                int w = view.getWidth();
                int h = view.getHeight();
                int size = Math.min(w, h);
                int left = (w - size) / 2;
                int top = (h - size) / 2;
                outline.setRoundRect(left, top, left + size, top + size, radius);
            }
        });
        setClipToOutline(true);
    }

    public void bindFolder(FolderInfo info) {
        mInfo = info;
        if (mFolderName != null && info.title != null) {
            mFolderName.setText(info.title);
        }
        if (mContent != null) {
            mContent.bindItems(info.getContents());
        }
        if (mFolderName != null) {
            mFolderName.setVisibility(View.GONE);
        }
        CharSequence title = info.title == null ? "" : info.title;
        setContentDescription(getContext().getString(
                R.string.enlarged_folder_description,
                title,
                info.getContents().size(),
                1,
                1));
        // Click listener is set by ItemInflater via activity.getItemOnClickListener().
        // Long-press is wired to workspace drag/popup flow.
        setOnLongClickListener(
                Launcher.getLauncher(getContext()).getWorkspace().getWorkspaceChildOnLongClickListener());
    }

    /**
     * Whether this enlarged folder will accept a drop of the given item.
     * Called by
     * {@link com.android.launcher3.Workspace#willAddToExistingUserFolder}.
     */
    public boolean acceptDrop(ItemInfo dragInfo) {
        return mInfo != null
                && FolderInfo.willAcceptItemType(dragInfo.itemType)
                && dragInfo != mInfo;
    }

    /**
     * Handles an item being dropped onto this enlarged folder.
     * Called by
     * {@link com.android.launcher3.Workspace#addToExistingFolderIfNecessary}.
     */
    public void onDrop(DropTarget.DragObject d, boolean itemReturnedOnFailedDrop) {
        if (mInfo == null)
            return;

        ItemInfo item;
        if (d.dragInfo instanceof com.android.launcher3.model.data.WorkspaceItemFactory) {
            item = ((com.android.launcher3.model.data.WorkspaceItemFactory) d.dragInfo)
                    .makeWorkspaceItem(getContext());
        } else {
            item = d.dragInfo;
        }

        item.cellX = -1;
        item.cellY = -1;

        int rank = itemReturnedOnFailedDrop ? item.rank : mInfo.getContents().size();
        rank = com.android.launcher3.Utilities.boundToRange(rank, 0, mInfo.getContents().size());
        mInfo.getContents().add(rank, item);

        EnlargedFolderGridOrganizer organizer = new EnlargedFolderGridOrganizer(
                ActivityContext.lookupContext(getContext()).getDeviceProfile())
                .setFolderInfo(mInfo);
        organizer.updateRankAndPos(item, rank);

        ActivityContext.lookupContext(getContext()).getModelWriter()
                .addOrMoveItemInDatabase(item, mInfo.id, 0, item.cellX, item.cellY);

        if (mContent != null) {
            mContent.bindItems(mInfo.getContents());
        }
    }

    @Override
    public void onDropCompleted(View target, DropTarget.DragObject d, boolean success) {
        // When a drag from inside this folder completes (success or failure),
        // ensure this view is visible again. Workspace.startDrag() sets
        // child.setVisibility(INVISIBLE) during drag.
        setVisibility(View.VISIBLE);
    }

    public FolderInfo getInfo() {
        return mInfo;
    }
}
