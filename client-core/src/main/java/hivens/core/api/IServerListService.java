package hivens.core.api;

import hivens.core.data.ServerListResponse;
import java.io.IOException;

/**
 * Контракт для сервиса, получающего список серверов (сборок).
 */
public interface IServerListService {

    /**
     * Загружает список доступных серверов с API.
     * (Вероятно, GET-запрос или POST с action="servers").
     *
     * @return Объект ServerListResponse, содержащий список ServerData.
     * @throws IOException в случае сетевых ошибок.
     */
    ServerListResponse getServerList() throws IOException;
}