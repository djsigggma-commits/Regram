package app.regram.plugins;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Мелкие обёртки над org.json.
 *
 * Существуют ради одной ловушки: {@code JSONObject.optString(name, null)}
 * возвращает fallback только когда ключа нет. Если ключ есть и его значение —
 * JSON {@code null}, {@code opt()} отдаёт синглтон {@link JSONObject#NULL},
 * а {@code String.valueOf} превращает его в строку {@code "null"} из четырёх
 * букв. Метаданные плагинов сериализуются на Python-стороне как есть, поэтому
 * отсутствующий {@code __sdk_version__} приезжал как строка "null" и
 * проверка версий отвергала плагин с сообщением «requires sdk null».
 */
public final class JsonUtils {

    private JsonUtils() {
    }

    /** Строка по ключу или настоящий null — и для отсутствующего ключа, и для JSON null. */
    public static String optStringOrNull(JSONObject json, String key) {
        if (json == null || key == null || json.isNull(key)) {
            return null;
        }
        String value = json.optString(key, null);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value;
    }

    /** То же для элемента массива. */
    public static String optStringOrNull(JSONArray array, int index) {
        if (array == null || array.isNull(index)) {
            return null;
        }
        String value = array.optString(index, null);
        return value == null || value.isEmpty() ? null : value;
    }
}
