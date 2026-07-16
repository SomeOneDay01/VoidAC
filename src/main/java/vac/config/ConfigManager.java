package vac.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import vac.VAC;
import vac.util.ColorUtils;

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
        ensureDefaultMessages();
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

    private void ensureDefaultMessages() {
        boolean changed = false;

        if (config.getString("language") == null) {
            config.set("language", "en");
            changed = true;
        }

        setDefault("messages.en.prefix", "&8[&cVAC&8] &7");
        setDefault("messages.en.no_permission", "&cYou don't have permission.");
        setDefault("messages.en.player_not_found", "&cPlayer not found.");
        setDefault("messages.en.player_not_online", "&cPlayer is not online.");
        setDefault("messages.en.only_players", "&cOnly players can use this command.");
        setDefault("messages.en.reload_success", "&aConfiguration reloaded.");
        setDefault("messages.en.reload_fail", "&cFailed to reload configuration.");
        setDefault("messages.en.banned", "&c{player} &7was banned by &cVAC&7!");
        setDefault("messages.en.banned_animation", "&c{player} &7was banned with animation by &cVAC&7!");
        setDefault("messages.en.ban_failed", "&cFailed to ban player &c{player}&7.");
        setDefault("messages.en.confidence_set", "&7Confidence for &c{player} &7set to &c{confidence}%&7.");
        setDefault("messages.en.confidence_add", "&7Confidence for &c{player} &7increased by &c{amount}%&7. Current: &c{confidence}%");
        setDefault("messages.en.confidence_remove", "&7Confidence for &c{player} &7decreased by &c{amount}%&7. Current: &c{confidence}%");
        setDefault("messages.en.confidence_info", "&7Confidence for &c{player}&7: &c{confidence}%");
        setDefault("messages.en.confidence_reset", "&7Confidence for &c{player} &7reset.");
        setDefault("messages.en.stats_header", "&c&lVAC Statistics");
        setDefault("messages.en.stats_players_tracked", " &8▶ &7Players tracked: &c{count}");
        setDefault("messages.en.stats_checks_total", " &8▶ &7Total checks: &c{count}");
        setDefault("messages.en.stats_banned", " &8▶ &7Banned: &c{count}");
        setDefault("messages.en.stats_reports", " &8▶ &7Reports: &c{count}");
        setDefault("messages.en.usage", "&cUsage: &7{usage}");
        setDefault("messages.en.unknown_command", "&cUnknown subcommand. Use &7/vac help");
        setDefault("messages.en.help_header", "&8&m----&8 [ &cVAC Help &8] &m----");
        setDefault("messages.en.help_ban", " &8- &7/vac ban <player> &c- ban with animation");
        setDefault("messages.en.help_profile", " &8- &7/vac profile <player> &c- player profile");
        setDefault("messages.en.help_lags", " &8- &7/vac lags <player> <category|stop> [time] &c- lag management");
        setDefault("messages.en.help_confidence", " &8- &7/vac confidence <player> <set|add|remove|reset|info> [value] &c- confidence management");
        setDefault("messages.en.help_reload", " &8- &7/vac reload &c- reload config");
        setDefault("messages.en.help_crash", " &8- &7/vac crash <player> [method] &c- crash client");
        setDefault("messages.en.help_alerts", " &8- &7/vac alerts [on|off] &c- alerts");
        setDefault("messages.en.help_spectate", " &8- &7/vac spectate <player|stop> &c- spectate");
        setDefault("messages.en.help_report", " &8- &7/vac report <player> <reason> &c- report");
        setDefault("messages.en.help_check", " &8- &7/vac check <player> &c- run check");
        setDefault("messages.en.help_checkvpn", " &8- &7/vac checkvpn <player> &c- VPN check");
        setDefault("messages.en.help_history", " &8- &7/vac history <player> &c- history");
        setDefault("messages.en.help_freeze", " &8- &7/vac freeze <player> [off] &c- freeze");
        setDefault("messages.en.help_cps", " &8- &7/vac cps <player> &c- CPS stats");
        setDefault("messages.en.help_reach", " &8- &7/vac reach <player> &c- reach stats");
        setDefault("messages.en.help_hits", " &8- &7/vac hits <player> &c- detailed hit stats");
        setDefault("messages.en.help_settings", " &8- &7/vac settings &c- settings GUI");
        setDefault("messages.en.help_footer", "&8&m--------------------------------------------");
        setDefault("messages.en.profile_header", "&8&m----&8 [ &cVAC Profile &8] &m----");
        setDefault("messages.en.profile_footer", "&8&m--------------------------------------------");
        setDefault("messages.en.profile_confidence", " &8▶ &7Confidence: &c{confidence}%");
        setDefault("messages.en.profile_health", " &8▶ &7Health: &c{health}&7/&c{max_health}");
        setDefault("messages.en.profile_food", " &8▶ &7Food: &c{food}");
        setDefault("messages.en.profile_ping", " &8▶ &7Ping: &c{ping}ms");
        setDefault("messages.en.profile_location", " &8▶ &7Location: &c{world} &7({x}, {y}, {z})");
        setDefault("messages.en.profile_checks", " &8▶ &7Checks (&c{total_violations}&7):");
        setDefault("messages.en.profile_check_line", "   &8- &c{check}&7: &c{violations} &7violations (confidence: &c{check_confidence}%&7)");
        setDefault("messages.en.profile_no_checks", "   &8- &7No violations");
        setDefault("messages.en.profile_grim_version", " &8▶ &7GrimAC &8| &7Version: &c{version}");
        setDefault("messages.en.lags_started", "&7Lag for &c{player} &7started. Category: &c{category}");
        setDefault("messages.en.lags_stopped", "&7Lag for &c{player} &7stopped.");
        setDefault("messages.en.lags_already", "&cPlayer &c{player} &calready has active lag.");
        setDefault("messages.en.lags_not_active", "&cPlayer &c{player} &chas no active lag.");
        setDefault("messages.en.lags_invalid_category", "&cInvalid category. Available: {categories}");
        setDefault("messages.en.lags_category_usage", "&7Usage: &c/vac lags <player> [category]");
        setDefault("messages.en.crash_started", "&7Crashing &c{player}&7. Method: &c{method}");
        setDefault("messages.en.crash_failed", "&cFailed to crash &c{player}&7.");
        setDefault("messages.en.crash_methods", "&7Available methods: &c{methods}");
        setDefault("messages.en.spectate_started", "&7Spectating &c{player}&7. Confidence in action bar.");
        setDefault("messages.en.spectate_stopped", "&7Spectating stopped.");
        setDefault("messages.en.report_sent", "&aReport for &c{player} &asent. Reason: &f{reason}");
        setDefault("messages.en.check_started", "&8[&cVAC&8] &7Checking &c{player}&7...");
        setDefault("messages.en.check_complete", "&8[&cVAC&8] &7Check for &c{player} &7complete. Confidence: &c{confidence}%");
        setDefault("messages.en.freeze_on", "&cYou are frozen! Wait for staff.");
        setDefault("messages.en.freeze_off", "&aYou are unfrozen.");
        setDefault("messages.en.vpn_checking", "&7Checking VPN for &c{player} &7(IP: {ip})...");
        setDefault("messages.en.vpn_result", "&7VPN for &c{player} &7(IP: {ip}): &f{result}");
        setDefault("messages.en.history_header", "&8&m----&8 [ &cHistory {player} &8] &m----");
        setDefault("messages.en.history_line", " &8- &c{check} &7x{violations} &7| &c{confidence}% &7| &f{time}");
        setDefault("messages.en.history_footer", "&8&m--------------------------------------------");
        setDefault("messages.en.history_empty", "&7No violations in history.");
        setDefault("messages.en.alerts_enabled", "&aVAC alerts enabled");
        setDefault("messages.en.alerts_disabled", "&cVAC alerts disabled");
        setDefault("messages.en.alert_message", "&8[&cVAC&8] &c{player} &7may be cheating! (&c{check}&7, confidence: &c{confidence}%&7)");
        setDefault("messages.en.ka_header", "&8&m----&8 [ &cKillAura &7{player} &8] &m----");
        setDefault("messages.en.ka_cps", " &8▶ &7CPS: &c{cps} &7(swings: {swings})");
        setDefault("messages.en.ka_reach", " &8▶ &7Reach: &c{reach} &7(hits: {hits})");
        setDefault("messages.en.ka_aim", " &8▶ &7Aim Deviation: &c{aim}");
        setDefault("messages.en.ka_wall", " &8▶ &7Wall hits: &c{walls}&7/{hits}");
        setDefault("messages.en.webhook_ban", "**[VAC] Ban**\nPlayer: {player}\nUUID: {uuid}\nConfidence: {confidence}%\nBanned by: {banned_by}");
        setDefault("messages.en.webhook_alert", "**[VAC] Alert**\nPlayer: {player}\nCheck: {check}\nConfidence: {confidence}%");

        setDefault("messages.ru.prefix", "&8[&cVAC&8] &7");
        setDefault("messages.ru.no_permission", "&cУ вас нет прав.");
        setDefault("messages.ru.player_not_found", "&cИгрок не найден.");
        setDefault("messages.ru.player_not_online", "&cИгрок не онлайн.");
        setDefault("messages.ru.only_players", "&cТолько игроки могут использовать эту команду.");
        setDefault("messages.ru.reload_success", "&aКонфигурация перезагружена.");
        setDefault("messages.ru.reload_fail", "&cОшибка перезагрузки конфигурации.");
        setDefault("messages.ru.banned", "&c{player} &7забанен анти-читом &cVAC&7!");
        setDefault("messages.ru.banned_animation", "&c{player} &7забанен с анимацией анти-читом &cVAC&7!");
        setDefault("messages.ru.ban_failed", "&cНе удалось забанить &c{player}&7.");
        setDefault("messages.ru.confidence_set", "&7Уверенность &c{player} &7установлена на &c{confidence}%&7.");
        setDefault("messages.ru.confidence_add", "&7Уверенность &c{player} &7увеличена на &c{amount}%&7. Текущая: &c{confidence}%");
        setDefault("messages.ru.confidence_remove", "&7Уверенность &c{player} &7уменьшена на &c{amount}%&7. Текущая: &c{confidence}%");
        setDefault("messages.ru.confidence_info", "&7Уверенность &c{player}&7: &c{confidence}%");
        setDefault("messages.ru.confidence_reset", "&7Уверенность &c{player} &7сброшена.");
        setDefault("messages.ru.stats_header", "&c&lVAC Статистика");
        setDefault("messages.ru.stats_players_tracked", " &8▶ &7Отслеживается игроков: &c{count}");
        setDefault("messages.ru.stats_checks_total", " &8▶ &7Всего проверок: &c{count}");
        setDefault("messages.ru.stats_banned", " &8▶ &7Забанено: &c{count}");
        setDefault("messages.ru.stats_reports", " &8▶ &7Репортов: &c{count}");
        setDefault("messages.ru.usage", "&cИспользование: &7{usage}");
        setDefault("messages.ru.unknown_command", "&cНеизвестная команда. Используйте &7/vac help");
        setDefault("messages.ru.help_header", "&8&m----&8 [ &cVAC Помощь &8] &m----");
        setDefault("messages.ru.help_ban", " &8- &7/vac ban <игрок> &c- бан с анимацией");
        setDefault("messages.ru.help_profile", " &8- &7/vac profile <игрок> &c- профиль");
        setDefault("messages.ru.help_lags", " &8- &7/vac lags <игрок> <категория|stop> [время] &c- лаги");
        setDefault("messages.ru.help_confidence", " &8- &7/vac confidence <игрок> <set|add|remove|reset|info> [знач] &c- уверенность");
        setDefault("messages.ru.help_reload", " &8- &7/vac reload &c- перезагрузка");
        setDefault("messages.ru.help_crash", " &8- &7/vac crash <игрок> [метод] &c- краш клиента");
        setDefault("messages.ru.help_alerts", " &8- &7/vac alerts [on|off] &c- алерты");
        setDefault("messages.ru.help_spectate", " &8- &7/vac spectate <игрок|stop> &c- слежка");
        setDefault("messages.ru.help_report", " &8- &7/vac report <игрок> <причина> &c- репорт");
        setDefault("messages.ru.help_check", " &8- &7/vac check <игрок> &c- проверка");
        setDefault("messages.ru.help_checkvpn", " &8- &7/vac checkvpn <игрок> &c- VPN");
        setDefault("messages.ru.help_history", " &8- &7/vac history <игрок> &c- история");
        setDefault("messages.ru.help_freeze", " &8- &7/vac freeze <игрок> [off] &c- заморозка");
        setDefault("messages.ru.help_cps", " &8- &7/vac cps <игрок> &c- CPS");
        setDefault("messages.ru.help_reach", " &8- &7/vac reach <игрок> &c- дистанция");
        setDefault("messages.ru.help_hits", " &8- &7/vac hits <игрок> &c- статистика атак");
        setDefault("messages.ru.help_settings", " &8- &7/vac settings &c- настройки");
        setDefault("messages.ru.help_footer", "&8&m---------------------");
        setDefault("messages.ru.profile_header", "&8&m----&8 [ &cVAC Профиль &8] &m----");
        setDefault("messages.ru.profile_footer", "&8&m------------------------------------");
        setDefault("messages.ru.profile_confidence", " &8▶ &7Уверенность: &c{confidence}%");
        setDefault("messages.ru.profile_health", " &8▶ &7Здоровье: &c{health}&7/&c{max_health}");
        setDefault("messages.ru.profile_food", " &8▶ &7Сытость: &c{food}");
        setDefault("messages.ru.profile_ping", " &8▶ &7Пинг: &c{ping}ms");
        setDefault("messages.ru.profile_location", " &8▶ &7Локация: &c{world} &7({x}, {y}, {z})");
        setDefault("messages.ru.profile_checks", " &8▶ &7Проверки (&c{total_violations}&7):");
        setDefault("messages.ru.profile_check_line", "   &8- &c{check}&7: &c{violations} &7нарушений (уверенность: &c{check_confidence}%&7)");
        setDefault("messages.ru.profile_no_checks", "   &8- &7Нет нарушений");
        setDefault("messages.ru.profile_grim_version", " &8▶ &7GrimAC &8| &7Версия: &c{version}");
        setDefault("messages.ru.lags_started", "&7Лаги для &c{player} &7запущены. Категория: &c{category}");
        setDefault("messages.ru.lags_stopped", "&7Лаги для &c{player} &7остановлены.");
        setDefault("messages.ru.lags_already", "&cУ &c{player} &cуже есть активные лаги.");
        setDefault("messages.ru.lags_not_active", "&cУ &c{player} &cнет активных лагов.");
        setDefault("messages.ru.lags_invalid_category", "&cНеверная категория. Доступны: {categories}");
        setDefault("messages.ru.lags_category_usage", "&7Использование: &c/vac lags <игрок> [категория]");
        setDefault("messages.ru.crash_started", "&7Клиент &c{player} &7крашится. Метод: &c{method}");
        setDefault("messages.ru.crash_failed", "&cНе удалось скрашить &c{player}&7.");
        setDefault("messages.ru.crash_methods", "&7Доступные методы: &c{methods}");
        setDefault("messages.ru.spectate_started", "&7Слежка за &c{player}&7. Уверенность в ActionBar.");
        setDefault("messages.ru.spectate_stopped", "&7Слежка остановлена.");
        setDefault("messages.ru.report_sent", "&aРепорт на &c{player} &aотправлен. Причина: &f{reason}");
        setDefault("messages.ru.check_started", "&8[&cVAC&8] &7Проверка &c{player}&7...");
        setDefault("messages.ru.check_complete", "&8[&cVAC&8] &7Проверка &c{player} &7завершена. Уверенность: &c{confidence}%");
        setDefault("messages.ru.freeze_on", "&cВы заморожены! Ожидайте администратора.");
        setDefault("messages.ru.freeze_off", "&aЗаморозка снята.");
        setDefault("messages.ru.vpn_checking", "&7Проверка VPN для &c{player} &7(IP: {ip})...");
        setDefault("messages.ru.vpn_result", "&7VPN для &c{player} &7(IP: {ip}): &f{result}");
        setDefault("messages.ru.history_header", "&8&m----&8 [ &cИстория {player} &8] &m----");
        setDefault("messages.ru.history_line", " &8- &c{check} &7x{violations} &7| &c{confidence}% &7| &f{time}");
        setDefault("messages.ru.history_footer", "&8&m-------------------------------");
        setDefault("messages.ru.history_empty", "&7История нарушений пуста.");
        setDefault("messages.ru.alerts_enabled", "&aАлерты VAC включены");
        setDefault("messages.ru.alerts_disabled", "&cАлерты VAC выключены");
        setDefault("messages.ru.alert_message", "&8[&cVAC&8] &c{player} &7возможно использует читы! (&c{check}&7, уверенность: &c{confidence}%&7)");
        setDefault("messages.ru.ka_header", "&8&m----&8 [ &cKillAura &7{player} &8] &m----");
        setDefault("messages.ru.ka_cps", " &8▶ &7CPS: &c{cps} &7(swings: {swings})");
        setDefault("messages.ru.ka_reach", " &8▶ &7Reach: &c{reach} &7(hits: {hits})");
        setDefault("messages.ru.ka_aim", " &8▶ &7Aim Deviation: &c{aim}");
        setDefault("messages.ru.ka_wall", " &8▶ &7Wall hits: &c{walls}&7/{hits}");
        setDefault("messages.ru.webhook_ban", "**[VAC] Бан**\nИгрок: {player}\nUUID: {uuid}\nУверенность: {confidence}%\nЗабанил: {banned_by}");
        setDefault("messages.ru.webhook_alert", "**[VAC] Алерт**\nИгрок: {player}\nПроверка: {check}\nУверенность: {confidence}%");
    }

    private void setDefault(String path, String value) {
        if (!config.contains(path)) {
            config.set(path, value);
        }
    }

    public String getLanguage() { return config.getString("language", "en"); }

    public String getMessage(String key) {
        String lang = getLanguage();
        String msg = config.getString("messages." + lang + "." + key);
        if (msg == null) msg = config.getString("messages.en." + key);
        if (msg == null) msg = "&cMissing message: " + key;
        String prefix = config.getString("messages." + lang + ".prefix",
                config.getString("messages.en.prefix", "&8[&cVAC&8] &7"));
        return ColorUtils.colorize(prefix + msg);
    }

    public String getMessageRaw(String key) {
        String lang = getLanguage();
        String msg = config.getString("messages." + lang + "." + key);
        if (msg == null) msg = config.getString("messages.en." + key);
        if (msg == null) msg = "&cMissing message: " + key;
        return ColorUtils.colorize(msg);
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
    public double getConfidenceDecayMin() { return config.getDouble("confidence.decay_min_confidence", 5.0); }
    public int getSpikeWindowSeconds() { return config.getInt("confidence.spike_window_seconds", 10); }
    public double getSpikeMultiplier() { return config.getDouble("confidence.spike_multiplier", 2.5); }

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
    public String getKickMessage() {
        return ColorUtils.colorize(config.getString("punishment.kick_message",
                "&c&lVAC Anti-Cheat\n&7You have been banned by VAC.\n&7Confidence: {confidence}%"));
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
