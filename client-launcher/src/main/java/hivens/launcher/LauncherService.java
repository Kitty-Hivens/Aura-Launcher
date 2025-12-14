package hivens.launcher;

import hivens.core.api.model.ServerProfile;
import hivens.core.data.SessionData;
import hivens.utils.JavaHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Сервис запуска клиента Minecraft.
 * Отвечает за сборку команды запуска, настройку JVM и старт процесса.
 */
public class LauncherService {

    private static final Logger logger = LoggerFactory.getLogger(LauncherService.class);

    /**
     * Запускает клиент Minecraft для указанного сервера.
     *
     * @param session       Данные сессии пользователя (токен, UUID).
     * @param serverProfile Профиль сервера (версия, IP, порт).
     * @return Объект Process запущенной игры.
     * @throws IOException Если не удалось запустить процесс.
     */
    public Process launch(SessionData session, ServerProfile serverProfile) throws IOException {
        logger.info("Preparing to launch server: {}", serverProfile.getName());

        // 1. Определяем пути
        Path clientDir = Paths.get(SettingsService.getGlobal().clientDir, serverProfile.getName());
        Path assetsDir = Paths.get(SettingsService.getGlobal().clientDir, "assets");
        Path nativesDir = clientDir.resolve("natives");

        // 2. Собираем аргументы JVM (память, джава, флаги)
        List<String> command = buildJvmArgs(serverProfile, clientDir, nativesDir);

        // 3. Добавляем Main Class
        // Для 1.12.2 (Forge) это обычно net.minecraft.launchwrapper.Launch
        // Если у тебя есть поддержка версий выше 1.13, тут нужна проверка версии
        String mainClass = "net.minecraft.launchwrapper.Launch";
        command.add(mainClass);

        // 4. Собираем аргументы Клиента (логин, пути, сервер)
        command.addAll(buildGameArgs(session, serverProfile, clientDir, assetsDir));

        // 5. Логируем команду (скрывая токен для безопасности)
        logCommand(command);

        // 6. Запуск
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(clientDir.toFile());
        builder.redirectErrorStream(true); // Объединяем stdout и stderr

        // Чистим переменные окружения, чтобы не мешали Java
        Map<String, String> env = builder.environment();
        env.remove("_JAVA_OPTIONS");
        env.remove("JAVA_TOOL_OPTIONS");
        env.remove("CLASSPATH");

        logger.info("Starting Minecraft process...");
        return builder.start();
    }

    /**
     * Формирует список аргументов для JVM (Java Virtual Machine).
     */
    private List<String> buildJvmArgs(ServerProfile profile, Path clientDir, Path nativesDir) {
        List<String> args = new ArrayList<>();

        // -- Исполняемый файл Java --
        // Используем наш умный JavaHelper
        args.add(JavaHelper.getJavaPath());

        // -- Память --
        // Берем эффективное значение (учитывает настройки сервера, глобальные и авто-режим)
        int ramMb = SettingsService.getEffectiveRam(profile.getName());
        args.add("-Xmx" + ramMb + "M");
        // Минимальную память можно ставить поменьше, чтобы Java не отжирала сразу всё
        args.add("-Xms512M");

        // -- Флаги производительности (GC) --
        args.add("-XX:+UseG1GC");
        args.add("-XX:+UnlockExperimentalVMOptions");
        args.add("-XX:G1NewSizePercent=20");
        args.add("-XX:G1ReservePercent=20");
        args.add("-XX:MaxGCPauseMillis=50");
        args.add("-XX:G1HeapRegionSize=32M");

        // -- Флаги совместимости --
        args.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
        args.add("-Dfml.ignorePatchDiscrepancies=true");

        // [CRITICAL] Флаг для Linux, чтобы не было "Timed Out" при подключении
        args.add("-Djava.net.preferIPv4Stack=true");

        // -- Брендинг (чтобы сервер видел нас как "родной" лаунчер) --
        args.add("-Dminecraft.launcher.brand=SmartyCraft");
        args.add("-Dlauncher.version=3.6.2"); // Версия из оригинала

        // -- Пути к нативным библиотекам --
        args.add("-Djava.library.path=" + nativesDir.toAbsolutePath());

        // -- Classpath (библиотеки + майнкрафт) --
        // Здесь должен быть список всех jar-файлов через разделитель
        // В реальном коде ты должен получить этот список от FileDownloadService или ManifestProcessor
        String classpath = getClasspath(clientDir);
        args.add("-cp");
        args.add(classpath);

        return args;
    }

