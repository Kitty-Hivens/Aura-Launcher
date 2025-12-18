package hivens.ui;

import hivens.core.api.model.ServerProfile;
import hivens.core.data.SessionData;
import hivens.core.data.SettingsData;
import hivens.launcher.LauncherDI;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;

public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final LauncherDI di;
    private final Main mainApp;

    @FXML private StackPane contentArea;
    @FXML private MediaView bgVideoView;

    // UI элементы
    @FXML private Label usernameLabel;
    @FXML private ImageView avatarView;

    @FXML private Button homeBtn;
    @FXML private Button profileBtn;
    @FXML private Button settingsBtn;

    @FXML private Button playButton;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;

    private MediaPlayer mediaPlayer;
    private ServerProfile selectedServer;
    private SessionData session;

    public DashboardController(LauncherDI di, Main mainApp) {
        this.di = di;
        this.mainApp = mainApp;
    }

    @FXML
    public void initialize() {
        initVideoBackground();
        showHome();
    }

    public void initSession(SessionData session) {
        this.session = session;
        if (session != null) {
            log.info("Session initialized for: {}", session.playerName());

            if (usernameLabel != null) {
                usernameLabel.setText(session.playerName());
            }

            if (avatarView != null) {
                loadProjectSkin(session.playerName());
            }
        }
    }

    private void loadProjectSkin(String playerName) {
        Image skinImage = SkinManager.getSkinImage(playerName);

        skinImage.progressProperty().addListener((obs, oldV, newV) -> {
            if (newV.doubleValue() == 1.0) {
                if (!skinImage.isError()) {
                    avatarView.setImage(skinImage);

                    // Вычисляем Ratio для HD скинов
                    double ratio = skinImage.getWidth() / 64.0;
                    if (ratio < 1) ratio = 1;

                    // Голова: x=8, y=8, w=8, h=8
                    avatarView.setViewport(new Rectangle2D(8 * ratio, 8 * ratio, 8 * ratio, 8 * ratio));

                    // [ВАЖНО] Убираем мыло с аватарки в сайдбаре
                    avatarView.setSmooth(false);
                } else {
                    log.warn("Skin not found for user: {}", playerName);
                }
            }
        });
    }

    // --- ЛОГИКА ВЫХОДА ---
    @FXML
    private void onLogout() {
        // Сброс настроек
        SettingsData settings = di.getSettingsService().getSettings();
        settings.setSavedAccessToken(null);
        settings.setSavedFileManifest(null);
        di.getSettingsService().saveSettings(settings);

        // Переход на экран входа
        try {
            mainApp.showLoginScene();
        } catch (IOException e) {
            log.error("Failed to switch to login", e);
        }
    }

    // --- ЗАПУСК ---
    @FXML
    private void onPlay() {
        if (selectedServer == null) {
            statusLabel.setText("Ошибка: Сервер не выбран!");
            return;
        }
        if (session == null || session.fileManifest() == null) {
            statusLabel.setText("Сессия устарела. Перезайдите.");
            onLogout();
            return;
        }

        playButton.setDisable(true);
        playButton.setText("ЗАПУСК...");
        progressBar.setVisible(true);
        progressBar.setProgress(-1);

        launchGameProcess(session);
    }

    private void launchGameProcess(SessionData readySession) {
        UpdateAndLaunchTask task = new UpdateAndLaunchTask(
                di, readySession, selectedServer,
                di.getDataDirectory().resolve("clients").resolve(selectedServer.getAssetDir()),
                null, di.getSettingsService().getSettings().getMemoryMB()
        );

        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> {
            unbindProgress(task);
            if (di.getSettingsService().getSettings().isCloseAfterStart()) mainApp.hideWindow();
            else { resetPlayButton(); statusLabel.setText("Игра запущена"); }
        });

        task.setOnFailed(e -> {
            unbindProgress(task);
            resetPlayButton();
            log.error("Launch failed", task.getException());
            statusLabel.setText("Ошибка запуска");
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void unbindProgress(UpdateAndLaunchTask task) {
        statusLabel.textProperty().unbind();
        progressBar.progressProperty().unbind();
    }

    private void resetPlayButton() {
        playButton.setDisable(false);
        playButton.setText("ИГРАТЬ");
        progressBar.setVisible(false);
    }

    // --- НАВИГАЦИЯ ---
    public Stage getStage() { return mainApp.getPrimaryStage(); }
    @FXML public void showHome() { setActiveButton(homeBtn); switchContent("HomeView.fxml"); }
    @FXML private void showProfile() { setActiveButton(profileBtn); switchContent("ProfileView.fxml"); }
    @FXML private void showSettings() { setActiveButton(settingsBtn); switchContent("SettingsView.fxml"); }

    private void setActiveButton(Button active) {
        if (homeBtn != null) homeBtn.getStyleClass().remove("active");
        if (profileBtn != null) profileBtn.getStyleClass().remove("active");
        if (settingsBtn != null) settingsBtn.getStyleClass().remove("active");
        if (active != null) active.getStyleClass().add("active");
    }

    private void switchContent(String fxmlFile) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/" + fxmlFile));
            loader.setControllerFactory(clz -> {
                if (clz == HomeController.class) return new HomeController(di, this);
                if (clz == SettingsController.class) return new SettingsController(di, mainApp);
                if (clz == ProfileController.class) return new ProfileController(di);
                try { return clz.getConstructor().newInstance(); } catch (Exception e) { return null; }
            });
            Node newContent = loader.load();

            Object controller = loader.getController();
            if (controller instanceof HomeController hc) hc.setSession(session);
            if (controller instanceof ProfileController pc) pc.setSession(session);

            setZeroOpacity(newContent);
        } catch (IOException e) { log.error("Failed to load view " + fxmlFile, e); }
    }

    private void setZeroOpacity(Node node) {
        node.setOpacity(0);
        contentArea.getChildren().clear();
        contentArea.getChildren().add(node);
        FadeTransition ft = new FadeTransition(Duration.millis(300), node);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }

    public void showServerSettings(ServerProfile server) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ServerSettingsView.fxml"));
            ServerSettingsController controller = new ServerSettingsController(di, this);
            loader.setController(controller);
            Node content = loader.load();
            controller.setServer(server);
            setZeroOpacity(content);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void initVideoBackground() {
        try {
            URL videoUrl = getClass().getResource("/video/bg.mp4");
            if (videoUrl != null) {
                Media media = new Media(videoUrl.toExternalForm());
                mediaPlayer = new MediaPlayer(media);
                mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                mediaPlayer.setVolume(0);
                mediaPlayer.setAutoPlay(true);
                bgVideoView.setMediaPlayer(mediaPlayer);
                bgVideoView.fitWidthProperty().bind(mainApp.getPrimaryStage().widthProperty());
                bgVideoView.fitHeightProperty().bind(mainApp.getPrimaryStage().heightProperty());
                mediaPlayer.setOnError(() -> bgVideoView.setVisible(false));
            }
        } catch (Exception e) { System.err.println("Video bg error"); }
    }

    public void setSelectedServer(ServerProfile server) {
        this.selectedServer = server;
        if (statusLabel != null) {
            // [FIX] Если свойство привязано к задаче, отвязываем его перед изменением
            if (statusLabel.textProperty().isBound()) {
                statusLabel.textProperty().unbind();
            }

            String serverName = (server != null) ? server.getName() : "";
            statusLabel.setText(server != null ? "Выбран: " + serverName : "Выберите сервер");
        }
    }

    @FXML private void minimize() { getStage().setIconified(true); }
    @FXML private void close() { Platform.exit(); System.exit(0); }
}
