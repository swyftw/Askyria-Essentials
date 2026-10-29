package com.askyria.essentials;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Adds a deterministic, one-time surface distortion to newly generated chunks
 * in the Far Lands and Decay Lands bands. Existing chunks are never rewritten.
 */
public final class FarLandsManager implements Listener {
    private static final double FAR_LANDS_START = 50_000.0;
    private static final double DECAY_START = 65_000.0;
    private static final double ZONE_END = 70_000.0;

    private final JavaPlugin plugin;

    public FarLandsManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNewChunk(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;

        World world = event.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return;

        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        var spawn = world.getSpawnLocation();
        double nearest = distanceToChunk(spawn.getX(), spawn.getZ(), minX, maxX, minZ, maxZ);
        double farthest = farthestCornerDistance(spawn.getX(), spawn.getZ(), minX, maxX, minZ, maxZ);
        if (farthest < FAR_LANDS_START || nearest >= ZONE_END) return;

        // Work only on newly generated chunks and defer the edits until chunk
        // generation has returned. One chunk is handled per scheduled task.
        plugin.getServer().getScheduler().runTask(plugin, () -> distortChunk(world, chunkX, chunkZ, spawn.getX(), spawn.getZ()));
    }

    private void distortChunk(World world, int chunkX, int chunkZ, double spawnX, double spawnZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        double nearest = distanceToChunk(spawnX, spawnZ, minX, maxX, minZ, maxZ);
        double farthest = farthestCornerDistance(spawnX, spawnZ, minX, maxX, minZ, maxZ);
        if (farthest < FAR_LANDS_START || nearest >= ZONE_END) return;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double distance = Math.hypot(x + 0.5 - spawnX, z + 0.5 - spawnZ);
                if (distance < FAR_LANDS_START || distance >= ZONE_END) continue;

                int surfaceY = world.getHighestBlockYAt(x, z);
                if (surfaceY <= world.getMinHeight() || surfaceY >= world.getMaxHeight() - 8) continue;

                double angle = Math.atan2(z - spawnZ, x - spawnX);
                double waveA = Math.sin((x + spawnZ * 0.17) * 0.035) * Math.cos((z - spawnX * 0.13) * 0.031);
                double waveB = Math.sin((x + z) * 0.012) * Math.cos((x - z) * 0.017);
                int amplitude = distance < DECAY_START ? 5 : 8;
                double blend = distance < DECAY_START ? 1.0 : Math.min(1.0, (distance - DECAY_START) / 500.0);
                int offset = (int) Math.round((waveA * 0.7 + waveB * 0.3) * amplitude * (1.0 + blend));
                // Add long ridges and sharp vertical walls, strongest in Far Lands.
                if (distance < DECAY_START) {
                    double ridge = Math.sin((x * Math.cos(angle) + z * Math.sin(angle)) * 0.004);
                    if (Math.abs(ridge) > 0.82) offset += ridge > 0 ? 7 : -5;
                } else {
                    offset += (int) Math.round(Math.sin(angle * 12.0 + distance * 0.025) * blend * 3.0);
                }

                int targetY = Math.max(world.getMinHeight() + 2,
                    Math.min(world.getMaxHeight() - 4, surfaceY + offset));
                shapeColumn(world, x, z, surfaceY, targetY, distance >= DECAY_START);
            }
        }
    }

    private void shapeColumn(World world, int x, int z, int surfaceY, int targetY, boolean decay) {
        Block top = world.getBlockAt(x, surfaceY, z);
        Material topType = top.getType();
        if (topType.isAir() || topType == Material.WATER || topType == Material.LAVA) return;

        if (targetY < surfaceY) {
            for (int y = surfaceY; y > targetY; y--) {
                Block block = world.getBlockAt(x, y, z);
                if (block.getType().isAir() || block.isLiquid()) continue;
                block.setType(Material.AIR, false);
            }
        } else if (targetY > surfaceY) {
            Material fill = decay ? Material.TUFF : (topType == Material.SAND || topType == Material.RED_SAND
                ? Material.SANDSTONE : Material.STONE);
            for (int y = surfaceY + 1; y <= targetY; y++) {
                world.getBlockAt(x, y, z).setType(fill, false);
            }
        }

        Material cap = decay ? decayCap(topType) : farCap(topType);
        world.getBlockAt(x, targetY, z).setType(cap, false);
        if (decay && targetY - 1 > world.getMinHeight()) {
            // Sparse dark seams make the transition band look fractured without
            // placing extra entities or running continuous tasks.
            long hash = ((long) x * 73428767L) ^ ((long) z * 912931L);
            if ((hash & 15L) == 0L) {
                world.getBlockAt(x, targetY - 1, z).setType(Material.COBBLED_DEEPSLATE, false);
            }
        }
    }

    private Material farCap(Material original) {
        if (original == Material.GRASS_BLOCK || original == Material.MYCELIUM || original == Material.PODZOL) return original;
        if (original == Material.SAND || original == Material.RED_SAND) return original;
        if (original == Material.SNOW_BLOCK || original == Material.SNOW) return Material.STONE;
        if (original == Material.GRAVEL) return Material.GRAVEL;
        return Material.STONE;
    }

    private Material decayCap(Material original) {
        if (original == Material.SAND || original == Material.RED_SAND) return Material.GRAVEL;
        if (original == Material.GRASS_BLOCK || original == Material.MYCELIUM || original == Material.PODZOL) return Material.TUFF;
        if (original == Material.SNOW || original == Material.SNOW_BLOCK) return Material.COBBLED_DEEPSLATE;
        return Material.TUFF;
    }

    private double distanceToChunk(double x, double z, int minX, int maxX, int minZ, int maxZ) {
        double dx = x < minX ? minX - x : x > maxX ? x - maxX : 0.0;
        double dz = z < minZ ? minZ - z : z > maxZ ? z - maxZ : 0.0;
        return Math.hypot(dx, dz);
    }

    private double farthestCornerDistance(double x, double z, int minX, int maxX, int minZ, int maxZ) {
        return Math.max(Math.max(Math.hypot(minX - x, minZ - z), Math.hypot(minX - x, maxZ - z)),
            Math.max(Math.hypot(maxX - x, minZ - z), Math.hypot(maxX - x, maxZ - z)));
    }
}
