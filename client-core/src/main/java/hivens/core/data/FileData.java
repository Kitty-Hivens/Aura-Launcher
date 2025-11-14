package hivens.core.data;

import com.google.gson.annotations.SerializedName;

/**
 * Модель данных (DTO) для отдельного файла в манифесте.
 */
public record FileData(
        @SerializedName("md5")
        String md5,
        @SerializedName("size")
        long size
) {}