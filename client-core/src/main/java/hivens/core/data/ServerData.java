package hivens.core.data;

import com.google.gson.annotations.SerializedName;

/**
 * Модель данных (DTO) для сервера/сборки.
 */
public record ServerData(
    @SerializedName("name")
    String name,

    @SerializedName("address")
    String address,

    @SerializedName("port")
    int port,

    @SerializedName("version")
    String version
    // (Опциональные моды и другие поля опущены для упрощения)
) {}