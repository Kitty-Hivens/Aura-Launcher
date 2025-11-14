package hivens.core.data;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * Модель конфигурации клиента (DTO), преобразованная в Java Record.
 * Аналог объекта 'client' в ответе API.
 */
public record ClientData(

        /* Версия клиента (например, "1.12.2-Custom"). */
        @SerializedName("id")
        String versionId,

        /* Список аргументов JVM для запуска Minecraft. */
        @SerializedName("jvmArguments")
        List<String> jvmArguments,

        /* Список аргументов Minecraft. */
        @SerializedName("mcArguments")
        List<String> mcArguments,

        /* Список файлов/модов, которые необходимо загрузить/проверить. */
        @SerializedName("mods")
        Map<String, String> filesWithHashes
) {}