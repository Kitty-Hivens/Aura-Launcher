package hivens.core.api;

import hivens.core.data.SettingsData;
import java.io.IOException;

/**
 * Контракт для сервиса управления настройками.
 * Отвечает за загрузку и сохранение SettingsData.
 */
public interface ISettingsService {

    /**
     * Загружает настройки (SettingsData) из файла конфигурации.
     * Если файл не найден, создает и возвращает SettingsData.defaults().
     *
     * @return Загруженные или стандартные настройки.
     * @throws IOException в случае ошибок I/O (кроме "Файл не найден").
     */
    SettingsData loadSettings() throws IOException;

    /**
     * Сохраняет SettingsData в файл конфигурации.
     *
     * @param settings Данные для сохранения.
     * @throws IOException в случае ошибок I/O.
     */
    void saveSettings(SettingsData settings) throws IOException;
}