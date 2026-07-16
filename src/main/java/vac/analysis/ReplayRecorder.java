package vac.analysis;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import vac.VAC;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ReplayRecorder {

    private final VAC plugin;
    private final Map<UUID, ReplayBuffer> buffers = new ConcurrentHashMap<>();
    private BukkitRunnable captureTask;
    private BukkitRunnable cleanupTask;

    private static final int SNAPSHOT_INTERVAL_TICKS = 2;
    private static final int MAX_SNAPSHOTS = 200;
    private static final long CLEANUP_INTERVAL = 600_000;

    public ReplayRecorder(VAC plugin) {
        this.plugin = plugin;
        start();
    }

    private void start() {
        captureTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ReplayBuffer buf = buffers.computeIfAbsent(player.getUniqueId(),
                            k -> new ReplayBuffer(MAX_SNAPSHOTS));
                    Location loc = player.getLocation();
                    buf.add(new Snapshot(
                        System.currentTimeMillis(),
                        loc.getWorld().getName(),
                        loc.getX(), loc.getY(), loc.getZ(),
                        loc.getYaw(), loc.getPitch(),
                        player.getVelocity().getX(),
                        player.getVelocity().getY(),
                        player.getVelocity().getZ(),
                        player.isSprinting() ? "sprint" :
                        player.isSneaking() ? "sneak" : "walk"
                    ));
                }
            }
        };
        captureTask.runTaskTimer(plugin, 20L, SNAPSHOT_INTERVAL_TICKS);

        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                buffers.entrySet().removeIf(e -> {
                    Player p = plugin.getServer().getPlayer(e.getKey());
                    return p == null || !p.isOnline();
                });
            }
        };
        cleanupTask.runTaskTimer(plugin, 6000L, 6000L);
    }

    public void shutdown() {
        if (captureTask != null) captureTask.cancel();
        if (cleanupTask != null) cleanupTask.cancel();
        buffers.clear();
    }

    public void remove(UUID uuid) {
        buffers.remove(uuid);
    }

    public ReplayBuffer getBuffer(Player player) {
        return buffers.get(player.getUniqueId());
    }

    public void saveReplay(Player player) {
        ReplayBuffer buf = buffers.get(player.getUniqueId());
        if (buf == null || buf.isEmpty()) return;

        File dir = new File(plugin.getDataFolder(), "replays");
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, player.getUniqueId() + "_" + System.currentTimeMillis() + ".json");
        StringBuilder json = new StringBuilder();
        json.append("{\"player\":\"").append(player.getName())
            .append("\",\"uuid\":\"").append(player.getUniqueId())
            .append("\",\"snapshots\":[");
        for (int i = 0; i < buf.snapshots.size(); i++) {
            if (i > 0) json.append(",");
            json.append(buf.snapshots.get(i).toJson());
        }
        json.append("]}");

        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(json.toString());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save replay: " + e.getMessage());
        }
    }

    public void playReplay(Player admin, Player target) {
        ReplayBuffer buf = buffers.get(target.getUniqueId());
        if (buf == null || buf.isEmpty()) {
            admin.sendMessage(plugin.getConfigManager().getMessageRaw("replay_none"));
            return;
        }

        new ReplayPlayback(plugin, admin, buf.snapshots).start();
    }

    public void playReplayFile(Player admin, File file) {
        try {
            StringBuilder content = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) content.append(line);
            }
            List<Snapshot> snapshots = parseSnapshots(content.toString());
            if (snapshots.isEmpty()) {
                admin.sendMessage(plugin.getConfigManager().getMessageRaw("replay_none"));
                return;
            }
            new ReplayPlayback(plugin, admin, snapshots).start();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load replay: " + e.getMessage());
        }
    }

    private List<Snapshot> parseSnapshots(String json) {
        List<Snapshot> result = new ArrayList<>();
        int idx = json.indexOf("\"snapshots\":[");
        if (idx < 0) return result;
        idx = json.indexOf('[', idx);
        int end = json.lastIndexOf(']');
        if (idx < 0 || end < 0) return result;

        String arr = json.substring(idx + 1, end);
        int depth = 0;
        int start = 0;
        for (int i = 0; i < arr.length(); i++) {
            char c = arr.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    result.add(Snapshot.fromJson(arr.substring(start, i + 1)));
                    start = i + 1;
                }
            }
        }
        return result;
    }

    public static class Snapshot {
        final long time;
        final String world;
        final double x, y, z;
        final float yaw, pitch;
        final double vx, vy, vz;
        final String action;

        public Snapshot(long time, String world, double x, double y, double z,
                        float yaw, float pitch, double vx, double vy, double vz, String action) {
            this.time = time;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.action = action;
        }

        String toJson() {
            return "{\"t\":" + time + ",\"w\":\"" + world + "\",\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z
                + ",\"yaw\":" + yaw + ",\"pitch\":" + pitch
                + ",\"vx\":" + vx + ",\"vy\":" + vy + ",\"vz\":" + vz
                + ",\"a\":\"" + action + "\"}";
        }

        static Snapshot fromJson(String json) {
            try {
                long t = getLong(json, "\"t\":");
                String w = getString(json, "\"w\":");
                double x = getDouble(json, "\"x\":");
                double y = getDouble(json, "\"y\":");
                double z = getDouble(json, "\"z\":");
                float yaw = (float) getDouble(json, "\"yaw\":");
                float pitch = (float) getDouble(json, "\"pitch\":");
                double vx = getDouble(json, "\"vx\":");
                double vy = getDouble(json, "\"vy\":");
                double vz = getDouble(json, "\"vz\":");
                String a = getString(json, "\"a\":");
                return new Snapshot(t, w, x, y, z, yaw, pitch, vx, vy, vz, a);
            } catch (Exception e) {
                return null;
            }
        }

        private static long getLong(String json, String key) {
            int idx = json.indexOf(key);
            if (idx < 0) return 0;
            idx += key.length();
            int end = json.indexOf(',', idx);
            if (end < 0) end = json.indexOf('}', idx);
            if (end < 0) return 0;
            return Long.parseLong(json.substring(idx, end).trim());
        }

        private static double getDouble(String json, String key) {
            int idx = json.indexOf(key);
            if (idx < 0) return 0;
            idx += key.length();
            int end = json.indexOf(',', idx);
            if (end < 0) end = json.indexOf('}', idx);
            if (end < 0) return 0;
            return Double.parseDouble(json.substring(idx, end).trim());
        }

        private static String getString(String json, String key) {
            int idx = json.indexOf(key);
            if (idx < 0) return "";
            idx += key.length();
            if (json.charAt(idx) == '"') idx++;
            int end = json.indexOf('"', idx);
            if (end < 0) end = json.indexOf(',', idx);
            if (end < 0) end = json.indexOf('}', idx);
            if (end < 0) return "";
            return json.substring(idx, end).trim();
        }

        boolean isSameLocation(Snapshot other) {
            return Math.abs(x - other.x) < 0.01 && Math.abs(y - other.y) < 0.01 && Math.abs(z - other.z) < 0.01;
        }
    }

    public static class ReplayBuffer {
        final List<Snapshot> snapshots;
        final int maxSize;

        ReplayBuffer(int maxSize) {
            this.snapshots = new ArrayList<>();
            this.maxSize = maxSize;
        }

        void add(Snapshot snapshot) {
            snapshots.add(snapshot);
            while (snapshots.size() > maxSize) {
                snapshots.remove(0);
            }
        }

        boolean isEmpty() {
            return snapshots.isEmpty();
        }

        int size() {
            return snapshots.size();
        }
    }

    private static class ReplayPlayback {
        private final VAC plugin;
        private final Player admin;
        private final List<Snapshot> snapshots;
        private int currentIndex;
        private BukkitRunnable task;

        ReplayPlayback(VAC plugin, Player admin, List<Snapshot> snapshots) {
            this.plugin = plugin;
            this.admin = admin;
            this.snapshots = snapshots;
            this.currentIndex = 0;
        }

        void start() {
            if (snapshots.isEmpty()) return;
            long startTime = snapshots.get(0).time;

            task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (currentIndex >= snapshots.size() || !admin.isOnline()) {
                        cancel();
                        admin.sendMessage(plugin.getConfigManager().getMessageRaw("replay_stopped"));
                        return;
                    }

                    Snapshot snap = snapshots.get(currentIndex);
                    World world = Bukkit.getWorld(snap.world);
                    if (world != null) {
                        Location loc = new Location(world, snap.x, snap.y, snap.z, snap.yaw, snap.pitch);
                        admin.teleport(loc);
                    }

                    long elapsed = snap.time - startTime;
                    int remaining = snapshots.size() - currentIndex;
                    if (remaining > 0 && currentIndex % 10 == 0) {
                        admin.sendMessage("§7Replay: §c" + (elapsed / 1000) + "s §7/ §c"
                                + ((snapshots.get(snapshots.size() - 1).time - startTime) / 1000) + "s");
                    }

                    currentIndex++;
                }
            };
            admin.sendMessage(plugin.getConfigManager().getMessageRaw("replay_started"));
            task.runTaskTimer(plugin, 0L, 2L);
        }

        void stop() {
            if (task != null) task.cancel();
        }
    }
}
