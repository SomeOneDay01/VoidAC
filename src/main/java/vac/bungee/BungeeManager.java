package vac.bungee;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import vac.VAC;

public class BungeeManager implements PluginMessageListener {

    private static final String CHANNEL = "vac:main";
    private final VAC plugin;
    private boolean enabled;

    public BungeeManager(VAC plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        enabled = true;
        plugin.getLogger().info("Bungee/Velocity messaging registered on channel " + CHANNEL);
    }

    public void disable() {
        if (enabled) {
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
            plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
            enabled = false;
        }
    }

    public boolean isEnabled() { return enabled; }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(CHANNEL)) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subChannel = in.readUTF();

        switch (subChannel) {
            case "Ban":
                receiveBan(in);
                break;
            case "Alert":
                receiveAlert(in);
                break;
            case "Freeze":
                receiveFreeze(in);
                break;
            case "Unfreeze":
                receiveUnfreeze(in);
                break;
        }
    }

    public void sendBan(String playerName, String reason, String bannedBy, double confidence) {
        if (!enabled) return;
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Ban");
        out.writeUTF(playerName);
        out.writeUTF(reason != null ? reason : "Cheating detected");
        out.writeUTF(bannedBy);
        out.writeDouble(confidence);
        sendMessage(out);
    }

    public void sendAlert(String playerName, String checkName, double confidence) {
        if (!enabled) return;
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Alert");
        out.writeUTF(playerName);
        out.writeUTF(checkName);
        out.writeDouble(confidence);
        sendMessage(out);
    }

    public void sendFreeze(String playerName) {
        if (!enabled) return;
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Freeze");
        out.writeUTF(playerName);
        sendMessage(out);
    }

    public void sendUnfreeze(String playerName) {
        if (!enabled) return;
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Unfreeze");
        out.writeUTF(playerName);
        sendMessage(out);
    }

    private void sendMessage(ByteArrayDataOutput out) {
        Player first = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (first != null) {
            first.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
        }
    }

    private void receiveBan(ByteArrayDataInput in) {
        String playerName = in.readUTF();
        String reason = in.readUTF();
        String bannedBy = in.readUTF();
        double confidence = in.readDouble();

        Player target = Bukkit.getPlayerExact(playerName);
        if (target != null && target.isOnline()) {
            plugin.getPunishmentManager().banPlayer(target, bannedBy + " (proxy)", false);
        }
    }

    private void receiveAlert(ByteArrayDataInput in) {
        String playerName = in.readUTF();
        String checkName = in.readUTF();
        double confidence = in.readDouble();

        String msg = plugin.getConfigManager().getMessageRaw("alert_message")
                .replace("{player}", playerName)
                .replace("{check}", checkName)
                .replace("{confidence}", String.format("%.1f", confidence));
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("vac.alerts"))
                .filter(p -> plugin.getAlertManager().hasAlertsEnabled(p))
                .forEach(p -> p.sendMessage(msg));
    }

    private void receiveFreeze(ByteArrayDataInput in) {
        String playerName = in.readUTF();
        Player target = Bukkit.getPlayerExact(playerName);
        if (target != null && target.isOnline()) {
            plugin.getFreezeManager().freeze(target);
        }
    }

    private void receiveUnfreeze(ByteArrayDataInput in) {
        String playerName = in.readUTF();
        Player target = Bukkit.getPlayerExact(playerName);
        if (target != null && target.isOnline()) {
            plugin.getFreezeManager().unfreeze(target);
        }
    }
}
