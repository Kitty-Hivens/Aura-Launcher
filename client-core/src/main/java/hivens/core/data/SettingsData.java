package hivens.core.data;

import java.io.File;

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

    public SettingsData() {
    }

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

    public String getJavaPath() {
        return javaPath;
    }

    public void setJavaPath(String javaPath) {
        this.javaPath = javaPath;
    }

    public int getMemoryMB() {
        return memoryMB;
    }

    public void setMemoryMB(int memoryMB) {
        this.memoryMB = memoryMB;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public boolean isCloseAfterStart() {
        return closeAfterStart;
    }

    public void setCloseAfterStart(boolean closeAfterStart) {
        this.closeAfterStart = closeAfterStart;
    }

    public boolean isSaveCredentials() {
        return saveCredentials;
    }

    public void setSaveCredentials(boolean saveCredentials) {
        this.saveCredentials = saveCredentials;
    }

    public String getSavedUsername() {
        return savedUsername;
    }

    public void setSavedUsername(String savedUsername) {
        this.savedUsername = savedUsername;
    }

    public String getSavedUuid() {
        return savedUuid;
    }

    public void setSavedUuid(String savedUuid) {
        this.savedUuid = savedUuid;
    }

    public String getSavedAccessToken() {
        return savedAccessToken;
    }

    public void setSavedAccessToken(String savedAccessToken) {
        this.savedAccessToken = savedAccessToken;
    }

    public FileManifest getSavedFileManifest() {
        return savedFileManifest;
    }

    public void setSavedFileManifest(FileManifest savedFileManifest) {
        this.savedFileManifest = savedFileManifest;
    }
}