package hivens.core.api.model;

import java.util.Map;

public class ServerProfile {
    private String name;
    private String title;
    private String version;
    private String ip;
    private int port;
    private String assetDir;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getAssetDir() {
        return assetDir;
    }

    public void setAssetDir(String assetDir) {
        this.assetDir = assetDir;
    }

    public String getExtraCheckSum() {
        return extraCheckSum;
    }

    public void setExtraCheckSum(String extraCheckSum) {
        this.extraCheckSum = extraCheckSum;
    }

    public Map<String, Object> getOptionalModsData() {
        return optionalModsData;
    }

    public void setOptionalModsData(Map<String, Object> optionalModsData) {
        this.optionalModsData = optionalModsData;
    }

    @Override
    public String toString() {
        return title != null ? title : name;
    }
}