package hivens.launcher;

import hivens.core.api.IManifestProcessorService;
import hivens.core.data.FileData;
import hivens.core.data.FileManifest;
import hivens.core.data.OptionalMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManifestProcessorService implements IManifestProcessorService {

    private static final Logger log = LoggerFactory.getLogger(ManifestProcessorService.class);

    @Override
    public FileManifest processManifest(String version) {
        return new FileManifest();
    }

    /**
     * Превращает дерево папок в плоский список файлов (для проверки хешей и classpath).
     */
    @Override
    public Map<String, FileData> flattenManifest(FileManifest manifest) {
        Map<String, FileData> result = new HashMap<>();
        if (manifest != null) {
            flattenRecursive(manifest, "", result);
        }
        return result;
    }

    private void flattenRecursive(FileManifest manifest, String currentPath, Map<String, FileData> result) {
        // 1. Добавляем файлы текущего уровня
        if (manifest.files() != null) {
            manifest.files().forEach((fileName, fileData) -> {
                result.put(currentPath + fileName, fileData);
            });
        }

        // 2. Рекурсивно заходим в подпапки
        if (manifest.directories() != null) {
            manifest.directories().forEach((dirName, subManifest) -> {
                String newPath = currentPath + dirName + "/";
                flattenRecursive(subManifest, newPath, result);
            });
        }
    }

    @Override
    public List<OptionalMod> getOptionalModsForClient(String version) {
        List<OptionalMod> mods = new ArrayList<>();

        // 1. Xaero's Minimap
        OptionalMod xaero = new OptionalMod();
        xaero.setId("XaerosMinimap");
        xaero.setName("Xaero's Minimap");
        xaero.setCategory("Карты");
        xaero.setDescription("Современная мини-карта. Рекомендуем.");
        xaero.setDefault(true);
        xaero.setJars(List.of("XaerosMinimap.jar"));
        xaero.setExcludings(List.of("VoxelMap.jar", "VoxelMapNoRadar.jar"));
        xaero.setIncompatibleIds(List.of("VoxelMap"));
        mods.add(xaero);

        // 2. VoxelMap
        OptionalMod voxel = new OptionalMod();
        voxel.setId("VoxelMap");
        voxel.setName("VoxelMap");
        voxel.setCategory("Карты");
        voxel.setDescription("Классическая карта.");
        voxel.setDefault(false);
        voxel.setJars(List.of("VoxelMap.jar"));
        voxel.setExcludings(List.of("XaerosMinimap.jar"));
        voxel.setIncompatibleIds(List.of("XaerosMinimap"));
        mods.add(voxel);

        // 3. OptiFine
        OptionalMod optifine = new OptionalMod();
        optifine.setId("OptiFine");
        optifine.setName("OptiFine HD");
        optifine.setCategory("Графика");
        optifine.setDescription("Улучшение FPS и шейдеры.");
        optifine.setDefault(true);
        optifine.setJars(List.of("OptiFine.jar"));
        mods.add(optifine);

        // 4. MouseTweaks
        OptionalMod mt = new OptionalMod();
        mt.setId("MouseTweaks");
        mt.setName("Mouse Tweaks");
        mt.setCategory("Удобство");
        mt.setDefault(true);
        mt.setJars(List.of("MouseTweaks.jar"));
        mods.add(mt);

        // 5. BetterFps
        OptionalMod bfps = new OptionalMod();
        bfps.setId("BetterFps");
        bfps.setName("BetterFps");
        bfps.setCategory("Производительность");
        bfps.setDefault(true);
        bfps.setJars(List.of("BetterFps.jar"));
        mods.add(bfps);

        return mods;
    }
}