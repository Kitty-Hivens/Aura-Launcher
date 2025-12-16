package hivens.ui;

import hivens.core.api.model.ServerProfile;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class ServerSettingsController {

    private final LauncherDI di;
    private final DashboardController dashboard;

    @FXML private Label serverTitleLabel;

    private ServerProfile server;

    public ServerSettingsController(LauncherDI di, DashboardController dashboard) {
        this.di = di;
        this.dashboard = dashboard;
    }

    public void setServer(ServerProfile server) {
        this.server = server;
        if (server != null) {
            serverTitleLabel.setText(server.getTitle().toUpperCase());
        }
    }

    @FXML
    private void onOpenFolder() {
        if (server == null) return;

        // Путь: DataDir/clients/AssetDir
        Path clientDir = di.getDataDirectory().resolve("clients").resolve(server.getAssetDir());

        // Создаем, если нет, чтобы не было ошибки
        if (!Files.exists(clientDir)) {
            try { Files.createDirectories(clientDir); } catch (IOException e) { e.printStackTrace(); }
        }

        new Thread(() -> {
            try {
                Desktop.getDesktop().open(clientDir.toFile());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    @FXML
    private void onReinstall() {
        if (server == null) return;

        // Удаляем папку клиента
        Path clientDir = di.getDataDirectory().resolve("clients").resolve(server.getAssetDir());

        if (Files.exists(clientDir)) {
            try (Stream<Path> walk = Files.walk(clientDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);

                System.out.println("Deleted client folder: " + clientDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Закрываем окно после удаления
        onClose();
    }

    @FXML
    private void onClose() {
        // Удаляем модальное окно из Dashboard
        // DashboardController должен иметь доступ к contentArea или методу закрытия модалки
        // Так как мы добавляли это View просто поверх, нам нужно его убрать.

        // Способ 1: Получить родителя и удалить себя
        Platform.runLater(() -> {
            if (serverTitleLabel.getScene() != null) {
                // Если мы загрузили это в contentArea через setZeroOpacity,
                // то onClose должен вернуть пользователя на HomeView
                dashboard.showHome();
            }
        });
    }
}
