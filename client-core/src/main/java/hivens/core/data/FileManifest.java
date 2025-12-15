package hivens.core.data;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.Map;

/**
 * Модель данных (DTO) для манифеста файлов клиента.
 */
public record FileManifest(
        @SerializedName("directories")
        Map<String, FileManifest> directories,

        @SerializedName("files")
        Map<String, FileData> files
) {
    /**
     * Конструктор без аргументов для создания пустых манифестов.
     */
    public FileManifest() {
        this(Collections.emptyMap(), Collections.emptyMap());
    }
}