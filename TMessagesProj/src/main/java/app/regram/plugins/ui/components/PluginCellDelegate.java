package app.regram.plugins.ui.components;

import android.view.View;

public interface PluginCellDelegate {

    void sharePlugin();

    void openInExternalApp();

    void deletePlugin();

    void togglePlugin(View view);

    void openPluginSettings();

    void pinPlugin(View view);

    boolean canOpenInExternalApp();
}
