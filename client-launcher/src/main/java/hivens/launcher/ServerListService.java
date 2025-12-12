package hivens.launcher;

import hivens.core.api.IServerListService;
import hivens.core.api.SmartyNetworkService;
import hivens.core.api.dto.SmartyServer;
import hivens.core.api.model.ServerProfile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ServerListService implements IServerListService {

    private final SmartyNetworkService networkService;

    public ServerListService() {
        this.networkService = new SmartyNetworkService();
    }

    /**
     * Загружает список серверов асинхронно.
     */
    public CompletableFuture<List<ServerProfile>> fetchProfiles() {
        return CompletableFuture.supplyAsync(() -> {
            List<ServerProfile> profiles = new ArrayList<>();

            try {
                // 1. Получаем "сырые" данные от SmartyNetworkService
                List<SmartyServer> rawServers = networkService.getServers();

                // 2. Превращаем их в наши профили
                for (SmartyServer srv : rawServers) {
                    ServerProfile profile = getProfile(srv);

                    // Тут можно добавить логику парсинга optionalMods, если твой ServerProfile поддерживает их
                    // if (srv.optionalMods != null) { ... }

                    profiles.add(profile);
                }

            } catch (Exception e) {
                System.err.println("Ошибка при загрузке списка серверов: " + e.getMessage());
                e.printStackTrace();
                // Тут можно вернуть хардкод-фоллбек, если сеть упала
            }

            return profiles;
        });
    }

    private static ServerProfile getProfile(SmartyServer srv) {
        ServerProfile profile = new ServerProfile();
        profile.setName(srv.name);
        profile.setTitle(srv.name + " " + srv.version);

        profile.setVersion(srv.version);
        profile.setIp(srv.address);
        profile.setPort(srv.port);
        profile.setAssetDir(srv.name);

        return profile;
    }
}