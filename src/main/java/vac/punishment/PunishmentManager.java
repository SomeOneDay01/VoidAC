package vac.punishment;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import vac.VAC;
import vac.models.PlayerData;

import java.util.List;

public class PunishmentManager {

    private final VAC plugin;

    public PunishmentManager(VAC plugin) {
        this.plugin = plugin;
    }

    public void banPlayer(Player player, String bannedBy, boolean animated) {
        PlayerData pd = plugin.getPlayerDataManager().get(player);
        if (pd == null) {
            pd = plugin.getPlayerDataManager().getOrCreate(player);
        }

        final PlayerData data = pd;
        final double confidence = data.getConfidence();
        data.setBanned(true);

        if (animated && plugin.getConfigManager().isAnimationEnabled()) {
            plugin.getBanAnimation().playAnimation(player, () -> executeBan(player, bannedBy, confidence, data));
        } else {
            executeBan(player, bannedBy, confidence, data);
        }
    }

    private void executeBan(Player player, String bannedBy, double confidence, PlayerData data) {
        executeCommands(player, confidence);
        saveToDatabase(player, bannedBy, confidence);

        if (plugin.getBungeeManager() != null && plugin.getBungeeManager().isEnabled()) {
            plugin.getBungeeManager().sendBan(player.getName(), null, bannedBy, confidence);
        }

        if (plugin.getConfigManager().isBanWebhook() && plugin.getConfigManager().isWebhookEnabled()) {
            plugin.getWebhookManager().sendBanWebhook(player.getName(), player.getUniqueId().toString(), confidence, bannedBy);
        }

        String kickMessage = plugin.getConfigManager().getKickMessage()
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{confidence}", String.format("%.1f", confidence));

        player.kickPlayer(kickMessage);

        if (plugin.getConfigManager().isBroadcastEnabled()) {
            Bukkit.broadcastMessage(plugin.getConfigManager().getMessageRaw("banned")
                    .replace("{player}", player.getName())
                    .replace("{confidence}", String.format("%.1f", confidence)));
        }
    }

    private void executeCommands(Player player, double confidence) {
        List<String> commands = plugin.getConfigManager().getPunishmentCommands();
        for (String command : commands) {
            String formatted = command
                    .replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString())
                    .replace("{confidence}", String.format("%.1f", confidence));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formatted);
        }
    }

    private void saveToDatabase(Player player, String bannedBy, double confidence) {
        if (plugin.getConfigManager().isMySQLEnabled()) {
            plugin.getMySQLManager().saveBan(
                player.getUniqueId(),
                player.getName(),
                confidence,
                "Cheating detected by VAC",
                bannedBy
            );
        } else if (plugin.getConfigManager().isSQLiteEnabled()) {
            plugin.getSQLiteManager().saveBan(
                player.getUniqueId(),
                player.getName(),
                confidence,
                "Cheating detected by VAC",
                bannedBy
            );
        }
    }

    public void handleSuspicion(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return;

        double confidence = data.getConfidence();
        if (confidence >= 50) {
            List<String> commands = plugin.getConfigManager().getSuspicionCommands();
            for (String command : commands) {
                String formatted = command
                        .replace("{player}", player.getName())
                        .replace("{uuid}", player.getUniqueId().toString())
                        .replace("{confidence}", String.format("%.1f", confidence));
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formatted);
            }
        }
    }
}
