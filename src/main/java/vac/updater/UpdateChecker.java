package vac.updater;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import vac.VAC;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class UpdateChecker implements Listener {

    private final VAC plugin;
    private final AtomicReference<UpdateInfo> latest;
    private static final String GITHUB_API = "https://api.github.com/repos/%s/%s/releases/latest";
    private static final String REPO_OWNER = "SomeOneDay01";
    private static final String REPO_NAME = "VoidAC";

    public UpdateChecker(VAC plugin) {
        this.plugin = plugin;
        this.latest = new AtomicReference<>(null);
    }

    public void checkAsync() {

        CompletableFuture.runAsync(() -> {
            try {
                String url = String.format(GITHUB_API, REPO_OWNER, REPO_NAME);
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "VAC-AntiCheat");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int code = conn.getResponseCode();
                if (code != 200) return;

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
                reader.close();

                JsonObject release = JsonParser.parseString(json.toString()).getAsJsonObject();
                String tagName = release.get("tag_name").getAsString();
                String htmlUrl = release.get("html_url").getAsString();
                String body = release.has("body") && !release.get("body").isJsonNull()
                        ? release.get("body").getAsString() : "";
                boolean preRelease = release.has("prerelease") && release.get("prerelease").getAsBoolean();
                JsonArray assets = release.getAsJsonArray("assets");
                String downloadUrl = "";
                if (assets != null && assets.size() > 0) {
                    downloadUrl = assets.get(0).getAsJsonObject().get("browser_download_url").getAsString();
                }

                String currentVer = plugin.getDescription().getVersion();
                if (isNewer(tagName, currentVer)) {
                    latest.set(new UpdateInfo(tagName, htmlUrl, downloadUrl, body, preRelease));
                    plugin.getLogger().info("Update available: " + currentVer + " -> " + tagName);
                }
            } catch (Exception e) {
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("Update check failed: " + e.getMessage());
                }
            }
        });
    }

    private boolean isNewer(String tagVersion, String currentVersion) {
        String tag = tagVersion.replaceAll("[^0-9.]", "");
        String cur = currentVersion.replaceAll("[^0-9.]", "");
        String[] tagParts = tag.split("\\.");
        String[] curParts = cur.split("\\.");
        int maxLen = Math.max(tagParts.length, curParts.length);
        for (int i = 0; i < maxLen; i++) {
            int t = i < tagParts.length ? parseInt(tagParts[i]) : 0;
            int c = i < curParts.length ? parseInt(curParts[i]) : 0;
            if (t > c) return true;
            if (t < c) return false;
        }
        return false;
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfigManager().isUpdaterNotify()) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("vac.admin")) return;

        UpdateInfo info = latest.get();
        if (info != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(ChatColor.RED + "[VAC] Update available: "
                        + ChatColor.WHITE + plugin.getDescription().getVersion()
                        + ChatColor.RED + " -> " + ChatColor.GREEN + info.tagName);
                player.sendMessage(ChatColor.GRAY + "Download: " + info.htmlUrl);
            }, 40L);
        }
    }

    public UpdateInfo getLatest() { return latest.get(); }

    public static class UpdateInfo {
        public final String tagName;
        public final String htmlUrl;
        public final String downloadUrl;
        public final String body;
        public final boolean preRelease;

        public UpdateInfo(String tagName, String htmlUrl, String downloadUrl, String body, boolean preRelease) {
            this.tagName = tagName;
            this.htmlUrl = htmlUrl;
            this.downloadUrl = downloadUrl;
            this.body = body;
            this.preRelease = preRelease;
        }
    }
}
