package hivens.core.data;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

/**
 * Продвинутая модель опционального мода.
 * Полностью покрывает возможности старого .config.
 */
public class OptionalMod {
    // Уникальный ID (например "xaero_minimap")
    private String id;
    
    // UI поля
    private String name;         // "Xaero's Minimap"
    private String description;  // "Лучшая мини-карта..."
    private String category;     // "HUD", "Графика", "Производительность"
    private boolean isDefault;   // Включен ли по умолчанию?

    // --- Логика Файловой Системы (как в .config) ---
    // Список файлов, которые НУЖНО добавить в папку mods
    private List<String> jars = new ArrayList<>();
    
    // Список файлов, которые НУЖНО удалить из папки mods (конфликты)
    private List<String> excludings = new ArrayList<>();
    
    // --- Логика UI (Новое) ---
    // ID модов, которые нельзя включить одновременно с этим (для RadioButton поведения)
    private List<String> incompatibleIds = new ArrayList<>();

    public OptionalMod() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public List<String> getJars() {
        return jars;
    }

    public void setJars(List<String> jars) {
        this.jars = jars;
    }

    public List<String> getExcludings() {
        return excludings;
    }

    public void setExcludings(List<String> excludings) {
        this.excludings = excludings;
    }

    public List<String> getIncompatibleIds() {
        return incompatibleIds;
    }

    public void setIncompatibleIds(List<String> incompatibleIds) {
        this.incompatibleIds = incompatibleIds;
    }
}