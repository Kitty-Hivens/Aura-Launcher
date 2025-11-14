package hivens.core.data;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Модель конфигурации клиента, содержащая информацию о модах и аргументах запуска.
 * Аналог объекта 'client' в ответе API.
 */
@Data
@NoArgsConstructor
public class ClientData {
    
    /** Версия клиента (например, "1.12.2-Custom"). */
    @SerializedName("id")
    private String versionId;

    /** Список аргументов JVM для запуска Minecraft. */
    @SerializedName("jvmArguments")
    private List<String> jvmArguments;

    /** Список аргументов Minecraft. */
    @SerializedName("mcArguments")
    private List<String> mcArguments;

    /** Список файлов/модов, которые необходимо загрузить/проверить.
     * Ключ: путь/имя файла, Значение: ожидаемый MD5/SHA хэш.
     * NOTE: Используется Map<String, String> для минималистичного маппинга JSON.
     */
    @SerializedName("mods")
    private Map<String, String> filesWithHashes;
}