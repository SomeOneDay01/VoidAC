package vac.models;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import vac.VAC;
import vac.punishment.PunishmentManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerData {

    private final VAC plugin;
    private final UUID uuid;
    private String playerName;
    private double confidence;
    private int totalViolations;
    private String clientType;
    private String clientVersion;
    private long lastViolationTime;
    private Map<String, Integer> violations;
    private boolean banned;
    private Map<String, long[]> checkTimestamps;

    public PlayerData(VAC plugin, UUID uuid) {
        this.checkTimestamps = new ConcurrentHashMap<>();
        this.plugin = plugin;
        this.uuid = uuid;
        this.playerName = "";
        this.confidence = 0;
        this.totalViolations = 0;
        this.clientType = "UNKNOWN";
        this.clientVersion = "UNKNOWN";
        this.lastViolationTime = System.currentTimeMillis();
        this.violations = new ConcurrentHashMap<>();
        this.banned = false;
    }

    public PlayerData(VAC plugin, Player player) {
        this(plugin, player.getUniqueId());
        this.playerName = player.getName();
    }

    public boolean addViolation(String checkName, int amount) {
        if (isOnCooldown(checkName)) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Cooldown: " + checkName + " for " + playerName + " - skipped");
            }
            return false;
        }

        violations.merge(checkName, amount, Integer::sum);
        totalViolations += amount;
        lastViolationTime = System.currentTimeMillis();

        double increment = plugin.getConfigManager().getConfidenceIncrement() * amount;
        double multiplier = getViolationMultiplier(checkName);
        increment *= multiplier;

        addConfidence(increment);

        plugin.getEvidenceManager().logViolation(uuid, playerName, checkName, amount, confidence);

        return true;
    }

    private boolean isOnCooldown(String checkName) {
        long now = System.currentTimeMillis();
        long[] timestamps = checkTimestamps.get(checkName);
        long window = 5000;

        if (timestamps == null) {
            checkTimestamps.put(checkName, new long[]{now});
            return false;
        }

        long[] newTimestamps = new long[timestamps.length + 1];
        int count = 0;
        for (long t : timestamps) {
            if (now - t <= window) {
                newTimestamps[count++] = t;
            }
        }
        newTimestamps[count++] = now;

        int spikeWindow = plugin.getConfigManager().getSpikeWindowSeconds() * 1000;
        int spikeCount = 0;
        for (long t : timestamps) {
            if (now - t <= spikeWindow) spikeCount++;
        }

        if (spikeCount > 10) {
            checkTimestamps.put(checkName, Arrays.copyOf(newTimestamps, count));
            return true;
        }

        checkTimestamps.put(checkName, Arrays.copyOf(newTimestamps, count));
        return false;
    }

    private double getViolationMultiplier(String checkName) {
        int count = violations.getOrDefault(checkName, 0);
        double base = count > 10 ? 2.0 : count > 5 ? 1.5 : 1.0;

        long now = System.currentTimeMillis();
        long[] timestamps = checkTimestamps.get(checkName);
        int spikeCount = 0;
        int spikeWindow = plugin.getConfigManager().getSpikeWindowSeconds() * 1000;
        if (timestamps != null) {
            for (long t : timestamps) {
                if (now - t <= spikeWindow) spikeCount++;
            }
        }
        if (spikeCount > 5) {
            base *= plugin.getConfigManager().getSpikeMultiplier();
        }

        return base;
    }

    public void addConfidence(double amount) {
        double max = plugin.getConfigManager().getMaxConfidence();
        this.confidence = Math.min(max, this.confidence + amount);
        updateLastViolationTime();
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Confidence +" + String.format("%.1f", amount) + "% for " + playerName + " (total: " + String.format("%.1f", confidence) + "%)");
        }
    }

    public void updateLastViolationTime() {
        this.lastViolationTime = System.currentTimeMillis();
    }

    public void removeConfidence(double amount) {
        this.confidence = Math.max(0, this.confidence - amount);
    }

    public void setConfidence(double confidence) {
        this.confidence = Math.max(0, Math.min(plugin.getConfigManager().getMaxConfidence(), confidence));
    }

    public void tickConfidenceDecay() {
        if (confidence <= 0) return;
        double minConfidence = plugin.getConfigManager().getConfidenceDecayMin();
        if (confidence <= minConfidence) return;

        long now = System.currentTimeMillis();
        long decayDelay = plugin.getConfigManager().getDecayDelaySeconds() * 1000L;

        if (now - lastViolationTime > decayDelay) {
            double decayPerSecond = plugin.getConfigManager().getConfidenceDecay();
            double interval = plugin.getConfigManager().getCheckIntervalSeconds();
            double decayAmount = decayPerSecond * interval;
            double newConfidence = Math.max(minConfidence, confidence - decayAmount);
            this.confidence = newConfidence;
        }
    }

    public boolean checkBan() {
        if (banned) return false;
        if (confidence >= plugin.getConfigManager().getBanThreshold()) {
            if (plugin.getConfigManager().isAutoBan()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    plugin.getPunishmentManager().banPlayer(player, "VAC", true);
                    return true;
                }
            }
        }
        return false;
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public boolean isOnline() {
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.isOnline();
    }

    public Map<String, Integer> getViolations() {
        return violations;
    }

    public UUID getUuid() { return uuid; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public double getConfidence() { return confidence; }
    public int getTotalViolations() { return totalViolations; }
    public void setTotalViolations(int totalViolations) { this.totalViolations = totalViolations; }
    public String getClientType() { return clientType; }
    public void setClientType(String clientType) { this.clientType = clientType; }
    public String getClientVersion() { return clientVersion; }
    public void setClientVersion(String clientVersion) { this.clientVersion = clientVersion; }
    public long getLastViolationTime() { return lastViolationTime; }
    public boolean isBanned() { return banned; }
    public void setBanned(boolean banned) { this.banned = banned; }
}
