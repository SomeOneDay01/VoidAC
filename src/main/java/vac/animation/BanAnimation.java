package vac.animation;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vac.VAC;

import java.util.Random;

public class BanAnimation {

    private final VAC plugin;
    private final Random random;

    public BanAnimation(VAC plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    public void playAnimation(Player player, Runnable onComplete) {
        if (!plugin.getConfigManager().isAnimationEnabled()) {
            onComplete.run();
            return;
        }

        Location startLoc = player.getLocation().clone();
        int durationTicks = plugin.getConfigManager().getAnimationDuration();
        double liftHeight = plugin.getConfigManager().getAnimationLiftHeight();
        int spiralLoops = plugin.getConfigManager().getAnimationSpiralLoops();
        int particlesPerLoop = plugin.getConfigManager().getAnimationParticlesPerLoop();

        player.setVelocity(new Vector(0, 0.3, 0));
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFlySpeed(0.0f);

        playSound(player, "ENTITY_WITHER_SPAWN", 1.0f, 0.5f);

        double innerRadius = 1.8;
        double outerRadius = 2.5;

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= durationTicks || !player.isOnline()) {
                    cancel();
                    finish(player, startLoc, onComplete);
                    return;
                }

                double progress = (double) tick / durationTicks;
                double yOffset = progress * liftHeight;
                double angle = progress * spiralLoops * 2 * Math.PI;
                double height = startLoc.getY() + yOffset;

                World world = startLoc.getWorld();
                if (world == null) return;

                // Двойная спираль (helix 1 + helix 2)
                for (int h = 0; h < 2; h++) {
                    double offsetAngle = h * Math.PI;
                    double r = innerRadius + (progress * 0.7);
                    double x = startLoc.getX() + Math.cos(angle + offsetAngle) * r;
                    double z = startLoc.getZ() + Math.sin(angle + offsetAngle) * r;

                    Location particleLoc = new Location(world, x, height, z);
                    spawnRedstone(world, particleLoc, 0.6f);
                    spawnParticle(world, particleLoc, Particle.CRIT_MAGIC, 1, 0, 0, 0, 0);
                }

                // Маленькие частицы вдоль спирали (след)
                for (int i = 0; i < 3; i++) {
                    double trailAngle = angle + i * 0.4;
                    double trailR = innerRadius * (0.5 + progress * 0.5);
                    double tx = startLoc.getX() + Math.cos(trailAngle) * trailR;
                    double tz = startLoc.getZ() + Math.sin(trailAngle) * trailR;
                    Location trailLoc = new Location(world, tx, height - 0.3, tz);
                    spawnRedstone(world, trailLoc, 0.3f);
                }

                // Кольцо из частиц вокруг игрока на текущей высоте
                if (tick % 3 == 0) {
                    for (int i = 0; i < 8; i++) {
                        double ringAngle = i * Math.PI / 4 + tick * 0.02;
                        double ringR = outerRadius + 0.3;
                        double rx = startLoc.getX() + Math.cos(ringAngle) * ringR;
                        double rz = startLoc.getZ() + Math.sin(ringAngle) * ringR;
                        Location ringLoc = new Location(world, rx, height, rz);
                        spawnRedstone(world, ringLoc, 0.2f);
                    }
                }

                // Эффект подъёма (частицы поднимаются снизу)
                if (tick % 4 == 0) {
                    double riseX = startLoc.getX() + (random.nextDouble() - 0.5) * 3;
                    double riseZ = startLoc.getZ() + (random.nextDouble() - 0.5) * 3;
                    Location riseLoc = new Location(world, riseX, startLoc.getY(), riseZ);
                    spawnParticle(world, riseLoc, Particle.LAVA, 1, 0.2, 0, 0.2, 0);
                }

                // Звук подъёма
                if (tick % 8 == 0) {
                    playSound(player, "ENTITY_BLAZE_AMBIENT", 0.3f, 1.2f);
                }

                player.teleport(new Location(world, startLoc.getX(), height, startLoc.getZ(), startLoc.getYaw(), startLoc.getPitch()));
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finish(Player player, Location startLoc, Runnable onComplete) {
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setFlySpeed(0.1f);

        World world = startLoc.getWorld();
        if (world != null) {
            playExplosion(player, startLoc);
            if (plugin.getConfigManager().isDropItems()) dropPlayerItems(player, startLoc);
            if (plugin.getConfigManager().isDropXP()) dropPlayerXP(player, startLoc);
            if (plugin.getConfigManager().isDropArmor()) dropPlayerArmor(player, startLoc);
        }

        onComplete.run();
    }

    private void playExplosion(Player player, Location location) {
        World world = location.getWorld();
        if (world == null) return;

        playSound(location, "ENTITY_GENERIC_EXPLODE", 2.0f, 0.6f);
        playSound(location, "ENTITY_WITHER_DEATH", 1.5f, 0.4f);

        // Основной взрыв — красный шар
        spawnParticle(world, location, Particle.EXPLOSION_HUGE, 2, 0, 0, 0, 0);

        // Красные частицы разлетаются во все стороны
        for (int i = 0; i < 60; i++) {
            double xOff = (random.nextDouble() - 0.5) * 6;
            double yOff = (random.nextDouble() - 0.5) * 6;
            double zOff = (random.nextDouble() - 0.5) * 6;
            Location burstLoc = location.clone().add(xOff, yOff, zOff);
            spawnRedstone(world, burstLoc, 1.0f);
        }

        // Критические частицы (красные искры)
        for (int i = 0; i < 40; i++) {
            double xOff = (random.nextDouble() - 0.5) * 5;
            double yOff = random.nextDouble() * 4;
            double zOff = (random.nextDouble() - 0.5) * 5;
            Location critLoc = location.clone().add(xOff, yOff, zOff);
            spawnParticle(world, critLoc, Particle.CRIT_MAGIC, 2, 0, 0, 0, 0);
        }

        // Красные кольца expanding
        for (int ring = 0; ring < 6; ring++) {
            double ringR = 1.0 + ring * 0.8;
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                double rx = location.getX() + Math.cos(a) * ringR;
                double rz = location.getZ() + Math.sin(a) * ringR;
                double ry = location.getY() + ring * 0.4;
                Location ringLoc = new Location(world, rx, ry, rz);
                spawnRedstone(world, ringLoc, 0.7f);
                spawnParticle(world, ringLoc, Particle.CRIT_MAGIC, 1, 0, 0, 0, 0);
            }
        }

        // Эффект лавы снизу
        for (int i = 0; i < 15; i++) {
            double xOff = (random.nextDouble() - 0.5) * 4;
            double zOff = (random.nextDouble() - 0.5) * 4;
            Location lavaLoc = location.clone().add(xOff, 0.5, zOff);
            spawnParticle(world, lavaLoc, Particle.LAVA, 1, 0, 0, 0, 0);
        }

        // Сердцевина взрыва — яркий красный свет
        for (int i = 0; i < 10; i++) {
            double xOff = (random.nextDouble() - 0.5) * 2;
            double yOff = (random.nextDouble() - 0.5) * 2;
            double zOff = (random.nextDouble() - 0.5) * 2;
            Location coreLoc = location.clone().add(xOff, yOff, zOff);
            spawnRedstone(world, coreLoc, 1.5f);
            spawnParticle(world, coreLoc, Particle.FLAME, 3, 0, 0, 0, 0.05);
        }

        world.playSound(location, Sound.BLOCK_ANVIL_PLACE, 1.0f, 0.3f);
    }

