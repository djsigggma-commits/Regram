package app.regram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import java.util.ArrayList;
import java.util.HashSet;

/** Local, per-account last-seen ordering. Does not query hidden status or track users. */
public class RecentlyOnlineActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private final ArrayList<TLRPC.User> users = new ArrayList<>();
    private RecyclerListView list;
    private RecyclerListView.SelectionAdapter adapter;
    private boolean refreshScheduled;
    private final Runnable refreshRunnable = () -> {
        refreshScheduled = false;
        if (list != null) refresh();
    };

    @Override public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        return true;
    }

    @Override public void onFragmentDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        AndroidUtilities.cancelRunOnUIThread(refreshRunnable);
        refreshScheduled = false;
        list = null;
        adapter = null;
        users.clear();
        super.onFragmentDestroy();
    }

    private int score(TLRPC.User user, int now, MessagesController controller) {
        if (controller.onlinePrivacy.containsKey(user.id)) return Integer.MAX_VALUE;
        if (user.status == null) return 0;
        int expires = user.status.expires;
        if (expires > now) return Integer.MAX_VALUE;
        if (expires > 0) return expires;
        if (user.status instanceof TLRPC.TL_userStatusRecently) return 3;
        if (user.status instanceof TLRPC.TL_userStatusLastWeek) return 2;
        if (user.status instanceof TLRPC.TL_userStatusLastMonth) return 1;
        return 0;
    }

    private void refresh() {
        final MessagesController controller = getMessagesController();
        final int now = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        users.clear();
        HashSet<Long> seen = new HashSet<>();
        for (TLRPC.Dialog dialog : controller.dialogsUsersOnly) {
            if (dialog.id <= 0 || !seen.add(dialog.id)) continue;
            TLRPC.User user = controller.getUser(dialog.id);
            if (user == null || user.bot || UserObject.isUserSelf(user) || UserObject.isDeleted(user)
                    || UserObject.isReplyUser(user) || UserObject.isService(dialog.id)
                    || MessagesController.isSupportUser(user)) continue;
            users.add(user);
        }
        users.sort((a, b) -> {
            int byStatus = Integer.compare(score(b, now, controller), score(a, now, controller));
            return byStatus != 0 ? byStatus : Long.compare(b.id, a.id);
        });
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateInterfaces && account == currentAccount &&
                (args.length == 0 || !(args[0] instanceof Integer) ||
                 (((Integer) args[0]) & MessagesController.UPDATE_MASK_STATUS) != 0)) {
            AndroidUtilities.runOnUIThread(() -> {
                if (list != null && !refreshScheduled) {
                    refreshScheduled = true;
                    AndroidUtilities.runOnUIThread(refreshRunnable, 350);
                }
            });
        }
    }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.RegramRecentlyOnline));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        EmptyTextProgressView empty = new EmptyTextProgressView(context);
        empty.setText(LocaleController.getString(R.string.RegramNoRecentUsers));
        empty.showTextView();
        frame.addView(empty, LayoutHelper.createFrame(-1, -1));
        list = new RecyclerListView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setAdapter(adapter = new RecyclerListView.SelectionAdapter() {
            @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
            @Override public int getItemCount() { return users.size(); }
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                UserCell cell = new UserCell(parent.getContext(), 8, 0, false);
                cell.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                return new RecyclerListView.Holder(cell);
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                TLRPC.User user = users.get(position);
                ((UserCell) holder.itemView).setData(user, UserObject.getUserName(user),
                        LocaleController.formatUserStatus(currentAccount, user), 0, position < users.size() - 1);
            }
        });
        list.setEmptyView(empty);
        list.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= users.size()) return;
            Bundle args = new Bundle();
            args.putLong("user_id", users.get(position).id);
            presentFragment(new ChatActivity(args));
        });
        frame.addView(list, LayoutHelper.createFrame(-1, -1));
        fragmentView = frame;
        refresh();
        return frame;
    }
}
