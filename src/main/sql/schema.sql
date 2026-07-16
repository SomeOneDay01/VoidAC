-- VAC Anti-Cheat Database Schema
-- MySQL 5.7+ / MariaDB 10.2+

CREATE DATABASE IF NOT EXISTS vac_anticheat
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE vac_anticheat;

-- Players table
CREATE TABLE IF NOT EXISTS vac_players (
    uuid VARCHAR(36) PRIMARY KEY,
    player_name VARCHAR(16) NOT NULL DEFAULT '',
    confidence DOUBLE NOT NULL DEFAULT 0,
    total_violations INT NOT NULL DEFAULT 0,
    first_detected TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    banned BOOLEAN NOT NULL DEFAULT FALSE,
    ban_date TIMESTAMP NULL DEFAULT NULL,
    client_type VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    client_version VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    INDEX idx_player_name (player_name),
    INDEX idx_confidence (confidence),
    INDEX idx_banned (banned)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Violations table
CREATE TABLE IF NOT EXISTS vac_violations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,
    check_name VARCHAR(64) NOT NULL,
    violations INT NOT NULL DEFAULT 1,
    last_violation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_check (uuid, check_name),
    INDEX idx_uuid (uuid),
    INDEX idx_check_name (check_name),
    FOREIGN KEY (uuid) REFERENCES vac_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Bans history table
CREATE TABLE IF NOT EXISTS vac_bans (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,
    player_name VARCHAR(16) NOT NULL,
    confidence DOUBLE NOT NULL DEFAULT 0,
    reason VARCHAR(255) NOT NULL DEFAULT 'Cheating detected by VAC',
    banned_by VARCHAR(36) NOT NULL DEFAULT 'VAC',
    ban_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_uuid (uuid),
    INDEX idx_player_name (player_name),
    INDEX idx_ban_date (ban_date),
    FOREIGN KEY (uuid) REFERENCES vac_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
