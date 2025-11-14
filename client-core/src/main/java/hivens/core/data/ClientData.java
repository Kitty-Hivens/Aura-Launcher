package hivens.core.data;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Модель данных (DTO) для конфигурации запуска клиента.
 * Эта модель парсится из локального JSON-файла (e.g., client.json),
 * а не из ответа API аутентификации.
 */
public record ClientData(
    
    /* Главный класс для запуска (e.g., net.minecraft.launchwrapper.Launch). */
    @SerializedName("mainClass")
    String mainClass,

    /* TweakClass для старых версий Forge (опционально). */
    @SerializedName("tweakClass")
    String tweakClass, // Может быть null

    /* Индекс ассетов (e.g., "1.12" or "1.21"). */
    @SerializedName("assetIndex")
    String assetIndex,

    /* Список аргументов JVM (из API). */
    @SerializedName("jvmArguments")
    List<String> jvmArguments,

    /* Список аргументов Minecraft (из API). */
    @SerializedName("mcArguments")
    List<String> mcArguments
) {}