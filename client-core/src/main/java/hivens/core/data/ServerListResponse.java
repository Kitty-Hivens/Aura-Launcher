package hivens.core.data;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Модель данных (DTO) для ответа API, содержащего список серверов.
 * (Аналог ba.class).
 */
public record ServerListResponse(
    
    // (Имя поля 'servers' - это предположение, 
    // основанное на n.java -> this.a.a.forEach(...))
    @SerializedName("servers") 
    List<ServerData> servers
    
    // (Могут быть и другие поля, например, 'news')
) {}