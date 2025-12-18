package hivens.core.data

import com.google.gson.annotations.SerializedName

/**
 * Модель данных (DTO) для ответа API, содержащего список серверов.
 */
data class ServerListResponse(
    @SerializedName("servers")
    val servers: List<ServerData> = emptyList()
)