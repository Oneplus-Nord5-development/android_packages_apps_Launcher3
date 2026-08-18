package com.android.launcher3.popup;

import static com.android.launcher3.AbstractFloatingView.TYPE_FOLDER;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS_PREDICTION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_DISMISS_PREDICTION_UNDO;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_INSTALL_SYSTEM_SHORTCUT_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_UNINSTALL_SYSTEM_SHORTCUT_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_APP_INFO_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_DONT_SUGGEST_APP_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_WIDGETS_TAP;
import static com.android.launcher3.widget.picker.model.data.WidgetPickerDataUtils.findAllWidgetsForPackageUser;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.graphics.drawable.Drawable;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.LauncherIcons;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import com.android.launcher3.LauncherPrefs;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.AbstractFloatingViewHelper;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DropTargetHandler;
import com.android.launcher3.Flags;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.SecondaryDropTarget;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.allapps.PrivateProfileManager;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.FolderStylePickerSheet;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.InstantAppResolver;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.Snackbar;
import com.android.launcher3.widget.WidgetsBottomSheet;
import com.android.launcher3.widget.picker.model.data.WidgetPickerData;
import com.android.wm.shell.shared.bubbles.logging.EntryPoint;

import java.util.Arrays;

/**
 * Represents a system shortcut for a given app. The shortcut should have a label and icon, and an
 * onClickListener that depends on the item that the shortcut services.
 *
 * Example system shortcuts, defined as inner classes, include Widgets and AppInfo.
 *
 * @param <T> extends {@link ActivityContext}
 */
