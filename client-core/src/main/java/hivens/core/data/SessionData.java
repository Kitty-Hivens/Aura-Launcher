package hivens.core.data;

import com.google.gson.annotations.SerializedName;

public record SessionData(
        @SerializedName("status") AuthStatus status,
        @SerializedName("playername") String playerName,
        @SerializedName("uuid") String uuid,
        @SerializedName("session") String accessToken,
        @SerializedName("client") FileManifest fileManifest,

        String serverId,
        String cachedPassword
) {
    // Конструктор совместимости (для старого кода, который создает сессию без пароля)
    public SessionData(AuthStatus status, String playerName, String uuid, String accessToken, FileManifest fileManifest) {
        this(status, playerName, uuid, accessToken, fileManifest, null, null);
    }
}
