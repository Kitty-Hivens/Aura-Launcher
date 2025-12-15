package hivens.core.data;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InstanceProfile {

    private String serverId;

    private int memoryMb = 2048;
    private String javaPath;
    private String jvmArgs;

    private int windowWidth = 925;
    private int windowHeight = 530;
    private boolean fullScreen = false;
    private Map<String, String> selectedMods = new HashMap<>();

    public InstanceProfile(String serverId) {
        this.serverId = serverId;
    }
}