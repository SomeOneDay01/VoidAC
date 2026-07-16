package vac.models;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import vac.VAC;

import java.util.*;

public class SpectateManager {

    private final VAC plugin;
    private final Map<UUID, UUID> spectating;
    private final Set<UUID> vanished;

    public SpectateManager(VAC plugin) {
        this.plugin = plugin;
        this.spectating = new HashMap<>();
        this.vanished = new HashSet<>();
        startActionBarTask();
    }

    public void startSpectate(Player spectator, Player target) {
        UUID prev = spectating.put(spectator.getUniqueId(), target.getUniqueId());
        if (prev != null) {
            Player old = Bukkit.getPlayer(prev);
            if (old != null) {
                old.showPlayer(plugin, spectator);
            }
        }

        vanished.add(spectator.getUniqueId());
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(spectator)) p.hidePlayer(plugin, spectator);
        }

        spectator.setGameMode(GameMode.SPECTATOR);
        spectator.teleport(target);
        spectator.setSpectatorTarget(target);

        plugin.getConfigManager().getMessageRaw("spectate_started")
                .replace("{player}", target.getName());
    }

    public void stopSpectate(Player spectator) {
        UUID targetId = spectating.remove(spectator.getUniqueId());
        vanished.remove(spectator.getUniqueId());

        spectator.setSpectatorTarget(null);
        spectator.setGameMode(GameMode.SURVIVAL);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showPlayer(plugin, spectator);
        }

        if (targetId != null) {
            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                target.showPlayer(plugin, spectator);
            }
        }

        plugin.getConfigManager().getMessageRaw("spectate_stopped");
    }

    public boolean isSpectating(Player player) {
        return spectating.containsKey(player.getUniqueId());
    }

    public boolean isVanished(Player player) {
        return vanished.contains(player.getUniqueId());
    }

    public Player getTarget(Player spectator) {
        UUID id = spectating.get(spectator.getUniqueId());
        return id != null ? Bukkit.getPlayer(id) : null;
    }

    public Set<UUID> getSpectators() {
        return spectating.keySet();
    }

    private void startActionBarTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, UUID> entry : spectating.entrySet()) {
                    Player spectator = Bukkit.getPlayer(entry.getKey());
                    Player target = Bukkit.getPlayer(entry.getValue());
                    if (spectator == null || target == null || !spectator.isOnline() || !target.isOnline()) {
                        spectating.remove(entry.getKey());
                        vanished.remove(entry.getKey());
                        continue;
                    }
                    PlayerData data = plugin.getPlayerDataManager().getOrCreate(target);
                    String msg = plugin.getConfigManager().getMessageRaw("spectate_actionbar")
                            .replace("{player}", target.getName())
                            .replace("{confidence}", String.format("%.1f", data.getConfidence()));
                    spectator.sendActionBar(msg);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
}
