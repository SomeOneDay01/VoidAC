# VoidAC — Advanced Anti-Cheat for Paper 1.16.5

**VoidAC** is a powerful, multi-module anti-cheat plugin for Paper 1.16.5 servers. It features a unique **confidence-based punishment system**, comprehensive cheat detection, and an extensive toolkit for server administrators.

> Unlike traditional anti-cheats that ban instantly, VoidAC builds a **confidence score** per player over time, eliminating false positives while catching even the most careful cheaters.

---

## Features

### Detection Modules
- **KillAura** — CPS, reach, aim deviation, through-wall hits (multi-factor scoring, no false flags)
- **PlayerProfiler** — statistical profiling with per-player baselines, anomaly detection (>3σ), trajectory prediction
- **AimAnalyzer** — snap detection, grid alignment, human noise analysis, lock-on tracking
- **ReplayRecorder** — rolling 20-second buffer, save/load replay to JSON, admin playback
- **BadPackets** — Packet spam, invalid coordinates, NBT manipulation
- **Anti-Xray** — Hides ores behind walls (async, zero-lag)
- **GrimAC Integration** — auto-detects GrimAC and feeds data into confidence system

### Administrative Tools
- **Confidence System** — configurable decay, thresholds, auto-ban, spike detection
- **ColorUtils** — full hex (`&#RRGGBB`), gradient (`<gradient:#:#>`), legacy color support
- **Language System** — English & Russian, all messages editable in `config.yml`
- **Ban Animation** — dramatic red spiral + explosion on ban (`/vac ban`)
- **Fake Lag** — 4 types of lag simulation: connection, block, entity, all (`/vac lags`)
- **Client Crash** — multiple crash methods: book, sign, explosion, particles, flood (`/vac crash`)
- **Spectate** — invisible spectating with confidence in actionbar (`/vac spectate`)
- **Freeze** — aqua particle freeze effect (`/vac freeze`)
- **Player Reports** — players report others, confidence increases (`/vac report`)
- **Provocation Checks** — 5-phase automated check (`/vac check`)
- **VPN Detection** — IP geolocation lookup (`/vac checkvpn`)
- **Evidence Logging** — all violations stored in database (`/vac history`)

### Notifications & Integration
- **In-Game Alerts** — real-time cheat alerts with configurable thresholds
- **ActionBar Spectate** — shows target confidence in real-time
- **Discord Bot** — native WebSocket-based bot (zero dependencies) with slash commands:
  - `/ban`, `/freeze`, `/unfreeze`, `/check`, `/stats`
  - Buttons for ban/freeze confirmation
- **Discord Webhooks** — ban and alert notifications
- **Bungee/Velocity** — cross-server ban/alert synchronization
- **Auto-Updater** — checks GitHub releases and notifies admins on join
- **PlaceholderAPI** (separate expansion) — `%vac_confidence_<player>%`, `%vac_cps_<player>%`, `%vac_reach_<player>%`, and more

### Database
- **MySQL** — full support with connection pooling
- **SQLite** — built-in fallback (zero configuration)

### Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/vac help` | Show help | `vac.admin` |
| `/vac ban <player>` | Ban with animation | `vac.command.ban` |
| `/vac profile <player>` | Player profile | `vac.command.profile` |
| `/vac lags <player> [type]` | Fake lag | `vac.command.lags` |
| `/vac confidence <player> <action> [value]` | Manage confidence | `vac.command.confidence` |
| `/vac crash <player> [method]` | Crash client | `vac.command.crash` |
| `/vac alerts [on/off]` | Toggle alerts | `vac.alerts` |
| `/vac spectate <player/stop>` | Spectate player | `vac.command.spectate` |
| `/vac report <player> <reason>` | Report player | `vac.report` |
| `/vac check <player>` | Provocation check | `vac.command.check` |
| `/vac checkvpn <player>` | VPN check | `vac.command.checkvpn` |
| `/vac history <player>` | Violation history | `vac.command.history` |
| `/vac freeze <player> [off]` | Freeze player | `vac.command.freeze` |
| `/vac cps <player>` | Show CPS stats | `vac.command.cps` |
| `/vac reach <player>` | Show reach stats | `vac.command.reach` |
| `/vac hits <player>` | Show detailed hit stats | `vac.command.hits` |
| `/vac update` | Check for updates | `vac.command.update` |
| `/vac version` | Show plugin version | `vac.command.version` |
| `/vac replay <player> [save]` | Replay recorded data | `vac.command.replay` |
| `/vac settings` | Settings GUI | `vac.admin` |
| `/vac reload` | Reload config | `vac.command.reload` |
| `/vac stats` | Server statistics | `vac.admin` |

