package hivens.ui;

import hivens.core.api.model.ServerProfile;
import hivens.core.data.SessionData;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;

public class DashboardController {

    private final LauncherDI di;
    private final Main mainApp;

    @FXML private StackPane contentArea;
    @FXML private MediaView bgVideoView;
    @FXML private Label usernameLabel;

    // Кнопки меню
    @FXML private Button homeBtn;
    @FXML private Button profileBtn;
    @FXML private Button settingsBtn;

    @FXML private Button playButton;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;

    private MediaPlayer mediaPlayer;
    private ServerProfile selectedServer;

    public DashboardController(LauncherDI di, Main mainApp) {
        this.di = di;
        this.mainApp = mainApp;
    }

    @FXML
    public void initialize() {
        initVideoBackground();
        showHome(); // По умолчанию
        usernameLabel.setText("Steve");
    }

    public void setSelectedServer(ServerProfile server) {
        this.selectedServer = server;
        statusLabel.setText("Выбран: " + server.getName());
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
            }
        } catch (Exception e) {
            System.err.println("Видео не найдено");
        }
    }

    // --- НАВИГАЦИЯ ---

    @FXML public void showHome() {
        setActiveButton(homeBtn);
        switchContent("HomeView.fxml");
    }

    @FXML private void showProfile() {
        setActiveButton(profileBtn);
        switchContent("ProfileView.fxml");
    }

    @FXML private void showSettings() {
        setActiveButton(settingsBtn);
        switchContent("SettingsView.fxml");
    }

    // [FIX] Логика переключения стиля кнопок
    private void setActiveButton(Button active) {
        // Снимаем класс 'active' со всех кнопок
        if (homeBtn != null) homeBtn.getStyleClass().remove("active");
        if (profileBtn != null) profileBtn.getStyleClass().remove("active");
        if (settingsBtn != null) settingsBtn.getStyleClass().remove("active");

        // Добавляем только нажатой
        if (active != null && !active.getStyleClass().contains("active")) {
            active.getStyleClass().add("active");
        }
    }

    private void switchContent(String fxmlFile) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/" + fxmlFile));
            loader.setControllerFactory(clz -> {
                if (clz == HomeController.class) return new HomeController(di, this);
                if (clz == SettingsController.class) return new SettingsController(di, mainApp);
                return null;
            });

            Node newContent = loader.load();
            setZeroOpacity(newContent);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setZeroOpacity(Node newContent) {
        newContent.setOpacity(0);
        contentArea.getChildren().clear();
        contentArea.getChildren().add(newContent);

        FadeTransition ft = new FadeTransition(Duration.millis(250), newContent);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }

    public Stage getStage() {
        return (Stage) contentArea.getScene().getWindow();
    }

    @FXML
    private void onPlay() {
        if (selectedServer == null) {
            statusLabel.setText("Ошибка: Сервер не выбран!");
            return;
        }

        playButton.setDisable(true);
        playButton.setText("АВТОРИЗАЦИЯ...");
        progressBar.setVisible(true);
        progressBar.setProgress(-1);

        String login = "TestUser";
        String pass = "123456";

        new Thread(() -> {
            try {
                SessionData session = di.getAuthService().login(login, pass, selectedServer.getAssetDir());
                Platform.runLater(() -> {
                    playButton.setText("ЗАГРУЗКА...");
                    launchGameProcess(session);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Ошибка входа: " + e.getMessage());
                    playButton.setDisable(false);
                    playButton.setText("ИГРАТЬ");
                    progressBar.setVisible(false);
                });
            }
        }).start();
    }

    public void showServerSettings(ServerProfile server) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ServerSettingsView.fxml"));

            // Создаем контроллер, передавая DI и this
            ServerSettingsController controller = new ServerSettingsController(di, this);
            loader.setController(controller);

            Node content = loader.load();

            // Инициализируем контроллер данными сервера
            controller.setServer(server);

            // Анимация перехода
            setZeroOpacity(content);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void launchGameProcess(SessionData session) {
        UpdateAndLaunchTask task = new UpdateAndLaunchTask(
                di, session, selectedServer,
                di.getDataDirectory().resolve("clients").resolve(selectedServer.getAssetDir()),
                null, 4096
        );

        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> {
            statusLabel.textProperty().unbind();
            progressBar.progressProperty().unbind();
            if (di.getSettingsService().getSettings().isCloseAfterStart()) {
                mainApp.hideWindow();
            } else {
                playButton.setDisable(false);
                playButton.setText("ИГРАТЬ");
                statusLabel.setText("Игра запущена");
                progressBar.setVisible(false);
            }
        });

        task.setOnFailed(e -> {
            statusLabel.textProperty().unbind();
            progressBar.progressProperty().unbind();
            playButton.setDisable(false);
            playButton.setText("ИГРАТЬ");
            statusLabel.setText("Ошибка запуска");
        });

        new Thread(task).start();
    }

    @FXML private void minimize() { ((Stage)contentArea.getScene().getWindow()).setIconified(true); }
    @FXML private void close() { Platform.exit(); System.exit(0); }
}
