package hivens.config;

/**
 * Конфигурация конечных точек API (Endpoints).
 * Централизованное место для всех URL, используемых в приложении.
 */
public final class ServiceEndpoints {

    /**
     * Приватный конструктор, чтобы класс нельзя было инстанцировать.
     */
    private ServiceEndpoints() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Базовый домен проекта. */
    public static final String BASE_URL = "http://www.smartycraft.ru";

    /**
     * URL для авторизации (API v3).
     * Используется скрипт launcher2/index.php, который мы "вскрыли" через декомпиляцию.
     */
    public static final String AUTH_LOGIN = BASE_URL + "/launcher2/index.php";

    /**
     * Базовый URL для загрузки файлов обновлений (клиентов, ассетов).
     * Обычно используется для построения путей к файлам.
     */
    public static final String UPDATE_BASE = BASE_URL + "/launcher/updates/";
}
