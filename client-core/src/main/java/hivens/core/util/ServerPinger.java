package hivens.core.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ServerPinger {

    public record ServerStatus(String description, int online, int max, long ping) {}

    public static ServerStatus ping(String ip, int port) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(3000); // Тайм-аут 3 секунды
            socket.connect(new InetSocketAddress(ip, port), 3000);

            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {

                // 1. Handshake packet
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                DataOutputStream handshake = new DataOutputStream(b);
                handshake.writeByte(0x00); // Packet ID
                writeVarInt(handshake, 47); // Protocol Version (1.8+)
                writeString(handshake, ip);
                handshake.writeShort(port);
                writeVarInt(handshake, 1); // State 1 (Status)

                writeVarInt(out, b.size());
                out.write(b.toByteArray());

                // 2. Request packet
                out.writeByte(0x01); // Size
                out.writeByte(0x00); // Packet ID

                // 3. Response
                readVarInt(in); // Packet size
                int packetId = readVarInt(in);

                if (packetId == -1) throw new IOException("Premature end of stream");
                if (packetId != 0x00) throw new IOException("Invalid packetID");

                int jsonLength = readVarInt(in);
                if (jsonLength == -1) throw new IOException("Premature end of stream");

                byte[] inBytes = new byte[jsonLength];
                in.readFully(inBytes);
                String json = new String(inBytes);

                long ping = System.currentTimeMillis() - start;

                // Парсим JSON
                Gson gson = new Gson();
                JsonObject root = gson.fromJson(json, JsonObject.class);
                
                String motd = "Сервер онлайн";
                if (root.has("description")) {
                    if (root.get("description").isJsonObject())
                        motd = root.getAsJsonObject("description").get("text").getAsString();
                    else 
                        motd = root.get("description").getAsString();
                }

                int online = 0;
                int max = 0;
                if (root.has("players")) {
                    JsonObject players = root.getAsJsonObject("players");
                    online = players.get("online").getAsInt();
                    max = players.get("max").getAsInt();
                }

                return new ServerStatus(motd, online, max, ping);
            }
        } catch (Exception e) {
            // Сервер офлайн
            return new ServerStatus("Офлайн", 0, 0, -1);
        }
    }

    // Утилиты для протокола Minecraft
    private static void writeVarInt(DataOutputStream out, int paramInt) throws IOException {
        while (true) {
            if ((paramInt & 0xFFFFFF80) == 0) {
                out.writeByte(paramInt);
                return;
            }
            out.writeByte(paramInt & 0x7F | 0x80);
            paramInt >>>= 7;
        }
    }
    
    private static void writeString(DataOutputStream out, String string) throws IOException {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int i = 0;
        int j = 0;
        while (true) {
            int k = in.readByte();
            i |= (k & 0x7F) << j++ * 7;
            if (j > 5) throw new RuntimeException("VarInt too big");
            if ((k & 0x80) != 128) break;
        }
        return i;
    }
}