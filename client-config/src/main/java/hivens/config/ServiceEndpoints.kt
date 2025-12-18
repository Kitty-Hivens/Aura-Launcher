package hivens.config

/**
 * Конфигурация конечных точек API.
 * Используется для изоляции URL-адресов проекта, повышения переносимости.
 */
object ServiceEndpoints {
    /** Базовый домен для API и CDN. */
    const val BASE_URL = "https://www.smartycraft.ru"

    /** Endpoint для аутентификации пользователя (POST). */
    const val AUTH_LOGIN = "$BASE_URL/launcher2/index.php"
}