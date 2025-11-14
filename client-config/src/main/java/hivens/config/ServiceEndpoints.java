package hivens.config;

/**
 * Конфигурация конечных точек API.
 * Используется для изоляции URL-адресов проекта, повышения переносимости.
 */
public final class ServiceEndpoints {

    /**
     * Приватный конструктор для предотвращения инстанцирования утилитного класса.
     */
    private ServiceEndpoints() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /** Базовый домен для API и CDN. */
    public static final String BASE_URL = "https://www.smartycraft.ru";

    /** Endpoint для аутентификации пользователя (POST). */
    public static final String AUTH_LOGIN = BASE_URL + "/auth/login";

    public static final String LAUNCHER_API = BASE_URL + "/launcher/";

    /** * Базовый путь на CDN для загрузки файлов клиента (моды, библиотеки). */
    public static final String CLIENT_DOWNLOAD_BASE = BASE_URL + "/launcher/clients/";

    // NOTE: По мере необходимости сюда будут добавляться другие эндпоинты,
    // например, для списка клиентов или проверки хешей
}