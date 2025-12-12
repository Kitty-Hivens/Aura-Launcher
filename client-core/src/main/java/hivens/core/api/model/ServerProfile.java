package hivens.core.api.model;

import java.util.Map;

/**
 * Модель данных, описывающая один профиль сервера в списке.
 * Используется в UI (ComboBox) и при запуске игры.
 */
public class ServerProfile {

    private String name;        // Название (например "Industrial")
    private String title;       // Красивое название для списка (например "Industrial 1.12.2")
    private String version;     // Версия игры ("1.12.2")
    private String ip;          // Адрес ("industrial.smartycraft.ru")
    private int port;           // Порт (25566)
    private String assetDir;    // Имя папки с клиентом (обычно совпадает с name)

    // Сюда сохраним сырые данные о модах, пригодятся потом при скачивании
    private Map<String, Object> optionalModsData;

    public ServerProfile() {
    }

    public ServerProfile(String name, String version, String ip, int port) {
        this.name = name;
        this.version = version;
        this.ip = ip;
        this.port = port;
        this.assetDir = name; // По умолчанию папка называется как сервер
        this.title = name + " " + version;
    }

    // --- Getters & Setters ---

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getAssetDir() { return assetDir; }
    public void setAssetDir(String assetDir) { this.assetDir = assetDir; }

    public Map<String, Object> getOptionalModsData() { return optionalModsData; }
    public void setOptionalModsData(Map<String, Object> optionalModsData) { this.optionalModsData = optionalModsData; }

    // Важно для JavaFX ComboBox: он вызывает toString() чтобы показать текст
    @Override
    public String toString() {
        return title != null ? title : name;
    }
}