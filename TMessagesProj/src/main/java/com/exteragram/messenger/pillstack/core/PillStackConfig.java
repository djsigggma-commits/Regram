package com.exteragram.messenger.pillstack.core;

import java.util.ArrayList;

public final class PillStackConfig {
    private PillStackConfig() {
    }

    public static ArrayList<Integer> getActivePills() {
        app.regram.pillstack.PillStackConfig.loadConfig(false);
        return app.regram.pillstack.PillStackConfig.getActivePills();
    }

    public static ArrayList<Integer> getHiddenPills() {
        app.regram.pillstack.PillStackConfig.loadConfig(false);
        return app.regram.pillstack.PillStackConfig.getHiddenPills();
    }

    public static int getLastActivePillId() {
        app.regram.pillstack.PillStackConfig.loadConfig(false);
        return app.regram.pillstack.PillStackConfig.lastActivePillId.Int();
    }
}
