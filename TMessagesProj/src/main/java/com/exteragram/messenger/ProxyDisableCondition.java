package com.exteragram.messenger;

public enum ProxyDisableCondition {
    VPN(1),
    MOBILE_DATA(2),
    WIFI(4);

    private final int flag;

    ProxyDisableCondition(int flag) {
        this.flag = flag;
    }

    public int getFlag() {
        return flag;
    }
}
