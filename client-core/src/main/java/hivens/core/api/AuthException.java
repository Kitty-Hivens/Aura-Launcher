package hivens.core.api;

import hivens.core.data.AuthStatus;

import java.io.Serial;

/**
 * Исключение, выбрасываемое при неудачной аутентификации на сервере.
 * Содержит специфичный статус ошибки для точной обработки в UI.
 */
public class AuthException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final AuthStatus status;

    /**
     * Конструктор для создания исключения.
     * @param status Статус ошибки, возвращенный API.
     * @param message Подробное сообщение об ошибке.
     */
    public AuthException(AuthStatus status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Получает статус ошибки для обработки в логике UI.
     * @return Статус аутентификации.
     */
    public AuthStatus getStatus() {
        return status;
    }
}