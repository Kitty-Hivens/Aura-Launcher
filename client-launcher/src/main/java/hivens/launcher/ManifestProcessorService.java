package hivens.launcher;

import com.google.gson.Gson;
import hivens.core.api.IManifestProcessorService;
import hivens.core.api.model.ServerProfile;
import hivens.core.data.FileData;
import hivens.core.data.FileManifest;
import hivens.core.data.OptionalMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ManifestProcessorService implements IManifestProcessorService {

    private static final Logger log = LoggerFactory.getLogger(ManifestProcessorService.class);
    private final Gson gson;

    public ManifestProcessorService(Gson gson) {
        this.gson = gson;
    }

    @Override
    public FileManifest processManifest(String version) {
        return new FileManifest();
    }

    @Override
    public Map<String, FileData> flattenManifest(FileManifest manifest) {
        Map<String, FileData> result = new HashMap<>();
        if (manifest != null) flattenRecursive(manifest, "", result);
        return result;
    }

    private void flattenRecursive(FileManifest manifest, String currentPath, Map<String, FileData> result) {
        if (manifest.files() != null) {
            manifest.files().forEach((k, v) -> result.put(currentPath + k, v));
        }
        if (manifest.directories() != null) {
            manifest.directories().forEach((k, v) -> flattenRecursive(v, currentPath + k + "/", result));
        }
    }

    /**
     * [FIX] Теперь этот метод принимает ServerProfile и парсит реальные данные.
     * Убедитесь, что вы обновили интерфейс IManifestProcessorService!
     */
    @Override
    public List<OptionalMod> getOptionalModsForClient(ServerProfile profile) {
        List<OptionalMod> result = new ArrayList<>();
        Map<String, Object> rawMods = profile.getOptionalModsData();

        if (rawMods == null || rawMods.isEmpty()) {
            return result;
        }

        for (Map.Entry<String, Object> entry : rawMods.entrySet()) {
            String modId = entry.getKey();
            Object modData = entry.getValue();

            try {
                // Превращаем Map в Json, а потом в OptionalMod
                String json = gson.toJson(modData);
                OptionalMod mod = gson.fromJson(json, OptionalMod.class);
                
                if (mod.getId() == null) mod.setId(modId);
                // Если с сервера не пришло jars, используем ID.jar как догадку
                if (mod.getJars() == null || mod.getJars().isEmpty()) {
                    mod.setJars(List.of(modId + ".jar"));
                }
                
                result.add(mod);
            } catch (Exception e) {
                log.error("Failed to parse mod {}", modId, e);
            }
        }
        return result;
    }

    @Deprecated
    public List<OptionalMod> getOptionalModsForClient(String version) {
        return Collections.emptyList();
    }
}