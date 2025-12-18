package hivens.core.data;

import java.util.HashMap;
import java.util.Map;

/**
 * Профиль настроек конкретного сервера.
 * Хранит выбор игрока и локальные настройки.
 */
public class InstanceProfile {
    private String serverId;
    private Integer memoryMb = 4096;
    private String javaPath;
    private String jvmArgs;
    private int windowWidth = 925;
    private int windowHeight = 530;
    private boolean fullScreen = false;
    private boolean autoConnect = true;
    private Map<String, Boolean> optionalModsState = new HashMap<>();


    public InstanceProfile(String serverId) {
        this.serverId = serverId;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public Integer getMemoryMb() {
        return memoryMb;
    }

    public void setMemoryMb(Integer memoryMb) {
        this.memoryMb = memoryMb;
    }

    public String getJavaPath() {
        return javaPath;
    }

    public void setJavaPath(String javaPath) {
        this.javaPath = javaPath;
    }

    public String getJvmArgs() {
        return jvmArgs;
    }

    public void setJvmArgs(String jvmArgs) {
        this.jvmArgs = jvmArgs;
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(int windowWidth) {
        this.windowWidth = windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    public void setWindowHeight(int windowHeight) {
        this.windowHeight = windowHeight;
    }

    public boolean isFullScreen() {
        return fullScreen;
    }

    public void setFullScreen(boolean fullScreen) {
        this.fullScreen = fullScreen;
    }

    public boolean isAutoConnect() {
        return autoConnect;
    }

    public void setAutoConnect(boolean autoConnect) {
        this.autoConnect = autoConnect;
    }

    public Map<String, Boolean> getOptionalModsState() {
        return optionalModsState;
    }

}
