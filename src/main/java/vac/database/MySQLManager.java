package vac.database;

import org.bukkit.Bukkit;
import vac.VAC;
import vac.models.PlayerData;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MySQLManager {

    private final VAC plugin;
    private Connection connection;
    private String prefix;

    public MySQLManager(VAC plugin) {
        this.plugin = plugin;
        this.prefix = plugin.getConfigManager().getMySQLTablePrefix();
    }

    public boolean connect() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String host = plugin.getConfigManager().getMySQLHost();
            int port = plugin.getConfigManager().getMySQLPort();
            String database = plugin.getConfigManager().getMySQLDatabase();
            String username = plugin.getConfigManager().getMySQLUsername();
            String password = plugin.getConfigManager().getMySQLPassword();
            boolean useSSL = plugin.getConfigManager().isMySQLUseSSL();

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=" + useSSL
                    + "&characterEncoding=UTF-8"
                    + "&autoReconnect=true";

            connection = DriverManager.getConnection(url, username, password);
            plugin.getLogger().info("Подключение к MySQL установлено.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка подключения к MySQL: " + e.getMessage());
            if (plugin.getConfigManager().isDebug()) {
                e.printStackTrace();
            }
            return false;
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            if (plugin.getConfigManager().isDebug()) {
                e.printStackTrace();
            }
        }
    }

    public void createTables() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS " + prefix + "players (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "player_name VARCHAR(16), " +
                "confidence DOUBLE DEFAULT 0, " +
                "total_violations INT DEFAULT 0, " +
                "first_detected TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "banned BOOLEAN DEFAULT FALSE, " +
                "ban_date TIMESTAMP NULL, " +
                "client_type VARCHAR(32) DEFAULT 'UNKNOWN', " +
                "client_version VARCHAR(16) DEFAULT 'UNKNOWN'" +
                ")"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS " + prefix + "violations (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "uuid VARCHAR(36), " +
                "check_name VARCHAR(64), " +
                "violations INT DEFAULT 1, " +
                "last_violation TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE KEY unique_check (uuid, check_name)" +
                ")"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS " + prefix + "bans (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "uuid VARCHAR(36), " +
                "player_name VARCHAR(16), " +
                "confidence DOUBLE, " +
                "reason VARCHAR(255), " +
                "banned_by VARCHAR(36), " +
                "ban_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );
            plugin.getLogger().info("Таблицы MySQL созданы/проверены.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка создания таблиц: " + e.getMessage());
            if (plugin.getConfigManager().isDebug()) {
                e.printStackTrace();
            }
        }
    }

    public CompletableFuture<Void> savePlayerData(PlayerData data) {
        return CompletableFuture.runAsync(() -> {
            try {
                String sql = "INSERT INTO " + prefix + "players " +
                        "(uuid, player_name, confidence, total_violations, last_seen, client_type, client_version) " +
                        "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "player_name = VALUES(player_name), " +
                        "confidence = VALUES(confidence), " +
                        "total_violations = VALUES(total_violations), " +
                        "last_seen = CURRENT_TIMESTAMP, " +
                        "client_type = VALUES(client_type), " +
                        "client_version = VALUES(client_version)";

                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, data.getUuid().toString());
                    stmt.setString(2, data.getPlayerName());
                    stmt.setDouble(3, data.getConfidence());
                    stmt.setInt(4, data.getTotalViolations());
                    stmt.setString(5, data.getClientType());
                    stmt.setString(6, data.getClientVersion());
                    stmt.executeUpdate();
                }

                saveViolations(data);
            } catch (SQLException e) {
                if (plugin.getConfigManager().isDebug()) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void saveViolations(PlayerData data) throws SQLException {
        String sql = "INSERT INTO " + prefix + "violations " +
                "(uuid, check_name, violations, last_violation) " +
                "VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON DUPLICATE KEY UPDATE " +
                "violations = VALUES(violations), " +
                "last_violation = CURRENT_TIMESTAMP";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (Map.Entry<String, Integer> entry : data.getViolations().entrySet()) {
                stmt.setString(1, data.getUuid().toString());
                stmt.setString(2, entry.getKey());
                stmt.setInt(3, entry.getValue());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    public CompletableFuture<PlayerData> loadPlayerData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerData data = new PlayerData(plugin, uuid);
            try {
                String sql = "SELECT * FROM " + prefix + "players WHERE uuid = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            data.setConfidence(rs.getDouble("confidence"));
                            data.setTotalViolations(rs.getInt("total_violations"));
                            data.setClientType(rs.getString("client_type"));
                            data.setClientVersion(rs.getString("client_version"));
                        }
                    }
                }

                String violSql = "SELECT * FROM " + prefix + "violations WHERE uuid = ?";
                try (PreparedStatement stmt = connection.prepareStatement(violSql)) {
                    stmt.setString(1, uuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            data.getViolations().put(
                                rs.getString("check_name"),
                                rs.getInt("violations")
                            );
                        }
                    }
                }

            } catch (SQLException e) {
                if (plugin.getConfigManager().isDebug()) {
                    e.printStackTrace();
                }
            }
            return data;
        });
    }

    public CompletableFuture<Void> saveBan(UUID uuid, String playerName, double confidence, String reason, String bannedBy) {
        return CompletableFuture.runAsync(() -> {
            try {
                String sql = "INSERT INTO " + prefix + "bans " +
                        "(uuid, player_name, confidence, reason, banned_by) " +
                        "VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, playerName);
                    stmt.setDouble(3, confidence);
                    stmt.setString(4, reason);
                    stmt.setString(5, bannedBy);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                if (plugin.getConfigManager().isDebug()) {
                    e.printStackTrace();
                }
            }
        });
    }

    public Connection getConnection() {
        return connection;
    }
}
