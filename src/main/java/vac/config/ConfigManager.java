package vac.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import vac.VAC;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final VAC plugin;
    private FileConfiguration config;

    private Map<String, String> lagDescriptions;

    public ConfigManager(VAC plugin) {
        this.plugin = plugin;
        this.lagDescriptions = new HashMap<>();
    }

    public void loadConfig() {
        this.config = plugin.getConfig();
        loadLagCategories();
    }

    private void loadLagCategories() {
        lagDescriptions.clear();
        ConfigurationSection section = config.getConfigurationSection("lags.categories");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String description = section.getString(key + ".description", "No description");
                lagDescriptions.put(key, description);
            }
        }
    }

    // MySQL
    public boolean isMySQLEnabled() { return config.getBoolean("mysql.enabled", false); }
    public String getMySQLHost() { return config.getString("mysql.host", "localhost"); }
    public int getMySQLPort() { return config.getInt("mysql.port", 3306); }
    public String getMySQLDatabase() { return config.getString("mysql.database", "vac_anticheat"); }
    public String getMySQLUsername() { return config.getString("mysql.username", "root"); }
    public String getMySQLPassword() { return config.getString("mysql.password", ""); }
    public boolean isMySQLUseSSL() { return config.getBoolean("mysql.useSSL", false); }
    public int getMySQLPoolSize() { return config.getInt("mysql.poolSize", 10); }
    public String getMySQLTablePrefix() { return config.getString("mysql.table_prefix", "vac_"); }

    // Confidence
    public double getConfidenceIncrement() { return config.getDouble("confidence.increment_per_violation", 2.0); }
    public double getMaxConfidence() { return config.getDouble("confidence.max_confidence", 100.0); }
    public double getBanThreshold() { return config.getDouble("confidence.ban_threshold", 100.0); }
    public double getConfidenceDecay() { return config.getDouble("confidence.decay_per_second", 0.5); }
    public int getDecayDelaySeconds() { return config.getInt("confidence.decay_delay_seconds", 30); }
    public double getCheckIntervalSeconds() { return config.getDouble("confidence.check_interval_seconds", 3); }
    public double getViolationMultiplier() { return config.getDouble("confidence.violation_multiplier", 1.5); }
    public boolean isAutoBan() { return config.getBoolean("confidence.auto_ban", true); }
    public double getMinConfidenceForManualBan() { return config.getDouble("confidence.min_confidence_for_manual_ban", 0.0); }

    // Animation
    public boolean isAnimationEnabled() { return config.getBoolean("animation.enabled", true); }
    public int getAnimationDuration() { return config.getInt("animation.duration_ticks", 60); }
    public int getAnimationSpiralLoops() { return config.getInt("animation.spiral_loops", 3); }
    public int getAnimationParticlesPerLoop() { return config.getInt("animation.particles_per_loop", 30); }
    public double getAnimationLiftHeight() { return config.getDouble("animation.lift_height", 5.0); }
    public String getAnimationSpiralParticle() { return config.getString("animation.spiral_particle", "FLAME"); }
    public String getAnimationTrailParticle() { return config.getString("animation.trail_particle", "REDSTONE"); }
    public String getAnimationExplosionParticle() { return config.getString("animation.explosion_particle", "EXPLOSION_LARGE"); }
    public String getAnimationShrapnelParticle() { return config.getString("animation.shrapnel_particle", "FIREWORKS_SPARK"); }
    public boolean isDropItems() { return config.getBoolean("animation.drop_items", true); }
    public boolean isDropXP() { return config.getBoolean("animation.drop_xp", true); }
    public boolean isDropArmor() { return config.getBoolean("animation.drop_armor", true); }
    public double getExplosionVolume() { return config.getDouble("animation.explosion_volume", 2.0); }
    public double getExplosionPitch() { return config.getDouble("animation.explosion_pitch", 0.8); }
    public String getBanSound() { return config.getString("animation.ban_sound", "ENTITY_WITHER_DEATH"); }
    public String getExplosionSound() { return config.getString("animation.explosion_sound", "ENTITY_GENERIC_EXPLODE"); }

    // Lags
    public int getDefaultLagDuration() { return config.getInt("lags.default_duration", 15); }
    public int getMaxLagDuration() { return config.getInt("lags.max_duration", 120); }
    public Map<String, String> getLagDescriptions() { return lagDescriptions; }

    public boolean isLagCategoryEnabled(String category) {
        return config.getBoolean("lags.categories." + category + ".enabled", true);
    }

    public double getLagCategoryDurationMultiplier(String category) {
        return config.getDouble("lags.categories." + category + ".duration_multiplier", 1.0);
    }

    public int getLagCategoryPacketDelay(String category) {
        return config.getInt("lags.categories." + category + ".packet_delay_ticks", 40);
    }

    public boolean isLagCategoryCancelMovement(String category) {
        return config.getBoolean("lags.categories." + category + ".cancel_movement", true);
    }

    // Punishment
    public boolean isBroadcastEnabled() { return config.getBoolean("punishment.broadcast", true); }
    public List<String> getPunishmentCommands() { return config.getStringList("punishment.console_commands"); }
    public List<String> getSuspicionCommands() { return config.getStringList("punishment.suspicion_commands"); }
    public List<String> getBanCommands() { return config.getStringList("punishment.ban_commands"); }
    public boolean isBanCommandsOverride() { return config.getBoolean("punishment.ban_commands_override", false); }
    public String getKickMessage() { return config.getString("punishment.kick_message", "§c§lVAC Anti-Cheat\n§7Вы были забанены."); }

    // Messages
    public String getMessage(String key) {
        String prefix = config.getString("messages.prefix", "&8[&cVAC&8] &7");
        String message = config.getString("messages." + key, "&cMessage not found: " + key);
        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }

    public String getMessageRaw(String key) {
        String message = config.getString("messages." + key, "&cMessage not found: " + key);
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    // Report
    public double getReportConfidenceIncrement() { return config.getDouble("report.confidence_increment", 5.0); }

    // Webhook
    public boolean isWebhookEnabled() { return config.getBoolean("webhook.enabled", false); }
    public String getDiscordWebhookUrl() { return config.getString("webhook.discord_url", ""); }
    public boolean isAlertWebhook() { return config.getBoolean("webhook.alert_webhook", false); }
    public boolean isBanWebhook() { return config.getBoolean("webhook.ban_webhook", true); }

    // KillAura
    public double getKaThreshold() { return config.getDouble("killaura.threshold", 60.0); }
    public double getKaConfidenceIncrement() { return config.getDouble("killaura.confidence_increment", 3.0); }
    public double getKaMaxCps() { return config.getDouble("killaura.max_cps", 8.0); }
    public double getKaMaxReach() { return config.getDouble("killaura.max_reach", 3.5); }
    public double getKaMinAimDeviation() { return config.getDouble("killaura.min_aim_deviation", 0.8); }
    public double getKaMaxWallRatio() { return config.getDouble("killaura.max_wall_ratio", 5.0); }

    // Discord Bot
    public boolean isDiscordBotEnabled() { return config.getBoolean("discord.enabled", false); }
    public String getDiscordBotToken() { return config.getString("discord.token", ""); }
    public String getDiscordGuildId() { return config.getString("discord.guild_id", ""); }
    public String getDiscordChannelId() { return config.getString("discord.channel_id", ""); }
    public String getDiscordAllowedRole() { return config.getString("discord.allowed_role", ""); }

    // SQLite
    public boolean isSQLiteEnabled() { return config.getBoolean("sqlite.enabled", true); }

    // Bungee/Velocity
    public boolean isBungeeEnabled() { return config.getBoolean("bungee.enabled", false); }

    // Anti-Xray
    public boolean isXrayEnabled() { return config.getBoolean("antixray.enabled", true); }

    // Auto-Updater
    public boolean isUpdaterEnabled() { return config.getBoolean("updater.enabled", true); }
    public boolean isUpdaterNotify() { return config.getBoolean("updater.notify_on_join", true); }

    // Alerts
    public boolean isAlertEnabled() { return config.getBoolean("alerts.enabled", true); }
    public int[] getAlertThresholds() {
        java.util.List<Integer> list = config.getIntegerList("alerts.thresholds");
        if (list.isEmpty()) return new int[]{30, 50, 70, 90};
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    // Advanced
    public int getUpdateIntervalTicks() { return config.getInt("advanced.update_interval_ticks", 20); }
    public boolean isAsyncLoading() { return config.getBoolean("advanced.async_loading", true); }
    public boolean isDebug() { return config.getBoolean("advanced.debug", false); }
    public boolean isLogToFile() { return config.getBoolean("advanced.log_to_file", true); }
    public int getCacheSize() { return config.getInt("advanced.cache_size", 1000); }
    public int getCacheLifetimeMinutes() { return config.getInt("advanced.cache_lifetime_minutes", 30); }

    public FileConfiguration getConfig() {
        return config;
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadLagCategories();
    }
}
