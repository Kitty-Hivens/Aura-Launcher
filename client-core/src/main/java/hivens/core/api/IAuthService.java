package hivens.core.api;

import hivens.core.data.SessionData;
import java.io.IOException;

/**
 * Контракт для сервиса аутентификации.
 * Отвечает за преобразование учетных данных в сессионные данные.
 */
public interface IAuthService {

    /**
     * Выполняет аутентификацию на сервере.
     * * @param username Логин пользователя.
     * @param password Пароль пользователя.
     * @param serverId Идентификатор выбранного сервера (как определено API).
     * @return Объект SessionData, содержащий токен, UUID и данные клиента.
     * @throws AuthException в случае ошибок аутентификации (неверный пароль, бан и т.п.).
     * @throws IOException в случае сетевых ошибок (I/O, таймауты, DNS).
     */
    SessionData login(String username, String password, String serverId) throws AuthException, IOException;
}