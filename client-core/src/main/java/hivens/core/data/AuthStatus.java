package hivens.core.data;

/**
 * Статусы аутентификации, возвращаемые API SmartyCraft.
 */
public enum AuthStatus {
    OK,              // Успешная авторизация
    BAD_LOGIN,       // Неверный логин
    BAD_PASSWORD,    // Неверный пароль
    NO_SERVER,       // Сервер не найден
    INTERNAL_ERROR   // Внутренняя ошибка сервера
}