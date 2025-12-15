package hivens.core.data;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.HashMap;
import java.util.Map;

/**
 * Профиль настроек конкретного сервера.
 * Хранит выбор игрока и локальные настройки.
 */
@Data
@NoArgsConstructor
public class InstanceProfile {
    private String serverId; // ID сервера (например "Industrial")

    // --- Настройки Java ---
    private Integer memoryMb = 4096;
    private String javaPath;
    private String jvmArgs;

    // --- Настройки Окна ---
    private int windowWidth = 925;
    private int windowHeight = 530;
    private boolean fullScreen = false;
    private boolean autoConnect = true;

    // --- Состояние Модов ---
    // Key: ID мода, Value: true (включен) / false (выключен)
    private Map<String, Boolean> optionalModsState = new HashMap<>();

    public InstanceProfile(String serverId) {
        this.serverId = serverId;
    }
}