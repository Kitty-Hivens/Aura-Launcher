package hivens.core.data;

import com.google.gson.annotations.SerializedName;

/**
 * Модель данных сессии (DTO), преобразованная в Java Record.
 * Содержит все необходимые данные для запуска клиента.
 */
public record SessionData(

        /* Статус ответа (должен быть AuthStatus.OK). */
        @SerializedName("status")
        AuthStatus status,

        /* Имя игрока. */
        @SerializedName("playername")
        String playerName,

        /* Уникальный идентификатор игрока (UUID). */
        @SerializedName("uuid")
        String uuid,

        /* Токен доступа (accessToken) для запуска Minecraft. */
        @SerializedName("session")
        String accessToken,

        /* Информация о текущем клиенте и модах (вложенный объект). */
        @SerializedName("client")
        ClientData clientData
) {}