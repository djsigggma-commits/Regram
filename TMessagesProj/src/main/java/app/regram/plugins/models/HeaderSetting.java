package app.regram.plugins.models;

/**
 * Модель заголовка из SDK exteraGram.
 *
 * Плагины помечают ею вставленный в чужой список {@code UItem}, чтобы экран
 * настроек отличал свою строку от чужой; текст рисует сам плагин.
 */
public class HeaderSetting extends SettingItem {

    private String text;

    public HeaderSetting(String text) {
        super("header", null, null, null);
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
