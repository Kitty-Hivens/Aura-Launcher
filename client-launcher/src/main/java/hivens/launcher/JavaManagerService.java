package hivens.launcher;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JavaManagerService {

    private static final Logger log = LoggerFactory.getLogger(JavaManagerService.class);

    private final Path runtimesDir;
    private final OkHttpClient httpClient;

    public JavaManagerService(Path dataDir, OkHttpClient httpClient) {
        this.runtimesDir = dataDir.resolve("runtimes");
        this.httpClient = httpClient;
    }

    public Path getJavaPath(String version) throws IOException {
        int javaVersion = detectJavaVersion(version);
        // Формируем уникальное имя папки: java-8-windows-x64, java-17-macos-aarch64
        String folderName = String.format("java-%d-%s-%s", javaVersion, getOsName(), getArchName());
        Path targetDir = runtimesDir.resolve(folderName);

        Path executable = findJavaExecutable(targetDir);
        if (executable != null) return executable;

        log.info("Java {} ({}/{}) not found locally. Downloading Liberica...", javaVersion, getOsName(), getArchName());
        downloadAndUnpack(javaVersion, targetDir);

        executable = findJavaExecutable(targetDir);
        if (executable == null) throw new IOException("Java downloaded but executable not found!");

        if (!SystemUtils.IS_OS_WINDOWS) executable.toFile().setExecutable(true);

        return executable;
    }

    private int detectJavaVersion(String mcVersion) {
        if (mcVersion.startsWith("1.21") || mcVersion.startsWith("1.20.5") || mcVersion.startsWith("1.20.6")) return 21;
        if (mcVersion.startsWith("1.17") || mcVersion.startsWith("1.18") || mcVersion.startsWith("1.19") || mcVersion.startsWith("1.20")) return 17;
        return 8; // Default for 1.7.10 - 1.16.5
    }

    private void downloadAndUnpack(int version, Path targetDir) throws IOException {
        String url = getDownloadUrl(version);
        if (url == null) {
            throw new IOException("No Java build available for this OS/Arch combination (" + getOsName() + " " + getArchName() + ")");
        }

        boolean isZip = url.endsWith(".zip");
        File archive = Files.createTempFile("java_pkg", isZip ? ".zip" : ".tar.gz").toFile();

        log.info("Downloading from: {}", url);
        Request request = new Request.Builder().url(url).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Download failed: " + response.code());
            try (FileOutputStream fos = new FileOutputStream(archive)) {
                fos.write(response.body().bytes());
            }
        }

        log.info("Unpacking to {}", targetDir);
        if (Files.exists(targetDir)) FileUtils.deleteDirectory(targetDir.toFile());
        Files.createDirectories(targetDir);

        if (isZip) unzip(archive, targetDir);
        else untargz(archive, targetDir);

        archive.delete();
    }

    /**
     * Возвращает прямую ссылку на BellSoft Liberica JDK (Full)
     * на основе ссылок, предоставленных пользователем.
     */
    private String getDownloadUrl(int version) {
        String os = getOsName();   // win, linux, mac
        String arch = getArchName(); // x64, x32, arm64

        // --- JAVA 8 (8u472+9) ---
        if (version == 8) {
            if (os.equals("win")) {
                if (arch.equals("x64")) return "https://download.bell-sw.com/java/8u472+9/bellsoft-jdk8u472+9-windows-amd64-full.zip";
                if (arch.equals("x32")) return "https://download.bell-sw.com/java/8u472+9/bellsoft-jdk8u472+9-windows-i586.zip";
            }
            if (os.equals("linux") && arch.equals("x64")) return "https://download.bell-sw.com/java/8u472+9/bellsoft-jdk8u472+9-linux-amd64-full.tar.gz";
            if (os.equals("mac")) {
                if (arch.equals("x64")) return "https://download.bell-sw.com/java/8u472+9/bellsoft-jdk8u472+9-macos-amd64-full.tar.gz";
                if (arch.equals("arm64")) return "https://download.bell-sw.com/java/8u472+9/bellsoft-jdk8u472+9-macos-aarch64.tar.gz";
            }
        }

        // --- JAVA 17 (17.0.17+15) ---
        if (version == 17) {
            if (os.equals("win")) {
                if (arch.equals("x64")) return "https://download.bell-sw.com/java/17.0.17+15/bellsoft-jdk17.0.17+15-windows-amd64-full.zip";
                if (arch.equals("x32")) return "https://download.bell-sw.com/java/17.0.17+15/bellsoft-jdk17.0.17+15-windows-i586-full.zip";
            }
            if (os.equals("linux") && arch.equals("x64")) return "https://download.bell-sw.com/java/17.0.17+15/bellsoft-jdk17.0.17+15-linux-amd64-full.tar.gz";
            if (os.equals("mac")) {
                if (arch.equals("x64")) return "https://download.bell-sw.com/java/17.0.17+15/bellsoft-jdk17.0.17+15-macos-amd64-full.tar.gz";
                if (arch.equals("arm64")) return "https://download.bell-sw.com/java/17.0.17+15/bellsoft-jdk17.0.17+15-macos-aarch64-full.tar.gz";
            }
        }

        // --- JAVA 21 (21.0.9+15) ---
        if (version == 21) {
            if (os.equals("win") && arch.equals("x64")) return "https://download.bell-sw.com/java/21.0.9+15/bellsoft-jdk21.0.9+15-windows-amd64-full.zip";
            if (os.equals("linux") && arch.equals("x64")) return "https://download.bell-sw.com/java/21.0.9+15/bellsoft-jdk21.0.9+15-linux-amd64-full.tar.gz";
            if (os.equals("mac")) {
                if (arch.equals("x64")) return "https://download.bell-sw.com/java/21.0.9+15/bellsoft-jdk21.0.9+15-macos-amd64-full.tar.gz";
                if (arch.equals("arm64")) return "https://download.bell-sw.com/java/21.0.9+15/bellsoft-jdk21.0.9+15-macos-aarch64-full.tar.gz";
            }
        }

        return null;
    }

    // --- Helpers ---

    private String getOsName() {
        if (SystemUtils.IS_OS_WINDOWS) return "win";
        if (SystemUtils.IS_OS_LINUX) return "linux";
        if (SystemUtils.IS_OS_MAC) return "mac";
        return "unknown";
    }

    private String getArchName() {
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if (arch.contains("aarch64") || arch.contains("arm64")) return "arm64";
        if (arch.contains("64")) return "x64";
        if (arch.contains("86") || arch.contains("32")) return "x32";
        return "x64";
    }

    private Path findJavaExecutable(Path dir) {
        if (!Files.exists(dir)) return null;
        try {
            return Files.walk(dir)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.equals("java") || name.equals("java.exe");
                    })
                    .findFirst().orElse(null);
        } catch (IOException e) { return null; }
    }

    private void unzip(File zip, Path dest) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path p = dest.resolve(entry.getName());
                if (entry.isDirectory()) Files.createDirectories(p);
                else {
                    Files.createDirectories(p.getParent());
                    Files.copy(zis, p, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void untargz(File tar, Path dest) throws IOException {
        try (InputStream fi = new FileInputStream(tar);
             InputStream bi = new BufferedInputStream(fi);
             GzipCompressorInputStream gzi = new GzipCompressorInputStream(bi);
             TarArchiveInputStream tai = new TarArchiveInputStream(gzi)) {
            TarArchiveEntry entry;
            while ((entry = tai.getNextTarEntry()) != null) {
                Path p = dest.resolve(entry.getName());
                if (entry.isDirectory()) Files.createDirectories(p);
                else {
                    Files.createDirectories(p.getParent());
                    Files.copy(tai, p, StandardCopyOption.REPLACE_EXISTING);
                    if (!SystemUtils.IS_OS_WINDOWS && (entry.getMode() & 73) != 0) p.toFile().setExecutable(true);
                }
            }
        }
    }
}