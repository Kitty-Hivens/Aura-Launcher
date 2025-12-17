package hivens.core.api.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Setter
@Getter
public class ServerProfile {
    private String name;
    private String title;
    private String version;
    private String ip;
    private int port;
    private String assetDir;

    // [FIX] Геттеры и сеттеры для extraCheckSum
    // [FIX] Поле для хранения хеша extra.zip
    private String extraCheckSum;

    private Map<String, Object> optionalModsData;

    public ServerProfile() {
    }

    public ServerProfile(String name, String version, String ip, int port) {
        this.name = name;
        this.version = version;
        this.ip = ip;
        this.port = port;
        this.assetDir = name;
        this.title = name + " " + version;
    }

    // --- Getters & Setters ---

    @Override
    public String toString() {
        return title != null ? title : name;
    }
}