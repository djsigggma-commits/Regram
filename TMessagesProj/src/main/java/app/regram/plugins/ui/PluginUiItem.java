package app.regram.plugins.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

final class PluginUiItem extends UItem {

    private PluginUiItem(int viewType) {
        super(viewType, false);
    }

    static PluginUiItem check(int id, CharSequence text, int iconResId) {
        PluginUiItem item = new PluginUiItem(UniversalAdapter.VIEW_TYPE_CHECK);
        item.id = id;
        item.text = text;
        item.iconResId = iconResId;
        return item;
    }

    static UItem engineToggle(int id, CharSequence text, boolean checked) {
        return EngineToggleFactory.of(id, text, checked);
    }

    static PluginUiItem fullscreen(View view, int minusHeight, boolean minusPadding) {
        PluginUiItem item = new PluginUiItem(UniversalAdapter.VIEW_TYPE_FULLSCREEN_CUSTOM);
        item.view = view;
        item.intValue = minusHeight;
        item.flags = minusPadding ? 1 : 0;
        item.transparent = true;
        return item;
    }

    @Override
    protected boolean contentsEquals(AdapterWithDiffUtils.Item candidate) {
        if (!(candidate instanceof PluginUiItem)) {
            return false;
        }
        PluginUiItem item = (PluginUiItem) candidate;
        if (viewType != item.viewType || id != item.id || enabled != item.enabled) {
            return false;
        }
        if (viewType == UniversalAdapter.VIEW_TYPE_CHECK) {
            return checked == item.checked
                    && iconResId == item.iconResId
                    && multiline == item.multiline
                    && TextUtils.equals(text, item.text)
                    && TextUtils.equals(textValue, item.textValue);
        }
        if (viewType == UniversalAdapter.VIEW_TYPE_FULLSCREEN_CUSTOM) {
            return view == item.view
                    && intValue == item.intValue
                    && flags == item.flags
                    && transparent == item.transparent;
        }
        return super.contentsEquals(candidate);
    }

    private static final class EngineToggleFactory extends UItem.UItemFactory<TextCheckCell> {
        static {
            setup(new EngineToggleFactory());
        }

        @Override
        public TextCheckCell createView(Context context, RecyclerListView listView,
                                        int currentAccount, int classGuid,
                                        Theme.ResourcesProvider resourcesProvider) {
            TextCheckCell cell = new TextCheckCell(context, resourcesProvider);
            cell.setDrawCheckRipple(true);
            cell.setTypeface(AndroidUtilities.bold());
            cell.setHeight(56);
            return cell;
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter,
                             UniversalRecyclerView listView) {
            TextCheckCell cell = (TextCheckCell) view;
            cell.setEnabled(item.enabled, null);
            cell.setIcon(0);
            cell.setTextAndCheck(item.text, item.checked, divider);
        }

        @Override
        public boolean equals(UItem first, UItem second) {
            return first.id == second.id;
        }

        @Override
        public boolean contentsEquals(UItem first, UItem second) {
            return first.id == second.id
                    && first.checked == second.checked
                    && first.enabled == second.enabled
                    && TextUtils.equals(first.text, second.text);
        }

        static UItem of(int id, CharSequence text, boolean checked) {
            UItem item = UItem.ofFactory(EngineToggleFactory.class);
            item.id = id;
            item.text = text;
            item.checked = checked;
            return item;
        }
    }
}
