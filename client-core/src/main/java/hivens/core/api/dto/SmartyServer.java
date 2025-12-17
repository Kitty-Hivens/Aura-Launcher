package hivens.core.api.dto;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public class SmartyServer {
    public String name;
    public String address;
    public int port;
    public String version;
    // Опциональные моды (сложная структура, пока можно хранить как Map или Object)
    public Map<String, Object> optionalMods;
    @SerializedName("extraCheckSum")
    public String extraCheckSum;
}