package vac;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import vac.commands.VACCommand;
import vac.config.ConfigManager;
import vac.database.MySQLManager;
import vac.listeners.GrimACListener;
import vac.listeners.PlayerListener;
import vac.models.*;
import vac.animation.BanAnimation;
import vac.lag.LagManager;
import vac.punishment.PunishmentManager;
import vac.crash.CrashManager;
import vac.webhook.WebhookManager;
import vac.killaura.KillAuraAnalyzer;
import vac.discord.DiscordBot;
import vac.packets.BadPacketsManager;
import vac.bungee.BungeeManager;
import vac.antixray.AntiXrayManager;
import vac.database.SQLiteManager;
import vac.updater.UpdateChecker;
import vac.analysis.PlayerProfiler;
import vac.analysis.AimAnalyzer;
import vac.analysis.ReplayRecorder;
import vac.ml.OnlineGaussianClassifier;
import vac.ml.FeatureExtractor;
import vac.ml.DataCollector;
import vac.hologram.CheatHologramManager;
import vac.gui.CheatListGUI;
import vac.listeners.GUIListener;

import java.io.File;

public class VAC extends JavaPlugin {

    private static VAC instance;
    private ConfigManager configManager;
    private MySQLManager mysqlManager;
    private PlayerDataManager playerDataManager;
    private AlertManager alertManager;
    private ReportManager reportManager;
    private SpectateManager spectateManager;
    private FreezeManager freezeManager;
    private EvidenceManager evidenceManager;
    private CheckManager checkManager;
    private WebhookManager webhookManager;
    private GrimACListener grimACListener;
    private PlayerListener playerListener;
    private BanAnimation banAnimation;
    private LagManager lagManager;
    private PunishmentManager punishmentManager;
    private CrashManager crashManager;
    private KillAuraAnalyzer killAuraAnalyzer;
    private DiscordBot discordBot;
    private BadPacketsManager badPacketsManager;
    private BungeeManager bungeeManager;
    private AntiXrayManager antiXrayManager;
    private SQLiteManager sqliteManager;
    private UpdateChecker updateChecker;
    private PlayerProfiler playerProfiler;
    private AimAnalyzer aimAnalyzer;
    private ReplayRecorder replayRecorder;
    private OnlineGaussianClassifier onlineGaussianClassifier;
    private FeatureExtractor featureExtractor;
    private DataCollector dataCollector;
    private CheatHologramManager cheatHologramManager;
    private CheatListGUI cheatListGUI;
    private GUIListener guiListener;

