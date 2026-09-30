package com.exteragram.messenger.pillstack.ui;

import android.content.Context;
import com.exteragram.messenger.pillstack.ui.pills.BasePill;

public class PillStackView extends app.regram.pillstack.PillStackView {
    public PillStackView(Context context) {
        super(context);
    }

    public void addPill(BasePill pill) {
        super.addPill(pill);
    }
}
