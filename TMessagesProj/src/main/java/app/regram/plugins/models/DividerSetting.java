package app.regram.plugins.models;

public class DividerSetting extends SettingItem {

    private String text;

    public DividerSetting(String text) {
        super("divider", null, null, null);
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
