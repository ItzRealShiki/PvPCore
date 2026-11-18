package de.leon.pvpcore;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class WorldManager {
    private final PvPCore plugin;

    public WorldManager(PvPCore plugin) {
        this.plugin = plugin;
    }

    private static final class PvPGenerator extends ChunkGenerator {
        private final Biome biome;

        private PvPGenerator(String key) {
            this.biome = switch (key.toLowerCase()) {
                case "desert" -> Biome.DESERT;
                case "snow" -> Biome.SNOWY_PLAINS;
                case "badlands" -> Biome.BADLANDS;
                default -> Biome.PLAINS;
            };
        }

        @Override
        public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biomeGrid) {
            ChunkData data = createChunkData(world);
            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight();

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int worldX = chunkX * 16 + x;
                    int worldZ = chunkZ * 16 + z;

                    // Gentle rolling terrain: small height variation, no tall peaks.
                    double broad = Math.sin(worldX * 0.035) * 2.0
                            + Math.cos(worldZ * 0.031) * 1.8
                            + Math.sin((worldX + worldZ) * 0.018) * 1.2;
                    int surfaceY = Math.max(minY + 8, Math.min(maxY - 8, 64 + (int) Math.round(broad)));

                    biomeGrid.setBiome(x, z, biome);
                    data.setBlock(x, minY, z, Material.BEDROCK);

                    Material top = switch (biome) {
                        case DESERT -> Material.SAND;
                        case SNOWY_PLAINS -> Material.SNOW_BLOCK;
                        case BADLANDS -> Material.RED_SAND;
                        default -> Material.GRASS_BLOCK;
                    };
                    Material filler = switch (biome) {
                        case DESERT -> Material.SANDSTONE;
                        case SNOWY_PLAINS, PLAINS -> Material.DIRT;
                        case BADLANDS -> Material.TERRACOTTA;
                        default -> Material.DIRT;
                    };

                    for (int y = minY + 1; y < surfaceY - 3; y++) {
                        data.setBlock(x, y, z, Material.STONE);
                    }
                    for (int y = Math.max(minY + 1, surfaceY - 3); y < surfaceY; y++) {
                        data.setBlock(x, y, z, filler);
                    }
                    data.setBlock(x, surfaceY, z, top);
                }
            }
            return data;
        }

        @Override public boolean shouldGenerateNoise() { return true; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }

    public boolean teleport(Player player, String key) {
        String base = "worlds." + key + ".";
        String name = plugin.getConfig().getString(base + "name", "pvp_" + key + "_arena");
        World world = Bukkit.getWorld(name);
        if (world == null) {
            WorldCreator creator = new WorldCreator(name)
                    .environment(World.Environment.NORMAL)
                    .type(WorldType.NORMAL)
                    .generator(new PvPGenerator(key))
                    .generateStructures(false);
            world = creator.createWorld();
        }
        if (world == null) {
            plugin.message(player, "&cThe world could not be loaded or created.");
            return false;
        }

        int radius = Math.max(100, plugin.getConfig().getInt(base + "radius", 5000));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 200; attempt++) {
            int x = random.nextInt(-radius, radius + 1);
            int z = random.nextInt(-radius, radius + 1);
            Block floor = world.getHighestBlockAt(x, z);
            Block feet = floor.getRelative(0, 1, 0);
            Block head = floor.getRelative(0, 2, 0);
            if (!floor.getType().isSolid() || !feet.isPassable() || !head.isPassable()) continue;
            if (floor.isLiquid() || feet.isLiquid() || head.isLiquid()) continue;

            Location destination = new Location(world, x + 0.5, floor.getY() + 1.0, z + 0.5);
            destination.setYaw(player.getLocation().getYaw());
            destination.setPitch(0);
            player.teleport(destination);
            plugin.message(player, "&aTeleported to &f" + key.toUpperCase() + "&a.");
            return true;
        }

        plugin.message(player, "&cNo safe spawn point was found. Please try again.");
        return false;
    }
}
