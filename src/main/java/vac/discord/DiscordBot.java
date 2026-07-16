package vac.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import vac.VAC;
import vac.models.PlayerData;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class DiscordBot {

    private final VAC plugin;
    private DiscordGateway gateway;
    private final AtomicBoolean ready;
    private String channelId;

    public DiscordBot(VAC plugin) {
        this.plugin = plugin;
        this.ready = new AtomicBoolean(false);
    }

    public boolean start() {
        String token = plugin.getConfigManager().getDiscordBotToken();
        if (token == null || token.isEmpty()) {
            plugin.getLogger().warning("Discord Bot token not configured.");
            return false;
        }
        this.channelId = plugin.getConfigManager().getDiscordChannelId();
        gateway = new DiscordGateway(plugin, token, this);
        gateway.start();
        plugin.getLogger().info("Discord Bot starting...");
        return true;
    }

    public void stop() {
        if (gateway != null) {
            gateway.shutdown();
        }
    }

    public boolean isEnabled() { return ready.get(); }

    void setReady(boolean v) { ready.set(v); }

    void handleInteraction(JsonObject data) {
        int type = data.get("type").getAsInt();
        if (type == 2) { // Slash command
            handleSlashCommand(data);
        } else if (type == 3) { // Button click
            handleButtonClick(data);
        }
    }

    private void handleSlashCommand(JsonObject interaction) {
        JsonObject d = interaction.getAsJsonObject("data");
        String name = d.get("name").getAsString();
        String token = interaction.get("token").getAsString();
        String id = interaction.get("id").getAsString();

        switch (name) {
            case "ban":
                cmdBan(interaction, d, id, token);
                break;
            case "freeze":
                cmdFreeze(interaction, d, id, token);
                break;
            case "unfreeze":
                cmdUnfreeze(interaction, d, id, token);
                break;
            case "check":
                cmdCheck(interaction, d, id, token);
                break;
            case "stats":
                cmdStats(interaction, id, token);
                break;
        }
    }

    private void handleButtonClick(JsonObject interaction) {
        JsonObject d = interaction.getAsJsonObject("data");
        String customId = d.get("custom_id").getAsString();
        String token = interaction.get("token").getAsString();
        String id = interaction.get("id").getAsString();

        String[] parts = customId.split(":");
        if (parts.length < 3) return;
        String action = parts[0];
        String playerName = parts[1];
        String result = parts[2];

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null || !target.isOnline()) {
            JsonObject resp = new JsonObject();
            resp.addProperty("type", 4);
            JsonObject msg = new JsonObject();
            msg.addProperty("content", ":x: Player **" + playerName + "** is no longer online.");
            resp.add("data", msg);
            gateway.respondToInteraction(id, token, resp);
            return;
        }

        if ("confirm".equals(result)) {
            if ("ban".equals(action)) {
                plugin.getPunishmentManager().banPlayer(target, "Discord", true);
            } else if ("freeze".equals(action)) {
                plugin.getFreezeManager().freeze(target);
            }
        }

        JsonObject resp = new JsonObject();
        resp.addProperty("type", 7);
        JsonObject msg = new JsonObject();
        msg.addProperty("content", ":white_check_mark: **" + playerName + "** "
                + ("ban".equals(action) ? "banned." : "frozen."));
        msg.add("components", new JsonArray());
        resp.add("data", msg);
        gateway.respondToInteraction(id, token, resp);
    }

    private void cmdBan(JsonObject interaction, JsonObject d, String id, String token) {
        JsonArray options = d.getAsJsonArray("options");
        String playerName = options.get(0).getAsJsonObject().get("value").getAsString();
        Player target = Bukkit.getPlayerExact(playerName);

        if (target == null || !target.isOnline()) {
            respond(id, token, ":x: Player **" + playerName + "** is not online.");
            return;
        }

        JsonObject resp = new JsonObject();
        resp.addProperty("type", 4);
        JsonObject msg = new JsonObject();
        msg.addProperty("content", ":warning: Ban **" + playerName + "**?");
        JsonArray components = new JsonArray();
        JsonObject row = new JsonObject();
        row.addProperty("type", 1);
        JsonArray buttons = new JsonArray();

        JsonObject confirmBtn = new JsonObject();
        confirmBtn.addProperty("type", 2);
        confirmBtn.addProperty("style", 4);
        confirmBtn.addProperty("label", "Confirm Ban");
        confirmBtn.addProperty("custom_id", "ban:" + playerName + ":confirm");

        JsonObject cancelBtn = new JsonObject();
        cancelBtn.addProperty("type", 2);
        cancelBtn.addProperty("style", 2);
        cancelBtn.addProperty("label", "Cancel");
        cancelBtn.addProperty("custom_id", "ban:" + playerName + ":cancel");

        buttons.add(confirmBtn);
        buttons.add(cancelBtn);
        row.add("components", buttons);
        components.add(row);
        msg.add("components", components);
        resp.add("data", msg);
        gateway.respondToInteraction(id, token, resp);
    }

    private void cmdFreeze(JsonObject interaction, JsonObject d, String id, String token) {
        JsonArray options = d.getAsJsonArray("options");
        String playerName = options.get(0).getAsJsonObject().get("value").getAsString();
        Player target = Bukkit.getPlayerExact(playerName);

        if (target == null || !target.isOnline()) {
            respond(id, token, ":x: Player **" + playerName + "** is not online.");
            return;
        }

        JsonObject resp = new JsonObject();
        resp.addProperty("type", 4);
        JsonObject msg = new JsonObject();
        msg.addProperty("content", ":snowflake: Freeze **" + playerName + "**?");
        JsonArray components = new JsonArray();
        JsonObject row = new JsonObject();
        row.addProperty("type", 1);
        JsonArray buttons = new JsonArray();

        JsonObject confirmBtn = new JsonObject();
        confirmBtn.addProperty("type", 2);
        confirmBtn.addProperty("style", 1);
        confirmBtn.addProperty("label", "Confirm Freeze");
        confirmBtn.addProperty("custom_id", "freeze:" + playerName + ":confirm");

        JsonObject cancelBtn = new JsonObject();
        cancelBtn.addProperty("type", 2);
        cancelBtn.addProperty("style", 2);
        cancelBtn.addProperty("label", "Cancel");
        cancelBtn.addProperty("custom_id", "freeze:" + playerName + ":cancel");

        buttons.add(confirmBtn);
        buttons.add(cancelBtn);
        row.add("components", buttons);
        components.add(row);
        msg.add("components", components);
        resp.add("data", msg);
        gateway.respondToInteraction(id, token, resp);
    }

    private void cmdUnfreeze(JsonObject interaction, JsonObject d, String id, String token) {
        JsonArray options = d.getAsJsonArray("options");
        String playerName = options.get(0).getAsJsonObject().get("value").getAsString();
        Player target = Bukkit.getPlayerExact(playerName);

        if (target == null || !target.isOnline()) {
            respond(id, token, ":x: Player **" + playerName + "** is not online.");
            return;
        }

        plugin.getFreezeManager().unfreeze(target);
        respond(id, token, ":white_check_mark: **" + playerName + "** unfrozen.");
    }

    private void cmdCheck(JsonObject interaction, JsonObject d, String id, String token) {
        JsonArray options = d.getAsJsonArray("options");
        String playerName = options.get(0).getAsJsonObject().get("value").getAsString();
        Player target = Bukkit.getPlayerExact(playerName);

        if (target == null || !target.isOnline()) {
            respond(id, token, ":x: Player **" + playerName + "** is not online.");
            return;
        }

        PlayerData pd = plugin.getPlayerDataManager().getOrCreate(target);
        String msg = "**" + playerName + "**\n"
                + ":bar_chart: Confidence: **" + String.format("%.1f", pd.getConfidence()) + "%**\n"
                + ":ping_pong: Ping: **" + target.spigot().getPing() + "ms**\n"
                + ":heart: Health: **" + String.format("%.0f/%.0f", target.getHealth(), target.getMaxHealth()) + "**\n"
                + ":warning: Violations: **" + pd.getTotalViolations() + "**";
        if (plugin.getKillAuraAnalyzer() != null) {
            msg += "\n:crossed_swords: CPS: **" + String.format("%.1f", plugin.getKillAuraAnalyzer().getCurrentCPS(target))
                    + "** | Reach: **" + String.format("%.2f", plugin.getKillAuraAnalyzer().getAverageReach(target)) + "**";
        }
        respond(id, token, msg);
    }

    private void cmdStats(JsonObject interaction, String id, String token) {
        String msg = ":bar_chart: **VAC Server Statistics**\n"
                + ":busts_in_silhouette: Online: **" + Bukkit.getOnlinePlayers().size() + "**\n"
                + ":mag: Tracked: **" + plugin.getPlayerDataManager().getTrackedPlayerCount() + "**\n"
                + ":hammer: Bans: **" + plugin.getPlayerDataManager().getTotalBans() + "**";
        respond(id, token, msg);
    }

    private void respond(String id, String token, String content) {
        JsonObject resp = new JsonObject();
        resp.addProperty("type", 4);
        JsonObject msg = new JsonObject();
        msg.addProperty("content", content);
        resp.add("data", msg);
        gateway.respondToInteraction(id, token, resp);
    }
}