    private void spawnRedstone(World world, Location loc, float size) {
        try {
            Particle.DustOptions dust = new Particle.DustOptions(
                    Color.RED, size
            );
            world.spawnParticle(Particle.REDSTONE, loc, 1, 0, 0, 0, dust);
        } catch (Exception ignored) {}
    }

    private void dropPlayerItems(Player player, Location location) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                location.getWorld().dropItemNaturally(location, item);
            }
        }
        player.getInventory().clear();
    }

    private void dropPlayerXP(Player player, Location location) {
        int xpLevels = player.getLevel();
        if (xpLevels > 0) {
            for (int i = 0; i < Math.min(xpLevels, 30); i++) {
                location.getWorld().spawn(
                    location.clone().add(
                        (random.nextDouble() - 0.5) * 2,
                        random.nextDouble() * 1.5,
                        (random.nextDouble() - 0.5) * 2
                    ),
                    org.bukkit.entity.ExperienceOrb.class
                ).setExperience(5 + random.nextInt(10));
            }
        }
        player.setLevel(0);
        player.setExp(0);
    }

    private void dropPlayerArmor(Player player, Location location) {
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && item.getType() != Material.AIR) {
                location.getWorld().dropItemNaturally(location, item);
            }
        }
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
    }

    private void playSound(Player player, String soundName, float volume, float pitch) {
        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception ignored) {}
    }

    private void playSound(Location location, String soundName, float volume, float pitch) {
        try {
            Sound sound = Sound.valueOf(soundName);
            location.getWorld().playSound(location, sound, volume, pitch);
        } catch (Exception ignored) {}
    }

    private void spawnParticle(World world, Location location, Particle particle, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        try {
            world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
        } catch (Exception ignored) {}
    }
}
