package vac.util;

import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;

public class VersionUtil {

    private static String nmsVersion;
    private static String mcVersion;
    private static int majorVer;
    private static int minorVer;
    private static int patchVer;
    private static boolean initialized;
    private static boolean useMojangMappings;

    public static void init() {
        if (initialized) return;
        initialized = true;

        String pkg = Bukkit.getServer().getClass().getPackage().getName();
        String rawVersion = pkg.substring(pkg.lastIndexOf('.') + 1);

        if ("craftbukkit".equals(rawVersion)) {
            useMojangMappings = true;
            nmsVersion = "";
        } else {
            nmsVersion = rawVersion;
        }

        String bukkitVer = Bukkit.getBukkitVersion();
        String[] parts = bukkitVer.split("-")[0].split("\\.");
        majorVer = parseInt(parts[0]);
        minorVer = parts.length > 1 ? parseInt(parts[1]) : 0;
        patchVer = parts.length > 2 ? parseInt(parts[2]) : 0;

        mcVersion = majorVer + "." + minorVer + (patchVer > 0 ? "." + patchVer : "");

        Bukkit.getLogger().info("[VAC] Detected: Minecraft " + mcVersion
                + (useMojangMappings ? " (Mojang mappings)" : " (NMS: " + nmsVersion + ")"));
    }

    public static String getNmsVersion() { return nmsVersion; }
    public static String getMcVersion() { return mcVersion; }
    public static int getMajor() { return majorVer; }
    public static int getMinor() { return minorVer; }
    public static boolean isMojangMappings() { return useMojangMappings; }

    public static Class<?> getNmsClass(String className) {
        if (!useMojangMappings && nmsVersion != null && !nmsVersion.isEmpty()) {
            try {
                return Class.forName("net.minecraft.server." + nmsVersion + "." + className);
            } catch (ClassNotFoundException ignored) {}
        }
        return tryMojangClass(className);
    }

    public static Class<?> getCraftClass(String className) {
        if (useMojangMappings) {
            try {
                return Class.forName("org.bukkit.craftbukkit." + className);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
        try {
            return Class.forName("org.bukkit.craftbukkit." + nmsVersion + "." + className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Class<?> tryMojangClass(String oldName) {
        String newName = MOJANG_MAP.get(oldName);
        if (newName != null) {
            try { return Class.forName(newName); } catch (ClassNotFoundException ignored) {}
        }

        String alt = MOJANG_ALT.get(oldName);
        if (alt != null) {
            try { return Class.forName(alt); } catch (ClassNotFoundException ignored) {}
        }

        if (oldName.startsWith("Packet")) {
            String guess = "net.minecraft.network.protocol.game." + oldName;
            try { return Class.forName(guess); } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    private static final Map<String, String> MOJANG_MAP = new HashMap<>();
    private static final Map<String, String> MOJANG_ALT = new HashMap<>();

    static {
        MOJANG_MAP.put("EntityPlayer", "net.minecraft.server.level.ServerPlayer");
        MOJANG_MAP.put("PlayerConnection", "net.minecraft.server.network.ServerGamePacketListenerImpl");
        MOJANG_MAP.put("Packet", "net.minecraft.network.protocol.Packet");
        MOJANG_MAP.put("PacketPlayOutPosition", "net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket");
        MOJANG_MAP.put("PacketPlayOutExplosion", "net.minecraft.network.protocol.game.ClientboundExplodePacket");
        MOJANG_MAP.put("PacketPlayOutWorldParticles", "net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket");
        MOJANG_MAP.put("PacketPlayOutBlockChange", "net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket");
        MOJANG_MAP.put("PacketPlayOutOpenSignEditor", "net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket");
        MOJANG_MAP.put("PacketPlayOutEntityMetadata", "net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
        MOJANG_MAP.put("PacketPlayOutSetSlot", "net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket");
        MOJANG_MAP.put("PacketPlayOutTileEntityData", "net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket");
        MOJANG_MAP.put("PacketPlayOutBlockBreakAnimation", "net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket");
        MOJANG_MAP.put("BlockPosition", "net.minecraft.core.BlockPos");
        MOJANG_MAP.put("IBlockData", "net.minecraft.world.level.block.state.BlockState");
        MOJANG_MAP.put("NBTTagCompound", "net.minecraft.nbt.CompoundTag");
        MOJANG_MAP.put("CraftItemStack", "org.bukkit.craftbukkit.inventory.CraftItemStack");
        MOJANG_MAP.put("ItemStack", "net.minecraft.world.item.ItemStack");
        MOJANG_MAP.put("Particles", "net.minecraft.core.particles.ParticleTypes");
        MOJANG_MAP.put("Particle", "net.minecraft.core.particles.Particle");
        MOJANG_MAP.put("Block", "net.minecraft.world.level.block.Block");
        MOJANG_MAP.put("EnumPlayerTeleportFlags", "net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket$RelativeArgument");
        MOJANG_MAP.put("DataWatcher", "net.minecraft.network.syncher.SynchedEntityData");
        MOJANG_MAP.put("SoundEffect", "net.minecraft.sounds.SoundEvent");
        MOJANG_MAP.put("Vec3D", "net.minecraft.world.phys.Vec3");

        MOJANG_ALT.put("BlockPosition", "net.minecraft.core.BlockPosition");
        MOJANG_ALT.put("DataWatcher", "net.minecraft.network.syncher.DataWatcher");
        MOJANG_ALT.put("IBlockData", "net.minecraft.world.level.block.state.IBlockData");
        MOJANG_ALT.put("NBTTagCompound", "net.minecraft.nbt.NBTTagCompound");
        MOJANG_ALT.put("Particles", "net.minecraft.core.particles.Particles");
        MOJANG_ALT.put("EnumPlayerTeleportFlags", "net.minecraft.network.protocol.game.PacketPlayOutPosition$EnumPlayerTeleportFlags");
    }

    public static boolean isNewMappings() {
        return useMojangMappings
                || getMajor() > 1
                || (getMajor() == 1 && getMinor() >= 21)
                || (getMajor() == 1 && getMinor() == 20 && patchVer >= 5);
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
}
