package hivens.core.data;

import com.google.gson.annotations.SerializedName;

/**
 * Модель данных сессии (DTO), преобразованная в Java Record.
 * Содержит все необходимые данные для запуска клиента.
 */
public record SessionData(

        @SerializedName("status")
        AuthStatus status,

        @SerializedName("playername")
        String playerName,

        @SerializedName("uuid")
        String uuid,

        @SerializedName("session")
        String accessToken,

        @SerializedName("client")
        FileManifest fileManifest
) {}