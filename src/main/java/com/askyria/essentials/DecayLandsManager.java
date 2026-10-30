package com.askyria.essentials;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

/**
 * Applies an original Far Lands / Decay Lands effect to newly populated
 * Overworld chunks in distance bands around the world's spawn.
 *
 * This is an approximation built into the Paper plugin, not Mojang's original
 * Far Lands generator. Existing chunks are deliberately never rewritten.
 */
public final class DecayLandsManager implements Listener {
    private final JavaPlugin plugin;

    public DecayLandsManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        if (!plugin.getConfig().getBoolean("decay-lands.enabled", true)) return;

        World world = event.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return;

        int minRadius = plugin.getConfig().getInt("decay-lands.start-radius", 50000);
        int decayRadius = plugin.getConfig().getInt("decay-lands.decay-radius", 65000);
        int endRadius = plugin.getConfig().getInt("decay-lands.end-radius", 70000);
        int centerX = world.getSpawnLocation().getBlockX();
        int centerZ = world.getSpawnLocation().getBlockZ();
        int baseX = event.getChunk().getX() << 4;
        int baseZ = event.getChunk().getZ() << 4;
        int maxY = world.getMaxHeight() - 1;
        int minY = Math.max(world.getMinHeight() + 8, 32);

        // A chunk only needs processing if its nearest point is within the zone.
        int nearestX = clamp(centerX, baseX, baseX + 15);
        int nearestZ = clamp(centerZ, baseZ, baseZ + 15);
        double nearestDistance = Math.hypot(nearestX - centerX, nearestZ - centerZ);
        double farthestDistance = 0;
        for (int x : new int[]{baseX, baseX + 15}) {
            for (int z : new int[]{baseZ, baseZ + 15}) {
                farthestDistance = Math.max(farthestDistance, Math.hypot(x - centerX, z - centerZ));
            }
        }
        if (farthestDistance < minRadius || nearestDistance >= endRadius) return;

        Random random = new Random((((long) event.getChunk().getX()) << 32)
            ^ (event.getChunk().getZ() & 0xffffffffL) ^ world.getSeed());

        for (int x = baseX; x < baseX + 16; x++) {
            for (int z = baseZ; z < baseZ + 16; z++) {
                double distance = Math.hypot(x - centerX, z - centerZ);
                if (distance < minRadius || distance >= endRadius) continue;

                int surfaceY = world.getHighestBlockYAt(x, z);
                if (surfaceY <= minY + 5) continue;

                if (distance < decayRadius) {
                    makeFarLandsColumn(world, x, z, surfaceY, minY, maxY, random);
                } else {
                    makeDecayColumn(world, x, z, surfaceY, minY, random);
                }
            }
        }
        plugin.getLogger().info("Generated " + (nearestDistance >= decayRadius ? "Decay Lands" : "Far Lands")
            + " terrain in chunk " + event.getChunk().getX() + ", " + event.getChunk().getZ());
    }

    private void makeFarLandsColumn(World world, int x, int z, int surfaceY, int minY, int maxY, Random random) {
        // Long, narrow fractures create the broken, unstable terrain silhouette.
        long hash = mix(((long) x * 341873128712L) ^ ((long) z * 132897987541L) ^ world.getSeed());
        int fracture = (int) Math.floorMod(hash, 29);
        if (fracture < 3) {
            int cutTo = minY + random.nextInt(12);
            for (int y = surfaceY; y >= cutTo; y--) {
                world.getBlockAt(x, y, z).setType(Material.AIR, false);
            }
            return;
        }

        // Jagged stone spikes rise out of the original surface.
        if (Math.floorMod(hash >>> 5, 41) == 0) {
            int height = 8 + (int) Math.floorMod(hash >>> 11, 34);
            int top = Math.min(maxY - 2, surfaceY + height);
            for (int y = surfaceY + 1; y <= top; y++) {
                world.getBlockAt(x, y, z).setType(y > top - 2 ? Material.STONE : Material.STONE, false);
            }
        }

        // Make occasional deep cuts through the terrain for a jagged, fractured look.
        if (Math.floorMod(hash >>> 17, 23) == 0) {
            int cutTo = Math.max(minY, surfaceY - 18);
            for (int y = surfaceY - 1; y >= cutTo; y--) {
                world.getBlockAt(x, y, z).setType(Material.AIR, false);
            }
        }
    }

    private void makeDecayColumn(World world, int x, int z, int surfaceY, int minY, Random random) {
        long hash = mix(((long) x * 341873128712L) ^ ((long) z * 132897987541L) ^ world.getSeed());
        int roll = (int) Math.floorMod(hash, 100);

        // Mostly empty space below isolated grass/dirt shelves, with some larger cliff fragments.
        if (roll < 63) {
            int keepDepth = 1 + (int) Math.floorMod(hash >>> 9, 4);
            for (int y = surfaceY - keepDepth; y >= minY; y--) {
                world.getBlockAt(x, y, z).setType(Material.AIR, false);
            }
        } else if (roll < 84) {
            int shelfDepth = 5 + (int) Math.floorMod(hash >>> 13, 15);
            int cutTo = Math.max(minY, surfaceY - shelfDepth);
            for (int y = cutTo - 1; y >= minY; y--) {
                world.getBlockAt(x, y, z).setType(Material.AIR, false);
            }
        } else {
            // Preserve a minority of columns as tall broken cliff remnants.
            int cutDepth = 12 + (int) Math.floorMod(hash >>> 19, 32);
            int cutTo = Math.max(minY, surfaceY - cutDepth);
            for (int y = cutTo - 1; y >= minY; y--) {
                world.getBlockAt(x, y, z).setType(Material.AIR, false);
            }
        }

        // Scattered fragments hang below the surface to make the zone visibly decayed.
        if (Math.floorMod(hash >>> 23, 7) == 0 && surfaceY - 7 > minY) {
            int fragmentY = surfaceY - 5 - (int) Math.floorMod(hash >>> 27, 12);
            if (fragmentY > minY && world.getBlockAt(x, fragmentY, z).getType() == Material.AIR) {
                Material fragment = Math.floorMod(hash >>> 31, 3) == 0 ? Material.STONE
                    : Math.floorMod(hash >>> 35, 2) == 0 ? Material.DIRT : Material.GRASS_BLOCK;
                world.getBlockAt(x, fragmentY, z).setType(fragment, false);
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
