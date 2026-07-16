package vac.lag;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vac.VAC;
import vac.util.PacketUtils;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LagManager {

    private final VAC plugin;
    private final Map<UUID, LagSession> activeLags;

    public LagManager(VAC plugin) {
        this.plugin = plugin;
        this.activeLags = new ConcurrentHashMap<>();
    }

    public void startLag(Player player, String category, int durationSeconds) {
        UUID uuid = player.getUniqueId();
        if (activeLags.containsKey(uuid)) {
            stopLag(player);
        }

        int maxDuration = plugin.getConfigManager().getMaxLagDuration();
        durationSeconds = Math.min(durationSeconds, maxDuration);

        double durationMultiplier = plugin.getConfigManager().getLagCategoryDurationMultiplier(category);
        int adjustedDuration = (int) (durationSeconds * durationMultiplier);

        int packetDelayTicks = plugin.getConfigManager().getLagCategoryPacketDelay(category);
        boolean cancelMovement = plugin.getConfigManager().isLagCategoryCancelMovement(category);

        LagSession session = new LagSession(player, category, adjustedDuration, packetDelayTicks, cancelMovement);
        activeLags.put(uuid, session);
        session.start();
    }

    public void stopLag(Player player) {
        UUID uuid = player.getUniqueId();
        LagSession session = activeLags.remove(uuid);
        if (session != null) session.stop();
    }

    public void stopAll() {
        for (LagSession session : activeLags.values()) session.stop();
        activeLags.clear();
    }

    public boolean hasActiveLag(Player player) {
        return activeLags.containsKey(player.getUniqueId());
    }

    public LagSession getSession(Player player) {
        return activeLags.get(player.getUniqueId());
    }

    public Collection<LagSession> getActiveSessions() {
        return activeLags.values();
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

    public List<String> getAvailableCategories() {
        List<String> cats = new ArrayList<>(plugin.getConfigManager().getLagDescriptions().keySet());
        if (cats.isEmpty()) {
            cats.addAll(Arrays.asList("connection", "block", "entity", "all"));
        }
        return cats;
    }

    public class LagSession {
        private final Player player;
        private final String category;
        private final int durationSeconds;
        private final int packetDelayTicks;
        private final boolean cancelMovement;
        private boolean active;
        private BukkitRunnable task;
        private int tickCount;
        private float originalFlySpeed;
        private float originalWalkSpeed;
        private Location[] rubberbandHistory;
        private int rubberbandIndex;
        private Random random;

        public LagSession(Player player, String category, int durationSeconds, int packetDelayTicks, boolean cancelMovement) {
            this.player = player;
            this.category = category;
            this.durationSeconds = durationSeconds;
            this.packetDelayTicks = packetDelayTicks;
            this.cancelMovement = cancelMovement;
            this.active = false;
            this.tickCount = 0;
            this.rubberbandHistory = new Location[20];
            this.rubberbandIndex = 0;
            this.random = new Random();
        }

        public void start() {
            this.active = true;
            this.originalFlySpeed = player.getFlySpeed();
            this.originalWalkSpeed = player.getWalkSpeed();

            this.task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!active || !player.isOnline()) {
                        stop();
                        return;
                    }
                    if (tickCount >= durationSeconds * 20) {
                        stop();
                        return;
                    }
                    tickCount++;
                    applyEffect();
                }
            };
            this.task.runTaskTimer(plugin, 0L, 1L);
        }

        private void applyEffect() {
            try {
                switch (category.toLowerCase()) {
                    case "connection": applyConnectionLag(); break;
                    case "block": applyBlockLag(); break;
                    case "entity": applyEntityLag(); break;
                    case "all": applyAllLag(); break;
                }
            } catch (Exception ignored) {}
        }

        private void applyConnectionLag() {
            Location loc = player.getLocation();

            if (tickCount % packetDelayTicks == 0) {
                double offsetX = (random.nextDouble() - 0.5) * 3;
                double offsetZ = (random.nextDouble() - 0.5) * 3;

                Object fakePos = PacketUtils.createPacketPlayOutPosition(
                        loc.getX() + offsetX,
                        loc.getY(),
                        loc.getZ() + offsetZ,
                        loc.getYaw() + (random.nextFloat() - 0.5f) * 20,
                        loc.getPitch() + (random.nextFloat() - 0.5f) * 10);
                PacketUtils.sendPacket(player, fakePos);

                Object revertPos = PacketUtils.createPacketPlayOutPosition(
                        loc.getX(), loc.getY(), loc.getZ(),
                        loc.getYaw(), loc.getPitch());
                PacketUtils.sendPacket(player, revertPos);
            }

            if (tickCount % 3 == 0) {
                rubberbandHistory[rubberbandIndex % rubberbandHistory.length] = loc.clone();
                rubberbandIndex++;

                if (rubberbandIndex > 5) {
                    int prevIdx = (rubberbandIndex - 5) % rubberbandHistory.length;
                    Location prev = rubberbandHistory[prevIdx];
                    if (prev != null) {
                        Object pos = PacketUtils.createPacketPlayOutPosition(
                                prev.getX(), prev.getY(), prev.getZ(),
                                prev.getYaw(), prev.getPitch());
                        PacketUtils.sendPacket(player, pos);
                    }
                }
            }

            if (tickCount % 10 == 0) {
                player.setVelocity(new Vector(
                        (random.nextDouble() - 0.5) * 0.8,
                        random.nextDouble() * 0.4,
                        (random.nextDouble() - 0.5) * 0.8));
            }

            player.setWalkSpeed(Math.min(originalWalkSpeed, 0.05f));
        }

        private void applyBlockLag() {
            Location loc = player.getLocation();

            if (tickCount % 5 == 0) {
                for (int i = 0; i < 8; i++) {
                    int bx = loc.getBlockX() + random.nextInt(10) - 5;
                    int by = Math.min(255, Math.max(0, loc.getBlockY() + random.nextInt(4) - 2));
                    int bz = loc.getBlockZ() + random.nextInt(10) - 5;

                    Material[] mats = {Material.BARRIER, Material.BEDROCK, Material.OBSIDIAN,
                                        Material.NETHER_PORTAL, Material.END_PORTAL, Material.LAVA};
                    Material mat = mats[random.nextInt(mats.length)];

                    sendBlockChange(player, new Location(loc.getWorld(), bx, by, bz), mat);
                }
            }

            if (tickCount % 10 == 0) {
                int bx = loc.getBlockX() + random.nextInt(6) - 3;
                int by = Math.min(255, Math.max(0, loc.getBlockY() + random.nextInt(3) - 1));
                int bz = loc.getBlockZ() + random.nextInt(6) - 3;

                sendBlockChange(player, new Location(loc.getWorld(), bx, by, bz),
                        Material.AIR);

                for (int i = 0; i < 3; i++) {
                    int rx = loc.getBlockX() + random.nextInt(10) - 5;
                    int rz = loc.getBlockZ() + random.nextInt(10) - 5;
                    int ry = loc.getWorld().getHighestBlockYAt(rx, rz);
                    sendBlockChange(player, new Location(loc.getWorld(), rx, ry, rz),
                            Material.BARRIER);
                }
            }
        }

        private void applyEntityLag() {
            if (tickCount % 3 == 0) {
                player.setVelocity(new Vector(
                        (random.nextDouble() - 0.5) * 1.2,
                        random.nextDouble() * 0.6,
                        (random.nextDouble() - 0.5) * 1.2));
            }

            if (tickCount % packetDelayTicks == 0) {
                Location loc = player.getLocation();
                Object pos = PacketUtils.createPacketPlayOutPosition(
                        loc.getX() + (random.nextDouble() - 0.5) * 4,
                        loc.getY() + (random.nextDouble() - 0.5) * 2,
                        loc.getZ() + (random.nextDouble() - 0.5) * 4,
                        loc.getYaw(), loc.getPitch());
                PacketUtils.sendPacket(player, pos);

                Object revert = PacketUtils.createPacketPlayOutPosition(
                        loc.getX(), loc.getY(), loc.getZ(),
                        loc.getYaw(), loc.getPitch());
                PacketUtils.sendPacket(player, revert);
            }

            player.setWalkSpeed(0.02f);
            player.setFlySpeed(0.0f);
            player.setAllowFlight(true);
        }

        private void applyAllLag() {
            applyConnectionLag();
            applyBlockLag();
            applyEntityLag();
        }

        public void stop() {
            this.active = false;
            if (task != null) {
                task.cancel();
                task = null;
            }
            if (player.isOnline()) {
                player.setWalkSpeed(originalWalkSpeed);
                player.setFlySpeed(originalFlySpeed);
                player.setAllowFlight(false);
                player.setFlying(false);
            }
            activeLags.remove(player.getUniqueId());
        }

        public Player getPlayer() { return player; }
        public String getCategory() { return category; }
        public boolean isActive() { return active; }
        public int getTickCount() { return tickCount; }
        public int getRemainingTicks() { return Math.max(0, (durationSeconds * 20) - tickCount); }
    }
}
