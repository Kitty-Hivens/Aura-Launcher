package hivens.launcher;

import hivens.core.api.IServerListService;
import hivens.core.api.SmartyNetworkService;
import hivens.core.api.dto.SmartyServer;
import hivens.core.api.model.ServerProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ServerListService implements IServerListService {

    private final SmartyNetworkService networkService;

    // Кэш в памяти
    private List<ServerProfile> cachedProfiles = null;

    public ServerListService() {
        this.networkService = new SmartyNetworkService();
    }

    public CompletableFuture<List<ServerProfile>> fetchProfiles() {
        // Если кэш есть - отдаем мгновенно
        if (cachedProfiles != null && !cachedProfiles.isEmpty()) {
            return CompletableFuture.completedFuture(cachedProfiles);
        }

        return CompletableFuture.supplyAsync(() -> {
            List<ServerProfile> profiles = new ArrayList<>();
            try {
                List<SmartyServer> rawServers = networkService.getServers();
                for (SmartyServer srv : rawServers) {
                    profiles.add(getProfile(srv));
                }
                // Сохраняем в кэш
                this.cachedProfiles = profiles;
            } catch (Exception e) {
                System.err.println("Error fetching profiles: " + e.getMessage());
            }
            return profiles;
        });
    }

    // Метод для принудительного обновления (например, кнопка "Обновить")
    public void clearCache() {
        this.cachedProfiles = null;
    }

    private static ServerProfile getProfile(SmartyServer srv) {
        ServerProfile profile = new ServerProfile();
        profile.setName(srv.name);
        profile.setTitle(srv.name);
        profile.setVersion(srv.version);
        profile.setIp(srv.address);
        profile.setPort(srv.port);
        profile.setAssetDir(srv.name);
        profile.setExtraCheckSum(srv.extraCheckSum);
        profile.setOptionalModsData(srv.optionalMods);

        return profile;
    }
}
