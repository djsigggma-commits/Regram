package com.exteragram.messenger.pillstack.core;

import android.content.Context;
import org.telegram.ui.ActionBar.Theme;
import com.exteragram.messenger.pillstack.ui.pills.BasePill;

/** Binary-compatible facade for Java/DEX plugins, not just Python attribute adapters. */
public final class PillRegistry {
    private PillRegistry() {}

    public interface PillCreator {
        BasePill create(Context context, Theme.ResourcesProvider resourcesProvider);
    }

    public static final class PillInfo {
        public final int id;
        public final CharSequence name;
        public final int iconRes, iconColorTop, iconColorBottom;
        public final PillCreator creator;

        public PillInfo(int id, CharSequence name, int iconRes, int iconColorTop,
                        int iconColorBottom, PillCreator creator) {
            this.id = id;
            this.name = name;
            this.iconRes = iconRes;
            this.iconColorTop = iconColorTop;
            this.iconColorBottom = iconColorBottom;
            this.creator = creator;
        }
        public int id() { return id; }
        public CharSequence name() { return name; }
        public int iconRes() { return iconRes; }
        public int iconColorTop() { return iconColorTop; }
        public int iconColorBottom() { return iconColorBottom; }
        public PillCreator creator() { return creator; }
    }

    public static void beginTransaction() { app.regram.pillstack.PillRegistry.beginTransaction(); }
    public static void endTransaction() { app.regram.pillstack.PillRegistry.endTransaction(); }
    public static void unregister(int id) { app.regram.pillstack.PillRegistry.unregister(id); }
    public static void activatePill(int id) { app.regram.pillstack.PillRegistry.activatePill(id); }
    public static boolean isRegistered(int id) { return app.regram.pillstack.PillRegistry.isRegistered(id); }

    public static void register(PillInfo info) {
        if (info == null || info.creator == null) return;
        app.regram.pillstack.PillRegistry.register(new app.regram.pillstack.PillRegistry.PillInfo(
                info.id, info.name, info.iconRes, info.iconColorTop, info.iconColorBottom,
                info.creator::create));
    }

    public static PillInfo getPillInfo(int id) {
        app.regram.pillstack.PillRegistry.PillInfo info = app.regram.pillstack.PillRegistry.getPillInfo(id);
        if (info == null) return null;
        return new PillInfo(info.id, info.name, info.iconRes, info.iconColorTop, info.iconColorBottom,
                (context, resourcesProvider) -> {
                    app.regram.pillstack.pills.BasePill pill = app.regram.pillstack.PillRegistry.createPill(id, context, resourcesProvider);
                    if (pill == null) return null;
                    return pill instanceof BasePill ? (BasePill) pill : new WrappedPill(pill, resourcesProvider);
                });
    }

    private static final class WrappedPill extends BasePill {
        private final app.regram.pillstack.pills.BasePill delegate;
        private WrappedPill(app.regram.pillstack.pills.BasePill delegate, Theme.ResourcesProvider resourcesProvider) {
            super(delegate.getContext(), resourcesProvider);
            this.delegate = delegate;
            addView(delegate);
        }
        @Override public int getPillId() { return delegate.getPillId(); }
        @Override public long getRefreshInterval() { return 0; }
        @Override public void onUpdateData(boolean force) { delegate.onUpdateData(force); }
        @Override public void onPillClicked() { delegate.onPillClicked(); }
        @Override public boolean onPillLongClicked() { return delegate.onPillLongClicked(); }
        @Override public void updateColors() { delegate.updateColors(); }
        @Override public void onPillSelected() { delegate.onPillSelected(); }
        @Override public void onPillUnselected() { delegate.onPillUnselected(); }
        @Override public void onStackVisibilityChanged(boolean visible) {
            super.onStackVisibilityChanged(visible);
            delegate.onStackVisibilityChanged(visible);
        }
    }
}
