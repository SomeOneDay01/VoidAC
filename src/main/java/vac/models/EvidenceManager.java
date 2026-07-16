package vac.models;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import vac.VAC;

import java.sql.*;
import java.util.*;
import java.util.Date;

public class EvidenceManager {

    private final VAC plugin;

    public EvidenceManager(VAC plugin) {
        this.plugin = plugin;
    }

    public void logViolation(UUID uuid, String playerName, String checkName, int violations, double confidence) {
        if (!plugin.getConfigManager().isMySQLEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String prefix = plugin.getConfigManager().getMySQLTablePrefix();
                Connection conn = plugin.getMySQLManager().getConnection();
                if (conn == null || conn.isClosed()) return;

                String sql = "INSERT INTO " + prefix + "evidence (uuid, player_name, check_name, violations, confidence, time) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, playerName);
                    stmt.setString(3, checkName);
                    stmt.setInt(4, violations);
                    stmt.setDouble(5, confidence);
                    stmt.setLong(6, System.currentTimeMillis());
                    stmt.executeUpdate();
                }
            } catch (Exception e) {
                if (plugin.getConfigManager().isDebug()) e.printStackTrace();
            }
        });
    }

    public List<EvidenceEntry> getHistory(UUID uuid) {
        List<EvidenceEntry> result = new ArrayList<>();
        if (!plugin.getConfigManager().isMySQLEnabled()) return result;

        try {
            String prefix = plugin.getConfigManager().getMySQLTablePrefix();
            Connection conn = plugin.getMySQLManager().getConnection();
            if (conn == null || conn.isClosed()) return result;

            String sql = "SELECT * FROM " + prefix + "evidence WHERE uuid = ? ORDER BY time DESC LIMIT 50";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        result.add(new EvidenceEntry(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("player_name"),
                            rs.getString("check_name"),
                            rs.getInt("violations"),
                            rs.getDouble("confidence"),
                            rs.getLong("time")
                        ));
                    }
                }
            }
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebug()) e.printStackTrace();
        }
        return result;
    }

    public void createTable() {
        if (!plugin.getConfigManager().isMySQLEnabled()) return;
        try {
            String prefix = plugin.getConfigManager().getMySQLTablePrefix();
            Connection conn = plugin.getMySQLManager().getConnection();
            if (conn == null || conn.isClosed()) return;

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + prefix + "evidence (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "uuid VARCHAR(36), " +
                    "player_name VARCHAR(16), " +
                    "check_name VARCHAR(64), " +
                    "violations INT DEFAULT 1, " +
                    "confidence DOUBLE DEFAULT 0, " +
                    "time BIGINT, " +
                    "INDEX idx_uuid (uuid), " +
                    "INDEX idx_time (time)" +
                    ")"
                );
            }
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebug()) e.printStackTrace();
        }
    }

    public void sendHistory(Player sender, UUID targetUuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<EvidenceEntry> history = getHistory(targetUuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (history.isEmpty()) {
                    sender.sendMessage(plugin.getConfigManager().getMessageRaw("history_empty"));
                    return;
                }

                Player target = Bukkit.getPlayer(targetUuid);
                String name = target != null ? target.getName() : history.get(0).playerName;

                sender.sendMessage(plugin.getConfigManager().getMessageRaw("history_header")
                        .replace("{player}", name));

                int count = 0;
                for (EvidenceEntry entry : history) {
                    if (count >= 20) break;
                    sender.sendMessage(plugin.getConfigManager().getMessageRaw("history_line")
                            .replace("{check}", entry.checkName)
                            .replace("{violations}", String.valueOf(entry.violations))
                            .replace("{confidence}", String.format("%.1f", entry.confidence))
                            .replace("{time}", new Date(entry.time).toString()));
                    count++;
                }
                sender.sendMessage(plugin.getConfigManager().getMessageRaw("history_footer")
                        .replace("{count}", String.valueOf(history.size())));
            });
        });
    }

    public static class EvidenceEntry {
        public final UUID uuid;
        public final String playerName;
        public final String checkName;
        public final int violations;
        public final double confidence;
        public final long time;

        public EvidenceEntry(UUID uuid, String playerName, String checkName, int violations, double confidence, long time) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.checkName = checkName;
            this.violations = violations;
            this.confidence = confidence;
            this.time = time;
        }
    }
}
