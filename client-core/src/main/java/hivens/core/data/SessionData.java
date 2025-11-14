package hivens.core.data;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Модель данных сессии, возвращаемая после успешной аутентификации.
 * Содержит все необходимые данные для запуска клиента.
 */
@Data
@NoArgsConstructor
public class SessionData {

    /** Статус ответа (должен быть AuthStatus.OK). */
    @SerializedName("status")
    private AuthStatus status;
    
    /** Имя игрока. */
    @SerializedName("playername")
    private String playerName; 
    
    /** Уникальный идентификатор игрока (UUID). */
    @SerializedName("uuid")
    private String uuid; 
    
    /** Токен доступа (accessToken) для запуска Minecraft. */
    @SerializedName("session") 
    private String accessToken; 

    /** Информация о текущем клиенте и модах (вложенный объект). */
    @SerializedName("client")
    private ClientData clientData;
}