package hivens.core.data;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * Модель конфигурации клиента (DTO), преобразованная в Java Record.
 * Содержит все данные, необходимые для запуска любой версии клиента.
 */
public record ClientData(

        /* Версия клиента (например, "1.12.2-Custom"). */
        @SerializedName("id")
        String versionId,

        /* Главный класс для запуска (e.g., net.minecraft.launchwrapper.Launch). */
        @SerializedName("mainClass")
        String mainClass,

        /* TweakClass для старых версий Forge (опционально). */
        @SerializedName("tweakClass")
        String tweakClass,

        /* Индекс ассетов (e.g., "1.12" or "1.21"). */
        @SerializedName("assetIndex")
        String assetIndex,

        /* Список аргументов JVM (из API). */
        @SerializedName("jvmArguments")
        List<String> jvmArguments,

        /* Список аргументов Minecraft (из API). */
        @SerializedName("mcArguments")
        List<String> mcArguments,

        /* Список файлов/модов, которые необходимо загрузить/проверить. */
        @SerializedName("mods")
        Map<String, String> filesWithHashes
) {}