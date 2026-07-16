package vac.models;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import vac.VAC;

import java.io.File;
import java.util.*;

public class ReportManager {

    private final VAC plugin;
    private final File reportsFile;
    private final List<Report> reports;

    public ReportManager(VAC plugin) {
        this.plugin = plugin;
        this.reportsFile = new File(plugin.getDataFolder(), "reports.yml");
        this.reports = new ArrayList<>();
        load();
    }

    public void report(Player reporter, Player target, String reason) {
        Report report = new Report(reporter.getName(), target.getUniqueId(), target.getName(), reason, System.currentTimeMillis());
        reports.add(report);

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(target);
        double increment = plugin.getConfigManager().getReportConfidenceIncrement();
        data.addConfidence(increment);

        save();

        String msg = plugin.getConfigManager().getMessageRaw("report_sent")
                .replace("{player}", target.getName())
                .replace("{reason}", reason);
        reporter.sendMessage(msg);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("vac.admin") || p.hasPermission("vac.alerts")) {
                p.sendMessage(plugin.getConfigManager().getMessageRaw("report_alert")
                        .replace("{reporter}", reporter.getName())
                        .replace("{player}", target.getName())
                        .replace("{reason}", reason));
            }
        }
    }

    public List<Report> getReportsFor(UUID uuid) {
        List<Report> result = new ArrayList<>();
        for (Report r : reports) {
            if (r.targetUuid.equals(uuid)) result.add(r);
        }
        return result;
    }

    public List<Report> getAllReports() {
        return new ArrayList<>(reports);
    }

    public void load() {
        if (!reportsFile.exists()) return;
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(reportsFile);
            for (String key : config.getKeys(false)) {
                String reporter = config.getString(key + ".reporter");
                UUID targetUuid = UUID.fromString(config.getString(key + ".target_uuid"));
                String targetName = config.getString(key + ".target_name");
                String reason = config.getString(key + ".reason");
                long time = config.getLong(key + ".time");
                reports.add(new Report(reporter, targetUuid, targetName, reason, time));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error loading reports: " + e.getMessage());
        }
    }

    public void save() {
        try {
            YamlConfiguration config = new YamlConfiguration();
            int i = 0;
            for (Report r : reports) {
                String key = "report_" + (i++);
                config.set(key + ".reporter", r.reporter);
                config.set(key + ".target_uuid", r.targetUuid.toString());
                config.set(key + ".target_name", r.targetName);
                config.set(key + ".reason", r.reason);
                config.set(key + ".time", r.time);
            }
            config.save(reportsFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Error saving reports: " + e.getMessage());
        }
    }

    public static class Report {
        public final String reporter;
        public final UUID targetUuid;
        public final String targetName;
        public final String reason;
        public final long time;

        public Report(String reporter, UUID targetUuid, String targetName, String reason, long time) {
            this.reporter = reporter;
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.reason = reason;
            this.time = time;
        }
    }
}
