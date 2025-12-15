package hivens.core.data;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

/**
 * Продвинутая модель опционального мода.
 * Полностью покрывает возможности старого .config.
 */
@Data
@NoArgsConstructor
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
}