package hivens.launcher;

import com.google.gson.Gson;
import hivens.core.util.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CredentialsManager {
    private static final Logger log = LoggerFactory.getLogger(CredentialsManager.class);
    private final Path dataFile;
    private final Gson gson;

    public CredentialsManager(Path dataDir, Gson gson) {
        this.dataFile = dataDir.resolve("credentials.dat");
        this.gson = gson;
    }

    public void save(String username, String password) {
        try {
            String encryptedPass = SecurityUtils.encrypt(password);
            Credentials data = new Credentials(username, encryptedPass);
            Files.writeString(dataFile, gson.toJson(data));
        } catch (IOException e) {
            log.error("Failed to save credentials", e);
        }
    }

    public Credentials load() {
        if (!Files.exists(dataFile)) return null;
        try {
            String json = Files.readString(dataFile);
            Credentials data = gson.fromJson(json, Credentials.class);
            if (data != null && data.encryptedPassword != null) {
                // Расшифровываем сразу при загрузке
                data.decryptedPassword = SecurityUtils.decrypt(data.encryptedPassword);
                return data;
            }
        } catch (Exception e) {
            log.error("Failed to load credentials", e);
        }
        return null;
    }

    public void clear() {
        try {
            Files.deleteIfExists(dataFile);
        } catch (IOException e) {
            log.error("Failed to clear credentials", e);
        }
    }

    public static class Credentials {
        public String username;
        public String encryptedPassword;
        public transient String decryptedPassword; // Не сохраняется в JSON

        public Credentials(String u, String p) {
            this.username = u;
            this.encryptedPassword = p;
        }
    }
}