package vac.models;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import vac.VAC;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AlertManager {

    private final VAC plugin;
    private final File alertsFile;
    private final Map<UUID, Boolean> alertToggles;

    public AlertManager(VAC plugin) {
        this.plugin = plugin;
        this.alertsFile = new File(plugin.getDataFolder(), "alerts.yml");
        this.alertToggles = new ConcurrentHashMap<>();
        load();
    }

    public boolean hasAlertsEnabled(Player player) {
        return alertToggles.getOrDefault(player.getUniqueId(), true);
    }

    public void setAlertsEnabled(Player player, boolean enabled) {
        alertToggles.put(player.getUniqueId(), enabled);
        save();
    }

    public void toggle(Player player) {
        boolean current = hasAlertsEnabled(player);
        setAlertsEnabled(player, !current);
    }

    public void load() {
        if (!alertsFile.exists()) return;
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(alertsFile);
            for (String key : config.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    boolean enabled = config.getBoolean(key, true);
                    alertToggles.put(uuid, enabled);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось загрузить alerts.yml: " + e.getMessage());
        }
    }

    public void save() {
        try {
            YamlConfiguration config = new YamlConfiguration();
            for (Map.Entry<UUID, Boolean> entry : alertToggles.entrySet()) {
                config.set(entry.getKey().toString(), entry.getValue());
            }
            config.save(alertsFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось сохранить alerts.yml: " + e.getMessage());
        }
    }
}
