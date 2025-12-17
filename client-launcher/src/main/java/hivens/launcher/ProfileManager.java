package hivens.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import hivens.core.data.InstanceProfile;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ProfileManager {

    private static final Logger log = LoggerFactory.getLogger(ProfileManager.class);
    private static final String FILENAME = "profiles.json";

    private final Path workDir;
    private final Gson gson;
    private final Map<String, InstanceProfile> profiles = new HashMap<>();

    @Getter
    private String lastServerId;

    public ProfileManager(Path workDir, Gson gson) {
        this.workDir = workDir;
        this.gson = gson;
        load();
    }

    public InstanceProfile getProfile(String serverId) {
        return profiles.computeIfAbsent(serverId, InstanceProfile::new);
    }

    public void saveProfile(InstanceProfile profile) {
        profiles.put(profile.getServerId(), profile);
        save();
    }

    public void setLastServerId(String lastServerId) {
        this.lastServerId = lastServerId;
        save(); // Сохраняем сразу при изменении
    }

    private void load() {
        Path file = workDir.resolve(FILENAME);
        if (!Files.exists(file)) return;

        try (Reader reader = Files.newBufferedReader(file)) {
            // Читаем как JsonElement, чтобы определить формат файла
            JsonElement root = gson.fromJson(reader, JsonElement.class);

            if (root.isJsonObject()) {
                JsonObject json = root.getAsJsonObject();

                // Проверяем, новый ли это формат (есть поле "profiles" или "lastServerId")
                if (json.has("profiles") || json.has("lastServerId")) {
                    if (json.has("lastServerId")) {
                        this.lastServerId = json.get("lastServerId").getAsString();
                    }
                    if (json.has("profiles")) {
                        Map<String, InstanceProfile> loaded = gson.fromJson(
                                json.get("profiles"),
                                new TypeToken<Map<String, InstanceProfile>>(){}.getType()
                        );
                        if (loaded != null) profiles.putAll(loaded);
                    }
                } else {
                    Map<String, InstanceProfile> loaded = gson.fromJson(
                            root,
                            new TypeToken<Map<String, InstanceProfile>>(){}.getType()
                    );
                    if (loaded != null) profiles.putAll(loaded);
                }
            }

            log.info("Loaded {} profiles. Last server: {}", profiles.size(), lastServerId);

        } catch (IOException e) {
            log.error("Failed to load profiles from {}", file, e);
        }
    }

    private void save() {
        Path file = workDir.resolve(FILENAME);
        try (Writer writer = Files.newBufferedWriter(file)) {
            // Создаем структуру для сохранения (обертка)
            Map<String, Object> data = new HashMap<>();
            data.put("lastServerId", lastServerId);
            data.put("profiles", profiles);

            gson.toJson(data, writer);
        } catch (IOException e) {
            log.error("Failed to save profiles!", e);
        }
    }
}
