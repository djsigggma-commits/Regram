package app.regram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.R;
import org.telegram.messenger.ProxyPingController;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

/** Only integrated features belong here; pending ports are documented, not dummy switches. */
public class RegramSettingsActivity extends BaseNekoSettingsActivity {
    private int creditsRow;
    private final ConfigItem[] options = {
            NekoConfig.regramM3Sliders,
            NekoConfig.regramDisableBrowserCollapse,
            NekoConfig.regramSortAlbumsBySize,
            NekoConfig.regramKeepPeerSearch,
            NekoConfig.regramNoPreloadRepeatOne,
            NekoConfig.regramDisableWallpaperParallax,
            NekoConfig.regramLiveProxyPing,
            NekoConfig.regramDisableSensitiveContent,
            NekoConfig.regramHideHashtagSuggestions,
            NekoConfig.regramDisableGeneralTopicSwipe
    };
    private final int[] titles = {
            R.string.RegramM3Sliders,
            R.string.RegramDisableBrowserCollapse,
            R.string.RegramSortAlbumsBySize,
            R.string.RegramKeepPeerSearch,
            R.string.RegramNoPreloadRepeatOne,
            R.string.RegramDisableWallpaperParallax,
            R.string.RegramLiveProxyPing,
            R.string.RegramDisableSensitiveContent,
            R.string.RegramHideHashtagSuggestions,
            R.string.RegramDisableGeneralTopicSwipe
    };
    private final int[] descriptions = {
            R.string.RegramM3SlidersInfo,
            R.string.RegramDisableBrowserCollapseInfo,
            R.string.RegramSortAlbumsBySizeInfo,
            R.string.RegramKeepPeerSearchInfo,
            R.string.RegramNoPreloadRepeatOneInfo,
            R.string.RegramDisableWallpaperParallaxInfo,
            R.string.RegramLiveProxyPingInfo,
            R.string.RegramDisableSensitiveContentInfo,
            R.string.RegramHideHashtagSuggestionsInfo,
            R.string.RegramDisableGeneralTopicSwipeInfo
    };

    @Override
    protected void updateRows() {
        super.updateRows();
        for (ConfigItem option : options) addRow(option.key);
        creditsRow = addRow("regramCreatorsAndSources");
        addRow();
    }

    @Override
    public int getSearchGuid() { return 28000; }

    @Override
    public int getSearchIcon() { return R.drawable.msg_settings_old; }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.RegramFeatures);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == creditsRow) {
            presentFragment(new RegramCreditsActivity());
            return;
        }
        if (position < 0 || position >= options.length) return;
        boolean checked = options[position].toggleConfigBool();
        if (options[position] == NekoConfig.regramLiveProxyPing) ProxyPingController.onSettingChanged();
        if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(checked);
        // New browser/album controls read the setting when opened; slider rendering reads it live.
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new BaseListAdapter(context) {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return holder.getItemViewType() == TYPE_CHECK || holder.getItemViewType() == TYPE_SETTINGS;
            }

            @Override
            public int getItemViewType(int position) {
                return position < options.length ? TYPE_CHECK
                        : position == creditsRow ? TYPE_SETTINGS : TYPE_INFO_PRIVACY;
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
                if (position < options.length) {
                    ((TextCheckCell) holder.itemView).setTextAndValueAndCheck(
                            getString(titles[position]), getString(descriptions[position]),
                            options[position].Bool(), true, position < options.length - 1);
                } else if (position == creditsRow) {
                    ((TextSettingsCell) holder.itemView).setText(
                            getString(R.string.RegramCreatorsAndSources), false);
                } else {
                    ((TextInfoPrivacyCell) holder.itemView).setText(getString(R.string.RegramPortsInfo));
                }
            }
        };
    }
}