---

## Installation

1. **Download** the latest jar from [Releases](https://github.com/SomeOneDay01/VoidAC/releases)
2. **Place** `VoidAC.jar` in your server's `plugins/` folder
3. **Restart** your server (or `/reload`)
4. **Configure** `plugins/VoidAC/config.yml` to your needs

### Requirements
- **Paper** 1.16.5 (or compatible fork)
- **Java** 8 or higher
- **GrimAC** (optional, recommended) — for GrimAC integration
- **PacketEvents** (optional) — for optimized packet handling
- **PlaceholderAPI** (optional) — for placeholder expansion

---

## Configuration

All settings are in `plugins/VoidAC/config.yml`. Key sections:

### MySQL (optional, SQLite used by default)
```yaml
mysql:
  enabled: false
  host: localhost
  port: 3306
  database: vac_anticheat
  username: root
  password: ""
```

### Confidence System
```yaml
confidence:
  increment_per_violation: 2.0
  max_confidence: 100.0
  ban_threshold: 100.0
  decay_per_second: 0.5
  decay_delay_seconds: 30
  decay_min_confidence: 5.0
  spike_window_seconds: 10
  spike_multiplier: 2.5
  auto_ban: true
```

### Language
```yaml
language: en  # "en" or "ru"
# Messages editable under messages.en.* / messages.ru.*
```

### Discord Bot
```yaml
discord:
  enabled: false
  token: "YOUR_BOT_TOKEN"
  channel_id: "YOUR_CHANNEL_ID"
```

### KillAura Detection
```yaml
killaura:
  threshold: 60.0
  confidence_increment: 3.0
  max_cps: 8.0
  max_reach: 3.5
  min_aim_deviation: 0.8
  max_wall_ratio: 5.0
```

---

## Building from Source

```bash
git clone https://github.com/SomeOneDay01/VoidAC.git
cd VoidAC
mvn clean package
```

The compiled jar will be in `target/VoidAC-1.0.3.jar`.

---

## How the Confidence System Works

1. **Violations** → each detected cheat action increments the player's violation counter for that check
2. **Confidence** → each violation adds confidence percentage (configurable), multiplied by repeat-offense multiplier and spike detection
3. **Decay** → confidence decays linearly over time if no new violations occur (configurable delay, rate, and minimum floor)
4. **Spike Detection** — rapid violations within `spike_window_seconds` trigger `spike_multiplier` boost
5. **Auto-Ban** → when confidence reaches `ban_threshold`, the player is automatically banned
6. **False Positive Protection** → minimum sample sizes, multi-factor scoring, cooldowns prevent hasty flags

This means:
- A player who accidentally clips once won't get banned
- A persistent cheater will inevitably reach 100% confidence
- Confidence never fully resets (`decay_min_confidence`) — habitual cheaters accumulate over sessions
- Server admins have full control over thresholds

---

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `vac.admin` | OP | Full access to all commands |
| `vac.alerts` | OP | Receive and manage alerts |
| `vac.report` | OP | Report players |
| `vac.command.*` | OP | Individual command permissions |

---

## Discord Bot Setup

1. Create a bot at [Discord Developer Portal](https://discord.com/developers/applications)
2. Enable **Message Content Intent** in Bot settings
3. Invite bot with `applications.commands` and `bot` scopes
4. Set `discord.token` and `discord.channel_id` in config.yml
5. Restart the server — slash commands register automatically

### Available Discord Commands
- `/ban <player>` — Ban with confirmation buttons
- `/freeze <player>` — Freeze with confirmation buttons
- `/unfreeze <player>` — Unfreeze a player
- `/check <player>` — Show player stats
- `/stats` — Server statistics

---

## API for Developers

VoidAC exposes its managers via `VAC.getInstance()`:

```java
VAC vac = VAC.getInstance();

// Access player data
PlayerData data = vac.getPlayerDataManager().getOrCreate(player);
data.addViolation("Speed", 1);

// Confidence
data.getConfidence();
data.addConfidence(5.0);

// Ban
vac.getPunishmentManager().banPlayer(player, "Console", true);

// Profiling
vac.getPlayerProfiler().getCpsAnomalyScore(player);
vac.getAimAnalyzer().getOverallAimScore(player);
vac.getReplayRecorder().saveReplay(player);
```

---

## Support

- **Issues**: [GitHub Issues](https://github.com/SomeOneDay01/VoidAC/issues)
- **Source**: [GitHub](https://github.com/SomeOneDay01/VoidAC)

---

## License

This project is licensed under the MIT License.
