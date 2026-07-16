package vac.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

public class PacketUtils {

    private static boolean packetEventsChecked;
    private static boolean packetEventsAvailable;
    private static Object packetEventsAPI;
    private static Object protocolManager;

    private static Method playerGetHandle;
    private static FieldAccessor playerConnectionField;
    private static Method sendPacketMethod;
    private static Class<?> packetClass;

    // Cache failed lookups so we don't spam reflection errors every send
    private static final Set<String> failedClasses = new HashSet<>();
    private static final Set<String> failedMethods = new HashSet<>();

    static {
        packetEventsChecked = false;
        packetEventsAvailable = false;
        checkPacketEvents();
    }

    private static void checkPacketEvents() {
        try {
            Class.forName("io.github.retrooper.packetevents.PacketEvents");
            packetEventsAvailable = true;
            Bukkit.getLogger().info("[VAC] PacketEvents найден!");
        } catch (ClassNotFoundException e) {
            packetEventsAvailable = false;
            Bukkit.getLogger().info("[VAC] PacketEvents не найден, используется NMS метод.");
        }
        packetEventsChecked = true;
    }

    public static boolean isPacketEventsAvailable() {
        if (!packetEventsChecked) checkPacketEvents();
        return packetEventsAvailable;
    }

    private static boolean initNMS() {
        if (playerGetHandle != null) return true;
        try {
            packetClass = Class.forName("net.minecraft.server.v1_16_R3.Packet");
            playerGetHandle = Class.forName("org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer")
                    .getMethod("getHandle");
            Object ep = playerGetHandle.invoke(Bukkit.getOnlinePlayers().stream().findAny().orElse(null));
            if (ep == null) {
                playerConnectionField = () -> {
                    try {
                        return Class.forName("net.minecraft.server.v1_16_R3.EntityPlayer")
                                .getField("playerConnection");
                    } catch (Exception e) {
                        return null;
                    }
                };
            } else {
                java.lang.reflect.Field f = ep.getClass().getField("playerConnection");
                playerConnectionField = () -> f;
            }
            sendPacketMethod = Class.forName("net.minecraft.server.v1_16_R3.PlayerConnection")
                    .getMethod("sendPacket", packetClass);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private interface FieldAccessor {
        java.lang.reflect.Field get();
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
            if (!packetEventsAvailable) {
                sendViaNMS(player, nmsPacket);
                return;
            }

            if (packetEventsAPI == null) {
                Class<?> peClass = Class.forName("io.github.retrooper.packetevents.PacketEvents");
                packetEventsAPI = peClass.getMethod("getAPI").invoke(null);
            }

            if (protocolManager == null) {
                protocolManager = packetEventsAPI.getClass()
                        .getMethod("getProtocolManager").invoke(packetEventsAPI);
            }

            protocolManager.getClass()
                    .getMethod("sendPacket", Player.class, Object.class)
                    .invoke(protocolManager, player, nmsPacket);
        } catch (Exception e) {
            sendViaNMS(player, nmsPacket);
        }
    }

    private static void sendViaNMS(Player player, Object packet) {
        try {
            if (playerGetHandle == null) {
                if (!initNMS()) return;
            }
            Object entityPlayer = playerGetHandle.invoke(player);
            Object connection = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);
            sendPacketMethod.invoke(connection, packet);
        } catch (Exception ignored) {}
    }

    // Packet creation utilities for 1.16.5
    public static Object createPacketPlayOutPosition(double x, double y, double z, float yaw, float pitch) {
        try {
            Class<?> clazz = Class.forName("net.minecraft.server.v1_16_R3.PacketPlayOutPosition");
            Class<?> flagsClass = Class.forName("net.minecraft.server.v1_16_R3.PacketPlayOutPosition$EnumPlayerTeleportFlags");
            @SuppressWarnings("unchecked")
            EnumSet<?> flags = EnumSet.noneOf((Class<? extends Enum>) flagsClass);
            Constructor<?> ctor = clazz.getConstructor(
                    double.class, double.class, double.class,
                    float.class, float.class,
                    Collection.class, int.class);
            return ctor.newInstance(x, y, z, yaw, pitch, flags, 0);
        } catch (Exception e) {
            return null;
        }
    }

    public static Object createPacketPlayOutExplosion(double x, double y, double z, float radius, List<Object> blocks,
                                                       float knockbackX, float knockbackY, float knockbackZ) {
        try {
            Class<?> clazz = Class.forName("net.minecraft.server.v1_16_R3.PacketPlayOutExplosion");
            Constructor<?> ctor = clazz.getConstructor(
                    double.class, double.class, double.class,
                    float.class, List.class,
                    float.class, float.class, float.class);
            return ctor.newInstance(x, y, z, radius, blocks != null ? blocks : new ArrayList<>(),
                    knockbackX, knockbackY, knockbackZ);
        } catch (Exception e) {
            return null;
        }
    }

