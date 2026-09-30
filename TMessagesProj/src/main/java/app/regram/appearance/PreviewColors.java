package app.regram.appearance;

import org.telegram.ui.ActionBar.Theme;

/**
 * Общая палитра всех превью настроек. Порт
 * {@code com.exteragram.messenger.preferences.components.PreviewColors} (12.9.0).
 *
 * Цвета не кэшируются: каждый вызов читает текущую тему, поэтому превью
 * перекрашивается сменой темы без пересоздания вьюх — достаточно invalidate().
 */
public final class PreviewColors {

    private PreviewColors() {
    }

    /** Подложка макета: текстовый цвет темы, приглушённый до еле заметного. */
    public static int getBackgroundColor() {
        return Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText),
                Theme.isCurrentThemeDark() ? 0.05f : 0.035f);
    }

    /** Контур макета: та же основа, что и подложка, плюс фиксированные 0.085 альфы. */
    public static int getOutlineColor() {
        return Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText),
                (Theme.isCurrentThemeDark() ? 0.05f : 0.035f) + 0.085f);
    }

    /**
     * Цвет «заглушек» — прямоугольников, изображающих текст внутри превью.
     *
     * @param primary true для строки-заголовка (заметнее), false для второстепенной
     */
    public static int getMockColor(boolean primary) {
        return Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2),
                primary ? 0.4f : 0.2f);
    }
}
