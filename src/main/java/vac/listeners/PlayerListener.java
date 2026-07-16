package vac.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import vac.VAC;
import vac.models.PlayerData;

public class PlayerListener implements Listener {

    private final VAC plugin;

    public PlayerListener(VAC plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getPlayerDataManager().getOrCreate(player);
        if (plugin.getAntiXrayManager() != null) {
            plugin.getAntiXrayManager().injectPlayer(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getAntiXrayManager() != null) {
            plugin.getAntiXrayManager().removePlayer(player);
        }
        PlayerData data = plugin.getPlayerDataManager().get(player);

        if (data != null) {
            if (plugin.getConfigManager().isMySQLEnabled()) {
                plugin.getMySQLManager().savePlayerData(data);
            } else if (plugin.getConfigManager().isSQLiteEnabled()) {
                plugin.getSQLiteManager().savePlayerData(data);
            }
        }

        if (plugin.getLagManager() != null) {
            plugin.getLagManager().stopLag(player);
        }
        if (plugin.getFreezeManager() != null) {
            plugin.getFreezeManager().unfreeze(player);
        }
        if (plugin.getKillAuraAnalyzer() != null) {
            plugin.getKillAuraAnalyzer().remove(player.getUniqueId());
        }

        plugin.getPlayerDataManager().remove(player.getUniqueId());
    }
}
