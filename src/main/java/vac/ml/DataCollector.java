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

    public void trainFromSaved() {
        File dir = new File(plugin.getDataFolder(), "datacollect");
        if (!dir.exists()) return;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return;
        for (File file : files) {
            try {
                StringBuilder content = new StringBuilder();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) content.append(line);
                }
                String json = content.toString();
                UUID uuid = extractUuid(json);
                long t = extractLong(json, "\"t\":");
                String label = extractLabel(json);
                if (label == null || label.equals("UNKNOWN")) continue;
                double[] f = extractFeatures(json);
                if (f == null) continue;
                FeatureVector fv = new FeatureVector(uuid, t, f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], label);
                plugin.getOnlineGaussianClassifier().train(fv);
            } catch (Exception ignored) {}
        }
        int total = plugin.getOnlineGaussianClassifier().getTotalSamples();
        plugin.getLogger().info("Trained model from " + total + " saved samples");
    }

    private UUID extractUuid(String json) {
        int idx = json.indexOf("\"uuid\":\"");
        if (idx < 0) return UUID.randomUUID();
        idx += 8;
        int end = json.indexOf('"', idx);
        if (end < 0) return UUID.randomUUID();
        return UUID.fromString(json.substring(idx, end));
    }

    private long extractLong(String json, String key) {
        int idx = json.indexOf(key);
        if (idx < 0) return 0;
        idx += key.length();
        int end = json.indexOf(',', idx);
        if (end < 0) end = json.indexOf('}', idx);
        if (end < 0) return 0;
        return Long.parseLong(json.substring(idx, end).trim());
    }

    private String extractLabel(String json) {
        int idx = json.indexOf("\"l\":\"");
        if (idx < 0) return null;
        idx += 5;
        int end = json.indexOf('"', idx);
        if (end < 0) return null;
        return json.substring(idx, end);
    }

    private double[] extractFeatures(String json) {
        int idx = json.indexOf("\"f\":[");
        if (idx < 0) return null;
        idx += 5;
        int end = json.indexOf(']', idx);
        if (end < 0) return null;
        String[] parts = json.substring(idx, end).split(",");
        double[] f = new double[9];
        for (int i = 0; i < Math.min(parts.length, 9); i++) {
            f[i] = Double.parseDouble(parts[i].trim());
        }
        return f;
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
