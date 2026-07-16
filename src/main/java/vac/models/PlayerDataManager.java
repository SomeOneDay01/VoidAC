package vac.models;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import vac.VAC;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private final VAC plugin;
    private final Map<UUID, PlayerData> playerDataMap;

    public PlayerDataManager(VAC plugin) {
        this.plugin = plugin;
        this.playerDataMap = new ConcurrentHashMap<>();
    }

    public PlayerData getOrCreate(Player player) {
        return playerDataMap.computeIfAbsent(player.getUniqueId(), uuid -> {
            PlayerData data = new PlayerData(plugin, player);
            if (plugin.getConfigManager().isMySQLEnabled()) {
                plugin.getMySQLManager().loadPlayerData(uuid).thenAcceptAsync(loaded -> {
                    loaded.setPlayerName(player.getName());
                    playerDataMap.put(uuid, loaded);
                }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
            } else if (plugin.getConfigManager().isSQLiteEnabled()) {
                plugin.getSQLiteManager().loadPlayerData(uuid).thenAcceptAsync(loaded -> {
                    loaded.setPlayerName(player.getName());
                    playerDataMap.put(uuid, loaded);
                }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
            }
            return data;
        });
    }

    public PlayerData getOrCreate(UUID uuid) {
        return playerDataMap.computeIfAbsent(uuid, k -> {
            PlayerData data = new PlayerData(plugin, uuid);
            if (plugin.getConfigManager().isMySQLEnabled()) {
                plugin.getMySQLManager().loadPlayerData(uuid).thenAcceptAsync(loaded -> {
                    playerDataMap.put(uuid, loaded);
                }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
            }
            return data;
        });
    }

    public PlayerData get(UUID uuid) {
        return playerDataMap.get(uuid);
    }

    public PlayerData get(Player player) {
        return playerDataMap.get(player.getUniqueId());
    }

    public void remove(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        if (data != null) {
            if (plugin.getConfigManager().isMySQLEnabled()) {
                plugin.getMySQLManager().savePlayerData(data);
            } else if (plugin.getConfigManager().isSQLiteEnabled()) {
                plugin.getSQLiteManager().savePlayerData(data);
            }
        }
        playerDataMap.remove(uuid);
    }

    public void tick() {
        long now = System.currentTimeMillis();
        List<PlayerData> toBan = new ArrayList<>();

        for (PlayerData data : playerDataMap.values()) {
            data.tickConfidenceDecay();
            if (data.getConfidence() >= plugin.getConfigManager().getBanThreshold()
                    && !data.isBanned()
                    && plugin.getConfigManager().isAutoBan()) {
                toBan.add(data);
            }
        }

        if (!toBan.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (PlayerData data : toBan) {
                    Player player = Bukkit.getPlayer(data.getUuid());
                    if (player != null && player.isOnline()) {
                        plugin.getPunishmentManager().banPlayer(player, "VAC", true);
                    }
                }
            });
        }
    }

    public void saveAll() {
        boolean mysql = plugin.getConfigManager().isMySQLEnabled();
        boolean sqlite = plugin.getConfigManager().isSQLiteEnabled();
        if (!mysql && !sqlite) return;
        for (PlayerData data : playerDataMap.values()) {
            if (mysql) plugin.getMySQLManager().savePlayerData(data);
            else if (sqlite) plugin.getSQLiteManager().savePlayerData(data);
        }
    }

    public void saveAsync(PlayerData data) {
        boolean mysql = plugin.getConfigManager().isMySQLEnabled();
        boolean sqlite = plugin.getConfigManager().isSQLiteEnabled();
        if (!mysql && !sqlite) return;
        CompletableFuture.runAsync(() -> {
            if (mysql) plugin.getMySQLManager().savePlayerData(data);
            else if (sqlite) plugin.getSQLiteManager().savePlayerData(data);
        });
    }

    public Collection<PlayerData> getAllData() {
        return playerDataMap.values();
    }

    public int getTrackedPlayerCount() {
        return playerDataMap.size();
    }

    public int getTotalViolations() {
        return playerDataMap.values().stream()
                .mapToInt(PlayerData::getTotalViolations)
                .sum();
    }

    public int getTotalBans() {
        return (int) playerDataMap.values().stream()
                .filter(PlayerData::isBanned)
                .count();
    }
}
