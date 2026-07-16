package vac.database;

import org.bukkit.Bukkit;
import vac.VAC;
import vac.models.PlayerData;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SQLiteManager {

    private final VAC plugin;
    private Connection connection;
    private final String prefix;

    public SQLiteManager(VAC plugin) {
        this.plugin = plugin;
        this.prefix = plugin.getConfigManager().getMySQLTablePrefix();
    }

    public boolean connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            File dbFile = new File(plugin.getDataFolder(), "vac.db");
            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }

            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            plugin.getLogger().info("SQLite connected: " + dbFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect SQLite: " + e.getMessage());
            return false;
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            if (plugin.getConfigManager().isDebug()) e.printStackTrace();
        }
    }

    public void createTables() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS " + prefix + "players (" +
                "uuid TEXT PRIMARY KEY, " +
                "player_name TEXT, " +
                "confidence REAL DEFAULT 0, " +
                "total_violations INTEGER DEFAULT 0, " +
                "first_detected TEXT DEFAULT (datetime('now')), " +
                "last_seen TEXT DEFAULT (datetime('now')), " +
                "banned INTEGER DEFAULT 0, " +
                "ban_date TEXT, " +
                "client_type TEXT DEFAULT 'UNKNOWN', " +
                "client_version TEXT DEFAULT 'UNKNOWN'" +
                ")"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS " + prefix + "violations (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "uuid TEXT, " +
                "check_name TEXT, " +
                "violations INTEGER DEFAULT 1, " +
                "last_violation TEXT DEFAULT (datetime('now')), " +
                "UNIQUE (uuid, check_name)" +
                ")"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS " + prefix + "bans (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "uuid TEXT, " +
                "player_name TEXT, " +
                "confidence REAL, " +
                "reason TEXT, " +
                "banned_by TEXT, " +
                "ban_date TEXT DEFAULT (datetime('now'))" +
                ")"
            );
            plugin.getLogger().info("SQLite tables created.");
        } catch (SQLException e) {
            plugin.getLogger().severe("SQLite table creation error: " + e.getMessage());
        }
    }

    public CompletableFuture<Void> savePlayerData(PlayerData data) {
        return CompletableFuture.runAsync(() -> {
            try {
                String sql = "INSERT OR REPLACE INTO " + prefix + "players " +
                        "(uuid, player_name, confidence, total_violations, last_seen, client_type, client_version) " +
                        "VALUES (?, ?, ?, ?, datetime('now'), ?, ?)";
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
                if (plugin.getConfigManager().isDebug()) e.printStackTrace();
            }
        });
    }

    private void saveViolations(PlayerData data) throws SQLException {
        String sql = "INSERT OR REPLACE INTO " + prefix + "violations " +
                "(uuid, check_name, violations, last_violation) " +
                "VALUES (?, ?, ?, datetime('now'))";
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
                if (plugin.getConfigManager().isDebug()) e.printStackTrace();
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
                if (plugin.getConfigManager().isDebug()) e.printStackTrace();
            }
        });
    }

    public Connection getConnection() { return connection; }
}