    /**
     * Формирует аргументы самой игры.
     */
    private List<String> buildGameArgs(SessionData session, ServerProfile profile, Path gameDir, Path assetsDir) {
        List<String> args = new ArrayList<>();

        // Аутентификация
        args.add("--username");
        args.add(session.playerName());
        args.add("--uuid");
        args.add(session.uuid());
        args.add("--accessToken");
        args.add(session.accessToken()); // Наш расшифрованный токен!

        // Тип пользователя (важно для Forge)
        args.add("--userType");
        args.add("mojang");
        args.add("--versionType");
        args.add("Forge");

        // Версия и пути
        args.add("--version");
        args.add(profile.getVersion()); // Например "1.12.2"
        args.add("--gameDir");
        args.add(gameDir.toAbsolutePath().toString());
        args.add("--assetsDir");
        args.add(assetsDir.toAbsolutePath().toString());
        args.add("--assetIndex");
        args.add(getAssetIndex(profile.getVersion())); // Обычно совпадает с версией

        // Настройки окна
        if (SettingsService.getGlobal().fullScreen) {
            args.add("--fullscreen");
            args.add("true");
        } else {
            args.add("--width"); args.add("925");
            args.add("--height"); args.add("530");
        }

        // Авто-подключение к серверу
        if (SettingsService.getGlobal().autoConnect) {
            logger.info("Auto-connect enabled for {}", profile.getAddress());
            args.add("--server");
            args.add(profile.getAddress());
            args.add("--port");
            args.add(String.valueOf(profile.getPort()));
        }

        return args;
    }

    // Вспомогательный метод для сборки Classpath
    // В реальном проекте это может быть сложнее (зависит от того, как ты хранишь библиотеки)
    private String getClasspath(Path clientDir) {
        // Пример простой реализации: сканируем папку libraries и добавляем minecraft.jar
        StringBuilder cp = new StringBuilder();
        String separator = File.pathSeparator;

        File libsDir = clientDir.resolve("libraries").toFile();
        if (libsDir.exists() && libsDir.isDirectory()) {
            File[] files = libsDir.listFiles((dir, name) -> name.endsWith(".jar"));
            if (files != null) {
                for (File f : files) {
                    cp.append(f.getAbsolutePath()).append(separator);
                }
            }
        }

        // Добавляем сам minecraft.jar (обычно он лежит в bin или versions)
        // Путь зависит от твоей структуры папок. Предположим bin/minecraft.jar
        cp.append(clientDir.resolve("bin").resolve("minecraft.jar").toAbsolutePath());

        return cp.toString();
    }

    // Определение индекса ассетов (для 1.7.10 это может быть "1.7.10", для 1.12.2 - "1.12")
    private String getAssetIndex(String version) {
        if (version.equals("1.7.10")) return "1.7.10";
        if (version.startsWith("1.12")) return "1.12";
        return version; // Фолбэк
    }

    private void logCommand(List<String> command) {
        // Копируем список, чтобы не менять оригинал
        List<String> safeCommand = new ArrayList<>(command);
        for (int i = 0; i < safeCommand.size(); i++) {
            if (safeCommand.get(i).equals("--accessToken")) {
                if (i + 1 < safeCommand.size()) {
                    safeCommand.set(i + 1, "********"); // Прячем токен в логах
                }
            }
        }
        logger.debug("Launch command: {}", String.join(" ", safeCommand));
    }
}