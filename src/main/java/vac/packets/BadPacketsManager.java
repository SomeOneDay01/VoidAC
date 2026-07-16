package vac.packets;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import vac.VAC;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BadPacketsManager implements Listener {

    private final VAC plugin;
    private final Map<UUID, PacketStats> playerStats;
    private static final int MAX_PACKETS_PER_SECOND = 200;
    private static final double MAX_MOVE_SPEED = 100.0;
    private static final int MAX_ENCHANT_LEVEL = 10;
    private static final int MAX_ITEM_STACK = 127;

    public BadPacketsManager(VAC plugin) {
        this.plugin = plugin;
        this.playerStats = new HashMap<>();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        PacketStats stats = getStats(player);
        long now = System.currentTimeMillis();

        stats.packetsThisSecond++;
        if (now - stats.lastSecondReset > 1000) {
            if (stats.packetsThisSecond > MAX_PACKETS_PER_SECOND) {
                flag(player, "PacketSpam", stats.packetsThisSecond);
            }
            stats.packetsThisSecond = 0;
            stats.lastSecondReset = now;
        }

        double dx = event.getTo().getX() - event.getFrom().getX();
        double dy = event.getTo().getY() - event.getFrom().getY();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq > MAX_MOVE_SPEED * MAX_MOVE_SPEED) {
            flag(player, "InvalidMove", (int) Math.sqrt(distSq));
            event.setCancelled(true);
        }

        if (event.getTo().getY() < -64 || event.getTo().getY() > 512) {
            flag(player, "OutOfBounds", (int) event.getTo().getY());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryCreative(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCursor();
        if (item == null || item.getType() == Material.AIR) return;

        if (item.getAmount() > MAX_ITEM_STACK) {
            flag(player, "InvalidStackSize", item.getAmount());
            event.setCancelled(true);
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getEnchants() != null) {
            meta.getEnchants().forEach((enchant, level) -> {
                if (level > MAX_ENCHANT_LEVEL) {
                    flag(player, "InvalidEnchant", level);
                    event.setCancelled(true);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (item == null) return;

        if (item.getAmount() > MAX_ITEM_STACK && item.getType() != Material.AIR) {
            flag(player, "InvalidStackSize", item.getAmount());
            event.setCancelled(true);
        }
    }

    private void flag(Player player, String check, int value) {
        plugin.getPlayerDataManager().getOrCreate(player).addViolation("BadPackets_" + check, 1);
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info(player.getName() + " BadPackets " + check + "=" + value);
        }
    }

    private PacketStats getStats(Player player) {
        return playerStats.computeIfAbsent(player.getUniqueId(), k -> new PacketStats());
    }

    private static class PacketStats {
        long lastSecondReset = System.currentTimeMillis();
        int packetsThisSecond;
    }
}
