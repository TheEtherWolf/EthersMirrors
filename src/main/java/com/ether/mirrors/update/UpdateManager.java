package com.ether.mirrors.update;

import com.ether.mirrors.EthersMirrors;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks GitHub Releases for a newer version of the mod.
 * If found, downloads it silently to the mods folder in the background.
 * On the next launch, cleanOldJars() removes the outdated JAR automatically.
 *
 * Flow:
 *   1. init() is called from EthersMirrors constructor
 *   2. cleanOldJars() deletes any stale older JARs left from a previous update
 *   3. A daemon thread fetches the GitHub Releases API and compares versions
 *   4. If newer: downloads the JAR, sets status = READY_RESTART
 *   5. MirrorsEventHandler reads the status and notifies players on login
 */
public final class UpdateManager {

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/TheEtherWolf/EthersMirrors/releases/latest";
    private static final String RELEASES_PAGE =
            "https://github.com/TheEtherWolf/EthersMirrors/releases/latest";
    private static final Pattern JAR_PATTERN =
            Pattern.compile("Ethers-Mirrors-([\\d.]+)\\.jar", Pattern.CASE_INSENSITIVE);

    public enum Status { IDLE, CHECKING, DOWNLOADING, READY_RESTART, UP_TO_DATE, FAILED }

    private static volatile Status status = Status.IDLE;
    private static volatile String latestVersion = null;

    private UpdateManager() {}

    public static Status getStatus()        { return status; }
    public static String getLatestVersion() { return latestVersion; }
    public static String getReleasesPage()  { return RELEASES_PAGE; }

    /** Call once from the mod constructor. */
    public static void init() {
        cleanOldJars();
        Thread t = new Thread(UpdateManager::checkForUpdates, "EthersMirrors-UpdateChecker");
        t.setDaemon(true);
        t.start();
    }

    // -------------------------------------------------------------------------
    // Startup cleanup — remove older JARs that were left beside the new one
    // -------------------------------------------------------------------------

    private static void cleanOldJars() {
        try {
            Path modsDir = FMLPaths.MODSDIR.get();
            List<Path> jars = Files.list(modsDir)
                    .filter(p -> JAR_PATTERN.matcher(p.getFileName().toString()).matches())
                    .sorted((a, b) -> compareVersionArrays(
                            versionFromFilename(a.getFileName().toString()),
                            versionFromFilename(b.getFileName().toString())))
                    .collect(Collectors.toList());

            // Keep only the newest (last after sort); delete the rest
            for (int i = 0; i < jars.size() - 1; i++) {
                Files.deleteIfExists(jars.get(i));
                EthersMirrors.LOGGER.info("[EthersMirrors] Removed outdated JAR: {}", jars.get(i).getFileName());
            }
        } catch (Exception e) {
            EthersMirrors.LOGGER.warn("[EthersMirrors] Could not clean old JARs: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Background update check
    // -------------------------------------------------------------------------

    private static void checkForUpdates() {
        status = Status.CHECKING;
        try {
            String json = fetchUrl(GITHUB_API_URL);
            if (json == null) { status = Status.FAILED; return; }

            String tag = extractJsonString(json, "tag_name");
            if (tag == null)  { status = Status.FAILED; return; }

            latestVersion = tag.startsWith("v") ? tag.substring(1) : tag;
            String current = EthersMirrors.VERSION;

            if (!isNewer(latestVersion, current)) {
                EthersMirrors.LOGGER.info("[EthersMirrors] Up to date (v{})", current);
                status = Status.UP_TO_DATE;
                return;
            }

            EthersMirrors.LOGGER.info("[EthersMirrors] Update available: v{} → v{}", current, latestVersion);

            String downloadUrl = extractJarDownloadUrl(json);
            if (downloadUrl == null) {
                EthersMirrors.LOGGER.warn("[EthersMirrors] No JAR asset found in release v{}", latestVersion);
                status = Status.FAILED;
                return;
            }

            status = Status.DOWNLOADING;
            downloadJar(downloadUrl, latestVersion);

        } catch (Exception e) {
            EthersMirrors.LOGGER.warn("[EthersMirrors] Update check failed: {}", e.getMessage());
            status = Status.FAILED;
        }
    }

    private static void downloadJar(String downloadUrl, String version) throws IOException {
        Path modsDir = FMLPaths.MODSDIR.get();
        Path dest = modsDir.resolve("Ethers-Mirrors-" + version + ".jar");

        if (Files.exists(dest)) {
            EthersMirrors.LOGGER.info("[EthersMirrors] Update already downloaded ({})", dest.getFileName());
            status = Status.READY_RESTART;
            return;
        }

        EthersMirrors.LOGGER.info("[EthersMirrors] Downloading v{}...", version);

        HttpURLConnection conn = openConnection(downloadUrl);
        Path tmp = modsDir.resolve("Ethers-Mirrors-" + version + ".tmp");
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }

        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        EthersMirrors.LOGGER.info("[EthersMirrors] Downloaded update → {}  (restart to apply)", dest.getFileName());
        status = Status.READY_RESTART;
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    private static String fetchUrl(String urlStr) throws IOException {
        HttpURLConnection conn = openConnection(urlStr);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            EthersMirrors.LOGGER.warn("[EthersMirrors] GitHub API returned HTTP {}", code);
            return null;
        }
        String body = new String(conn.getInputStream().readAllBytes());
        conn.disconnect();
        return body;
    }

    private static HttpURLConnection openConnection(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", "EthersMirrors/" + EthersMirrors.VERSION);
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(60000);
        return conn;
    }

    // -------------------------------------------------------------------------
    // JSON / version parsing (no external library needed)
    // -------------------------------------------------------------------------

    /** Extracts a top-level string value from a JSON blob without a full parser. */
    private static String extractJsonString(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) return null;
        start += needle.length();
        int end = json.indexOf('"', start);
        return end < 0 ? null : json.substring(start, end);
    }

    /**
     * Finds the browser_download_url for the mod JAR in the release assets block.
     * Skips -sources and -api jars.
     */
    private static String extractJarDownloadUrl(String json) {
        Matcher m = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"(https://[^\"]+\\.jar)\"").matcher(json);
        while (m.find()) {
            String url = m.group(1);
            String lower = url.toLowerCase();
            if (!lower.contains("-sources") && !lower.contains("-api") && !lower.contains("-javadoc")) {
                return url;
            }
        }
        return null;
    }

    /** Returns true if {@code candidate} is a strictly newer semantic version than {@code current}. */
    private static boolean isNewer(String candidate, String current) {
        try {
            int[] c  = parseVersion(candidate);
            int[] cur = parseVersion(current);
            int len = Math.max(c.length, cur.length);
            for (int i = 0; i < len; i++) {
                int cv  = i < c.length   ? c[i]   : 0;
                int curv = i < cur.length ? cur[i] : 0;
                if (cv != curv) return cv > curv;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static int[] parseVersion(String v) {
        String[] parts = v.replaceAll("[^0-9.]", "").split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
        }
        return result;
    }

    /** Parses the version number out of a filename like {@code Ethers-Mirrors-1.2.3.jar}. */
    private static int[] versionFromFilename(String filename) {
        Matcher m = JAR_PATTERN.matcher(filename);
        return m.find() ? parseVersion(m.group(1)) : new int[]{0};
    }

    private static int compareVersionArrays(int[] a, int[] b) {
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }
}