public abstract class SystemShortcut<T extends ActivityContext> extends ItemInfo
        implements View.OnClickListener {
    private static final String TAG = "SystemShortcut";

    private final int mIconResId;
    protected final int mLabelResId;
    protected int mAccessibilityActionId;

    protected final T mTarget;
    protected final ItemInfo mItemInfo;
    protected final View mOriginalView;
    protected final boolean mIsCollapsible;

    private final AbstractFloatingViewHelper mAbstractFloatingViewHelper;

    public SystemShortcut(int iconResId, int labelResId, T target, ItemInfo itemInfo,
            View originalView) {
        this(iconResId, labelResId, target, itemInfo, originalView,
                new AbstractFloatingViewHelper(), /* isCollapsible */ true);
    }

    public SystemShortcut(int iconResId, int labelResId, T target, ItemInfo itemInfo,
            View originalView, boolean isCollapsible) {
        this(iconResId, labelResId, target, itemInfo, originalView,
                new AbstractFloatingViewHelper(), isCollapsible);
    }

    public SystemShortcut(int iconResId, int labelResId, T target, ItemInfo itemInfo,
            View originalView, AbstractFloatingViewHelper abstractFloatingViewHelper) {
        this(iconResId, labelResId, target, itemInfo, originalView, abstractFloatingViewHelper,
                /* isCollapsible */ true);
    }

    public SystemShortcut(int iconResId, int labelResId, T target, ItemInfo itemInfo,
            View originalView, AbstractFloatingViewHelper abstractFloatingViewHelper,
            boolean isCollapsible) {
        mIconResId = iconResId;
        mLabelResId = labelResId;
        mAccessibilityActionId = labelResId;
        mTarget = target;
        mItemInfo = itemInfo;
        mOriginalView = originalView;
        mAbstractFloatingViewHelper = abstractFloatingViewHelper;
        mIsCollapsible = isCollapsible;
    }

    public void setIconAndLabelFor(View iconView, TextView labelView) {
        iconView.setBackgroundResource(mIconResId);
        labelView.setText(mLabelResId);
    }

    public void setIconAndContentDescriptionFor(ImageView view) {
        view.setImageResource(mIconResId);
        view.setContentDescription(view.getContext().getText(mLabelResId));
    }

    public AccessibilityNodeInfo.AccessibilityAction createAccessibilityAction(Context context) {
        return new AccessibilityNodeInfo.AccessibilityAction(
                mAccessibilityActionId, context.getText(mLabelResId));
    }

    public boolean hasHandlerForAction(int action) {
        return mAccessibilityActionId == action;
    }

    public interface Factory<T extends ActivityContext> {

        @Nullable
        SystemShortcut<T> getShortcut(T context, ItemInfo itemInfo, @NonNull View originalView);
    }

    public static final Factory<ActivityContext> WIDGETS = (context, itemInfo, originalView) -> {
        final PackageUserKey packageUserKey = PackageUserKey.fromItemInfo(itemInfo);
        if (packageUserKey == null) return null;

        final WidgetPickerData data = context.getWidgetPickerDataProvider().get();
        if (findAllWidgetsForPackageUser(data, packageUserKey).isEmpty()) {
            // hides widget picker shortcut if there are no widgets for the package.
            return null;
        }
        return new Widgets(context, itemInfo, originalView);
    };

    public static class Widgets<T extends ActivityContext> extends SystemShortcut<T> {

        public Widgets(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(getDrawableId(), R.string.widget_button_text, target, itemInfo, originalView,
                    false);
        }

        /**
         * @return drawable for Widget shortcut icon
         */
        public static int getDrawableId() {
            if (Flags.enableLauncherVisualRefresh()) {
                return R.drawable.widgets_24px;
            } else {
                return R.drawable.ic_widget;
            }
        }

        @Override
        public void onClick(View view) {
            if (!Utilities.isWorkspaceEditAllowed((Context) mTarget)) return;
            AbstractFloatingView.closeAllOpenViews(mTarget);
            WidgetsBottomSheet widgetsBottomSheet =
                    (WidgetsBottomSheet) mTarget.getLayoutInflater().inflate(
                            R.layout.widgets_bottom_sheet, mTarget.getDragLayer(), false);
            widgetsBottomSheet.populateAndShow(mItemInfo);
            mTarget.getStatsLogManager().logger().withItemInfo(mItemInfo)
                    .log(LAUNCHER_SYSTEM_SHORTCUT_WIDGETS_TAP);
        }
    }

    public static final Factory<ActivityContext> APP_INFO = AppInfo::new;

    public static class AppInfo<T extends ActivityContext> extends SystemShortcut<T> {

        @Nullable
        private SplitAccessibilityInfo mSplitA11yInfo;

        public AppInfo(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(getDrawableId(), R.string.app_info_drop_target_label, target,
                    itemInfo, originalView);
        }

        /**
         * @return drawable for App Info shortcut icon
         */
        public static int getDrawableId() {
            if (Flags.enableLauncherVisualRefresh()) {
                return R.drawable.info_24px;
            } else {
                return R.drawable.ic_info_no_shadow;
            }
        }

        /**
         * Constructor used by overview for staged split to provide custom A11y information.
         *
         * Future improvements considerations:
         * Have the logic in {@link #createAccessibilityAction(Context)} be moved to super
         * call in {@link SystemShortcut#createAccessibilityAction(Context)} by having
         * SystemShortcut be aware of TaskContainers and staged split.
         * That way it could directly create the correct node info for any shortcut that supports
         * split, but then we'll need custom resIDs for each pair of shortcuts.
         */
        public AppInfo(T target, ItemInfo itemInfo, View originalView,
                SplitAccessibilityInfo accessibilityInfo) {
            this(target, itemInfo, originalView);
            mSplitA11yInfo = accessibilityInfo;
            mAccessibilityActionId = accessibilityInfo.nodeId;
        }

        @Override
        public AccessibilityNodeInfo.AccessibilityAction createAccessibilityAction(
                Context context) {
            if (mSplitA11yInfo != null && mSplitA11yInfo.containsMultipleTasks) {
                String accessibilityLabel = context.getString(R.string.split_app_info_accessibility,
                        mSplitA11yInfo.taskTitle);
                return new AccessibilityNodeInfo.AccessibilityAction(mAccessibilityActionId,
                        accessibilityLabel);
            } else {
                return super.createAccessibilityAction(context);
            }
        }

        @Override
        public void onClick(View view) {
            Rect sourceBounds = Utilities.getViewBounds(view);
            ActivityOptionsWrapper options = mTarget.getActivityLaunchOptions(view, mItemInfo);
            // Dismiss the taskMenu when the app launch animation is complete
            options.onEndCallback.add(this::dismissTaskMenuView);
            PackageManagerHelper.startDetailsActivityForInfo(view.getContext(), mItemInfo,
                    sourceBounds, options.toBundle());
            mTarget.getStatsLogManager().logger().withItemInfo(mItemInfo)
                    .log(LAUNCHER_SYSTEM_SHORTCUT_APP_INFO_TAP);
        }

        public static class SplitAccessibilityInfo {
            public final boolean containsMultipleTasks;
            public final CharSequence taskTitle;
            public final int nodeId;

            public SplitAccessibilityInfo(boolean containsMultipleTasks,
                    CharSequence taskTitle, int nodeId) {
                this.containsMultipleTasks = containsMultipleTasks;
                this.taskTitle = taskTitle;
                this.nodeId = nodeId;
            }
        }
    }

    public static final Factory<ActivityContext> EDIT_LABEL = (context, itemInfo, originalView) -> {
        if (originalView == null || !(itemInfo instanceof ItemInfoWithIcon)) {
            return null;
        }
        return new EditLabel<>(context, (ItemInfoWithIcon) itemInfo, originalView);
    };

    public static class EditLabel<T extends ActivityContext> extends SystemShortcut<T> {

        private static final String[] CUSTOM_ICON_NAMES = {
            "ic_custom_phone", "ic_custom_messages", "ic_custom_browser", "ic_custom_camera",
            "ic_custom_gallery", "ic_custom_music", "ic_custom_calendar", "ic_custom_email",
            "ic_custom_settings", "ic_custom_games", "ic_custom_contacts", "ic_custom_calculator",
            "ic_custom_clock", "ic_custom_notes", "ic_custom_files"
        };

        private static final int[] CUSTOM_ICON_RES_IDS = {
            R.drawable.ic_custom_phone, R.drawable.ic_custom_messages, R.drawable.ic_custom_browser,
            R.drawable.ic_custom_camera, R.drawable.ic_custom_gallery, R.drawable.ic_custom_music,
            R.drawable.ic_custom_calendar, R.drawable.ic_custom_email, R.drawable.ic_custom_settings,
            R.drawable.ic_custom_games, R.drawable.ic_custom_contacts, R.drawable.ic_custom_calculator,
            R.drawable.ic_custom_clock, R.drawable.ic_custom_notes, R.drawable.ic_custom_files
        };

        public EditLabel(T target, ItemInfoWithIcon itemInfo, @NonNull View originalView) {
            super(R.drawable.gm_edit_24, R.string.edit_label, target, itemInfo, originalView);
        }

        @Override
        public void onClick(View view) {
            Context activityContext = mTarget.asContext();
            if (!Utilities.isWorkspaceEditAllowed(activityContext)) {
                return;
            }
            ComponentKey key = mItemInfo.getComponentKey();
            if (key == null) {
                return;
            }
            AbstractFloatingView.closeAllOpenViews(mTarget);

            Context themeWrapper = new android.view.ContextThemeWrapper(activityContext, R.style.EditLabelTheme);
            View dialogView = LayoutInflater.from(themeWrapper)
                    .inflate(R.layout.dialog_edit_label, null);
            TextInputLayout inputLayout = dialogView.findViewById(R.id.edit_label_input_layout);
            TextInputEditText input = dialogView.findViewById(R.id.edit_label_input);
            ImageView previewIcon = dialogView.findViewById(R.id.edit_label_icon_preview);
            LinearLayout pickerLayout = dialogView.findViewById(R.id.edit_label_picker_layout);

            CharSequence currentLabel = TextUtils.isEmpty(mItemInfo.title)
                    ? mItemInfo.appTitle : mItemInfo.title;
            if (currentLabel != null) {
                input.setText(currentLabel);
                input.setSelection(input.length());
            }

            // Load original default app icon
            Drawable tempIcon;
            try {
                tempIcon = activityContext.getPackageManager().getActivityIcon(key.componentName);
            } catch (Exception e) {
                tempIcon = activityContext.getPackageManager().getDefaultActivityIcon();
            }
            final Drawable originalAppIcon = tempIcon;

            // Display current icon in preview
            Drawable currentIconDrawable = null;
            if (mItemInfo instanceof ItemInfoWithIcon) {
                currentIconDrawable = ((ItemInfoWithIcon) mItemInfo).newIcon(activityContext);
            }
            if (currentIconDrawable == null) {
                currentIconDrawable = originalAppIcon;
            }
            previewIcon.setImageDrawable(currentIconDrawable);

            // Fetch current custom icon name
            String currentCustomIcon = LauncherPrefs.getPrefs(activityContext).getString("custom_icon_" + key.toString(), "");
            final String[] selectedIconName = { currentCustomIcon };

            // Add original default option to picker
            pickerLayout.addView(createIconPickerItem(themeWrapper, originalAppIcon, "", selectedIconName, previewIcon, pickerLayout, true));

            // Add custom icons to picker
            for (int resId : CUSTOM_ICON_RES_IDS) {
                try {
                    Drawable customIconDrawable = activityContext.getDrawable(resId);
                    String iconName = activityContext.getResources().getResourceEntryName(resId);
                    pickerLayout.addView(createIconPickerItem(themeWrapper, customIconDrawable, iconName, selectedIconName, previewIcon, pickerLayout, false));
                } catch (Exception e) {
                    Log.e("SystemShortcut", "Failed to load custom icon resource " + resId, e);
                }
            }

            new android.app.AlertDialog.Builder(themeWrapper)
                    .setTitle(R.string.edit_label)
                    .setView(dialogView)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        CharSequence newLabel = input.getText();
                        String newLabelStr = newLabel == null ? "" : newLabel.toString().trim();
                        String prefKey = "custom_label_" + key.toString();
                        SharedPreferences prefs = LauncherPrefs.getPrefs(activityContext);
                        
                        // Save label
                        if (TextUtils.isEmpty(newLabelStr)) {
                            prefs.edit().remove(prefKey).apply();
                        } else {
                            prefs.edit().putString(prefKey, newLabelStr).apply();
                        }

                        // Save icon
                        String newIconName = selectedIconName[0];
                        String iconPrefKey = "custom_icon_" + key.toString();
                        if (TextUtils.isEmpty(newIconName)) {
                            prefs.edit().remove(iconPrefKey).apply();
                        } else {
                            prefs.edit().putString(iconPrefKey, newIconName).apply();
                        }

                        // Generate new BitmapInfo
                        BitmapInfo newBitmapInfo = null;
                        if (!TextUtils.isEmpty(newIconName)) {
                            int resId = activityContext.getResources().getIdentifier(newIconName, "drawable", activityContext.getPackageName());
                            if (resId != 0) {
                                Drawable customDrawable = activityContext.getDrawable(resId);
                                if (customDrawable != null) {
                                    try (LauncherIcons li = LauncherIcons.obtain(activityContext)) {
                                        newBitmapInfo = li.createBadgedIconBitmap(customDrawable,
                                                new com.android.launcher3.icons.BaseIconFactory.IconOptions().setWrapNonAdaptiveIcon(false).setIconScale(1f));
                                    }
                                }
                            }
                        } else {
                            try (LauncherIcons li = LauncherIcons.obtain(activityContext)) {
                                newBitmapInfo = li.createBadgedIconBitmap(originalAppIcon);
                            }
                        }

                        if (mItemInfo instanceof WorkspaceItemInfo) {
                            CharSequence dbLabel = TextUtils.isEmpty(newLabelStr) ? mItemInfo.appTitle : newLabelStr;
                            ((WorkspaceItemInfo) mItemInfo).setTitle(
                                    dbLabel, activityContext, mTarget.getModelWriter());
                            if (newBitmapInfo != null) {
                                ((WorkspaceItemInfo) mItemInfo).bitmap = newBitmapInfo;
                                mTarget.getModelWriter().updateItemInDatabase(mItemInfo);
                            }
                        } else {
                            mItemInfo.title = newLabelStr;
                            if (newBitmapInfo != null && mItemInfo instanceof ItemInfoWithIcon) {
                                ((ItemInfoWithIcon) mItemInfo).bitmap = newBitmapInfo;
                            }
                            mTarget.getModelWriter().notifyItemModified(mItemInfo);
                        }

                        updateWorkspaceAndAllAppsTitlesAndIcons(activityContext, key, newLabelStr, newBitmapInfo);
                    })
                    .show();
        }

        private View createIconPickerItem(Context context, Drawable drawable, String name,
                String[] selectedIconName, ImageView previewIcon, LinearLayout parent, boolean isDefault) {
            ImageView imageView = new ImageView(context);
            int size = (int) (48 * context.getResources().getDisplayMetrics().density);
            int margin = (int) (6 * context.getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(margin, margin, margin, margin);
            imageView.setLayoutParams(params);
            imageView.setPadding(margin, margin, margin, margin);
            imageView.setImageDrawable(drawable);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(0x11000000); // semi-transparent black
            gd.setCornerRadius(12 * context.getResources().getDisplayMetrics().density);
            imageView.setBackground(gd);

            boolean isCurrent = isDefault ? TextUtils.isEmpty(selectedIconName[0]) : name.equals(selectedIconName[0]);
            if (isCurrent) {
                gd.setStroke((int) (2 * context.getResources().getDisplayMetrics().density), 0xFF3B82F6); // blue border
            }

            imageView.setOnClickListener(v -> {
                selectedIconName[0] = isDefault ? "" : name;
                previewIcon.setImageDrawable(drawable);

                for (int i = 0; i < parent.getChildCount(); i++) {
                    View child = parent.getChildAt(i);
                    android.graphics.drawable.GradientDrawable childGd = (android.graphics.drawable.GradientDrawable) child.getBackground();
                    if (child == imageView) {
                        childGd.setStroke((int) (2 * context.getResources().getDisplayMetrics().density), 0xFF3B82F6);
                    } else {
                        childGd.setStroke(0, 0);
                    }
                }
            });
            return imageView;
        }

        private void updateWorkspaceAndAllAppsTitlesAndIcons(Context context, ComponentKey key, String newLabel, BitmapInfo newIcon) {
            if (context instanceof Launcher) {
                Launcher launcher = (Launcher) context;
                
                // 1. Update in-memory AppInfo title and bitmap in AllAppsStore
                com.android.launcher3.allapps.AllAppsStore appsStore = launcher.getAppsView().getAppsStore();
                com.android.launcher3.model.data.AppInfo appInfo = appsStore.getApp(key);
                if (appInfo != null) {
                    if (newLabel != null) {
                        appInfo.title = newLabel;
                        appInfo.contentDescription = appsStore.lookUpForUid(key.componentName.getPackageName(), key.user) >= 0
                                ? launcher.getPackageManager().getUserBadgedLabel(newLabel, key.user)
                                : newLabel;
                    }
                    if (newIcon != null) {
                        appInfo.bitmap = newIcon;
                    }
                }
                
                // 2. Traverse view hierarchy of launcher's root view to update all matching BubbleTextViews
                updateTitleAndIconInViewHierarchy(launcher.getDragLayer(), key, newLabel, newIcon);
            }
        }

        private void updateTitleAndIconInViewHierarchy(View view, ComponentKey key, String newLabel, BitmapInfo newIcon) {
            if (view instanceof com.android.launcher3.BubbleTextView) {
                com.android.launcher3.BubbleTextView btv = (com.android.launcher3.BubbleTextView) view;
                if (btv.getTag() instanceof ItemInfo) {
                    ItemInfo info = (ItemInfo) btv.getTag();
                    if (key.equals(info.getComponentKey())) {
                        if (info instanceof ItemInfoWithIcon) {
                            ItemInfoWithIcon iiwi = (ItemInfoWithIcon) info;
                            if (newLabel != null) {
                                iiwi.title = newLabel;
                            }
                            if (newIcon != null) {
                                iiwi.bitmap = newIcon;
                            }
                            btv.applyIconAndLabel(iiwi);
                        }
                    }
                }
            } else if (view instanceof android.view.ViewGroup) {
                android.view.ViewGroup vg = (android.view.ViewGroup) view;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    updateTitleAndIconInViewHierarchy(vg.getChildAt(i), key, newLabel, newIcon);
                }
            }
        }
    }

    public static final Factory<ActivityContext> REMOVE = RemoveApp::new;

    public static class RemoveApp<T extends ActivityContext> extends SystemShortcut<T> {

        public RemoveApp(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(R.drawable.ic_remove_no_shadow, R.string.remove_drop_target_label, target,
                    itemInfo, originalView, false);
        }

        @Override
        public void onClick(View view) {
            AbstractFloatingView.closeAllOpenViewsExcept(mTarget, TYPE_FOLDER);
            DropTargetHandler dropTargetHandler =
                    ActivityContext.lookupContext(view.getContext()).getDropTargetHandler();
            dropTargetHandler.prepareToUndoDelete();
            dropTargetHandler.onDeleteComplete(mItemInfo, mOriginalView);
        }
    }


    public static final Factory<ActivityContext> ADD_TO_HOME_SCREEN =
            (activity, itemInfo, originalView) -> {
                if (itemInfo.container != CONTAINER_ALL_APPS
                        && itemInfo.container != CONTAINER_ALL_APPS_PREDICTION) {
                    return null;
                }
                return new AddToHomeScreen<>(activity, itemInfo, originalView);
            };
    public static class AddToHomeScreen<T extends ActivityContext> extends SystemShortcut<T> {

        public AddToHomeScreen(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(R.drawable.ic_plus, R.string.action_add_to_workspace, target,
                    itemInfo, originalView, false);
        }

        @Override
        public void onClick(View view) {
            AbstractFloatingView.closeAllOpenViews(mTarget);
            LauncherAccessibilityDelegate launcherAccessibilityDelegate =
                    (LauncherAccessibilityDelegate) mTarget.getAccessibilityDelegate();
            launcherAccessibilityDelegate.addToWorkspace(mItemInfo,
                    /*accessibility=*/ false,
                    /*finishCallback=*/ (success) -> {
                        mTarget.getStatsLogManager().logger()
                                .withItemInfo(mItemInfo)
                                .log(StatsLogManager.LauncherEvent
                                        .LAUNCHER_TAP_TO_ADD_TO_HOME_SCREEN_FROM_ALL_APPS);
                    });
        }
    }

    public static final Factory<ActivityContext> PRIVATE_PROFILE_INSTALL =
            (context, itemInfo, originalView) -> {
                if (originalView == null) {
                    return null;
                }
                if (itemInfo.getTargetComponent() == null
                        || !(itemInfo instanceof com.android.launcher3.model.data.AppInfo)
                        || !itemInfo.getContainerInfo().hasAllAppsContainer()
                        || !Process.myUserHandle().equals(itemInfo.user)) {
                    return null;
                }

                PrivateProfileManager privateProfileManager =
                        context.getAppsView().getPrivateProfileManager();
                if (privateProfileManager == null || !privateProfileManager.isEnabled()) {
                    return null;
                }

                UserHandle privateProfileUser = privateProfileManager.getProfileUser();
                if (privateProfileUser == null) {
                    return null;
                }
                // Do not show shortcut if an app is already installed to the space
                ComponentName targetComponent = itemInfo.getTargetComponent();
                if (context.getAppsView().getAppsStore().getApp(
                        new ComponentKey(targetComponent, privateProfileUser)) != null) {
                    return null;
                }

                // Do not show shortcut for settings
                String[] packagesToSkip =
                        originalView.getContext().getResources()
                                .getStringArray(R.array.skip_private_profile_shortcut_packages);
                if (Arrays.asList(packagesToSkip).contains(targetComponent.getPackageName())) {
                    return null;
                }

                return new InstallToPrivateProfile<>(
                        context, itemInfo, originalView, privateProfileUser);
            };

    static class InstallToPrivateProfile<T extends ActivityContext> extends SystemShortcut<T> {
        UserHandle mSpaceUser;

        InstallToPrivateProfile(T target, ItemInfo itemInfo, @NonNull View originalView,
                UserHandle spaceUser) {
            // TODO(b/302666597): update icon once available
            super(
                    R.drawable.ic_install_to_private,
                    R.string.install_private_system_shortcut_label,
                    target,
                    itemInfo,
                    originalView);
            mSpaceUser = spaceUser;
        }

        @Override
        public void onClick(View view) {
            Intent intent =
                    ApiWrapper.INSTANCE.get(view.getContext()).getAppMarketActivityIntent(
                            mItemInfo.getTargetComponent().getPackageName(), mSpaceUser);
            mTarget.startActivitySafely(view, intent, mItemInfo);
            AbstractFloatingView.closeAllOpenViews(mTarget);
            mTarget.getStatsLogManager()
                    .logger()
                    .withItemInfo(mItemInfo)
                    .log(LAUNCHER_PRIVATE_SPACE_INSTALL_SYSTEM_SHORTCUT_TAP);
        }
    }

    public static final Factory<ActivityContext> INSTALL =
            (activity, itemInfo, originalView) -> {
                if (originalView == null) {
                    return null;
                }
                boolean supportsWebUI = (itemInfo instanceof WorkspaceItemInfo)
                        && ((WorkspaceItemInfo) itemInfo).hasStatusFlag(
                        WorkspaceItemInfo.FLAG_SUPPORTS_WEB_UI);
                boolean isInstantApp = false;
                if (itemInfo instanceof com.android.launcher3.model.data.AppInfo appInfo) {
                    isInstantApp = InstantAppResolver.newInstance(
                            originalView.getContext()).isInstantApp(appInfo);
                }
                boolean enabled = supportsWebUI || isInstantApp;
                if (!enabled) {
                    return null;
                }
                return new Install(activity, itemInfo, originalView);
            };

    public static class Install<T extends ActivityContext> extends SystemShortcut<T> {

        public Install(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(R.drawable.ic_install_no_shadow, R.string.install_drop_target_label,
                    target, itemInfo, originalView);
        }

        @Override
        public void onClick(View view) {
            Intent intent = ApiWrapper.INSTANCE.get(view.getContext()).getAppMarketActivityIntent(
                    mItemInfo.getTargetComponent().getPackageName(), Process.myUserHandle());
            mTarget.startActivitySafely(view, intent, mItemInfo);
            AbstractFloatingView.closeAllOpenViews(mTarget);
        }
    }

    public static final Factory<ActivityContext> DONT_SUGGEST_APP =
            (activity, itemInfo, originalView) -> {
                if (!itemInfo.isPredictedItem()) {
                    return null;
                }
                return new DontSuggestApp<>(activity, itemInfo, originalView);
            };

    private static class DontSuggestApp<T extends ActivityContext> extends SystemShortcut<T> {
        DontSuggestApp(T target, ItemInfo itemInfo, View originalView) {
            super(R.drawable.ic_block_no_shadow, R.string.dismiss_prediction_label, target,
                    itemInfo, originalView);
        }

        @Override
        public void onClick(View view) {
            dismissTaskMenuView();
            mTarget.getStatsLogManager().logger()
                    .withItemInfo(mItemInfo)
                    .log(LAUNCHER_SYSTEM_SHORTCUT_DONT_SUGGEST_APP_TAP);
            Snackbar.show(mTarget,
                    view.getContext().getString(R.string.item_removed),
                    R.string.undo,
                    () -> {},
                    () -> mTarget.getStatsLogManager().logger()
                            .withItemInfo(mItemInfo)
                            .log(LAUNCHER_DISMISS_PREDICTION_UNDO));
        }
    }

    public static final Factory<ActivityContext> UNINSTALL_APP =
            (activityContext, itemInfo, originalView) -> {
                if (originalView == null || itemInfo == null) {
                    return null;
                }
                Context context = originalView.getContext();
                if (itemInfo.user != null) {
                    UserManager userManager = context.getSystemService(UserManager.class);
                    if (userManager != null) {
                        Bundle restrictions = userManager.getUserRestrictions(itemInfo.user);
                        if (restrictions != null && (restrictions.getBoolean(UserManager.DISALLOW_APPS_CONTROL, false)
                                || restrictions.getBoolean(UserManager.DISALLOW_UNINSTALL_APPS, false))) {
                            return null;
                        }
                    }
                }
                ComponentName cn = SecondaryDropTarget.getUninstallTarget(context, itemInfo);
                if (cn == null) {
                    // If component name is null, don't show uninstall shortcut.
                    // System apps will have component name as null.
                    return null;
                }
                return new UninstallApp<>(activityContext, itemInfo, originalView, cn);
            };

    public static class UninstallApp<T extends ActivityContext> extends SystemShortcut<T> {
        @NonNull
        private final ComponentName mComponentName;

        public UninstallApp(T target, ItemInfo itemInfo, @NonNull View originalView,
                @NonNull ComponentName cn) {
            super(R.drawable.ic_uninstall_no_shadow,
                    R.string.uninstall_drop_target_label, target,
                    itemInfo, originalView, false);
            mComponentName = cn;
        }

        @Override
        public void onClick(View view) {
            dismissTaskMenuView();
            SecondaryDropTarget.performUninstall(view.getContext(), mComponentName, mItemInfo);
            mTarget.getStatsLogManager()
                    .logger()
                    .withItemInfo(mItemInfo)
                    .log(LAUNCHER_PRIVATE_SPACE_UNINSTALL_SYSTEM_SHORTCUT_TAP);
        }
    }

    protected void dismissTaskMenuView() {
        mAbstractFloatingViewHelper.closeOpenViews(mTarget, true,
                AbstractFloatingView.TYPE_ALL & ~AbstractFloatingView.TYPE_REBIND_SAFE);
    }

    public static final Factory<ActivityContext> ENLARGE =
            (context, itemInfo, originalView) -> {
                if (originalView == null) {
                    return null;
                }
                if ((itemInfo.itemType != LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT)
                        && (itemInfo.itemType != LauncherSettings.Favorites.ITEM_TYPE_APPLICATION)
                        && (itemInfo.itemType != LauncherSettings.Favorites.ITEM_TYPE_FOLDER)
                        && !(itemInfo instanceof WorkspaceItemInfo)) {
                    return null;
                }
                if (itemInfo.spanX != 1 || itemInfo.spanY != 1) {
                    return null;
                }

                if (context instanceof Launcher) {
                    Launcher launcher = (Launcher) context;
                    Workspace workspace = launcher.getWorkspace();
                    int screenId = itemInfo.screenId;
                    int cellX = itemInfo.cellX;
                    int cellY = itemInfo.cellY;

                    CellLayout layout = workspace.getScreenWithId(screenId);
                    if (layout != null) {
                        boolean isVacant = false;

                        int countX = layout.getCountX();
                        int countY = layout.getCountY();

                        if (cellX + 1 < countX && cellY + 1 < countY) {
                            boolean right = layout.isRegionVacant(cellX + 1, cellY, 1, 1);
                            boolean bottom = layout.isRegionVacant(cellX, cellY + 1, 1, 1);
                            boolean diag = layout.isRegionVacant(cellX + 1, cellY + 1, 1, 1);

                            Log.d(TAG, "Checking Enlarge for " + itemInfo.title + " at (" + cellX + "," + cellY + ")" +
                                    " right=" + right + " bottom=" + bottom + " diag=" + diag);

                            if (right && bottom && diag) {
                                isVacant = true;
                            }
                        } else {
                            Log.d(TAG, "Checking Enlarge for " + itemInfo.title + " at (" + cellX + "," + cellY + ")" +
                                    " failed boundary check: " + (cellX+1) + "<" + countX + " && " + (cellY+1) + "<" + countY);
                        }

                        if (!isVacant) {
                            return null;
                        }
                    }
                }

                return new EnlargeIcon<>(context, itemInfo, originalView);
            };

    public static class EnlargeIcon<T extends ActivityContext> extends SystemShortcut<T> {

        public EnlargeIcon(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(R.drawable.ic_enlarge, R.string.action_enlarge, target, itemInfo, originalView);
        }

        @Override
        public void onClick(View view) {
            if (!(mTarget instanceof Launcher)) {
                return;
            }
            Launcher launcher = (Launcher) mTarget;
            Workspace workspace = launcher.getWorkspace();
            CellLayout layout = workspace.getScreenWithId(mItemInfo.screenId);
            if (layout == null) {
                return;
            }

            View workspaceView = layout.getChildAt(mItemInfo.cellX, mItemInfo.cellY);
            if (workspaceView == null) {
                return;
            }

            CellLayoutLayoutParams lp =
                    (CellLayoutLayoutParams)
                            workspaceView.getLayoutParams();

            layout.markCellsAsUnoccupiedForView(workspaceView);

            int newX = mItemInfo.cellX;
            int newY = mItemInfo.cellY;

            if (layout.isRegionVacant(mItemInfo.cellX, mItemInfo.cellY, 2, 2)) {
            } else if (layout.isRegionVacant(mItemInfo.cellX - 1, mItemInfo.cellY, 2, 2)) {
                newX = mItemInfo.cellX - 1;
            } else if (layout.isRegionVacant(mItemInfo.cellX, mItemInfo.cellY - 1, 2, 2)) {
                newY = mItemInfo.cellY - 1;
            } else if (layout.isRegionVacant(mItemInfo.cellX - 1, mItemInfo.cellY - 1, 2, 2)) {
                newX = mItemInfo.cellX - 1;
                newY = mItemInfo.cellY - 1;
            }

            lp.setCellX(newX);
            lp.setCellY(newY);
            lp.cellHSpan = 2;
            lp.cellVSpan = 2;
            mItemInfo.cellX = newX;
            mItemInfo.cellY = newY;
            mItemInfo.spanX = 2;
            mItemInfo.spanY = 2;

            layout.markCellsAsOccupiedForView(workspaceView);
            workspaceView.requestLayout();
            mTarget.getModelWriter().updateItemInDatabase(mItemInfo);
            AbstractFloatingView.closeAllOpenViews(mTarget);
        }
    }

    public static final Factory<ActivityContext> MINIMIZE =
            (context, itemInfo, originalView) -> {
                if (originalView == null) {
                    return null;
                }
                if (itemInfo.spanX != 2 || itemInfo.spanY != 2) {
                    return null;
                }
                return new MinimizeIcon<>(context, itemInfo, originalView);
            };

    public static class MinimizeIcon<T extends ActivityContext> extends SystemShortcut<T> {

        public MinimizeIcon(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(R.drawable.ic_minimize, R.string.action_minimize, target, itemInfo, originalView);
        }

        @Override
        public void onClick(View view) {
            if (!(mTarget instanceof Launcher)) {
                return;
            }
            Launcher launcher = (Launcher) mTarget;
            Workspace workspace = launcher.getWorkspace();
            CellLayout layout = workspace.getScreenWithId(mItemInfo.screenId);
            if (layout == null) {
                return;
            }

            View workspaceView = layout.getChildAt(mItemInfo.cellX, mItemInfo.cellY);
            if (workspaceView == null) {
                return;
            }

            CellLayoutLayoutParams lp =
                    (CellLayoutLayoutParams)
                            workspaceView.getLayoutParams();

            layout.markCellsAsUnoccupiedForView(workspaceView);

            lp.cellHSpan = 1;
            lp.cellVSpan = 1;
            mItemInfo.spanX = 1;
            mItemInfo.spanY = 1;

            layout.markCellsAsOccupiedForView(workspaceView);
            workspaceView.requestLayout();
            mTarget.getModelWriter().updateItemInDatabase(mItemInfo);
            AbstractFloatingView.closeAllOpenViews(mTarget);
        }
    }

    public static final Factory<Launcher> CUSTOMIZE_FOLDER =
            (context, itemInfo, originalView) -> {
                if (itemInfo.itemType != LauncherSettings.Favorites.ITEM_TYPE_FOLDER) {
                    return null;
                }
                return new CustomizeFolder(context, itemInfo, originalView);
            };

    public static class CustomizeFolder extends SystemShortcut<Launcher> {
        public CustomizeFolder(Launcher target, ItemInfo itemInfo, View originalView) {
            super(R.drawable.ic_customize, R.string.action_customize_folder, target, itemInfo, originalView);
        }

        @Override
        public void onClick(View view) {
            AbstractFloatingView.closeAllOpenViews(mTarget);
            if (!(mItemInfo instanceof FolderInfo folderInfo)) {
                return;
            }
            FolderIcon folderIcon = findFolderIcon(mOriginalView);
            if (folderIcon != null) {
                FolderStylePickerSheet.Companion.show(mTarget, folderIcon, folderInfo);
            }
        }

        private FolderIcon findFolderIcon(View view) {
            View current = view;
            while (current != null) {
                if (current instanceof FolderIcon) {
                    return (FolderIcon) current;
                }
                if (current.getParent() instanceof View) {
                    current = (View) current.getParent();
                } else {
                    break;
                }
            }
            return null;
        }
    }
    public static final Factory<ActivityContext> BUBBLE_SHORTCUT =
            (activity, itemInfo, originalView) -> {
                if ((itemInfo.itemType != LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT)
                        && (itemInfo.itemType != LauncherSettings.Favorites.ITEM_TYPE_APPLICATION)
                        && !(itemInfo instanceof WorkspaceItemInfo)) {
                    return null;
                }
                if (itemInfo instanceof ItemInfoWithIcon itemInfoWithIcon) {
                    // Don't show bubble shortcut option for non-resizeable apps on small screens.
                    // TODO(b/411558731): isPhone just checks for smallest width < 600dp, so it
                    // basically is a check for small screens including Foldables when folded.
                    // However, the name is a bit misleading, so considering renaming.
                    if (itemInfoWithIcon.isNonResizeable()
                            && activity.getDeviceProfile().getDeviceProperties().isPhone()) {
                        return null;
                    }
                }
                return new BubbleShortcut<>(activity, itemInfo, originalView);
            };

    public interface BubbleActivityStarter {
        /** Tell SysUI to show the provided shortcut in a bubble. */
        void showShortcutBubble(ShortcutInfo info, EntryPoint entryPoint);

        /** Tell SysUI to show the provided intent in a bubble. */
        void showAppBubble(Intent intent, UserHandle user, EntryPoint entryPoint);
    }

    /** Marker interface for identifying bubbles starting from taskbar. */
    public interface TaskbarBubbleActivityStarter extends BubbleActivityStarter {}

    public static class BubbleShortcut<T extends ActivityContext> extends SystemShortcut<T> {

        private BubbleActivityStarter mStarter;
        private final boolean mInTaskbar;

        public BubbleShortcut(T target, ItemInfo itemInfo, View originalView) {
            super(R.drawable.ic_bubble_button, R.string.bubble, target,
                    itemInfo, originalView);
            if (target instanceof BubbleActivityStarter) {
                mStarter = (BubbleActivityStarter) target;
            }
            mInTaskbar = target instanceof TaskbarBubbleActivityStarter;
        }

        private EntryPoint getEntryPoint() {
            if (mItemInfo.isInAllApps()) {
                return EntryPoint.ALL_APPS_ICON_MENU;
            }
            if (mItemInfo.isInHotseat()) {
                return mInTaskbar ? EntryPoint.TASKBAR_ICON_MENU : EntryPoint.HOTSEAT_ICON_MENU;
            }
            return EntryPoint.LAUNCHER_ICON_MENU;
        }

        @Override
        public void onClick(View view) {
            dismissTaskMenuView();
            if (mStarter == null) {
                Log.w(TAG, "starter null!");
                return;
            }
            // TODO: handle GroupTask (single) items so that recent items in taskbar work
            if (mItemInfo instanceof WorkspaceItemInfo) {
                WorkspaceItemInfo workspaceItemInfo = (WorkspaceItemInfo) mItemInfo;
                ShortcutInfo shortcutInfo = workspaceItemInfo.getDeepShortcutInfo();
                if (shortcutInfo != null) {
                    mStarter.showShortcutBubble(shortcutInfo, getEntryPoint());
                    return;
                }
            }
            // If we're here check for an intent
            if (mItemInfo.getIntent() != null) {
                final Intent intent = new Intent(mItemInfo.getIntent());
                if (intent.getPackage() == null) {
                    intent.setPackage(mItemInfo.getTargetPackage());
                }
                mStarter.showAppBubble(intent, mItemInfo.user, getEntryPoint());
            } else {
                Log.w(TAG, "unable to bubble, no intent: " + mItemInfo);
            }
        }
    }
}
