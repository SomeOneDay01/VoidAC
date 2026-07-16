package vac.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import vac.VAC;
import vac.gui.CheatListGUI;

public class GUIListener implements Listener {

    private final VAC plugin;
    private final CheatListGUI cheatListGUI;

    public GUIListener(VAC plugin, CheatListGUI cheatListGUI) {
        this.plugin = plugin;
        this.cheatListGUI = cheatListGUI;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory inv = event.getInventory();
        String title = event.getView().getTitle();

        if (title.startsWith("§c§lCheat List")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;
            int slot = event.getSlot();
            String itemName = event.getCurrentItem().getItemMeta().getDisplayName();

            if (itemName.equals("§cClose")) {
                player.closeInventory();
                cheatListGUI.removeOpen(player);
                return;
            }

            if (itemName.equals("§a← Previous")) {
                int page = cheatListGUI.getOpenPage(player);
                cheatListGUI.openPage(player, page - 1);
                return;
            }

            if (itemName.equals("§aNext →")) {
                int page = cheatListGUI.getOpenPage(player);
                cheatListGUI.openPage(player, page + 1);
                return;
            }

            int page = cheatListGUI.getOpenPage(player);
            cheatListGUI.handleClick(player, slot, page);
            return;
        }

        if (event.getView().getTitle().startsWith("§c") && event.getInventory().getSize() == 27) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;
            String itemName = event.getCurrentItem().getItemMeta().getDisplayName();

            if (itemName.equals("§7← Back")) {
                cheatListGUI.open(player);
                return;
            }

            if (itemName.equals("§7← Back")) return;

            Player target = null;
            ItemStack center = inv.getItem(13);
            if (center != null && center.hasItemMeta()) {
                String name = center.getItemMeta().getDisplayName();
                name = name.replace("§c", "").replace("§e", "").replace("§a", "");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().equalsIgnoreCase(name)) {
                        target = p;
                        break;
                    }
                }
            }

            if (target == null) {
                player.sendMessage(plugin.getConfigManager().getMessageRaw("player_not_online"));
                player.closeInventory();
                return;
            }

            if (itemName.equals("§bTeleport")) {
                player.teleport(target);
                player.sendMessage("§aTeleported to §c" + target.getName());
                player.closeInventory();
            } else if (itemName.equals("§cBan")) {
                plugin.getPunishmentManager().banPlayer(target, player.getName(), true);
                player.sendMessage(plugin.getConfigManager().getMessage("banned_animation")
                        .replace("{player}", target.getName()));
                player.closeInventory();
            } else if (itemName.equals("§eSpectate")) {
                plugin.getSpectateManager().startSpectate(player, target);
                player.closeInventory();
            } else if (itemName.equals("§aProfile")) {
                player.closeInventory();
                Bukkit.dispatchCommand(player, "vac profile " + target.getName());
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        String title = event.getView().getTitle();
        if (title.startsWith("§c§lCheat List") || title.startsWith("§c")) {
            Player player = (Player) event.getPlayer();
            cheatListGUI.removeOpen(player);
        }
    }
}
