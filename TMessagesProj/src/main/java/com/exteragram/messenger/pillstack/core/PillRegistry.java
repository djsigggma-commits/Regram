package com.exteragram.messenger.pillstack.core;

import android.content.Context;
import org.telegram.ui.ActionBar.Theme;
import com.exteragram.messenger.pillstack.ui.pills.BasePill;

public final class PillRegistry {
    private PillRegistry() {
    }

    public static PillInfo getPillInfo(int id) {
        return app.regram.pillstack.PillRegistry.isRegistered(id) ? new PillInfo(id) : null;
    }

    public static final class PillInfo {
        private final int id;

        private PillInfo(int id) {
            this.id = id;
        }

        public PillCreator creator() {
            return new PillCreator(id);
        }
    }

    public static final class PillCreator {
        private final int id;

        private PillCreator(int id) {
            this.id = id;
        }

        public BasePill create(Context context, Theme.ResourcesProvider resourcesProvider) {
            app.regram.pillstack.pills.BasePill pill =
                    app.regram.pillstack.PillRegistry.createPill(id, context, resourcesProvider);
            return pill == null ? null : new WrappedPill(pill, resourcesProvider);
        }
    }

    private static final class WrappedPill extends BasePill {
        private final app.regram.pillstack.pills.BasePill delegate;

        private WrappedPill(app.regram.pillstack.pills.BasePill delegate,
                            Theme.ResourcesProvider resourcesProvider) {
            super(delegate.getContext(), resourcesProvider);
            this.delegate = delegate;
            addView(delegate);
        }

        @Override
        public int getPillId() { return delegate.getPillId(); }

        @Override
        public long getRefreshInterval() { return 0; }

        @Override
        public void onUpdateData(boolean force) { delegate.onUpdateData(force); }

        @Override
        public void onPillClicked() { delegate.onPillClicked(); }

        @Override
        public boolean onPillLongClicked() { return delegate.onPillLongClicked(); }

        @Override
        public void updateColors() { delegate.updateColors(); }

        @Override
        public void onPillSelected() { delegate.onPillSelected(); }

        @Override
        public void onPillUnselected() { delegate.onPillUnselected(); }

        @Override
        public void onStackVisibilityChanged(boolean visible) {
            super.onStackVisibilityChanged(visible);
            delegate.onStackVisibilityChanged(visible);
        }
    }
}
