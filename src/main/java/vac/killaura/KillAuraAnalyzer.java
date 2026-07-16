package vac.killaura;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vac.VAC;
import vac.models.PlayerData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class KillAuraAnalyzer implements Listener {

    private final VAC plugin;
    private final Map<UUID, PlayerHitData> dataMap;

    private static final double REACH_MAX_LEGIT = 3.2;
    private static final double CPS_MAX_LEGIT = 8;
    private static final double AIM_DEVIATION_MIN_LEGIT = 0.5;
    private static final int MIN_HITS_REQUIRED = 30;
    private static final long WINDOW_MS = 3000;

    public KillAuraAnalyzer(VAC plugin) {
        this.plugin = plugin;
        this.dataMap = new ConcurrentHashMap<>();
        startAnalyzerTask();
    }

    public PlayerHitData getData(Player player) {
        return dataMap.computeIfAbsent(player.getUniqueId(), k -> new PlayerHitData());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        Player attacker = (Player) event.getDamager();
        Entity target = event.getEntity();
        Location attackerLoc = attacker.getEyeLocation();
        Location targetLoc = target instanceof Player
                ? ((Player) target).getEyeLocation()
                : target.getLocation().add(0, target.getHeight() / 2, 0);

        PlayerHitData data = getData(attacker);
        long now = System.currentTimeMillis();

        double reach = attackerLoc.distance(targetLoc);
        Vector toTarget = targetLoc.toVector().subtract(attackerLoc.toVector()).normalize();
        double aimDiff = attackerLoc.getDirection().angle(toTarget);
        boolean throughWall = hasBlockBetween(attackerLoc, targetLoc);

        data.addHit(now, reach, aimDiff, throughWall);
    }

    public void recordSwing(Player player) {
        getData(player).recordSwing(System.currentTimeMillis());
    }

    private boolean hasBlockBetween(Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        double dist = dir.length();
        dir.normalize();
        int steps = (int) (dist * 4);
        for (int i = 1; i < steps; i++) {
            Location check = from.clone().add(dir.clone().multiply(i / 4.0));
            if (check.getBlock().getType().isOccluding()) {
                return true;
            }
        }
        return false;
    }

    private void startAnalyzerTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Map.Entry<UUID, PlayerHitData> entry : dataMap.entrySet()) {
                    Player player = plugin.getServer().getPlayer(entry.getKey());
                    if (player == null || !player.isOnline()) continue;

                    PlayerHitData data = entry.getValue();
                    data.purgeOld(now, WINDOW_MS);

                    if (data.getHitCount() < MIN_HITS_REQUIRED) continue;

                    double cpsScore = calcCpsScore(data, now);
                    double reachScore = calcReachScore(data);
                    double aimScore = calcAimScore(data);
                    double wallScore = calcWallScore(data);

                    double totalScore = (cpsScore * 0.25 + reachScore * 0.30 + aimScore * 0.30 + wallScore * 0.15);

                    double threshold = plugin.getConfigManager().getKaThreshold();
                    if (totalScore >= threshold) {
                        PlayerData pd = plugin.getPlayerDataManager().getOrCreate(player);
                        double increment = plugin.getConfigManager().getKaConfidenceIncrement()
                                * (totalScore / 100.0);
                        pd.addConfidence(increment);

                        if (plugin.getConfigManager().isDebug()) {
                            plugin.getLogger().info("KA " + player.getName()
                                    + " score=" + String.format("%.1f", totalScore)
                                    + "% cps=" + String.format("%.1f", cpsScore)
                                    + "% reach=" + String.format("%.1f", reachScore)
                                    + "% aim=" + String.format("%.1f", aimScore)
                                    + "% wall=" + String.format("%.1f", wallScore));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 100L, 40L);
    }

    private double calcCpsScore(PlayerHitData data, long now) {
        long window = 2000;
        int swings = data.getSwingCount(now - window);
        double cps = swings / (window / 1000.0);
        double maxLegit = plugin.getConfigManager().getKaMaxCps();
        if (cps <= maxLegit) return 0;
        if (cps >= maxLegit * 2) return 100;
        return (cps - maxLegit) / maxLegit * 100;
    }

    private double calcReachScore(PlayerHitData data) {
        if (data.getHitCount() < 10) return 0;
        double avg = data.getAverageReach();
        double maxLegit = plugin.getConfigManager().getKaMaxReach();
        if (avg <= maxLegit) return 0;
        if (avg >= maxLegit + 1.5) return 100;
        return (avg - maxLegit) / 1.5 * 100;
    }

    private double calcAimScore(PlayerHitData data) {
        if (data.getHitCount() < 10) return 0;
        double avgDev = data.getAverageAimDeviation();
        double minLegit = plugin.getConfigManager().getKaMinAimDeviation();
        if (avgDev >= minLegit) return 0;
        if (avgDev <= 0.01) return 100;
        return (1.0 - avgDev / minLegit) * 100;
    }

    private double calcWallScore(PlayerHitData data) {
        int hits = data.getHitCount();
        if (hits < 10) return 0;
        int walls = data.getThroughWallCount();
        double ratio = (double) walls / hits * 100;
        double maxRatio = plugin.getConfigManager().getKaMaxWallRatio();
        if (ratio <= maxRatio) return 0;
        if (ratio >= 100) return 100;
        return (ratio - maxRatio) / (100 - maxRatio) * 100;
    }

    // Stats for commands
    public double getCurrentCPS(Player player) {
        PlayerHitData data = dataMap.get(player.getUniqueId());
        if (data == null) return 0;
        long now = System.currentTimeMillis();
        int swings = data.getSwingCount(now - 2000);
        return swings / 2.0;
    }

    public double getAverageReach(Player player) {
        PlayerHitData data = dataMap.get(player.getUniqueId());
        if (data == null || data.getHitCount() == 0) return 0;
        return data.getAverageReach();
    }

    public HitStats getStats(Player player) {
        PlayerHitData data = dataMap.get(player.getUniqueId());
        if (data == null) return new HitStats(0, 0, 0, 0, 0, 0);
        long now = System.currentTimeMillis();
        data.purgeOld(now, WINDOW_MS);
        return new HitStats(
            data.getHitCount(),
            data.getSwingCount(now - 2000),
            getCurrentCPS(player),
            data.getAverageReach(),
            data.getAverageAimDeviation(),
            data.getThroughWallCount()
        );
    }

    public void remove(UUID uuid) {
        dataMap.remove(uuid);
    }

    public static class HitStats {
        public final int totalHits;
        public final int swings2s;
        public final double cps;
        public final double avgReach;
        public final double avgAimDev;
        public final int wallHits;

        public HitStats(int totalHits, int swings2s, double cps, double avgReach, double avgAimDev, int wallHits) {
            this.totalHits = totalHits;
            this.swings2s = swings2s;
            this.cps = cps;
            this.avgReach = avgReach;
            this.avgAimDev = avgAimDev;
            this.wallHits = wallHits;
        }
    }
}
