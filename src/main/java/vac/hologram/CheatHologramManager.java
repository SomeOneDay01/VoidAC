package vac.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import vac.VAC;
import vac.ml.FeatureExtractor;
import vac.ml.FeatureVector;
import vac.ml.OnlineGaussianClassifier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CheatHologramManager {

    private final VAC plugin;
    private final OnlineGaussianClassifier classifier;
    private final FeatureExtractor extractor;
    private final Map<UUID, HoloData> holograms = new ConcurrentHashMap<>();
    private final Set<UUID> enabledPlayers = new HashSet<>();
    private BukkitRunnable updateTask;

    private static final double DECAY_AFTER_MS = 600_000;
    private static final double DECAY_RATE = 0.5;

    public CheatHologramManager(VAC plugin, OnlineGaussianClassifier classifier, FeatureExtractor extractor) {
        this.plugin = plugin;
        this.classifier = classifier;
        this.extractor = extractor;
    }

    public void start() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!enabledPlayers.contains(player.getUniqueId())) continue;
                    updateHologram(player, now);
                }
            }
        };
        updateTask.runTaskTimer(plugin, 40L, 20L);
    }

    private void updateHologram(Player player, long now) {
        HoloData holo = holograms.get(player.getUniqueId());
        if (holo == null) {
            holo = new HoloData();
            holograms.put(player.getUniqueId(), holo);
        }

        if (!classifier.isReady()) {
            setHoloText(player, holo, "§7VAC: §etype...");
            return;
        }

        double prob = classifier.predict(extractor.extract(player, null));

        if (holo.lastActive == 0) holo.lastActive = now;
        if (prob > 0.05) holo.lastActive = now;

        double displayed;
        if (now - holo.lastActive > DECAY_AFTER_MS) {
            double elapsed = (now - holo.lastActive - DECAY_AFTER_MS) / 1000.0;
            displayed = Math.max(0, prob - elapsed * DECAY_RATE);
        } else {
            displayed = prob;
        }

        holo.lastProb = displayed;

        String color;
        if (displayed > 0.7) color = "§c";
        else if (displayed > 0.3) color = "§e";
        else color = "§a";

        setHoloText(player, holo, color + "VAC: §f" + String.format("%.1f", displayed * 100) + "%");
        holo.updateLocation(player);
    }

    private void setHoloText(Player target, HoloData holo, String text) {
        if (holo.armorStand == null) {
            holo.armorStand = (ArmorStand) target.getWorld().spawnEntity(
                    target.getLocation().add(0, 2.5, 0), EntityType.ARMOR_STAND);
            holo.armorStand.setMarker(true);
            holo.armorStand.setVisible(false);
            holo.armorStand.setSmall(true);
            holo.armorStand.setGravity(false);
            holo.armorStand.setCustomNameVisible(true);
        }
        holo.armorStand.setCustomName(text);
    }

    public void setEnabled(Player player, boolean enabled) {
        if (enabled) {
            enabledPlayers.add(player.getUniqueId());
        } else {
            enabledPlayers.remove(player.getUniqueId());
            removeHologram(player.getUniqueId());
        }
    }

    public boolean isEnabled(Player player) {
        return enabledPlayers.contains(player.getUniqueId());
    }

    public void removeHologram(UUID uuid) {
        HoloData holo = holograms.remove(uuid);
        if (holo != null && holo.armorStand != null) {
            holo.armorStand.remove();
        }
    }

    public void removeAll() {
        for (HoloData holo : holograms.values()) {
            if (holo.armorStand != null) holo.armorStand.remove();
        }
        holograms.clear();
    }

    public void shutdown() {
        if (updateTask != null) updateTask.cancel();
        removeAll();
    }

    private static class HoloData {
        ArmorStand armorStand;
        double lastProb;
        long lastActive;

        void updateLocation(Player player) {
            if (armorStand != null && player.isOnline()) {
                Location loc = player.getLocation().add(0, 2.5, 0);
                armorStand.teleport(loc);
            }
        }
    }
}
