package hivens.ui;

import hivens.core.data.InstanceProfile;
import hivens.core.data.OptionalMod;
import hivens.launcher.ProfileManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerSettingsController {

    @FXML private Label titleLabel;
    @FXML private Slider ramSlider;
    @FXML private Label ramValueLabel;
    @FXML private TextField javaPathField;
    @FXML private TextField jvmArgsField;
    @FXML private VBox modsContainer;

    private InstanceProfile profile;
    private ProfileManager profileManager;
    private Stage stage;
    private final Map<String, CheckBox> modCheckboxes = new HashMap<>();

    public void setup(InstanceProfile profile, ProfileManager manager, List<OptionalMod> mods, Stage stage) {
        this.profile = profile;
        this.profileManager = manager;
        this.stage = stage;

        if (profile != null) {
            titleLabel.setText("НАСТРОЙКИ: " + profile.getServerId().toUpperCase());

            int ram = (profile.getMemoryMb() != null && profile.getMemoryMb() > 0) ? profile.getMemoryMb() : 4096;
            ramSlider.setValue(ram);
            ramValueLabel.setText(ram + " MB");

            javaPathField.setText(profile.getJavaPath() != null ? profile.getJavaPath() : "");
            jvmArgsField.setText(profile.getJvmArgs() != null ? profile.getJvmArgs() : "");

            buildModsList(mods);
        }
    }

    @FXML
    public void initialize() {
        ramSlider.valueProperty().addListener((obs, old, val) ->
                ramValueLabel.setText(val.intValue() + " MB"));
    }

    private void buildModsList(List<OptionalMod> mods) {
        modsContainer.getChildren().clear();
        modCheckboxes.clear();
        Map<String, Boolean> userState = profile.getOptionalModsState();

        for (OptionalMod mod : mods) {
            CheckBox box = new CheckBox(mod.getName());
            // Используем стандартный стиль, так как aura-checkbox может быть не определен
            box.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");

            boolean isActive = userState.getOrDefault(mod.getId(), mod.isDefault());
            box.setSelected(isActive);

            box.selectedProperty().addListener((obs, o, n) -> {
                userState.put(mod.getId(), n);
                // Тут можно добавить логику конфликтов (resolveConflicts)
            });

            modCheckboxes.put(mod.getId(), box);
            modsContainer.getChildren().add(box);
        }
    }

    @FXML
    private void save() {
        if (profile != null) {
            profile.setMemoryMb((int) ramSlider.getValue());
            profile.setJavaPath(javaPathField.getText());
            profile.setJvmArgs(jvmArgsField.getText());
            profileManager.saveProfile(profile);
        }
        close();
    }

    @FXML private void close() { if (stage != null) stage.close(); }

    @FXML private void browseJava() {
        DirectoryChooser dc = new DirectoryChooser();
        File f = dc.showDialog(stage);
        if (f != null) javaPathField.setText(f.getAbsolutePath() + "/bin/java");
    }
}
