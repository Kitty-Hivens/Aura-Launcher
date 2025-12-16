package hivens.core.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SettingsData {

    // --- Система ---
    private String javaPath;
    private int memoryMB = 4096;

    // --- Визуал ---
    private String theme = "Warm";

    // --- Поведение ---
    private boolean closeAfterStart = true;
    private boolean saveCredentials = true;

    private String savedUsername;
    private String savedUuid;
    private String savedAccessToken;
    private FileManifest savedFileManifest;

    public static SettingsData defaults() {
        SettingsData data = new SettingsData();

        // Определение Java
        String os = System.getProperty("os.name").toLowerCase();
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + (os.contains("win") ? "java.exe" : "java");

        data.setJavaPath(javaBin);
        data.setMemoryMB(4096);
        data.setTheme("Warm");
        data.setSaveCredentials(true);
        return data;
    }
}