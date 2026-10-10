package com.exteragram.messenger.pillstack.core;

import java.util.ArrayList;

public final class PillStackConfig {
    public static final ArrayList<Integer> activePills;
    public static final ArrayList<Integer> hiddenPills;
    static {
        app.regram.pillstack.PillStackConfig.loadConfig(false);
        activePills = app.regram.pillstack.PillStackConfig.getActivePills();
        hiddenPills = app.regram.pillstack.PillStackConfig.getHiddenPills();
    }

    public static void savePillsLayout() {
        app.regram.pillstack.PillStackConfig.savePillsLayout();
    }

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
