package app.regram.pillstack;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Простая шина событий Pill Stack.
 *
 * В оригинале использовались собственные константы NotificationCenter; здесь берём отдельный
 * список слушателей, чтобы не править общий NotificationCenter.
 */
public class PillStackEvents {

    public interface Listener {
        /** Изменился состав/порядок пилюль — полосу надо пересобрать. */
        default void onPillStackLayoutChanged() {}

        /** Изменились настройки конкретных пилюль (пустой массив — всех). */
        default void onPillStackSettingsChanged(int[] pillIds) {}
    }

    private static final List<Listener> listeners = new ArrayList<>();
    private static final HashSet<Integer> pendingUpdates = new HashSet<>();

    public static void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public static void notifyLayoutChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(
                    org.telegram.messenger.NotificationCenter.pillStackLayoutChanged);
            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onPillStackLayoutChanged();
            }
        });
    }

    public static void notifySettingsChanged(int... pillIds) {
        if (pillIds == null || pillIds.length == 0) {
            for (int id : PillRegistry.getRegisteredIds()) {
                pendingUpdates.add(id);
            }
        } else {
            for (int id : pillIds) {
                pendingUpdates.add(id);
            }
        }
        final int[] ids = pillIds == null ? new int[0] : pillIds;
        AndroidUtilities.runOnUIThread(() -> {
            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onPillStackSettingsChanged(ids);
            }
        });
    }

    /** Помечена ли пилюля как требующая перезагрузки данных (и снимает пометку). */
    public static boolean checkAndClearPendingUpdate(int pillId) {
        return pendingUpdates.remove(pillId);
    }

    /** Относится ли событие настроек к данной пилюле. */
    public static boolean shouldUpdatePill(int[] changedIds, int... pillIds) {
        if (changedIds == null || changedIds.length == 0 || pillIds.length == 0) {
            return true;
        }
        for (int changed : changedIds) {
            for (int pillId : pillIds) {
                if (changed == pillId) {
                    return true;
                }
            }
        }
        return false;
    }
}
