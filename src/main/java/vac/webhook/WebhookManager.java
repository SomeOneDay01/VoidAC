package vac.webhook;

import vac.VAC;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class WebhookManager {

    private final VAC plugin;

    public WebhookManager(VAC plugin) {
        this.plugin = plugin;
    }

    public void sendBanWebhook(String playerName, String uuid, double confidence, String bannedBy) {
        String url = plugin.getConfigManager().getDiscordWebhookUrl();
        if (url == null || url.isEmpty()) return;
        String content = plugin.getConfigManager().getMessageRaw("webhook_ban")
                .replace("{player}", playerName)
                .replace("{uuid}", uuid)
                .replace("{confidence}", String.format("%.1f", confidence))
                .replace("{banned_by}", bannedBy);
        send(url, content);
    }

    public void sendAlertWebhook(String playerName, String check, double confidence) {
        String url = plugin.getConfigManager().getDiscordWebhookUrl();
        if (url == null || url.isEmpty()) return;
        String content = plugin.getConfigManager().getMessageRaw("webhook_alert")
                .replace("{player}", playerName)
                .replace("{check}", check)
                .replace("{confidence}", String.format("%.1f", confidence));
        send(url, content);
    }

    private void send(String urlString, String message) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);

            String json = "{\"content\":\"" + escapeJson(message) + "\"}";
            byte[] data = json.getBytes(StandardCharsets.UTF_8);

            conn.setFixedLengthStreamingMode(data.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data);
            }

            int response = conn.getResponseCode();
            if (response < 200 || response >= 300) {
                plugin.getLogger().warning("Webhook response: " + response);
            }
            conn.disconnect();
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("Webhook error: " + e.getMessage());
            }
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
