package vac.ml;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import vac.VAC;
import vac.analysis.AimAnalyzer;
import vac.analysis.PlayerProfiler;
import vac.killaura.KillAuraAnalyzer;

import java.util.List;

public class FeatureExtractor {

    private final VAC plugin;

    public FeatureExtractor(VAC plugin) {
        this.plugin = plugin;
    }

    public FeatureVector extract(Player player, String label) {
        PlayerProfiler profiler = plugin.getPlayerProfiler();
        KillAuraAnalyzer ka = plugin.getKillAuraAnalyzer();
        AimAnalyzer aim = plugin.getAimAnalyzer();

        long now = System.currentTimeMillis();
        KillAuraAnalyzer.HitStats stats = ka.getStats(player);
        Location loc = player.getLocation();

        double cps = stats.cps;
        double reach = stats.avgReach;
        double aimDev = stats.avgAimDev;

        PlayerProfiler.ProfileData pd = profiler != null ? profiler.getOrCreate(player) : null;
        double yawDelta = 0;
        double pitchDelta = 0;
        double speed = 0;
        double jumpRate = 0;

        if (pd != null) {
            double yawAvg = pd.getRecentYawAverage();
            double pitchAvg = pd.getRecentPitchAverage();
            yawDelta = pd.baselinesComputed && pd.baseYawDelta > 0
                    ? Math.abs(yawAvg - pd.baseYawDelta) / Math.max(pd.yawDeltaStddev, 0.1)
                    : yawAvg;
            pitchDelta = pd.baselinesComputed && pd.basePitchDelta > 0
                    ? Math.abs(pitchAvg - pd.basePitchDelta) / Math.max(pd.pitchDeltaStddev, 0.1)
                    : pitchAvg;

            double jumpCount = pd.getJumpTimes().size();
            jumpRate = jumpCount / 60.0;
        }

        if (player.isOnline()) {
            long flightTicks = player.getTicksLived();
            Location from = player.getLocation();
            if (from != null) {
                double dx = from.getX() - (player.getVelocity().getX() * 20);
                double dz = from.getZ() - (player.getVelocity().getZ() * 20);
                speed = Math.sqrt(dx * dx + dz * dz);
            }
        }

        double wallRatio = stats.totalHits > 0 ? (double) stats.wallHits / stats.totalHits : 0;

        double aimAngleStddev = 0;
        if (aim != null) {
            AimAnalyzer.AimStats aimStats = aim.getStats(player);
            double snap = aimStats.snapScore;
            double grid = aimStats.gridScore;
            double lock = aimStats.lockOnScore;
            aimAngleStddev = (snap + grid + lock) / 3.0;
        }

        return new FeatureVector(
            player.getUniqueId(), now,
            clamp(cps, 0, 50), clamp(reach, 0, 10),
            clamp(aimDev, 0, 5), clamp(yawDelta, 0, 50),
            clamp(pitchDelta, 0, 50), clamp(wallRatio, 0, 1),
            clamp(aimAngleStddev, 0, 100), clamp(speed, 0, 20),
            clamp(jumpRate, 0, 5), label
        );
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
}
