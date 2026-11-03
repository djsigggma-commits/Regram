package app.regram.player;

import java.util.Locale;

/** Two latest labels per player; progress ticks within one second reuse their strings. */
final class PlaybackTimeText {
    private int elapsedSeconds;
    private int remainingSeconds;
    private String elapsedText;
    private String remainingText;

    String elapsed(int seconds) {
        if (elapsedText == null || elapsedSeconds != seconds) {
            elapsedSeconds = seconds;
            elapsedText = format(seconds);
        }
        return elapsedText;
    }

    String remaining(int seconds) {
        if (remainingText == null || remainingSeconds != seconds) {
            remainingSeconds = seconds;
            remainingText = "−" + format(seconds);
        }
        return remainingText;
    }

    static String format(int seconds) {
        if (seconds >= 3600) {
            return String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        }
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }
}
