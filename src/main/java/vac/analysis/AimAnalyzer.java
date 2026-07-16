package vac.analysis;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AimAnalyzer {

    private final Map<UUID, AimData> aimDataMap = new ConcurrentHashMap<>();

    private static final int SNAP_HISTORY_SIZE = 50;
    private static final int GRID_CHECK_SIZE = 30;
    private static final double NOISE_WINDOW_MS = 3000;
    private static final double LOCK_ANGLE_THRESHOLD = 0.5;

    public AimData getData(Player player) {
        return aimDataMap.computeIfAbsent(player.getUniqueId(), k -> new AimData());
    }

    public void remove(UUID uuid) {
        aimDataMap.remove(uuid);
    }

    public void recordAimChange(Player player, Location from, Location to) {
        AimData data = getData(player);
        long now = System.currentTimeMillis();

        double yawDiff = Math.abs(to.getYaw() - from.getYaw());
        double pitchDiff = Math.abs(to.getPitch() - from.getPitch());
        double totalDiff = yawDiff + pitchDiff;

        data.aimHistory.add(new double[]{now, yawDiff, pitchDiff, totalDiff});
        if (data.aimHistory.size() > SNAP_HISTORY_SIZE) {
            data.aimHistory.remove(0);
        }

        data.lastYaw = to.getYaw();
        data.lastPitch = to.getPitch();
        data.lastAimTime = now;
    }

    public void recordHit(Player player, Player target, Location attackerLoc, Location targetLoc) {
        AimData data = getData(player);
        long now = System.currentTimeMillis();

        Vector toTarget = targetLoc.toVector().subtract(attackerLoc.toVector()).normalize();
        Vector aimDir = attackerLoc.getDirection();

        double aimAngle = aimDir.angle(toTarget);
        data.lastHitAimAngle = aimAngle;

        Location targetHitbox = getHitboxEdge(targetLoc, aimDir);
        if (targetHitbox != null) {
            Vector toEdge = targetHitbox.toVector().subtract(attackerLoc.toVector()).normalize();
            double edgeAngle = aimDir.angle(toEdge);
            data.snapAngles.add(new double[]{now, Math.abs(edgeAngle), aimAngle});
            if (data.snapAngles.size() > GRID_CHECK_SIZE) data.snapAngles.remove(0);
        }

        if (target != null && hasBlockBetween(attackerLoc, targetLoc)) {
            data.lockOnAngles.add(new double[]{now, aimAngle});
            if (data.lockOnAngles.size() > 20) data.lockOnAngles.remove(0);
        }
    }

    public double getSnapScore(Player player) {
        AimData data = aimDataMap.get(player.getUniqueId());
        if (data == null || data.snapAngles.size() < 3) return 0;

        double perfectSnapCount = 0;
        for (double[] snap : data.snapAngles) {
            if (snap[1] < 0.01) {
                perfectSnapCount++;
            }
        }
        double ratio = perfectSnapCount / data.snapAngles.size();
        return Math.min(100, ratio * 100 * 2);
    }

    public double getGridScore(Player player) {
        AimData data = aimDataMap.get(player.getUniqueId());
        if (data == null || data.aimHistory.size() < 5) return 0;

        int gridHits = 0;
        int checks = 0;
        for (int i = 1; i < data.aimHistory.size(); i++) {
            double prevYaw = data.aimHistory.get(i - 1)[1];
            double currYaw = data.aimHistory.get(i)[1];
            double currPitch = data.aimHistory.get(i)[2];

            if (currYaw > 0.001) {
                checks++;
                double yawMod = currYaw % 0.5;
                double pitchMod = currPitch % 0.5;
                if (Math.abs(yawMod - 0) < 0.001 || Math.abs(yawMod - 0.5) < 0.001 ||
                    Math.abs(pitchMod - 0) < 0.001 || Math.abs(pitchMod - 0.5) < 0.001) {
                    gridHits++;
                }
            }
        }
        if (checks == 0) return 0;
        double gridRatio = (double) gridHits / checks;
        return Math.min(100, gridRatio * 100 * 1.5);
    }

    public double getNoiseScore(Player player) {
        AimData data = aimDataMap.get(player.getUniqueId());
        if (data == null || data.aimHistory.size() < 10) return 0;

        long now = System.currentTimeMillis();
        long cutoff = now - (long) NOISE_WINDOW_MS;

        double[] yawDiffs = data.aimHistory.stream()
                .filter(d -> d[0] >= cutoff)
                .mapToDouble(d -> d[1])
                .toArray();

        if (yawDiffs.length < 10) return 0;

        double stddev = stddev(yawDiffs, mean(yawDiffs));
        if (stddev < 0.01) return Math.min(100, (1.0 - stddev / 0.01) * 100);

        return 0;
    }

    public double getLockOnScore(Player player) {
        AimData data = aimDataMap.get(player.getUniqueId());
        if (data == null || data.lockOnAngles.size() < 3) return 0;

        double consistentCount = 0;
        for (double[] lock : data.lockOnAngles) {
            if (lock[1] < LOCK_ANGLE_THRESHOLD) {
                consistentCount++;
            }
        }
        double lockRatio = consistentCount / data.lockOnAngles.size();
        return Math.min(100, lockRatio * 100 * 2);
    }

    public double getOverallAimScore(Player player) {
        double snap = getSnapScore(player);
        double grid = getGridScore(player);
        double noise = getNoiseScore(player);
        double lock = getLockOnScore(player);

        return Math.max(snap, Math.max(grid, Math.max(noise, lock)));
    }

    public AimStats getStats(Player player) {
        double snap = getSnapScore(player);
        double grid = getGridScore(player);
        double noise = getNoiseScore(player);
        double lock = getLockOnScore(player);
        return new AimStats(snap, grid, noise, lock, getOverallAimScore(player));
    }

    private Location getHitboxEdge(Location targetLoc, Vector aimDir) {
        return targetLoc.clone().add(aimDir.clone().multiply(0.3));
    }

    private boolean hasBlockBetween(Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        double dist = dir.length();
        dir.normalize();
        int steps = (int) (dist * 4);
        for (int i = 1; i < steps; i++) {
            Location check = from.clone().add(dir.clone().multiply(i / 4.0));
            if (check.getBlock().getType().isOccluding()) return true;
        }
        return false;
    }

    private double mean(double[] arr) {
        double sum = 0;
        for (double v : arr) sum += v;
        return arr.length > 0 ? sum / arr.length : 0;
    }

    private double stddev(double[] arr, double mean) {
        double sum = 0;
        for (double v : arr) sum += Math.pow(v - mean, 2);
        return Math.sqrt(sum / arr.length);
    }

    public static class AimData {
        final List<double[]> aimHistory = new ArrayList<>();
        final List<double[]> snapAngles = new ArrayList<>();
        final List<double[]> lockOnAngles = new ArrayList<>();
        double lastYaw;
        double lastPitch;
        long lastAimTime;
        double lastHitAimAngle;
    }

    public static class AimStats {
        public final double snapScore;
        public final double gridScore;
        public final double noiseScore;
        public final double lockOnScore;
        public final double overallScore;

        public AimStats(double snap, double grid, double noise, double lock, double overall) {
            this.snapScore = snap;
            this.gridScore = grid;
            this.noiseScore = noise;
            this.lockOnScore = lock;
            this.overallScore = overall;
        }
    }
}
