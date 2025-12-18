package hivens.core.data

import com.google.gson.annotations.SerializedName

/**
 * Продвинутая модель опционального мода.
 */
data class OptionalMod(
    // Уникальный ID
    var id: String = "",

    // UI поля
    var name: String = "",
    var description: String? = null,
    var category: String? = null,

    @SerializedName("default") // Gson часто ищет "default", если в json поле так называется
    var isDefault: Boolean = false,

    // --- Логика Файловой Системы ---
    // Список файлов для добавления
    var jars: MutableList<String> = ArrayList(),

    // Список файлов для удаления (конфликты)
    var excludings: MutableList<String> = ArrayList(),

    // --- Логика UI ---
    // ID несовместимых модов (RadioButton поведение)
    var incompatibleIds: MutableList<String> = ArrayList()
)