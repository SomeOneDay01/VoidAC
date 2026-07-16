package vac.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import vac.VAC;
import vac.models.PlayerData;

import java.lang.reflect.Method;

public class GrimACListener implements Listener {

    private final VAC plugin;
    private boolean grimacEnabled;

    public GrimACListener(VAC plugin) {
        this.plugin = plugin;

        Plugin grimacPlugin = findGrimacPlugin();
        if (grimacPlugin == null) {
            this.grimacEnabled = false;
            plugin.getLogger().warning("GrimAC не найден. VAC будет работать без интеграции GrimAC.");
            return;
        }

        plugin.getLogger().info("GrimAC найден: " + grimacPlugin.getName() + " v" + grimacPlugin.getDescription().getVersion());
        setupGrimACListener(grimacPlugin);
    }

    private Plugin findGrimacPlugin() {
        String[] checkNames = {
            "GrimAC", "grimac", "GrimACBukkit", "GrimAC-Bukkit",
            "GrimACBukkitBridge", "Grim", "AcidGrimAC",
            "grim", "GrimAC_", "GrimACPlugin"
        };

        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            String name = p.getName().toLowerCase();
            String mainClass = p.getDescription().getMain().toLowerCase();
            if (name.contains("grim") || name.contains("acid")
                    || mainClass.contains("grim") || mainClass.contains("acid")) {
                plugin.getLogger().info("Найден потенциальный GrimAC: " + p.getName() + " (main: " + p.getDescription().getMain() + ")");
                return p;
            }
        }

        for (String checkName : checkNames) {
            Plugin p = Bukkit.getPluginManager().getPlugin(checkName);
            if (p != null) return p;
        }