    public static Object createPacketPlayOutWorldParticles(String particleName, float x, float y, float z,
                                                            int count, float offX, float offY, float offZ, float speed) {
        try {
            Class<?> clazz = Class.forName("net.minecraft.server.v1_16_R3.PacketPlayOutWorldParticles");

            Object particle = getParticleHandle(particleName);
            if (particle == null) return null;

            Constructor<?> ctor = clazz.getConstructor(
                    Class.forName("net.minecraft.server.v1_16_R3.Particle"),
                    boolean.class, float.class, float.class, float.class,
                    float.class, float.class, float.class, float.class, int.class);
            return ctor.newInstance(particle, true, x, y, z, offX, offY, offZ, speed, count);
        } catch (Exception e) {
            return null;
        }
    }

    public static Object createPacketPlayOutOpenSignEditor(int x, int y, int z) {
        try {
            Class<?> clazz = Class.forName("net.minecraft.server.v1_16_R3.PacketPlayOutOpenSignEditor");
            Class<?> bpClass = Class.forName("net.minecraft.server.v1_16_R3.BlockPosition");
            Constructor<?> bpCtor = bpClass.getConstructor(int.class, int.class, int.class);
            Constructor<?> ctor = clazz.getConstructor(bpClass);
            return ctor.newInstance(bpCtor.newInstance(x, y, z));
        } catch (Exception e) {
            return null;
        }
    }

    public static Object createPacketPlayOutBlockChange(int x, int y, int z, int blockId, int blockData) {
        try {
            Class<?> clazz = Class.forName("net.minecraft.server.v1_16_R3.PacketPlayOutBlockChange");
            Class<?> bpClass = Class.forName("net.minecraft.server.v1_16_R3.BlockPosition");
            Class<?> ibdClass = Class.forName("net.minecraft.server.v1_16_R3.IBlockData");

            Constructor<?> bpCtor = bpClass.getConstructor(int.class, int.class, int.class);
            Object bp = bpCtor.newInstance(x, y, z);

            Class<?> blocksClass = Class.forName("net.minecraft.server.v1_16_R3.Block");
            Object block = blocksClass.getMethod("getByCombinedId", int.class).invoke(null,
                    blockId + (blockData << 12));

            Constructor<?> ctor = clazz.getConstructor(bpClass, ibdClass);
            return ctor.newInstance(bp, block);
        } catch (Exception e) {
            return null;
        }
    }

    public static Object createPacketPlayOutEntityMetadata(int entityId, Object dataWatcher) {
        try {
            Class<?> clazz = Class.forName("net.minecraft.server.v1_16_R3.PacketPlayOutEntityMetadata");
            Constructor<?> ctor = clazz.getConstructor(int.class, Class.forName("net.minecraft.server.v1_16_R3.DataWatcher"), boolean.class);
            return ctor.newInstance(entityId, dataWatcher, true);
        } catch (Exception e) {
            return null;
        }
    }

    public static Object createNMSItemStack(org.bukkit.inventory.ItemStack bukkitItem) {
        try {
            Class<?> cisClass = Class.forName("org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack");
            return cisClass.getMethod("asNMSCopy", org.bukkit.inventory.ItemStack.class).invoke(null, bukkitItem);
        } catch (Exception e) {
            return null;
        }
    }

    public static Object createPacketPlayOutSetSlot(int windowId, int slot, org.bukkit.inventory.ItemStack item) {
        try {
            Class<?> clazz = Class.forName("net.minecraft.server.v1_16_R3.PacketPlayOutSetSlot");
            Object nmsItem = createNMSItemStack(item);
            if (nmsItem == null) return null;
            Constructor<?> ctor = clazz.getConstructor(int.class, int.class,
                    Class.forName("net.minecraft.server.v1_16_R3.ItemStack"));
            return ctor.newInstance(windowId, slot, nmsItem);
        } catch (Exception e) {
            return null;
        }
    }

    public static Object createBlockPos(int x, int y, int z) {
        try {
            Class<?> bpClass = Class.forName("net.minecraft.server.v1_16_R3.BlockPosition");
            return bpClass.getConstructor(int.class, int.class, int.class).newInstance(x, y, z);
        } catch (Exception e) {
            return null;
        }
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
            Class<?> particlesClass = Class.forName("net.minecraft.server.v1_16_R3.Particles");
            java.lang.reflect.Field field = particlesClass.getField(name.toUpperCase());
            return field.get(null);
        } catch (Exception e) {
            try {
                Class<?> particlesClass = Class.forName("net.minecraft.server.v1_16_R3.Particles");
                java.lang.reflect.Field field = particlesClass.getField("FLAME");
                return field.get(null);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
