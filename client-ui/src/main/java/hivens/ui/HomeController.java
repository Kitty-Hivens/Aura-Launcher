package hivens.ui;

import hivens.core.api.model.ServerProfile;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class HomeController {

    private final LauncherDI di;
    private final DashboardController dashboard;

    @FXML private ListView<ServerProfile> serverListView;

    public HomeController(LauncherDI di, DashboardController dashboard) {
        this.di = di;
        this.dashboard = dashboard;
    }

    @FXML
    public void initialize() {
        di.getServerListService().fetchProfiles().thenAccept(profiles -> 
            Platform.runLater(() -> {
                serverListView.getItems().setAll(profiles);
                if (!profiles.isEmpty()) {
                    serverListView.getSelectionModel().selectFirst();
                    dashboard.setSelectedServer(profiles.get(0));
                }
            })
        );

        serverListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                dashboard.setSelectedServer(newVal);
            }
        });

        serverListView.setCellFactory(param -> new ServerListCell());
    }

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
                root.getStyleClass().add("server-card");
                root.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                // Иконка
                Label iconPlaceholder = new Label(item.getName().substring(0, 1));
                iconPlaceholder.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #00c6ff; " +
                        "-fx-background-color: rgba(0,0,0,0.3); -fx-background-radius: 50%; -fx-min-width: 50; -fx-min-height: 50; -fx-alignment: center;");

                // Инфо
                VBox info = new VBox(3);
                Label name = new Label(item.getName());
                name.getStyleClass().add("server-name");
                Label desc = new Label(item.getIp() + ":" + item.getPort());
                desc.getStyleClass().add("server-desc");
                info.getChildren().addAll(name, desc);

                // Версия
                Label version = new Label(item.getVersion());
                version.getStyleClass().add("server-version");
                
                HBox spacer = new HBox();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                // [FIX] КНОПКА НАСТРОЕК
                Button settingsBtn = new Button("⚙");
                settingsBtn.getStyleClass().add("icon-btn");
                settingsBtn.setStyle("-fx-font-size: 16px; -fx-padding: 5 10;");
                settingsBtn.setOnAction(e -> {
                    // Открываем настройки ЭТОГО сервера
                    dashboard.showServerSettings(item); 
                });

                root.getChildren().addAll(iconPlaceholder, info, spacer, version, settingsBtn);

                setGraphic(root);
                setText(null);
            }
        }
    }
}