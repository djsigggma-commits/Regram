package app.regram.proxy;

/**
 * Условие, при котором прокси выключается автоматически.
 *
 * Значения — биты одной маски, поэтому условия комбинируются: exteraGram 12.9.2 держит
 * их в целочисленной настройке вместо прежнего булева «не использовать прокси с VPN».
 */
public enum ProxyDisableCondition {
    VPN(1),
    MOBILE_DATA(2),
    WIFI(4);

    public final int flag;

    ProxyDisableCondition(int flag) {
        this.flag = flag;
    }

    public int getFlag() {
        return flag;
    }
}
