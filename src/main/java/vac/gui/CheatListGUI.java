package vac.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import vac.VAC;
import vac.killaura.KillAuraAnalyzer;

import java.util.*;
import java.util.stream.Collectors;

public class CheatListGUI {

    private final VAC plugin;
    private final Map<UUID, Integer> openPages = new HashMap<>();

    private static final int SLOTS = 54;
    private static final int PER_PAGE = 45;

    public CheatListGUI(VAC plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        openPages.put(player.getUniqueId(), 0);
        openPage(player, 0);
    }

    public void openPage(Player player, int page) {
        List<CheatEntry> entries = getSortedCheatEntries();
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) PER_PAGE));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;
        openPages.put(player.getUniqueId(), page);

        String title = "§c§lCheat List §7(" + (page + 1) + "/" + totalPages + ")";
        Inventory inv = Bukkit.createInventory(null, SLOTS, title);

        int start = page * PER_PAGE;
        int end = Math.min(start + PER_PAGE, entries.size());

        for (int i = start; i < end; i++) {
            CheatEntry entry = entries.get(i);
            int slot = (i - start);
            inv.setItem(slot, createPlayerHead(entry));
        }

        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            prevMeta.setDisplayName("§a← Previous");
            prev.setItemMeta(prevMeta);
            inv.setItem(48, prev);
        }

        if (page + 1 < totalPages) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            nextMeta.setDisplayName("§aNext →");
            next.setItemMeta(nextMeta);
            inv.setItem(50, next);
        }

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName("§cClose");
        close.setItemMeta(closeMeta);
        inv.setItem(49, close);

        player.openInventory(inv);
    }

    public boolean handleClick(Player player, int slot, int page) {
        if (slot >= 0 && slot < PER_PAGE) {
            List<CheatEntry> entries = getSortedCheatEntries();
            int index = page * PER_PAGE + slot;
            if (index >= 0 && index < entries.size()) {
                CheatEntry entry = entries.get(index);
                openPlayerMenu(player, entry);
                return true;
            }
        }
        return false;
    }

    public int getOpenPage(Player player) {
        return openPages.getOrDefault(player.getUniqueId(), 0);
    }

    public void removeOpen(Player player) {
        openPages.remove(player.getUniqueId());
    }

    private void openPlayerMenu(Player admin, CheatEntry entry) {
        Player target = Bukkit.getPlayer(entry.uuid);
        if (target == null || !target.isOnline()) {
            admin.sendMessage(plugin.getConfigManager().getMessageRaw("player_not_online"));
            return;
        }

        Inventory menu = Bukkit.createInventory(null, 27, "§c" + target.getName());

        ItemStack head = createPlayerHead(entry);
        menu.setItem(13, head);

        ItemStack teleport = new ItemStack(Material.ENDER_PEARL);
        ItemMeta tpMeta = teleport.getItemMeta();
        tpMeta.setDisplayName("§bTeleport");
        teleport.setItemMeta(tpMeta);
        menu.setItem(10, teleport);

        ItemStack ban = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta banMeta = ban.getItemMeta();
        banMeta.setDisplayName("§cBan");
        ban.setItemMeta(banMeta);
        menu.setItem(12, ban);

        ItemStack spectate = new ItemStack(Material.COMPASS);
        ItemMeta specMeta = spectate.getItemMeta();
        specMeta.setDisplayName("§eSpectate");
        spectate.setItemMeta(specMeta);
        menu.setItem(14, spectate);

        ItemStack profile = new ItemStack(Material.BOOK);
        ItemMeta profMeta = profile.getItemMeta();
        profMeta.setDisplayName("§aProfile");
        profile.setItemMeta(profMeta);
        menu.setItem(16, profile);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§7← Back");
        back.setItemMeta(backMeta);
        menu.setItem(22, back);

        admin.openInventory(menu);
    }

    private ItemStack createPlayerHead(CheatEntry entry) {
        ItemStack head;
        Player target = Bukkit.getPlayer(entry.uuid);
        if (target != null && target.isOnline()) {
            head = new ItemStack(Material.PLAYER_HEAD, 1, (short) 3);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.setDisplayName(getColorForProb(entry.probability) + entry.name);
            meta.setLore(Arrays.asList(
                "§7Probability: " + getColorForProb(entry.probability) + String.format("%.1f%%", entry.probability * 100),
                "§7CPS: §f" + String.format("%.1f", entry.cps),
                "§7Reach: §f" + String.format("%.2f", entry.reach),
                "§7Aim: §f" + String.format("%.4f", entry.aimDev),
                "§7Click to manage"
            ));
            head.setItemMeta(meta);
        } else {
            head = new ItemStack(Material.SKELETON_SKULL);
            ItemMeta meta = head.getItemMeta();
            meta.setDisplayName("§7" + entry.name + " §c(offline)");
            head.setItemMeta(meta);
        }
        return head;
    }

    private String getColorForProb(double prob) {
        if (prob > 0.7) return "§c";
        else if (prob > 0.3) return "§e";
        else return "§a";
    }

    private List<CheatEntry> getSortedCheatEntries() {
        List<CheatEntry> entries = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            double prob = 0;
            double cps = 0;
            double reach = 0;
            double aimDev = 0;

            try {
                prob = plugin.getOnlineGaussianClassifier().predict(
                        plugin.getFeatureExtractor().extract(player, null));
                KillAuraAnalyzer.HitStats stats = plugin.getKillAuraAnalyzer().getStats(player);
                cps = stats.cps;
                reach = stats.avgReach;
                aimDev = stats.avgAimDev;
            } catch (Exception ignored) {}

            entries.add(new CheatEntry(
                player.getUniqueId(), player.getName(), prob, cps, reach, aimDev
            ));
        }

        entries.sort((a, b) -> Double.compare(b.probability, a.probability));
        return entries;
    }

    public static class CheatEntry {
        public final UUID uuid;
        public final String name;
        public final double probability;
        public final double cps;
        public final double reach;
        public final double aimDev;

        CheatEntry(UUID uuid, String name, double prob, double cps, double reach, double aimDev) {
            this.uuid = uuid;
            this.name = name;
            this.probability = prob;
            this.cps = cps;
            this.reach = reach;
            this.aimDev = aimDev;
        }
    }
}
