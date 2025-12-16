package hivens.ui;

import hivens.config.ServiceEndpoints;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SkinManager {

    private static final int OUTPUT_SCALE = 8;

    // Хранилище "меток времени" для сброса кеша конкретных пользователей
    private static final Map<String, Long> cacheBusters = new ConcurrentHashMap<>();

    /**
     * Сбрасывает кеш для указанного игрока (вызывать после загрузки нового скина)
     */
    public static void invalidate(String username) {
        cacheBusters.put(username, System.currentTimeMillis());
    }

    /**
     * Загружает скин. Если он не менялся — берет из кеша JavaFX.
     * Если менялся (был вызван invalidate) — грузит свежий.
     */
    public static Image getSkinImage(String username) {
        String url = ServiceEndpoints.BASE_URL + "/skins/" + username + ".png";
        if (cacheBusters.containsKey(username)) {
            url += "?t=" + cacheBusters.get(username);
        }
        return new Image(url, true); // true = background loading
    }

    public static Image getCloakImage(String username) {
        String url = ServiceEndpoints.BASE_URL + "/cloaks/" + username + ".png";
        if (cacheBusters.containsKey(username)) {
            url += "?t=" + cacheBusters.get(username);
        }
        return new Image(url, true);
    }

    // --- Методы сборки (без изменений, работают как часы) ---

    public static Image assembleSkinFront(Image rawSkin) {
        return assemble(rawSkin, false);
    }

    public static Image assembleSkinBack(Image rawSkin) {
        return assemble(rawSkin, true);
    }

    private static Image assemble(Image skin, boolean backView) {
        int w = (int) skin.getWidth();
        int h = (int) skin.getHeight();

        if (w == 0 || h == 0) return null;

        boolean is64x64 = (h == w);

        int finalW = 16 * OUTPUT_SCALE;
        int finalH = 32 * OUTPUT_SCALE;

        WritableImage dest = new WritableImage(finalW, finalH);
        PixelWriter pw = dest.getPixelWriter();
        PixelReader pr = skin.getPixelReader();

        if (!backView) {
            drawLimb(pr, pw, w, h, 8, 8, 8, 8, 4, 0, false);   // Head
            drawLimb(pr, pw, w, h, 40, 8, 8, 8, 4, 0, false);  // Hat

            drawLimb(pr, pw, w, h, 20, 20, 8, 12, 4, 8, false); // Body
            if (is64x64) drawLimb(pr, pw, w, h, 20, 36, 8, 12, 4, 8, false); // Jacket

            drawLimb(pr, pw, w, h, 44, 20, 4, 12, 0, 8, false); // Right Arm
            if (is64x64) drawLimb(pr, pw, w, h, 44, 36, 4, 12, 0, 8, false);

            if (is64x64) {
                drawLimb(pr, pw, w, h, 36, 52, 4, 12, 12, 8, false); // Left Arm
                drawLimb(pr, pw, w, h, 52, 52, 4, 12, 12, 8, false);
            } else {
                drawLimb(pr, pw, w, h, 44, 20, 4, 12, 12, 8, true); // Left Arm (Flip)
            }

            drawLimb(pr, pw, w, h, 4, 20, 4, 12, 4, 20, false); // Right Leg
            if (is64x64) drawLimb(pr, pw, w, h, 4, 36, 4, 12, 4, 20, false);

            if (is64x64) {
                drawLimb(pr, pw, w, h, 20, 52, 4, 12, 8, 20, false); // Left Leg
                drawLimb(pr, pw, w, h, 4, 52, 4, 12, 8, 20, false);
            } else {
                drawLimb(pr, pw, w, h, 4, 20, 4, 12, 8, 20, true); // Left Leg (Flip)
            }
        } else {
            drawLimb(pr, pw, w, h, 24, 8, 8, 8, 4, 0, false);  // Head Back
            drawLimb(pr, pw, w, h, 56, 8, 8, 8, 4, 0, false);

            drawLimb(pr, pw, w, h, 32, 20, 8, 12, 4, 8, false); // Body Back
            if (is64x64) drawLimb(pr, pw, w, h, 32, 36, 8, 12, 4, 8, false);

            drawLimb(pr, pw, w, h, 52, 20, 4, 12, 12, 8, false); // Right Arm Back
            if (is64x64) drawLimb(pr, pw, w, h, 52, 36, 4, 12, 12, 8, false);

            if (is64x64) {
                drawLimb(pr, pw, w, h, 44, 52, 4, 12, 0, 8, false); // Left Arm Back
                drawLimb(pr, pw, w, h, 60, 52, 4, 12, 0, 8, false);
            } else {
                drawLimb(pr, pw, w, h, 52, 20, 4, 12, 0, 8, true);
            }

            drawLimb(pr, pw, w, h, 12, 20, 4, 12, 8, 20, false); // Right Leg Back
            if (is64x64) drawLimb(pr, pw, w, h, 12, 36, 4, 12, 8, 20, false);

            if (is64x64) {
                drawLimb(pr, pw, w, h, 28, 52, 4, 12, 4, 20, false); // Left Leg Back
                drawLimb(pr, pw, w, h, 12, 52, 4, 12, 4, 20, false);
            } else {
                drawLimb(pr, pw, w, h, 12, 20, 4, 12, 4, 20, true);
            }
        }

        return dest;
    }

    private static void drawLimb(PixelReader pr, PixelWriter pw,
                                 int srcImgW, int srcImgH,
                                 int sx, int sy, int sw, int sh,
                                 int dx, int dy, boolean flipX) {

        double ratio = (double) srcImgW / 64.0;
        double actualSX = sx * ratio;
        double actualSY = sy * ratio;
        double actualSW = sw * ratio;
        double actualSH = sh * ratio;

        int destW = sw * OUTPUT_SCALE;
        int destH = sh * OUTPUT_SCALE;
        int destXOffset = dx * OUTPUT_SCALE;
        int destYOffset = dy * OUTPUT_SCALE;

        for (int y = 0; y < destH; y++) {
            for (int x = 0; x < destW; x++) {
                double normX = (double) x / destW;
                double normY = (double) y / destH;

                if (flipX) normX = 1.0 - normX;

                int readX = (int) Math.floor(actualSX + (normX * actualSW));
                int readY = (int) Math.floor(actualSY + (normY * actualSH));

                int maxX = (int) Math.floor(actualSX + actualSW) - 1;
                int maxY = (int) Math.floor(actualSY + actualSH) - 1;
                if (readX > maxX) readX = maxX;
                if (readY > maxY) readY = maxY;

                if (readX >= srcImgW) readX = srcImgW - 1;
                if (readY >= srcImgH) readY = srcImgH - 1;
                if (readX < 0) readX = 0;
                if (readY < 0) readY = 0;

                Color color = pr.getColor(readX, readY);
                if (color.getOpacity() > 0) {
                    pw.setColor(destXOffset + x, destYOffset + y, color);
                }
            }
        }
    }
}
