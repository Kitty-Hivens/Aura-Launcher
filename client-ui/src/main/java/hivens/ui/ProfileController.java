package hivens.ui;

import hivens.core.data.SessionData;
import hivens.launcher.LauncherDI;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import java.io.File;

public class ProfileController {

    private final LauncherDI di;

    @FXML private Label nicknameLabel;
    @FXML private Label statusLabel;

    @FXML private ImageView skinFront;
    @FXML private ImageView skinBack;
    @FXML private ImageView cloakPreview;

    @FXML private Button uploadSkinBtn;
    @FXML private Button uploadCloakBtn;

    private SessionData session;

    public ProfileController(LauncherDI di) {
        this.di = di;
    }

    public void setSession(SessionData session) {
        this.session = session;
        if (session != null) {
            nicknameLabel.setText(session.playerName());
            loadSkinPreviews();
        }
    }

    private void loadSkinPreviews() {
        if (session == null) return;

        statusLabel.setText("Загрузка...");
        String username = session.playerName();

        // --- 1. СКИН ---
        Image rawSkin = SkinManager.getSkinImage(username);

        // Проверяем: вдруг картинка уже в кеше и готова?
        if (rawSkin.getProgress() == 1.0) {
            updateSkinView(rawSkin);
        } else {
            // Если нет, ждем загрузку
            rawSkin.progressProperty().addListener((obs, oldV, newV) -> {
                if (newV.doubleValue() == 1.0) {
                    updateSkinView(rawSkin);
                }
            });
        }

        // --- 2. ПЛАЩ ---
        if (cloakPreview != null) {
            Image rawCloak = SkinManager.getCloakImage(username);

            if (rawCloak.getProgress() == 1.0) {
                updateCloakView(rawCloak);
            } else {
                rawCloak.progressProperty().addListener((obs, o, n) -> {
                    if (n.doubleValue() == 1.0) {
                        updateCloakView(rawCloak);
                    }
                });
            }
        }
    }

    // Вынесли логику отрисовки скина, чтобы не дублировать код
    private void updateSkinView(Image rawSkin) {
        Platform.runLater(() -> {
            if (!rawSkin.isError()) {
                skinFront.setImage(SkinManager.assembleSkinFront(rawSkin));
                skinBack.setImage(SkinManager.assembleSkinBack(rawSkin));

                skinFront.setSmooth(false);
                skinBack.setSmooth(false);
                statusLabel.setText("Активен");
            } else {
                statusLabel.setText("Скин не установлен");
                skinFront.setImage(null);
                skinBack.setImage(null);
            }
        });
    }

    // Логика отрисовки плаща
    private void updateCloakView(Image rawCloak) {
        Platform.runLater(() -> {
            if (!rawCloak.isError()) {
                cloakPreview.setImage(rawCloak);
                cloakPreview.setSmooth(false);
            } else {
                cloakPreview.setImage(null);
            }
        });
    }

    @FXML
    private void onUploadSkin() {
        File file = chooseFile("Выберите скин (PNG)");
        if (file != null) {
            uploadFile(file, "skin");
        }
    }

    @FXML
    private void onUploadCloak() {
        File file = chooseFile("Выберите плащ (PNG)");
        if (file != null) {
            uploadFile(file, "cloak");
        }
    }

    private void uploadFile(File file, String type) {
        statusLabel.setText("Загрузка...");
        setButtonsDisabled(true);

        new Thread(() -> {
            try {
                // Имитация или реальная загрузка
                // String uploadEndpoint = ServiceEndpoints.BASE_URL + "/launcher/upload.php";
                System.out.println("Uploading " + type + ": " + file.getName());
                Thread.sleep(1000);

                Platform.runLater(() -> {
                    // Важно: Инвалидируем кеш именно для этого юзера
                    SkinManager.invalidate(session.playerName());

                    statusLabel.setText("Загружено!");
                    loadSkinPreviews(); // Перезагружаем (теперь SkinManager вернет новую ссылку с ?t=...)
                    setButtonsDisabled(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Ошибка загрузки");
                    setButtonsDisabled(false);
                });
            }
        }).start();
    }

    private void setButtonsDisabled(boolean val) {
        if (uploadSkinBtn != null) uploadSkinBtn.setDisable(val);
        if (uploadCloakBtn != null) uploadCloakBtn.setDisable(val);
    }

    private File chooseFile(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Images", "*.png"));
        try {
            return chooser.showOpenDialog(skinFront.getScene().getWindow());
        } catch (Exception e) { return null; }
    }
}
