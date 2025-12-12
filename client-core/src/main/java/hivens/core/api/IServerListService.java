package hivens.core.api;

import hivens.core.api.model.ServerProfile;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IServerListService {
    /**
     * Асинхронно получает список профилей серверов.
     */
    CompletableFuture<List<ServerProfile>> fetchProfiles();
}