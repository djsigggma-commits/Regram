package app.regram.icons;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Модель пака иконок (порт com.exteragram.messenger.icons.IconPack).
 *
 * Пак — это каталог с файлом metadata.json и картинками. metadata.json:
 * <pre>
 * {
 *   "schemaVersion": 1,
 *   "packId": "my_pack",
 *   "packName": "My pack",
 *   "author": "Someone",
 *   "version": "1.0",
 *   "icons": { "msg_delete": "icons/delete.png", ... }
 * }
 * </pre>
 * Ключи в {@code icons} — имена ресурсов drawable в приложении, значения — относительные пути
 * внутри каталога пака.
 */
public class IconPack {

    private final String id;
    private final String name;
    private final String author;
    private final String version;
    private final Map<String, String> icons;
    private final File location;

    public IconPack(String id, String name, String author, String version, Map<String, String> icons, File location) {
        this.id = id;
        this.name = name;
        this.author = author == null ? "" : author;
        this.version = version == null ? "1.0" : version;
        this.icons = icons == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(icons));
        this.location = location;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAuthor() {
        return author;
    }

    public String getVersion() {
        return version;
    }

    public Map<String, String> getIcons() {
        return icons;
    }

    /** Каталог, в котором лежит пак. */
    public File getLocation() {
        return location;
    }

    public int getIconCount() {
        return icons.size();
    }

    public JSONObject toJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("schemaVersion", 1);
        root.put("packId", id);
        root.put("packName", name);
        root.put("author", author);
        root.put("version", version);
        JSONObject iconsJson = new JSONObject();
        for (Map.Entry<String, String> entry : icons.entrySet()) {
            iconsJson.put(entry.getKey(), entry.getValue());
        }
        root.put("icons", iconsJson);
        return root;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IconPack)) return false;
        IconPack other = (IconPack) o;
        return id.equals(other.id) && version.equals(other.version) && icons.equals(other.icons);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * id.hashCode() + version.hashCode()) + icons.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return "IconPack(id=" + id + ", name=" + name + ", icons=" + icons.size() + ")";
    }
}
