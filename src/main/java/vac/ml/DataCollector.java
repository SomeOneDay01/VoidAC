package vac.ml;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import vac.VAC;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DataCollector {

    private final VAC plugin;
    private final FeatureExtractor extractor;
    private final Map<UUID, CollectionSession> activeSessions = new ConcurrentHashMap<>();
    private final List<FeatureVector> pendingSamples = new ArrayList<>();
    private BukkitRunnable collectTask;

    private static final int COLLECT_INTERVAL_TICKS = 20;

    public DataCollector(VAC plugin, FeatureExtractor extractor) {
        this.plugin = plugin;
        this.extractor = extractor;
    }

    public void start() {
        collectTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Map.Entry<UUID, CollectionSession> e : activeSessions.entrySet()) {
                    Player player = plugin.getServer().getPlayer(e.getKey());
                    if (player == null || !player.isOnline()) continue;
                    CollectionSession session = e.getValue();
                    if (now - session.lastCollect < 1000) continue;
                    session.lastCollect = now;
                    FeatureVector fv = extractor.extract(player, session.label);
                    synchronized (pendingSamples) {
                        pendingSamples.add(fv);
                    }
                }
            }
        };
        collectTask.runTaskTimer(plugin, 60L, COLLECT_INTERVAL_TICKS);

        saveTask();
    }

    private void saveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                List<FeatureVector> toSave;
                synchronized (pendingSamples) {
                    if (pendingSamples.isEmpty()) return;
                    toSave = new ArrayList<>(pendingSamples);
                    pendingSamples.clear();
                }
                saveBatch(toSave);
            }
        }.runTaskTimerAsynchronously(plugin, 200L, 200L);
    }

    private void saveBatch(List<FeatureVector> samples) {
        File dir = new File(plugin.getDataFolder(), "datacollect");
        if (!dir.exists()) dir.mkdirs();
        for (FeatureVector fv : samples) {
            String filename = fv.playerUuid + "_" + fv.timestamp + "_" + fv.label + ".json";
            File file = new File(dir, filename);
            try (OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8)) {
                w.write(fv.toJson());
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save feature vector: " + e.getMessage());
            }
        }
    }

    public boolean startCollecting(Player player, String label) {
        UUID uuid = player.getUniqueId();
        if (activeSessions.containsKey(uuid)) return false;
        String normalized = label.equalsIgnoreCase("LEGIT") ? "LEGIT" : "CHEAT";
        activeSessions.put(uuid, new CollectionSession(normalized));
        return true;
    }

    public boolean stopCollecting(Player player) {
        UUID uuid = player.getUniqueId();
        CollectionSession session = activeSessions.remove(uuid);
        return session != null;
    }

    public boolean isCollecting(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public String getCollectionLabel(Player player) {
        CollectionSession session = activeSessions.get(player.getUniqueId());
        return session != null ? session.label : null;
    }

    public int getActiveSessions() {
        return activeSessions.size();
    }

    public int getPendingSamples() {
        synchronized (pendingSamples) {
            return pendingSamples.size();
        }
    }

    public void shutdown() {
        if (collectTask != null) collectTask.cancel();
        synchronized (pendingSamples) {
            if (!pendingSamples.isEmpty()) {
                saveBatch(new ArrayList<>(pendingSamples));
                pendingSamples.clear();
            }
        }
        activeSessions.clear();
    }

    private static class CollectionSession {
        final String label;
        long lastCollect;

        CollectionSession(String label) {
            this.label = label;
            this.lastCollect = 0;
        }
    }
}