        return null;
    }

    private void setupGrimACListener(Plugin grimacPlugin) {
        try {
            Class<?> mainClass = Class.forName(grimacPlugin.getDescription().getMain());

            Object api = null;
            Class<?> apiClass = null;

            String[] possibleApiClasses = {
                "ac.grim.grimac.api.GrimAPI",
                "ac.grim.grimac.GrimAPI",
                "ac.grim.grimac.api.AbstractAPI",
                "ac.grim.grimac.GrimAC"
            };

            for (String apiClassName : possibleApiClasses) {
                try {
                    apiClass = Class.forName(apiClassName);
                    plugin.getLogger().info("GrimAC API класс найден: " + apiClassName);
                    break;
                } catch (ClassNotFoundException ignored) {}
            }

            if (apiClass == null) {
                Class<?>[] declaredClasses = mainClass.getDeclaredClasses();
                for (Class<?> dc : declaredClasses) {
                    plugin.getLogger().info("  Внутренний класс GrimAC: " + dc.getName());
                }

                try {
                    api = mainClass.getMethod("getInstance").invoke(null);
                    apiClass = api.getClass();
                    plugin.getLogger().info("GrimAC instance получен через getInstance(): " + apiClass.getName());
                } catch (NoSuchMethodException e) {
                    try {
                        java.lang.reflect.Field instanceField = mainClass.getDeclaredField("instance");
                        instanceField.setAccessible(true);
                        api = instanceField.get(null);
                        apiClass = api.getClass();
                        plugin.getLogger().info("GrimAC instance получен через instance field: " + apiClass.getName());
                    } catch (Exception e2) {
                        plugin.getLogger().warning("Не удалось получить instance GrimAC: " + e2.getMessage());
                        this.grimacEnabled = false;
                        return;
                    }
                }
            }

            if (apiClass != null && api == null) {
                try {
                    java.lang.reflect.Field instField = apiClass.getDeclaredField("INSTANCE");
                    instField.setAccessible(true);
                    api = instField.get(null);
                } catch (Exception e) {
                    try {
                        java.lang.reflect.Field instField = apiClass.getDeclaredField("instance");
                        instField.setAccessible(true);
                        api = instField.get(null);
                    } catch (Exception e2) {
                        try {
                            api = apiClass.getMethod("getInstance").invoke(null);
                        } catch (Exception e3) {
                            plugin.getLogger().warning("Не удалось получить API instance: " + e3.getMessage());
                            this.grimacEnabled = false;
                            return;
                        }
                    }
                }
            }

            if (api == null || apiClass == null) {
                this.grimacEnabled = false;
                plugin.getLogger().warning("GrimAC API instance не найден");
                return;
            }

            Object alertManager = null;
            try {
                alertManager = apiClass.getMethod("getAlertManager").invoke(api);
            } catch (NoSuchMethodException e) {
                try {
                    alertManager = api.getClass().getMethod("getAlertManager").invoke(api);
                } catch (NoSuchMethodException e2) {
                    plugin.getLogger().warning("getAlertManager() не найден в GrimAC API");
                    this.grimacEnabled = true;
                    return;
                }
            }

            if (alertManager == null) {
                plugin.getLogger().warning("AlertManager is null");
                this.grimacEnabled = true;
                return;
            }

            try {
                Class<?> alertListenerClass = Class.forName("ac.grim.grimac.api.events.AlertListener");
                Method registerMethod = alertManager.getClass().getMethod("registerListener", Object.class);
                registerMethod.invoke(alertManager, createAlertListener(alertListenerClass));
                plugin.getLogger().info("VAC зарегистрировал AlertListener в GrimAC!");
            } catch (ClassNotFoundException e) {
                plugin.getLogger().info("AlertListener API не найден (старая версия GrimAC), будет использован Event-based метод.");

                try {
                    Method registerMethod = alertManager.getClass().getMethod("addListener", Object.class);
                    registerMethod.invoke(alertManager, createLegacyListener());
                    plugin.getLogger().info("VAC зарегистрировал legacy Listener в GrimAC!");
                } catch (Exception e2) {
                    plugin.getLogger().warning("Не удалось зарегистрировать слушатель GrimAC: " + e2.getMessage());
                }
            }

            this.grimacEnabled = true;
            plugin.getLogger().info("VAC успешно подключился к GrimAC!");

        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при подключении к GrimAC: " + e.getMessage());
            if (plugin.getConfigManager().isDebug()) {
                e.printStackTrace();
            }
            this.grimacEnabled = false;
        }
    }

    private Object createAlertListener(Class<?> alertListenerClass) {
        try {
            return java.lang.reflect.Proxy.newProxyInstance(
                alertListenerClass.getClassLoader(),
                new Class[]{alertListenerClass},
                (proxy, method, args) -> {
                    if (method.getName().equals("onAlert") && args != null && args.length > 0) {
                        processAlert(args[0]);
                        return null;
                    }
                    if (method.getName().equals("toString")) return "VAC-AlertListener";
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == args[0];
                    return null;
                }
            );
        } catch (Exception e) {
            return null;
        }
    }

    private Object createLegacyListener() {
        return new Object() {
            @Override
            public String toString() {
                return "VAC-LegacyListener";
            }
        };
    }

    private void processAlert(Object alertEvent) {
        try {
            Class<?> eventClass = alertEvent.getClass();
            Object playerObj = eventClass.getMethod("getPlayer").invoke(alertEvent);
            if (!(playerObj instanceof Player)) return;
            Player player = (Player) playerObj;

            String checkName = "unknown";
            int violations = 1;
            String clientType = "UNKNOWN";

            try { checkName = (String) eventClass.getMethod("getCheckName").invoke(alertEvent); } catch (Exception ignored) {}
            try { violations = (int) eventClass.getMethod("getViolations").invoke(alertEvent); } catch (Exception ignored) {}
            try { clientType = (String) eventClass.getMethod("getClientType").invoke(alertEvent); } catch (Exception ignored) {}

            PlayerData data = plugin.getPlayerDataManager().getOrCreate(player);
            if (clientType != null && !clientType.isEmpty() && !clientType.equals("UNKNOWN")) {
                data.setClientType(clientType);
            }
            data.addViolation(checkName, violations);

            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("GrimAC Alert: " + player.getName() + " - " + checkName + " x" + violations
                    + " [Confidence: " + String.format("%.1f", data.getConfidence()) + "%]");
            }

            dispatchAlerts(player, checkName, violations, data.getConfidence());
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebug()) {
                e.printStackTrace();
            }
        }
    }

    private void dispatchAlerts(Player target, String checkName, int violations, double confidence) {
        if (!plugin.getConfigManager().isAlertEnabled()) return;

        if (plugin.getConfigManager().isAlertWebhook() && plugin.getConfigManager().isWebhookEnabled()) {
            plugin.getWebhookManager().sendAlertWebhook(target.getName(), checkName, confidence);
        }

        int thresholdMet = -1;
        int[] thresholds = plugin.getConfigManager().getAlertThresholds();
        for (int i = thresholds.length - 1; i >= 0; i--) {
            if (confidence >= thresholds[i]) { thresholdMet = thresholds[i]; break; }
        }
        if (thresholdMet < 0) return;

        String alertMsg = plugin.getConfigManager().getMessageRaw("alert_message")
                .replace("{player}", target.getName())
                .replace("{check}", checkName)
                .replace("{violations}", String.valueOf(violations))
                .replace("{confidence}", String.format("%.1f", confidence));

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("vac.alerts")) continue;
            if (!plugin.getAlertManager().hasAlertsEnabled(player)) continue;
            if (player.equals(target)) continue;
            player.sendMessage(alertMsg);
        }
    }

    public boolean isGrimacEnabled() {
        return grimacEnabled;
    }

    public void dispatchAlert(Player target, String checkName, int violations, double confidence) {
        dispatchAlerts(target, checkName, violations, confidence);
    }
}
