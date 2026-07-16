package vac.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import vac.VAC;

import java.util.Arrays;

public class SettingsGUI {

    private static final String TITLE = "§8§lVAC Settings";

    public static void open(Player player, VAC plugin) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        inv.setItem(10, createItem(Material.REDSTONE, "§c§lУверенность",
                "§7Инкремент: §f" + plugin.getConfigManager().getConfidenceIncrement(),
                "§7Максимум: §f" + plugin.getConfigManager().getMaxConfidence() + "%",
                "§7Порог бана: §f" + plugin.getConfigManager().getBanThreshold() + "%",
                "§7Спад в сек: §f" + plugin.getConfigManager().getConfidenceDecay()));

        inv.setItem(12, createItem(Material.ENDER_PEARL, "§b§lАнимация",
                "§7Статус: " + (plugin.getConfigManager().isAnimationEnabled() ? "§aВкл" : "§cВыкл"),
                "§7Высота подъёма: §f" + plugin.getConfigManager().getAnimationLiftHeight(),
                "§7Длительность: §f" + plugin.getConfigManager().getAnimationDuration() + " тиков"));

        inv.setItem(14, createItem(Material.BOOK, "§e§lАлерты",
                "§7Статус: " + (plugin.getConfigManager().isAlertEnabled() ? "§aВкл" : "§cВыкл")));

        inv.setItem(16, createItem(Material.LEVER, "§a§lАвто-бан",
                "§7Статус: " + (plugin.getConfigManager().isAutoBan() ? "§aВкл" : "§cВыкл")));

        inv.setItem(22, createItem(Material.BARRIER, "§c§lЗакрыть",
                "§7Нажмите чтобы закрыть"));

        player.openInventory(inv);
    }

    private static ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