    @Override
    public void onEnable() {
        try {
            instance = this;
            vac.util.VersionUtil.init();
            saveDefaultConfig();
            reloadConfig();

            configManager = new ConfigManager(this);
            configManager.loadConfig();

            mysqlManager = new MySQLManager(this);
            sqliteManager = new SQLiteManager(this);
            if (configManager.isMySQLEnabled()) {
                if (!mysqlManager.connect()) {
                    getLogger().severe("Ошибка подключения к MySQL!");
                } else {
                    mysqlManager.createTables();
                }
            } else if (configManager.isSQLiteEnabled()) {
                if (sqliteManager.connect()) {
                    sqliteManager.createTables();
                }
            }

            playerDataManager = new PlayerDataManager(this);
            alertManager = new AlertManager(this);
            reportManager = new ReportManager(this);
            spectateManager = new SpectateManager(this);
            freezeManager = new FreezeManager(this);
            evidenceManager = new EvidenceManager(this);
            checkManager = new CheckManager(this);
            webhookManager = new WebhookManager(this);
            banAnimation = new BanAnimation(this);
            lagManager = new LagManager(this);
            punishmentManager = new PunishmentManager(this);
            crashManager = new CrashManager(this);
            killAuraAnalyzer = new KillAuraAnalyzer(this);
            discordBot = new DiscordBot(this);
            badPacketsManager = new BadPacketsManager(this);
            bungeeManager = new BungeeManager(this);
            antiXrayManager = new AntiXrayManager(this);
            playerProfiler = new PlayerProfiler(this);
            aimAnalyzer = new AimAnalyzer();
            replayRecorder = new ReplayRecorder(this);
            onlineGaussianClassifier = new OnlineGaussianClassifier();
            featureExtractor = new FeatureExtractor(this);
            dataCollector = new DataCollector(this, featureExtractor);
            cheatHologramManager = new CheatHologramManager(this, onlineGaussianClassifier, featureExtractor);
            cheatListGUI = new CheatListGUI(this);
            guiListener = new GUIListener(this, cheatListGUI);
            updateChecker = new UpdateChecker(this);

            if (configManager.isMySQLEnabled() || configManager.isSQLiteEnabled()) {
                evidenceManager.createTable();
            }

            VACCommand vacCmd = new VACCommand(this);
            PluginCommand command = getCommand("vac");
            if (command != null) {
                command.setExecutor(vacCmd);
                command.setTabCompleter(vacCmd);
            }

            grimACListener = new GrimACListener(this);
            playerListener = new PlayerListener(this);
            getServer().getPluginManager().registerEvents(grimACListener, this);
            getServer().getPluginManager().registerEvents(playerListener, this);
            getServer().getPluginManager().registerEvents(killAuraAnalyzer, this);
            getServer().getPluginManager().registerEvents(badPacketsManager, this);
            getServer().getPluginManager().registerEvents(antiXrayManager, this);
            getServer().getPluginManager().registerEvents(playerProfiler, this);
            getServer().getPluginManager().registerEvents(guiListener, this);
            getServer().getPluginManager().registerEvents(updateChecker, this);

            if (configManager.isBungeeEnabled()) {
                bungeeManager.enable();
            }

            if (configManager.isUpdaterEnabled()) {
                updateChecker.checkAsync();
            }

            File modelFile = new File(getDataFolder(), "model.json");
            if (modelFile.exists()) {
                onlineGaussianClassifier.load(modelFile);
                getLogger().info("ML model loaded: " + onlineGaussianClassifier.getTotalSamples() + " samples");
            }

            dataCollector.start();
            dataCollector.trainFromSaved();
            cheatHologramManager.start();

            if (configManager.isDiscordBotEnabled()) {
                discordBot.start();
            }

            int interval = (int) (configManager.getCheckIntervalSeconds() * 20);
            if (interval < 1) interval = 20;
            Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                    () -> { if (playerDataManager != null) playerDataManager.tick(); },
                    interval, interval);

            getLogger().info("VAC Anti-Cheat v" + getDescription().getVersion() + " включён!");
        } catch (Throwable t) {
            getLogger().severe("Ошибка включения VAC: " + t.getMessage());
            t.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (discordBot != null) discordBot.stop();
        if (bungeeManager != null) bungeeManager.disable();
        if (lagManager != null) lagManager.stopAll();
        if (freezeManager != null) freezeManager.unfreezeAll();
        if (cheatHologramManager != null) cheatHologramManager.shutdown();
        if (dataCollector != null) dataCollector.shutdown();
        if (onlineGaussianClassifier != null) {
            onlineGaussianClassifier.save(new File(getDataFolder(), "model.json"));
        }
        if (crashManager != null) crashManager.shutdown();
        if (replayRecorder != null) replayRecorder.shutdown();
        if (playerProfiler != null) playerProfiler.cleanup();
        if (playerDataManager != null) playerDataManager.saveAll();
        if (alertManager != null) alertManager.save();
        if (mysqlManager != null) mysqlManager.disconnect();
        if (sqliteManager != null) sqliteManager.disconnect();
    }

    public void reloadConfigFromFile() {
        reloadConfig();
        if (configManager != null) configManager.loadConfig();
    }

    public static VAC getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public MySQLManager getMySQLManager() { return mysqlManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public AlertManager getAlertManager() { return alertManager; }
    public ReportManager getReportManager() { return reportManager; }
    public SpectateManager getSpectateManager() { return spectateManager; }
    public FreezeManager getFreezeManager() { return freezeManager; }
    public EvidenceManager getEvidenceManager() { return evidenceManager; }
    public CheckManager getCheckManager() { return checkManager; }
    public WebhookManager getWebhookManager() { return webhookManager; }
    public GrimACListener getGrimACListener() { return grimACListener; }
    public BanAnimation getBanAnimation() { return banAnimation; }
    public LagManager getLagManager() { return lagManager; }
    public PunishmentManager getPunishmentManager() { return punishmentManager; }
    public CrashManager getCrashManager() { return crashManager; }
    public KillAuraAnalyzer getKillAuraAnalyzer() { return killAuraAnalyzer; }
    public DiscordBot getDiscordBot() { return discordBot; }
    public BadPacketsManager getBadPacketsManager() { return badPacketsManager; }
    public BungeeManager getBungeeManager() { return bungeeManager; }
    public AntiXrayManager getAntiXrayManager() { return antiXrayManager; }
    public SQLiteManager getSQLiteManager() { return sqliteManager; }
    public PlayerProfiler getPlayerProfiler() { return playerProfiler; }
    public AimAnalyzer getAimAnalyzer() { return aimAnalyzer; }
    public ReplayRecorder getReplayRecorder() { return replayRecorder; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
    public OnlineGaussianClassifier getOnlineGaussianClassifier() { return onlineGaussianClassifier; }
    public FeatureExtractor getFeatureExtractor() { return featureExtractor; }
    public DataCollector getDataCollector() { return dataCollector; }
    public CheatHologramManager getCheatHologramManager() { return cheatHologramManager; }
    public CheatListGUI getCheatListGUI() { return cheatListGUI; }
}
