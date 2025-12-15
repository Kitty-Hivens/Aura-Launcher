package hivens.launcher;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import hivens.core.data.InstanceProfile;
import org.slf4j.Logger;         // <--- 1. Импорт интерфейса
import org.slf4j.LoggerFactory;  // <--- 2. Импорт фабрики

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ProfileManager {

    // 3. Создаем логгер для этого класса
    private static final Logger log = LoggerFactory.getLogger(ProfileManager.class);

    private static final String FILENAME = "profiles.json";
    private final Path workDir;
    private final Gson gson;
    private final Map<String, InstanceProfile> profiles = new HashMap<>();

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

    private void load() {
        Path file = workDir.resolve(FILENAME);
        if (!Files.exists(file)) {
            log.info("Profiles file not found at {}, creating new one.", file); // Информационное сообщение
            return;
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, InstanceProfile> loaded = gson.fromJson(
                    reader,
                    new TypeToken<Map<String, InstanceProfile>>(){}.getType()
            );
            if (loaded != null) profiles.putAll(loaded);

            log.info("Loaded {} profiles from disk.", profiles.size());

        } catch (IOException e) {
            // 4. Логируем ошибку с полным стектрейсом (ERROR)
            log.error("Failed to load instance profiles from " + file, e);
        }
    }

    private void save() {
        Path file = workDir.resolve(FILENAME);
        try (Writer writer = Files.newBufferedWriter(file)) {
            gson.toJson(profiles, writer);
            log.debug("Profiles saved successfully.");
        } catch (IOException e) {
            log.error("CRITICAL: Failed to save profiles!", e);
        }
    }
}
