package vac.analysis;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import vac.VAC;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerProfiler implements Listener {

    private final VAC plugin;
    private final Map<UUID, ProfileData> profiles = new ConcurrentHashMap<>();

    private static final long BASELINE_WARMUP_MS = 300_000;
    private static final int MIN_SAMPLES = 20;
    private static final double ANOMALY_STDDEV = 3.0;
    private static final int PREDICTION_HISTORY = 3;

    public PlayerProfiler(VAC plugin) {
        this.plugin = plugin;
    }

    public ProfileData getOrCreate(Player player) {
        return profiles.computeIfAbsent(player.getUniqueId(), k -> new ProfileData());
    }

    public void remove(UUID uuid) {
        profiles.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        ProfileData pd = getOrCreate(player);
        Location from = event.getFrom();
        Location to = event.getTo();
        long now = System.currentTimeMillis();

        double yawDelta = Math.abs(to.getYaw() - from.getYaw());
        if (yawDelta > 0.001) {
            pd.yawDeltas.add(new double[]{now, yawDelta});
            purgeOld(pd.yawDeltas, now - 60000);
        }

        double pitchDelta = Math.abs(to.getPitch() - from.getPitch());
        if (pitchDelta > 0.001) {
            pd.pitchDeltas.add(new double[]{now, pitchDelta});
            purgeOld(pd.pitchDeltas, now - 60000);
        }

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double speed = Math.sqrt(dx * dx + dz * dz);
        if (speed > 0.001) {
            pd.velocities.add(new double[]{now, dx, dz});
            purgeOld(pd.velocities, now - 5000);
        }

        pd.updatePrediction(from, to, now);
        pd.tryComputeBaselines(now);
    }

    public void recordHit(Player player, double reach, double aimDev) {
        ProfileData pd = getOrCreate(player);
        long now = System.currentTimeMillis();
        pd.hitData.add(new double[]{now, reach, aimDev});
        purgeOld(pd.hitData, now - 60000);
    }

    public void recordSwing(Player player) {
        ProfileData pd = getOrCreate(player);
        long now = System.currentTimeMillis();
        pd.swingTimes.add(now);
        purgeOldLong(pd.swingTimes, now - 2000);
    }

    public void recordJump(Player player) {
        ProfileData pd = getOrCreate(player);
        pd.jumpTimes.add(System.currentTimeMillis());
        purgeOldLong(pd.jumpTimes, System.currentTimeMillis() - 60000);
    }

    private void purgeOld(List<double[]> list, long cutoff) {
        list.removeIf(d -> d[0] < cutoff);
    }

    private void purgeOldLong(List<Long> list, long cutoff) {
        list.removeIf(t -> t < cutoff);
    }

    public double getCpsAnomalyScore(Player player) {
        ProfileData pd = profiles.get(player.getUniqueId());
        if (pd == null || !pd.baselinesComputed) return 0;
        double current = pd.getCurrentCps();
        if (current <= pd.baseCps) return 0;
        double diff = current - pd.baseCps;
        double threshold = Math.max(pd.cpsStddev * ANOMALY_STDDEV, 1.0);
        return Math.min(100, diff / threshold * 100);
    }

    public double getReachAnomalyScore(Player player) {
        ProfileData pd = profiles.get(player.getUniqueId());
        if (pd == null || !pd.baselinesComputed) return 0;
        double avg = pd.getRecentReachAverage();
        if (avg <= pd.baseReach) return 0;
        double diff = avg - pd.baseReach;
        double threshold = Math.max(pd.reachStddev * ANOMALY_STDDEV, 0.3);
        return Math.min(100, diff / threshold * 100);
    }

    public double getAimAnomalyScore(Player player) {
        ProfileData pd = profiles.get(player.getUniqueId());
        if (pd == null || !pd.baselinesComputed) return 0;
        double avg = pd.getRecentAimAverage();
        if (avg >= pd.baseAimDev) return 0;
        double diff = pd.baseAimDev - avg;
        double threshold = Math.max(pd.aimDevStddev * ANOMALY_STDDEV, 0.2);
        return Math.min(100, diff / threshold * 100);
    }

    public double getTrajectoryDeviation(Player player) {
        ProfileData pd = profiles.get(player.getUniqueId());
        if (pd == null || pd.predictionHistory.size() < PREDICTION_HISTORY) return 0;
        return pd.lastTrajectoryDeviation;
    }

    public double getYawAnomalyScore(Player player) {
        ProfileData pd = profiles.get(player.getUniqueId());
        if (pd == null || !pd.baselinesComputed) return 0;
        double avg = pd.getRecentYawAverage();
        if (avg <= pd.baseYawDelta) return 0;
        double diff = avg - pd.baseYawDelta;
        double threshold = Math.max(pd.yawDeltaStddev * ANOMALY_STDDEV, 1.0);
        return Math.min(100, diff / threshold * 100);
    }

    public double getPitchAnomalyScore(Player player) {
        ProfileData pd = profiles.get(player.getUniqueId());
        if (pd == null || !pd.baselinesComputed) return 0;
        double avg = pd.getRecentPitchAverage();
        if (avg <= pd.basePitchDelta) return 0;
        double diff = avg - pd.basePitchDelta;
        double threshold = Math.max(pd.pitchDeltaStddev * ANOMALY_STDDEV, 1.0);
        return Math.min(100, diff / threshold * 100);
    }

    public void cleanup() {
        profiles.entrySet().removeIf(e -> {
            Player p = plugin.getServer().getPlayer(e.getKey());
            return p == null || !p.isOnline();
        });
    }

    public static class ProfileData {
        final List<double[]> yawDeltas = new ArrayList<>();
        final List<double[]> pitchDeltas = new ArrayList<>();
        final List<double[]> velocities = new ArrayList<>();
        final List<double[]> hitData = new ArrayList<>();
        final List<Long> swingTimes = new ArrayList<>();
        final List<Long> jumpTimes = new ArrayList<>();

        double baseCps;
        double cpsStddev;
        double baseReach;
        double reachStddev;
        double baseAimDev;
        double aimDevStddev;
        double baseYawDelta;
        double yawDeltaStddev;
        double basePitchDelta;
        double pitchDeltaStddev;
        boolean baselinesComputed;
        long baselineStartTime;
        long lastBaselineCompute;

        final List<double[]> predictionHistory = new ArrayList<>();
        double lastTrajectoryDeviation;

        double getCurrentCps() {
            long now = System.currentTimeMillis();
            swingTimes.removeIf(t -> t < now - 2000);
            return swingTimes.size() / 2.0;
        }

        double getRecentReachAverage() {
            if (hitData.isEmpty()) return 0;
            int start = Math.max(0, hitData.size() - 10);
            double sum = 0;
            int count = 0;
            for (int i = start; i < hitData.size(); i++) {
                sum += hitData.get(i)[1];
                count++;
            }
            return count > 0 ? sum / count : 0;
        }

        double getRecentAimAverage() {
            if (hitData.isEmpty()) return 0;
            int start = Math.max(0, hitData.size() - 10);
            double sum = 0;
            int count = 0;
            for (int i = start; i < hitData.size(); i++) {
                sum += hitData.get(i)[2];
                count++;
            }
            return count > 0 ? sum / count : 0;
        }

        double getRecentYawAverage() {
            if (yawDeltas.isEmpty()) return 0;
            int start = Math.max(0, yawDeltas.size() - 20);
            double sum = 0;
            int count = 0;
            for (int i = start; i < yawDeltas.size(); i++) {
                sum += yawDeltas.get(i)[1];
                count++;
            }
            return count > 0 ? sum / count : 0;
        }

        double getRecentPitchAverage() {
            if (pitchDeltas.isEmpty()) return 0;
            int start = Math.max(0, pitchDeltas.size() - 20);
            double sum = 0;
            int count = 0;
            for (int i = start; i < pitchDeltas.size(); i++) {
                sum += pitchDeltas.get(i)[1];
                count++;
            }
            return count > 0 ? sum / count : 0;
        }

        void updatePrediction(Location from, Location to, long now) {
            predictionHistory.add(new double[]{to.getX(), to.getY(), to.getZ(), now});
            if (predictionHistory.size() > 5) predictionHistory.remove(0);

            if (predictionHistory.size() >= PREDICTION_HISTORY) {
                int n = predictionHistory.size();
                double[] prev3 = predictionHistory.get(n - 3);
                double[] prev2 = predictionHistory.get(n - 2);
                double[] prev1 = predictionHistory.get(n - 1);

                double dt1 = prev2[3] - prev3[3];
                double dt2 = prev1[3] - prev2[3];
                double dt3 = now - prev1[3];

                if (dt1 > 0 && dt2 > 0 && dt3 > 0) {
                    double vx = (prev1[0] - prev2[0]) / (dt2 / 1000.0);
                    double vy = (prev1[1] - prev2[1]) / (dt2 / 1000.0);
                    double vz = (prev1[2] - prev2[2]) / (dt2 / 1000.0);

                    double px = prev1[0] + vx * (dt3 / 1000.0);
                    double py = prev1[1] + vy * (dt3 / 1000.0);
                    double pz = prev1[2] + vz * (dt3 / 1000.0);

                    double deviation = Math.sqrt(
                        Math.pow(px - to.getX(), 2) +
                        Math.pow(py - to.getY(), 2) +
                        Math.pow(pz - to.getZ(), 2)
                    );
                    lastTrajectoryDeviation = deviation;
                }
            }
        }

        void tryComputeBaselines(long now) {
            if (baselinesComputed) return;
            if (baselineStartTime == 0) {
                baselineStartTime = now;
                return;
            }
            if (now - baselineStartTime < BASELINE_WARMUP_MS) return;
            if (now - lastBaselineCompute < 10000) return;
            lastBaselineCompute = now;

            if (hitData.size() >= MIN_SAMPLES) {
                double[] reaches = new double[hitData.size()];
                double[] aims = new double[hitData.size()];
                for (int i = 0; i < hitData.size(); i++) {
                    reaches[i] = hitData.get(i)[1];
                    aims[i] = hitData.get(i)[2];
                }
                baseReach = mean(reaches);
                reachStddev = stddev(reaches, baseReach);
                baseAimDev = mean(aims);
                aimDevStddev = stddev(aims, baseAimDev);
            }

            if (yawDeltas.size() >= MIN_SAMPLES) {
                double[] yaws = new double[yawDeltas.size()];
                for (int i = 0; i < yawDeltas.size(); i++) yaws[i] = yawDeltas.get(i)[1];
                baseYawDelta = mean(yaws);
                yawDeltaStddev = stddev(yaws, baseYawDelta);
            }

            if (pitchDeltas.size() >= MIN_SAMPLES) {
                double[] pitches = new double[pitchDeltas.size()];
                for (int i = 0; i < pitchDeltas.size(); i++) pitches[i] = pitchDeltas.get(i)[1];
                basePitchDelta = mean(pitches);
                pitchDeltaStddev = stddev(pitches, basePitchDelta);
            }

            swingTimes.removeIf(t -> t < now - 120000);
            if (swingTimes.size() >= 100) {
                double currentCps = getCurrentCps();
                baseCps = Math.max(1, currentCps * 0.8);
                cpsStddev = 2.0;
            }

            baselinesComputed = true;
        }

        private double mean(double[] arr) {
            double sum = 0;
            for (double v : arr) sum += v;
            return sum / arr.length;
        }

        private double stddev(double[] arr, double mean) {
            double sum = 0;
            for (double v : arr) sum += Math.pow(v - mean, 2);
            return Math.sqrt(sum / arr.length);
        }
    }
}
