package vac.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class PacketUtils {

    private static boolean packetEventsChecked;
    private static boolean packetEventsAvailable;
    private static Object packetEventsAPI;
    private static Object protocolManager;

    private static Method playerGetHandle;
    private static Field playerConnectionField;
    private static Method sendPacketMethod;
    private static Class<?> packetClass;

    public static boolean isPacketEventsAvailable() {
        if (!packetEventsChecked) checkPacketEvents();
        return packetEventsAvailable;
    }

    private static void checkPacketEvents() {
        try {
            Class.forName("io.github.retrooper.packetevents.PacketEvents");
            packetEventsAvailable = true;
            Bukkit.getLogger().info("[VAC] PacketEvents found!");
        } catch (ClassNotFoundException e) {
            packetEventsAvailable = false;
            Bukkit.getLogger().info("[VAC] PacketEvents not found, using NMS reflection.");
        }
        packetEventsChecked = true;
    }

    private static boolean initNMS() {
        if (playerGetHandle != null && sendPacketMethod != null) return true;
        try {
            VersionUtil.init();
            Class<?> craftPlayer = VersionUtil.getCraftClass("entity.CraftPlayer");
            if (craftPlayer == null) return false;
            playerGetHandle = craftPlayer.getMethod("getHandle");

            packetClass = VersionUtil.getNmsClass("Packet");
            if (packetClass == null) packetClass = Class.forName("net.minecraft.network.protocol.Packet");

            Object ep = playerGetHandle.invoke(Bukkit.getOnlinePlayers().stream().findAny().orElse(null));
            if (ep != null) {
                playerConnectionField = findField(ep.getClass(), "playerConnection", "connection");
            } else {
                Class<?> entityPlayer = VersionUtil.getNmsClass("EntityPlayer");
                if (entityPlayer == null) return false;
                playerConnectionField = findField(entityPlayer, "playerConnection", "connection");
            }
            if (playerConnectionField == null) return false;

            Class<?> connClass = playerConnectionField.getType();
            sendPacketMethod = findMethod(connClass, packetClass, "sendPacket", "send");
            return sendPacketMethod != null;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[VAC] NMS init failed: " + e.getMessage());
            return false;
        }
    }

    private static Field findField(Class<?> clazz, String... names) {
        for (String name : names) {
            try { return clazz.getField(name); } catch (NoSuchFieldException ignored) {}
            try { return clazz.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, Class<?> paramType, String... names) {
        for (String name : names) {
            try { return clazz.getMethod(name, paramType); } catch (NoSuchMethodException ignored) {}
            try { return clazz.getDeclaredMethod(name, paramType); } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    public static void sendPacket(Player player, Object nmsPacket) {
        if (nmsPacket == null) return;
        if (isPacketEventsAvailable()) {
            sendViaPacketEvents(player, nmsPacket);
        } else {
            sendViaNMS(player, nmsPacket);
        }
    }

    private static void sendViaPacketEvents(Player player, Object nmsPacket) {
        try {
            if (!packetEventsChecked) checkPacketEvents();
            if (!packetEventsAvailable) { sendViaNMS(player, nmsPacket); return; }
            if (packetEventsAPI == null) {
                Class<?> peClass = Class.forName("io.github.retrooper.packetevents.PacketEvents");
                packetEventsAPI = peClass.getMethod("getAPI").invoke(null);
            }
            if (protocolManager == null) {
                protocolManager = packetEventsAPI.getClass().getMethod("getProtocolManager").invoke(packetEventsAPI);
            }
            protocolManager.getClass().getMethod("sendPacket", Player.class, Object.class).invoke(protocolManager, player, nmsPacket);
        } catch (Exception e) {
            sendViaNMS(player, nmsPacket);
        }
    }

    private static void sendViaNMS(Player player, Object packet) {
        try {
            if (!initNMS()) return;
            Object entityPlayer = playerGetHandle.invoke(player);
            Object connection = playerConnectionField.get(entityPlayer);
            sendPacketMethod.invoke(connection, packet);
        } catch (Exception ignored) {}
    }

    public static Object createPacketPlayOutPosition(double x, double y, double z, float yaw, float pitch) {
        try {
            Class<?> clazz = VersionUtil.getNmsClass("PacketPlayOutPosition");
            if (clazz == null) return null;
            Class<?> flagsClass = null;
            try {
                flagsClass = VersionUtil.getNmsClass("EnumPlayerTeleportFlags");
            } catch (Exception e) {
                for (Class<?> inner : clazz.getDeclaredClasses()) {
                    if (inner.getSimpleName().contains("Flags") || inner.getSimpleName().contains("Relative")) {
                        flagsClass = inner; break;
                    }
                }
            }
            if (flagsClass == null) return null;
            @SuppressWarnings("unchecked")
            EnumSet<?> flags = EnumSet.noneOf((Class<? extends Enum>) flagsClass);
            Constructor<?> ctor = clazz.getConstructor(double.class, double.class, double.class, float.class, float.class, Collection.class, int.class);
            return ctor.newInstance(x, y, z, yaw, pitch, flags, 0);
        } catch (Exception e) { return null; }
    }

    public static Object createPacketPlayOutExplosion(double x, double y, double z, float radius, List<Object> blocks,
                                                       float knockbackX, float knockbackY, float knockbackZ) {
        try {
            Class<?> clazz = VersionUtil.getNmsClass("PacketPlayOutExplosion");
            if (clazz == null) return null;

            // 1.16-1.20.4: (double, double, double, float, List, float, float, float)
            try {
                Constructor<?> ctor = clazz.getConstructor(double.class, double.class, double.class, float.class, List.class, float.class, float.class, float.class);
                return ctor.newInstance(x, y, z, radius, blocks != null ? blocks : new ArrayList<>(), knockbackX, knockbackY, knockbackZ);
            } catch (NoSuchMethodException ignored) {}

            // 1.20.5+: (double, double, double, float, List, Optional, ParticleOptions, SoundEvent)
            List<Object> blockList = blocks != null ? blocks : new ArrayList<>();
            Optional<?> emptyOptional = Optional.empty();
            Constructor<?> ctor = clazz.getConstructor(double.class, double.class, double.class, float.class, List.class, Optional.class, Object.class, Object.class);
            return ctor.newInstance(x, y, z, radius, blockList, emptyOptional, null, null);
        } catch (Exception e) { return null; }
    }

    public static Object createPacketPlayOutWorldParticles(String particleName, float x, float y, float z,
                                                            int count, float offX, float offY, float offZ, float speed) {
        try {
            Class<?> clazz = VersionUtil.getNmsClass("PacketPlayOutWorldParticles");
            if (clazz == null) return null;
            Object particle = getParticleHandle(particleName);
            if (particle == null) return null;

            // Try old constructor: (Particle, boolean, float, float, float, float, float, float, float, int)
            try {
                Class<?> particleClass = VersionUtil.getNmsClass("Particle");
                if (particleClass == null) particleClass = particle.getClass();
                Constructor<?> ctor = clazz.getConstructor(particleClass, boolean.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class, int.class);
                return ctor.newInstance(particle, true, x, y, z, offX, offY, offZ, speed, count);
            } catch (NoSuchMethodException ignored) {}

            // Try with Object as particle type (1.19.3+ where Particle is an interface with subclasses)
            Constructor<?> ctor = clazz.getConstructor(Object.class, boolean.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class, int.class);
            return ctor.newInstance(particle, true, x, y, z, offX, offY, offZ, speed, count);
        } catch (Exception e) { return null; }
    }

    public static Object createPacketPlayOutOpenSignEditor(int x, int y, int z) {
        try {
            Class<?> clazz = VersionUtil.getNmsClass("PacketPlayOutOpenSignEditor");
            if (clazz == null) return null;
            Class<?> bpClass = VersionUtil.getNmsClass("BlockPosition");
            if (bpClass == null) return null;
            Constructor<?> bpCtor = bpClass.getConstructor(int.class, int.class, int.class);
            Object bp = bpCtor.newInstance(x, y, z);

            // 1.16-1.19: (BlockPos)
            try {
                Constructor<?> ctor = clazz.getConstructor(bpClass);
                return ctor.newInstance(bp);
            } catch (NoSuchMethodException ignored) {}

            // 1.20+: (BlockPos, boolean)
            Constructor<?> ctor = clazz.getConstructor(bpClass, boolean.class);
            return ctor.newInstance(bp, true);
        } catch (Exception e) { return null; }
    }

    public static Object createPacketPlayOutBlockChange(int x, int y, int z, Material material) {
        try {
            Class<?> clazz = VersionUtil.getNmsClass("PacketPlayOutBlockChange");
            if (clazz == null) return null;
            Class<?> bpClass = VersionUtil.getNmsClass("BlockPosition");
            if (bpClass == null) return null;
            Class<?> ibdClass = VersionUtil.getNmsClass("IBlockData");
            if (ibdClass == null) return null;

            Constructor<?> bpCtor = bpClass.getConstructor(int.class, int.class, int.class);
            Object bp = bpCtor.newInstance(x, y, z);

            Object blockData = getNMSBlockData(material);
            if (blockData == null) return null;

            Constructor<?> ctor = clazz.getConstructor(bpClass, ibdClass);
            return ctor.newInstance(bp, blockData);
        } catch (Exception e) { return null; }
    }

    private static Object getNMSBlockData(Material material) {
        try {
            BlockData bukkitData = material.createBlockData();
            if (bukkitData == null) return null;

            Class<?> craftBlockDataClass = VersionUtil.getCraftClass("block.data.CraftBlockData");
            if (craftBlockDataClass != null && craftBlockDataClass.isInstance(bukkitData)) {
                return bukkitData;
            }

            Method createNew = Objects.requireNonNull(craftBlockDataClass).getMethod("createNew", Material.class);
            return createNew.invoke(null, material);
        } catch (Exception e) {
            try {
                Class<?> blocksClass = VersionUtil.getNmsClass("Block");
                if (blocksClass == null) return null;
                Material stoneType = material.isBlock() ? material : Material.STONE;
                String fieldName = stoneType.name();
                Object nmsBlock = blocksClass.getField(fieldName).get(null);
                Method defaultState = nmsBlock.getClass().getMethod("getBlockData");
                return defaultState.invoke(nmsBlock);
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static Object createPacketPlayOutEntityMetadata(int entityId, Object dataWatcher) {
        try {
            Class<?> clazz = VersionUtil.getNmsClass("PacketPlayOutEntityMetadata");
            if (clazz == null) return null;

            Class<?> dwClass = VersionUtil.getNmsClass("DataWatcher");
            if (dwClass != null) {
                try {
                    Constructor<?> ctor = clazz.getConstructor(int.class, dwClass, boolean.class);
                    return ctor.newInstance(entityId, dataWatcher, true);
                } catch (NoSuchMethodException ignored) {}
            }

            // Try alternative: (int, List) — newer versions
            Constructor<?> ctor = clazz.getConstructor(int.class, List.class);
            return ctor.newInstance(entityId, new ArrayList<>());
        } catch (Exception e) { return null; }
    }

    public static Object createNMSItemStack(org.bukkit.inventory.ItemStack bukkitItem) {
        try {
            Class<?> cisClass = VersionUtil.getCraftClass("inventory.CraftItemStack");
            if (cisClass == null) cisClass = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
            return cisClass.getMethod("asNMSCopy", org.bukkit.inventory.ItemStack.class).invoke(null, bukkitItem);
        } catch (Exception e) { return null; }
    }

    public static Object createPacketPlayOutSetSlot(int windowId, int slot, org.bukkit.inventory.ItemStack item) {
        try {
            Class<?> clazz = VersionUtil.getNmsClass("PacketPlayOutSetSlot");
            if (clazz == null) return null;
            Object nmsItem = createNMSItemStack(item);
            if (nmsItem == null) return null;
            Class<?> itemClass = VersionUtil.getNmsClass("ItemStack");
            if (itemClass == null) itemClass = nmsItem.getClass();

            // 1.16-1.17: (int, int, ItemStack)
            try {
                Constructor<?> ctor = clazz.getConstructor(int.class, int.class, itemClass);
                return ctor.newInstance(windowId, slot, nmsItem);
            } catch (NoSuchMethodException ignored) {}

            // 1.18+: (int, int, int, ItemStack) — adds stateId
            Constructor<?> ctor = clazz.getConstructor(int.class, int.class, int.class, itemClass);
            return ctor.newInstance(windowId, 0, slot, nmsItem);
        } catch (Exception e) { return null; }
    }

    public static Object createBlockPos(int x, int y, int z) {
        try {
            Class<?> bpClass = VersionUtil.getNmsClass("BlockPosition");
            if (bpClass == null) {
                bpClass = Class.forName("net.minecraft.core.BlockPosition");
            }
            return bpClass.getConstructor(int.class, int.class, int.class).newInstance(x, y, z);
        } catch (Exception e) { return null; }
    }

    public static List<Object> createExplodedBlockPositions(int centerX, int centerY, int centerZ, int radius) {
        List<Object> list = new ArrayList<>();
        Random rand = new Random();
        int count = Math.min(radius * radius * radius / 8, 2000);
        for (int i = 0; i < count; i++) {
            int x = centerX + rand.nextInt(radius * 2) - radius;
            int y = centerY + rand.nextInt(radius * 2) - radius;
            int z = centerZ + rand.nextInt(radius * 2) - radius;
            Object bp = createBlockPos(x, y, z);
            if (bp != null) list.add(bp);
        }
        return list;
    }

    private static Object getParticleHandle(String name) {
        try {
            Class<?> particlesClass = VersionUtil.getNmsClass("Particles");
            if (particlesClass == null) {
                particlesClass = Class.forName("net.minecraft.core.particles.Particles");
            }
            Field field = particlesClass.getField(name.toUpperCase());
            return field.get(null);
        } catch (Exception e) {
            try {
                Class<?> particlesClass = VersionUtil.getNmsClass("Particles");
                if (particlesClass == null) return null;
                Field field = particlesClass.getField("FLAME");
                return field.get(null);
            } catch (Exception ex) { return null; }
        }
    }

    @Deprecated
    public static Object createPacketPlayOutBlockChange(int x, int y, int z, int blockId, int blockData) {
        return createPacketPlayOutBlockChange(x, y, z, blockId >= 0 && blockData >= 0 ? Material.STONE : Material.AIR);
    }
}
