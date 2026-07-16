package vac.models;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import vac.VAC;

import java.util.*;

public class FreezeManager {

    private final VAC plugin;
    private final Set<UUID> frozen;

    public FreezeManager(VAC plugin) {
        this.plugin = plugin;
        this.frozen = new HashSet<>();
    }

    public void freeze(Player player) {
        if (frozen.contains(player.getUniqueId())) {
            unfreeze(player);
            return;
        }

        frozen.add(player.getUniqueId());
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        player.setAllowFlight(true);

        Location loc = player.getLocation();

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!player.isOnline() || !frozen.contains(player.getUniqueId())) {
                    this.cancel();
                    return;
                }
                tick++;

                Location ploc = player.getLocation();
                if (ploc.distanceSquared(loc) > 0.5) {
                    player.teleport(loc);
                }

                for (int i = 0; i < 4; i++) {
                    double angle = (tick * 0.2 + i * Math.PI / 2) % (2 * Math.PI);
                    double x = loc.getX() + Math.cos(angle) * 1.8;
                    double z = loc.getZ() + Math.sin(angle) * 1.8;
                    Location particleLoc = new Location(loc.getWorld(), x, loc.getY(), z);
                    try {
                        Particle.DustOptions dust = new Particle.DustOptions(Color.AQUA, 0.8f);
                        particleLoc.getWorld().spawnParticle(Particle.REDSTONE, particleLoc, 1, 0, 0, 0, dust);
                    } catch (Exception ignored) {}
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        player.sendMessage(plugin.getConfigManager().getMessageRaw("freeze_on"));
    }

    public void unfreeze(Player player) {
        if (!frozen.contains(player.getUniqueId())) return;
        frozen.remove(player.getUniqueId());
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.sendMessage(plugin.getConfigManager().getMessageRaw("freeze_off"));
    }

    public boolean isFrozen(Player player) {
        return frozen.contains(player.getUniqueId());
    }

    public void unfreezeAll() {
        for (UUID uuid : new HashSet<>(frozen)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) unfreeze(p);
        }
        frozen.clear();
    }
}
