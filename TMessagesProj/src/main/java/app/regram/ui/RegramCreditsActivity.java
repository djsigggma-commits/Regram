package app.regram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.messenger.browser.Browser;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/** Credits for this fork. Keep upstream copyright notices and licenses intact. */
public class RegramCreditsActivity extends BaseNekoSettingsActivity {
    private static final int TYPE_ART = 100;
    private static final String[] CREATORS = {
            "@ihufe", "@lime_2612", "@atb_ptzhn"
    };
    private static final String[] SOURCES = {
            "@nagramxf", "@exteraless", "@inugram", "@exteragram", "@ayugram"
    };
    private static final int[] ROLES = {
            R.string.RegramDesigner, R.string.RegramCoder1, R.string.RegramCoder2
    };

    private int artRow;
    private int creatorsHeaderRow;
    private int firstCreatorRow;
    private static final String MIR_CARD = "2200 7012 4526 7221";
    private static final String VISA_CARD = "4937 2410 0693 1323";

    private int sourcesHeaderRow;
    private int firstSourceRow;
    private int donationHeaderRow;
    private int mirRow;
    private int visaRow;
    private int donationInfoRow;

    @Override
    protected void updateRows() {
        super.updateRows();
        artRow = addRow();
        creatorsHeaderRow = addRow("regramCreators");
        firstCreatorRow = rowCount;
        for (int i = 0; i < CREATORS.length; i++) addRow();
        sourcesHeaderRow = addRow("regramCodeSources");
        firstSourceRow = rowCount;
        for (int i = 0; i < SOURCES.length; i++) addRow();
        donationHeaderRow = addRow("regramDonation");
        mirRow = addRow("regramDonationMir");
        visaRow = addRow("regramDonationVisa");
        donationInfoRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.RegramCreatorsAndSources);
    }

    @Override
    public int getSearchGuid() { return 28001; }

    @Override
    public int getSearchIcon() { return R.drawable.msg_settings_old; }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new BaseListAdapter(context) {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                int position = holder.getAdapterPosition();
                return position == mirRow || position == visaRow
                        || position >= firstCreatorRow && position < firstCreatorRow + CREATORS.length
                            && isUsername(CREATORS[position - firstCreatorRow])
                        || position >= firstSourceRow && position < firstSourceRow + SOURCES.length
                            && isUsername(SOURCES[position - firstSourceRow]);
            }

            @Override
            public int getItemViewType(int position) {
                return position == artRow ? TYPE_ART
                        : position == creatorsHeaderRow || position == sourcesHeaderRow || position == donationHeaderRow
                        ? TYPE_HEADER : position == mirRow || position == visaRow
                        ? TYPE_DETAIL_SETTINGS : position == donationInfoRow
                        ? TYPE_INFO_PRIVACY : TYPE_SETTINGS;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                if (viewType != TYPE_ART) return super.onCreateViewHolder(parent, viewType);
                FrameLayout frame = new FrameLayout(context);
                frame.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(260)));
                ImageView art = new ImageView(context);
                art.setImageResource(R.drawable.regram_credits_art);
                art.setScaleType(ImageView.ScaleType.FIT_CENTER);
                art.setContentDescription(getString(R.string.RegramCreditsArtDescription));
                FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(
                        AndroidUtilities.dp(180), AndroidUtilities.dp(244), Gravity.CENTER);
                frame.addView(art, layout);
                return new RecyclerListView.Holder(frame);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
                if (position == creatorsHeaderRow) {
                    ((HeaderCell) holder.itemView).setText(getString(R.string.RegramCreators));
                } else if (position == sourcesHeaderRow) {
                    ((HeaderCell) holder.itemView).setText(getString(R.string.RegramCodeSources));
                } else if (position == donationHeaderRow) {
                    ((HeaderCell) holder.itemView).setText(getString(R.string.RegramDonation));
                } else if (position == mirRow || position == visaRow) {
                    ((TextDetailSettingsCell) holder.itemView).setTextAndValue(
                            getString(position == mirRow ? R.string.RegramDonationMir : R.string.RegramDonationVisa),
                            position == mirRow ? MIR_CARD : VISA_CARD, position == mirRow);
                } else if (position == donationInfoRow) {
                    ((TextInfoPrivacyCell) holder.itemView).setText(getString(R.string.RegramDonationCopyHint));
                } else if (position >= firstCreatorRow && position < firstCreatorRow + CREATORS.length) {
                    int index = position - firstCreatorRow;
                    ((TextSettingsCell) holder.itemView).setTextAndValue(
                            getString(ROLES[index]), CREATORS[index], index != CREATORS.length - 1);
                } else if (position >= firstSourceRow && position < firstSourceRow + SOURCES.length) {
                    int index = position - firstSourceRow;
                    ((TextSettingsCell) holder.itemView).setText(SOURCES[index], index != SOURCES.length - 1);
                }
            }
        };
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == mirRow || position == visaRow) {
            AndroidUtilities.addToClipboard((position == mirRow ? MIR_CARD : VISA_CARD).replace(" ", ""));
            BulletinFactory.of(this).createCopyBulletin(getString(R.string.CardNumberCopied)).show();
        } else if (position >= firstCreatorRow && position < firstCreatorRow + CREATORS.length) {
            openUsername(CREATORS[position - firstCreatorRow]);
        } else if (position >= firstSourceRow && position < firstSourceRow + SOURCES.length) {
            openUsername(SOURCES[position - firstSourceRow]);
        }
    }

    private static boolean isUsername(String text) {
        return text != null && text.matches("@[A-Za-z][A-Za-z0-9_]{3,31}");
    }

    private void openUsername(String username) {
        if (isUsername(username) && getParentActivity() != null) {
            Browser.openUrl(getParentActivity(), "https://t.me/" + username.substring(1));
        }
    }
}
