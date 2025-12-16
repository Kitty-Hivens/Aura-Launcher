package hivens.ui;

import hivens.core.api.model.ServerProfile;
import hivens.core.data.SessionData;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Setter;

public class HomeController {

    private final LauncherDI di;
    private final DashboardController dashboard;

    /**
     * -- SETTER --
     *  [FIX] Метод, который вызывает DashboardController при смене экрана.
     *  Теперь код в DashboardController не будет выдавать ошибку.
     */ // В будущем здесь можно добавить проверку вайтлиста для игрока
    // или загрузку его статистики на конкретном сервере.
    // [FIX] Поле для хранения сессии
    @Setter
    private SessionData session;

    @FXML private ListView<ServerProfile> serverListView;

    public HomeController(LauncherDI di, DashboardController dashboard) {
        this.di = di;
        this.dashboard = dashboard;
    }

    @FXML
    public void initialize() {
        // Асинхронная загрузка списка серверов
        di.getServerListService().fetchProfiles().thenAccept(profiles ->
                Platform.runLater(() -> {
                    serverListView.getItems().setAll(profiles);

                    // Выбираем первый сервер по умолчанию, если список не пуст
                    if (!profiles.isEmpty()) {
                        serverListView.getSelectionModel().selectFirst();
                        dashboard.setSelectedServer(profiles.getFirst());
                    }
                })
        );

        // Слушатель выбора в списке
        serverListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // Сообщаем Даш борду, что сервер изменился (чтобы обновилась кнопка "Играть")
                dashboard.setSelectedServer(newVal);
            }
        });

        // Кастомный вид ячеек (Карточки серверов)
        serverListView.setCellFactory(param -> new ServerListCell());
    }

    /**
     * Внутренний класс для отрисовки красивой карточки сервера
     */
    private class ServerListCell extends ListCell<ServerProfile> {
        @Override
        protected void updateItem(ServerProfile item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setStyle("-fx-background-color: transparent;");
            } else {
                HBox root = new HBox(15);
                root.getStyleClass().add("server-card"); // CSS класс для карточки
                root.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                // 1. Иконка (Заглушка с первой буквой названия)
                // Можно заменить на реальную иконку сервера (base64) в будущем
                String letter = (item.getName() != null && !item.getName().isEmpty())
                        ? item.getName().substring(0, 1) : "?";

                Label iconPlaceholder = new Label(letter);
                iconPlaceholder.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #00c6ff; " +
                        "-fx-background-color: rgba(0,0,0,0.3); -fx-background-radius: 50%; " +
                        "-fx-min-width: 50; -fx-min-height: 50; -fx-alignment: center;");

                // 2. Информация (Название и IP/Порт)
                VBox info = new VBox(3);
                Label name = new Label(item.getName());
                name.getStyleClass().add("server-name"); // CSS

                Label desc = new Label(item.getIp() + ":" + item.getPort());
                desc.getStyleClass().add("server-desc"); // CSS

                info.getChildren().addAll(name, desc);

                // 3. Версия
                Label version = new Label(item.getVersion());
                version.getStyleClass().add("server-version"); // CSS

                // Распорка (чтобы кнопки уехали вправо)
                HBox spacer = new HBox();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                // 4. Кнопка настроек сервера
                Button settingsBtn = new Button("⚙");
                settingsBtn.getStyleClass().add("icon-btn");
                settingsBtn.setStyle("-fx-font-size: 16px; -fx-padding: 5 10;");
                settingsBtn.setOnAction(e -> {
                    // Переход в настройки конкретного сервера
                    dashboard.showServerSettings(item);
                });

                root.getChildren().addAll(iconPlaceholder, info, spacer, version, settingsBtn);

                setGraphic(root);
                setText(null);
            }
        }
    }
}
