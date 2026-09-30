package app.regram.pillstack;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

import app.regram.pillstack.pills.BasePill;

/**
 * Вся склейка Pill Stack со строкой поиска.
 *
 * Наружу торчит один метод — {@link #attach(FrameLayout, EditText)}: контроллер сам создаёт,
 * пересобирает и убирает полосу пилюль, поэтому в экране-хосте правок почти нет.
 */
public class PillStackController implements PillStackEvents.Listener {

    private final FrameLayout container;
    private final EditText editText;
    private PillStackView stackView;
    private boolean attached;

    /** Подключает полосу пилюль к контейнеру строки поиска. */
    public static PillStackController attach(FrameLayout container, EditText editText) {
        if (container == null) {
            return null;
        }
        // на случай, если init() ещё не вызвали из ApplicationLoader
        PillStackConfig.init();
        return new PillStackController(container, editText);
    }

    private PillStackController(FrameLayout container, EditText editText) {
        this.container = container;
        this.editText = editText;
        if (container instanceof org.telegram.ui.Components.FragmentSearchField) {
            ((org.telegram.ui.Components.FragmentSearchField) container).setPillStackController(this);
        }

        container.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                attached = true;
                PillStackEvents.addListener(PillStackController.this);
                rebuild();
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                attached = false;
                PillStackEvents.removeListener(PillStackController.this);
            }
        });

        if (editText != null) {
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    updateVisibility();
                }
            });
        }

        if (container.isAttachedToWindow()) {
            attached = true;
            PillStackEvents.addListener(this);
            rebuild();
        }
    }

    @Override
    public void onPillStackLayoutChanged() {
        rebuild();
    }

    /** Пересобирает полосу по текущей раскладке. */
    public void rebuild() {
        if (container instanceof org.telegram.ui.Components.FragmentSearchField) {
            ((org.telegram.ui.Components.FragmentSearchField) container).updatePillStack(false);
        } else {
            rebuildContents();
        }
    }

    public PillStackView getStackView() {
        return stackView;
    }

    public void rebuildContents() {
        if (stackView != null && stackView.getParent() != container) {
            stackView.clearPills();
            stackView = null;
        }
        List<Integer> active = new ArrayList<>(PillStackConfig.getActivePills());
        if (active.isEmpty()) {
            if (stackView != null) {
                stackView.clearPills();
                container.removeView(stackView);
                stackView = null;
            }
            return;
        }
        if (stackView == null) {
            stackView = new PillStackView(container.getContext());
            // Отступ от края строки поиска — 6dp с обеих сторон, как в exteraGram
            // (FragmentSearchField.updatePillStack, :421). Раньше со стороны края
            // стояло 44dp «под крестик очистки», из-за чего полоса заметно не
            // доходила до правого бока поиска; крестика при пустом поле нет, а с
            // набранным текстом пилюли и так спрятаны.
            container.addView(stackView, LayoutHelper.createFrame(
                    LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT,
                    (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL,
                    6, 0, 6, 0));
        }
        stackView.clearPills();

        Theme.ResourcesProvider resourcesProvider = null;
        if (container instanceof Theme.ResourcesProvider) {
            resourcesProvider = (Theme.ResourcesProvider) container;
        }

        int lastActiveId = PillStackConfig.lastActivePillId();
        int selectedIndex = 0;
        for (int id : active) {
            BasePill pill = PillRegistry.createPill(id, container.getContext(), resourcesProvider);
            if (pill == null) {
                continue;
            }
            stackView.addPill(pill);
            if (id == lastActiveId) {
                selectedIndex = stackView.getPillsCount() - 1;
            }
        }
        if (stackView.getPillsCount() == 0) {
            container.removeView(stackView);
            stackView = null;
            return;
        }
        stackView.setCurrentIndex(selectedIndex);
        updateVisibility();
    }

    /** Пока в поиске что-то набрано, пилюли прячутся, чтобы не мешать тексту. */
    private void updateVisibility() {
        if (stackView == null) {
            return;
        }
        boolean hasText = editText != null && editText.getText() != null && editText.getText().length() > 0;
        stackView.setVisibilityFactor(hasText ? 0f : 1f);
    }

    public void updateColors() {
        if (stackView != null) {
            stackView.updateColors();
        }
    }

    public boolean isAttached() {
        return attached;
    }
}
