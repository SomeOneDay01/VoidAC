package vac.antixray;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import vac.VAC;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AntiXrayManager implements Listener {

    private final VAC plugin;

    private static final Set<Material> ORES = new HashSet<>(Arrays.asList(
        Material.DIAMOND_ORE, Material.EMERALD_ORE,
        Material.GOLD_ORE, Material.IRON_ORE,
        Material.COAL_ORE, Material.LAPIS_ORE,
        Material.REDSTONE_ORE, Material.NETHER_GOLD_ORE,
        Material.NETHER_QUARTZ_ORE, Material.ANCIENT_DEBRIS
    ));

    static {
        tryAdd(Material.class, "DEEPSLATE_DIAMOND_ORE");
        tryAdd(Material.class, "DEEPSLATE_EMERALD_ORE");
        tryAdd(Material.class, "DEEPSLATE_GOLD_ORE");
        tryAdd(Material.class, "DEEPSLATE_IRON_ORE");
        tryAdd(Material.class, "DEEPSLATE_COAL_ORE");
        tryAdd(Material.class, "DEEPSLATE_LAPIS_ORE");
        tryAdd(Material.class, "DEEPSLATE_REDSTONE_ORE");
        tryAdd(Material.class, "COPPER_ORE");
        tryAdd(Material.class, "DEEPSLATE_COPPER_ORE");
    }

    private static void tryAdd(Class<?> clazz, String field) {
        try { ORES.add((Material) clazz.getField(field).get(null)); } catch (Exception ignored) {}
    }

    private static final Set<Material> TRANSPARENT = new HashSet<>(Arrays.asList(
            Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
            Material.WATER, Material.LAVA, Material.VINE
    ));
    static {
        tryAddTrans("GRASS");
        tryAddTrans("TALL_GRASS");
        tryAddTrans("SHORT_GRASS");
    }

    private static void tryAddTrans(String name) {
        try { TRANSPARENT.add((Material) Material.class.getField(name).get(null)); } catch (Exception ignored) {}
    }

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final Map<ChunkKey, Set<Location>> hiddenOres;
    private final Set<UUID> injectedPlayers;

    public AntiXrayManager(VAC plugin) {
        this.plugin = plugin;
        this.hiddenOres = new ConcurrentHashMap<>();
        this.injectedPlayers = ConcurrentHashMap.newKeySet();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.getConfigManager().isXrayEnabled()) return;
        Chunk chunk = event.getChunk();
        ChunkKey key = new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Set<Location> ores = findHiddenOres(chunk);
            if (!ores.isEmpty()) {
                hiddenOres.put(key, ores);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (Player player : getPlayersInRange(chunk)) {
                        sendOreHides(player, ores);
                    }
                });
            }
        });
    }

    private Set<Location> findHiddenOres(Chunk chunk) {
        Set<Location> result = new HashSet<>();
        int minY = chunk.getWorld().getMinHeight();
        int maxY = chunk.getWorld().getMaxHeight() - 1;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (ORES.contains(block.getType())) {
                        if (!isExposed(block)) {
                            result.add(block.getLocation().clone());
                        }
                    }
                }
            }
        }
        return result;
    }

    private boolean isExposed(Block block) {
        for (BlockFace face : FACES) {
            Block relative = block.getRelative(face);
            if (TRANSPARENT.contains(relative.getType())) {
                return true;
            }
        }
        return false;
    }

    private List<Player> getPlayersInRange(Chunk chunk) {
        List<Player> result = new ArrayList<>();
        World world = chunk.getWorld();
        int cx = chunk.getX() * 16 + 8;
        int cz = chunk.getZ() * 16 + 8;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(new Location(world, cx, 64, cz)) < 25600) {
                result.add(p);
            }
        }
        return result;
    }

    private void sendOreHides(Player player, Set<Location> ores) {
        for (Location loc : ores) {
            sendBlockChange(player, loc, Material.STONE);
        }
    }

    private void sendBlockChange(Player player, Location loc, Material mat) {
        try {
            player.getClass().getMethod("sendBlockChange", Location.class, Material.class).invoke(player, loc, mat);
        } catch (Exception e) {
            try {
                player.getClass().getMethod("sendBlockChange", Location.class, Material.class, byte.class).invoke(player, loc, mat, (byte) 0);
            } catch (Exception ignored) {}
        }
    }

    public void onBlockBreak(Location loc) {
        for (Map.Entry<ChunkKey, Set<Location>> entry : hiddenOres.entrySet()) {
            if (entry.getValue().remove(loc)) {
                if (entry.getValue().isEmpty()) {
                    hiddenOres.remove(entry.getKey());
                }
                break;
            }
        }
    }

    public void injectPlayer(Player player) {
        if (!plugin.getConfigManager().isXrayEnabled()) return;
        if (!injectedPlayers.add(player.getUniqueId())) return;

        UUID worldName = player.getWorld().getUID();
        int px = player.getLocation().getBlockX() >> 4;
        int pz = player.getLocation().getBlockZ() >> 4;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int radius = 6;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    ChunkKey key = new ChunkKey(worldName, px + dx, pz + dz);
                    Set<Location> ores = hiddenOres.get(key);
                    if (ores != null && !ores.isEmpty()) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sendOreHides(player, ores);
                        });
                    }
                }
            }
        });
    }

    public void removePlayer(Player player) {
        injectedPlayers.remove(player.getUniqueId());
    }

    private static class ChunkKey {
        final String world;
        final int x, z;

        ChunkKey(String world, int x, int z) {
            this.world = world; this.x = x; this.z = z;
        }

        ChunkKey(UUID worldUid, int x, int z) {
            this.world = worldUid.toString(); this.x = x; this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkKey)) return false;
            ChunkKey key = (ChunkKey) o;
            return x == key.x && z == key.z && world.equals(key.world);
        }

        @Override
        public int hashCode() {
            return world.hashCode() * 31 * 31 + x * 31 + z;
        }
    }
}
