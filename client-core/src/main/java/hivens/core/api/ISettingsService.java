package hivens.core.api;

import hivens.core.data.SettingsData;
import java.io.IOException;

public interface ISettingsService {
    /**
     * Возвращает текущие настройки (из кэша памяти).
     * Если не загружены — загружает.
     */
    SettingsData getSettings();

    /**
     * Сохраняет настройки на диск.
     */
    void saveSettings(SettingsData settings);
}