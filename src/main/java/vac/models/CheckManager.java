package vac.models;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import vac.VAC;
import vac.util.PacketUtils;

import java.net.URL;
import java.util.*;

public class CheckManager {

    private final VAC plugin;

    public CheckManager(VAC plugin) {
        this.plugin = plugin;
    }

    public void runCheck(Player target) {
        PlayerData data = plugin.getPlayerDataManager().getOrCreate(target);

        String alertMsg = plugin.getConfigManager().getMessageRaw("check_started")
                .replace("{player}", target.getName());
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("vac.admin") || p.hasPermission("vac.alerts")) {
                p.sendMessage(alertMsg);
            }
        }

        new BukkitRunnable() {
            int phase = 0;
            @Override
            public void run() {
                if (!target.isOnline()) { this.cancel(); return; }
                if (phase >= 5) {
                    plugin.getConfigManager().getMessageRaw("check_complete")
                            .replace("{player}", target.getName())
                            .replace("{confidence}", String.format("%.1f", data.getConfidence()));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.hasPermission("vac.admin") || p.hasPermission("vac.alerts")) {
                            p.sendMessage(plugin.getConfigManager().getMessageRaw("check_complete")
                                    .replace("{player}", target.getName())
                                    .replace("{confidence}", String.format("%.1f", data.getConfidence())));
                        }
                    }
                    this.cancel();
                    return;
                }

                Location loc = target.getLocation();
                switch (phase) {
                    case 0:
                        target.setVelocity(target.getVelocity().setY(1.5));
                        sendPosition(target, loc.getX(), loc.getY() + 5, loc.getZ(), loc.getYaw(), loc.getPitch());
                        break;
                    case 1:
                        try {
                            Object bp = vac.util.PacketUtils.createBlockPos(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());
                            if (bp != null) {
                                Class<?> clazz = vac.util.VersionUtil.getNmsClass("PacketPlayOutBlockBreakAnimation");
                                if (clazz != null) {
                                    java.lang.reflect.Constructor<?> ctor = clazz.getConstructor(int.class, bp.getClass(), int.class);
                                    sendPacket(target, ctor.newInstance(0, bp, 5));
                                }
                            }
                        } catch (Exception e) {}
                        break;
                    case 2:
                        sendPosition(target, loc.getX() + 0.5, loc.getY(), loc.getZ() + 0.5, loc.getYaw(), loc.getPitch());
                        break;
                    case 3:
                        for (int i = 0; i < 5; i++) {
                            sendPosition(target, loc.getX() + (Math.random() - 0.5) * 10, loc.getY(), loc.getZ() + (Math.random() - 0.5) * 10, loc.getYaw(), loc.getPitch());
                        }
                        break;
                    case 4:
                        target.setVelocity(new org.bukkit.util.Vector(0, -2, 0));
                        break;
                }
                phase++;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void sendPosition(Player player, double x, double y, double z, float yaw, float pitch) {
        PacketUtils.sendPacket(player, PacketUtils.createPacketPlayOutPosition(x, y, z, yaw, pitch));
    }

    private void sendPacket(Player player, Object packet) {
        PacketUtils.sendPacket(player, packet);
    }

    public void checkVPN(Player target, Player executor) {
        String ip = target.getAddress().getAddress().getHostAddress();
        executor.sendMessage(plugin.getConfigManager().getMessageRaw("vpn_checking")
                .replace("{player}", target.getName())
                .replace("{ip}", ip));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String result = checkVPNIP(ip);
            Bukkit.getScheduler().runTask(plugin, () -> {
                executor.sendMessage(plugin.getConfigManager().getMessageRaw("vpn_result")
                        .replace("{player}", target.getName())
                        .replace("{ip}", ip)
                        .replace("{result}", result));
            });
        });
    }

    private String checkVPNIP(String ip) {
        try {
            URL url = new URL("http://ip-api.com/json/" + ip + "?fields=status,country,proxy,hosting");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            try (java.util.Scanner s = new java.util.Scanner(conn.getInputStream(), "UTF-8")) {
                String json = s.useDelimiter("\\A").next();
                boolean proxy = json.contains("\"proxy\":true");
                boolean hosting = json.contains("\"hosting\":true");
                if (proxy || hosting) {
                    String type = proxy ? "VPN/Proxy" : "Hosting/Дата-центр";
                    return "§c" + type + " §7обнаружен!";
                }
                return "§aНет §7(обычный IP)";
            }
        } catch (Exception e) {
            return "§eОшибка проверки: " + e.getMessage();
        }
    }
}
