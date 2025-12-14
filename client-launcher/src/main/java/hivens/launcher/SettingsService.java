package hivens.launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.management.OperatingSystemMXBean;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Сервис управления настройками лаунчера.
 * Отвечает за загрузку и сохранение файла settings.json.
 * Реализует логику "умного" выбора памяти.
 */
public class SettingsService {
    // Папка настроек: ~/.SCOL
    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".SCOL");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("settings.json");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static RootConfig config;

    static {
        load();
    }

    // === Структура конфига (POJO) ===

    /** Корневой объект конфигурации. */
    public static class RootConfig {
        public GlobalSettings global = new GlobalSettings();
        // Настройки для конкретных серверов (ключ - имя сервера, например "Industrial")
        public Map<String, ServerSettings> servers = new HashMap<>();
    }

    /** Глобальные настройки (действуют по умолчанию). */
    public static class GlobalSettings {
        public int ramMb = 2048;          // Выделенная память
        public boolean autoMemory = true; // Авто-выбор памяти
        public boolean fullScreen = false;
        public boolean autoConnect = true;
        public boolean debugMode = false; // Режим отладки
        public boolean offlineMode = false; // Офлайн режим

        public String javaPath = "";      // Кастомный путь к Java
        public String clientDir = CONFIG_DIR.resolve("updates").toString(); // Папка клиентов
    }

    /** Настройки конкретного сервера (переопределяют глобальные). */
    public static class ServerSettings {
        public Integer ramMb = null;      // Если null, берется из global
        public String javaPath = null;
        public Boolean fullScreen = null;
    }

    // === Публичное API ===

    public static GlobalSettings getGlobal() {
        return config.global;
    }

    /**
     * Возвращает настройки для конкретного сервера (или создает пустые, если нет).
     */
    public static ServerSettings getServer(String serverName) {
        return config.servers.computeIfAbsent(serverName, k -> new ServerSettings());
    }

    /**
     * Вычисляет эффективное количество ОЗУ для запуска.
     * Учитывает: Авто-режим, настройки сервера и глобальные настройки.
     */
    public static int getEffectiveRam(String serverName) {
        // 1. Если включено Авто (глобально)
        if (config.global.autoMemory) {
            return calculateAutoMemory();
        }

        // 2. Если есть настройки для сервера и там задана память
        if (serverName != null && config.servers.containsKey(serverName)) {
            Integer serverRam = config.servers.get(serverName).ramMb;
            if (serverRam != null) return serverRam;
        }

        // 3. Иначе глобальное значение
        return config.global.ramMb;
    }

    /** Загрузка настроек из JSON. */
    public static void load() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                    config = gson.fromJson(reader, RootConfig.class);
                }
            }
            if (config == null) config = new RootConfig();
        } catch (IOException e) {
            System.err.println("Failed to load settings, using defaults.");
            config = new RootConfig();
        }
    }

    /** Сохранение настроек в JSON. */
    public static void save() {
        try {
            if (!Files.exists(CONFIG_DIR)) Files.createDirectories(CONFIG_DIR);
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                gson.toJson(config, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // === Внутренняя логика ===

    /**
     * Рассчитывает оптимальное кол-во памяти исходя из системных ресурсов.
     * Логика: 50% от свободной ОЗУ, но в разумных пределах (1GB - 4GB).
     */
    private static int calculateAutoMemory() {
        try {
            OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            long totalMb = osBean.getTotalPhysicalMemorySize() / (1024 * 1024);
            int target = (int) (totalMb / 2);
            // Ограничиваем: минимум 1ГБ, максимум 4ГБ (оптимально для 1.12.2)
            return Math.max(1024, Math.min(4096, target));
        } catch (Exception e) {
            return 2048; // Безопасный дефолт
        }
    }
}
