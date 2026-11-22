package de.leon.pvpcore;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.stream.Stream;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;

public final class WorldManager {
    private final PvPCore plugin;
    private static final int RTP_CACHE_TARGET = 12;
    private final Map<String, Deque<Location>> rtpLocationCache = new HashMap<>();
    private final Set<String> rtpCacheWarming = new HashSet<>();
    private final File rtpCacheFile;
    private final Deque<PregenChunk> pregenQueue = new ArrayDeque<>();
    private boolean pregenBusy;
    private boolean regeneratingArenas;
    private record PregenChunk(String worldKey, int x, int z) {}

    public WorldManager(PvPCore plugin) {
        this.plugin = plugin;
        this.rtpCacheFile = new File(plugin.getDataFolder(), "rtp-cache.yml");
        for (String key : WORLD_KEYS) rtpLocationCache.put(key, new ArrayDeque<>());
    }

    private static final class PvPGenerator extends ChunkGenerator {
        private final Biome biome;
        private final String terrainKey;
        private PvPGenerator(String key) {
            terrainKey = key.toLowerCase();
            String biomeKey = switch (terrainKey) {
                case "desert" -> "desert";
                case "snow" -> "snowy_plains";
                case "badlands" -> "badlands";
                default -> "plains";
            };
            biome = Registry.BIOME.get(NamespacedKey.minecraft(biomeKey));
        }

        private static double smooth(double t) { return t * t * (3.0 - 2.0 * t); }
        private static double hash(int x, int z, long seed) {
            long n = x * 341873128712L + z * 132897987541L + seed * 42317861L;
            n = (n ^ (n >> 13)) * 1274126177L;
            n ^= n >> 16;
            return (n & 0x7fffffffL) / (double) 0x7fffffffL * 2.0 - 1.0;
        }
        private static double noise(double x, double z, int scale, long seed) {
            int x0 = Math.floorDiv((int)Math.floor(x), scale);
            int z0 = Math.floorDiv((int)Math.floor(z), scale);
            double fx = smooth((x - x0 * scale) / scale);
            double fz = smooth((z - z0 * scale) / scale);
            double a = hash(x0, z0, seed), b = hash(x0 + 1, z0, seed);
            double c = hash(x0, z0 + 1, seed), d = hash(x0 + 1, z0 + 1, seed);
            return (a + (b-a)*fx) * (1-fz) + (c + (d-c)*fx) * fz;
        }
        private static int height(int x, int z, long seed) {
            // Restore the original gently rolling terrain: broad hills with smaller,
            // softer variations layered on top instead of a completely flat surface.
            double n = noise(x, z, 150, seed) * 2.8
                    + noise(x, z, 55, seed + 7) * 1.6
                    + noise(x, z, 22, seed + 19) * 0.65;
            return 64 + (int) Math.round(n);
        }

        @Override
        public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biomeGrid) {
            ChunkData data = createChunkData(world);
            int minY = world.getMinHeight();
            long seed = world.getSeed();
            Material top = switch (terrainKey) {
                case "desert" -> Material.SAND;
                case "snow" -> Material.GRASS_BLOCK;
                case "badlands" -> Material.RED_SAND;
                default -> Material.GRASS_BLOCK;
            };
            Material filler = switch (terrainKey) {
                case "desert" -> Material.SANDSTONE;
                case "badlands" -> Material.TERRACOTTA;
                default -> Material.DIRT;
            };

            int[][] surfaceHeights = new int[16][16];

            // Fill the deep layers in bulk. The old per-column loop wrote tens of
            // thousands of individual blocks for every generated chunk.
            for (int x=0; x<16; x++) for (int z=0; z<16; z++) {
                data.setBlock(x, minY, z, Material.BEDROCK);
                if (biome != null) biomeGrid.setBiome(x, z, biome);
            }
            if (minY < 0) data.setRegion(0, minY + 1, 0, 16, 1, 16, Material.DEEPSLATE);
            data.setRegion(0, Math.max(1, minY + 1), 0, 16, 60, 16, Material.STONE);

            for (int x=0; x<16; x++) for (int z=0; z<16; z++) {
                int wx=chunkX*16+x, wz=chunkZ*16+z;
                int surface=height(wx,wz,seed);
                surfaceHeights[x][z] = surface;
                for (int y=surface-4; y<surface; y++) data.setBlock(x,y,z,filler);
                data.setBlock(x,surface,z,top);
                // No snow layers or surface decorations: the entire arena stays clear.
            }

            // Ore veins are placed only inside existing stone, never in open air.
            Random ores = new Random(seed ^ (chunkX * 341873128712L) ^ (chunkZ * 132897987541L));
            Material[] types = {Material.COAL_ORE, Material.IRON_ORE, Material.COPPER_ORE,
                    Material.GOLD_ORE, Material.REDSTONE_ORE, Material.LAPIS_ORE,
                    Material.DIAMOND_ORE, Material.EMERALD_ORE};
            Material[] deepslateTypes = {Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE,
                    Material.DEEPSLATE_COPPER_ORE, Material.DEEPSLATE_GOLD_ORE,
                    Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_LAPIS_ORE,
                    Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE};
            int[] counts = {14, 10, 9, 5, 5, 4, 3, 1};
            int[] sizes = {9, 8, 9, 7, 7, 6, 5, 3};
            int[] ceilings = {120, 64, 48, 32, 16, 24, 16, 24};
            for (int i=0;i<types.length;i++) for (int vein=0;vein<counts[i];vein++) {
                int ox=ores.nextInt(16), oz=ores.nextInt(16);
                int oy=minY+5+ores.nextInt(Math.max(1, ceilings[i]-minY-4));
                for (int j=0;j<sizes[i];j++) {
                    int px=ox+ores.nextInt(3)-1, py=oy+ores.nextInt(3)-1, pz=oz+ores.nextInt(3)-1;
                    if (px<0||px>15||pz<0||pz>15||py<=minY||py>=surfaceHeights[px][pz]-4) continue;
                    Material base = data.getType(px,py,pz);
                    if (base == Material.STONE || base == Material.DEEPSLATE) {
                        data.setBlock(px,py,pz,py <= 0 ? deepslateTypes[i] : types[i]);
                    }
                }
            }

            // Deterministic sparse oak trees: 5% of 300x300 cells, with candidates
            // kept 40 blocks from each cell edge (therefore at least 220 blocks apart).
            int minCellX = Math.floorDiv(chunkX * 16 - 200, 300);
            int maxCellX = Math.floorDiv(chunkX * 16 + 215, 300);
            int minCellZ = Math.floorDiv(chunkZ * 16 - 200, 300);
            int maxCellZ = Math.floorDiv(chunkZ * 16 + 215, 300);
            org.bukkit.block.data.BlockData leaves = Bukkit.createBlockData("minecraft:oak_leaves[persistent=true]");
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                    long treeSeed = seed ^ (cellX * 0x9E3779B97F4A7C15L) ^ (cellZ * 0xC2B2AE3D27D4EB4FL);
                    Random treeRandom = new Random(treeSeed);
                    if (treeRandom.nextDouble() >= 0.05) continue;
                    int treeX = cellX * 300 + 150 + treeRandom.nextInt(81) - 40;
                    int treeZ = cellZ * 300 + 150 + treeRandom.nextInt(81) - 40;
                    int lx = treeX - chunkX * 16;
                    int lz = treeZ - chunkZ * 16;
                    int groundY = height(treeX, treeZ, seed);
                    if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                        for (int y = groundY + 1; y <= groundY + 4; y++) {
                            data.setBlock(lx, y, lz, Material.OAK_LOG);
                        }
                    }
                    for (int dy = 2; dy <= 5; dy++) {
                        int radius = dy == 5 ? 1 : (dy == 2 ? 1 : 2);
                        for (int dx = -radius; dx <= radius; dx++) {
                            for (int dz = -radius; dz <= radius; dz++) {
                                if (Math.abs(dx) == radius && Math.abs(dz) == radius && radius == 2) continue;
                                int tx = lx + dx, tz = lz + dz, ty = groundY + dy;
                                if (tx < 0 || tx > 15 || tz < 0 || tz > 15) continue;
                                if (data.getType(tx, ty, tz).isAir()) data.setBlock(tx, ty, tz, leaves);
                            }
                        }
                    }
                }
            }
            return data;
        }
        @Override public boolean shouldGenerateNoise(){return false;}
        @Override public boolean shouldGenerateSurface(){return false;}
        @Override public boolean shouldGenerateCaves(){return false;}
        @Override public boolean shouldGenerateDecorations(){return false;}
        @Override public boolean shouldGenerateMobs(){return false;}
        @Override public boolean shouldGenerateStructures(){return false;}
    }


    private void migrateArenaSettings() {
        if (!plugin.getConfig().getBoolean("worlds.fixed-format-migrated", false)) {
            String[][] legacy = {
                    {"plains", "pvp_plains_arena_v2", "pvp_plains_arena_v3", "731942105"},
                    {"desert", "pvp_desert_arena_v2", "pvp_desert_arena_v3", "284710639"},
                    {"snow", "pvp_snow_arena_v2", "pvp_snow_arena_v3", "918273645"},
                    {"badlands", "pvp_badlands_arena_v2", "pvp_badlands_arena_v3", "563829174"}
            };
            for (String[] arena : legacy) {
                String path = "worlds." + arena[0] + ".name";
                String configuredName = plugin.getConfig().getString(path, arena[1]);
                if (configuredName.equalsIgnoreCase(arena[1])) plugin.getConfig().set(path, arena[2]);
                String seedPath = "worlds." + arena[0] + ".seed";
                if (!plugin.getConfig().contains(seedPath)) plugin.getConfig().set(seedPath, Long.parseLong(arena[3]));
            }
            plugin.getConfig().set("worlds.fixed-format-migrated", true);
        }

        // Switch only the arena folder names so old vanilla-format worlds are never mistaken
        // for worlds created with the restored PvPCore terrain generator.
        if (!plugin.getConfig().getBoolean("worlds.custom-terrain-migrated", false)) {
            String[][] arenas = {
                    {"plains", "pvp_plains_arena_v3", "pvp_plains_arena_v4"},
                    {"desert", "pvp_desert_arena_v3", "pvp_desert_arena_v4"},
                    {"snow", "pvp_snow_arena_v3", "pvp_snow_arena_v4"},
                    {"badlands", "pvp_badlands_arena_v3", "pvp_badlands_arena_v4"}
            };
            for (String[] arena : arenas) {
                String path = "worlds." + arena[0] + ".name";
                if (plugin.getConfig().getString(path, "").equalsIgnoreCase(arena[1])) {
                    plugin.getConfig().set(path, arena[2]);
                }
            }
            plugin.getConfig().set("worlds.custom-terrain-migrated", true);
        }
        if (!plugin.getConfig().getBoolean("worlds.rolling-terrain-restored-migrated", false)) {
            String[][] arenas = {
                    {"plains", "pvp_plains_arena_v5", "pvp_plains_arena_v6"},
                    {"desert", "pvp_desert_arena_v5", "pvp_desert_arena_v6"},
                    {"snow", "pvp_snow_arena_v5", "pvp_snow_arena_v6"},
                    {"badlands", "pvp_badlands_arena_v5", "pvp_badlands_arena_v6"}
            };
            for (String[] arena : arenas) {
                String path = "worlds." + arena[0] + ".name";
                if (plugin.getConfig().getString(path, "").equalsIgnoreCase(arena[1])) {
                    plugin.getConfig().set(path, arena[2]);
                }
            }
            plugin.getConfig().set("worlds.rolling-terrain-restored-migrated", true);
        }
        plugin.saveConfig();
        deleteOldArenaWorlds();
    }

    /**
     * Remove only PvPCore's known legacy arena folders. The current v6 worlds
     * and all unrelated server worlds are deliberately excluded.
     */
    private void deleteOldArenaWorlds() {
        String[] keys = {"plains", "desert", "snow", "badlands"};
        for (String key : keys) {
            String[] oldNames = {
                    "pvp_" + key + "_arena",
                    "pvp_" + key + "_arena_v1",
                    "pvp_" + key + "_arena_v2",
                    "pvp_" + key + "_arena_v3",
                    "pvp_" + key + "_arena_v4",
                    "pvp_" + key + "_arena_v5"
            };
            for (String oldName : oldNames) {
                World loaded = Bukkit.getWorld(oldName);
                if (loaded != null) {
                    if (!loaded.getPlayers().isEmpty() || !Bukkit.unloadWorld(loaded, false)) {
                        plugin.getLogger().warning("Keeping old arena " + oldName
                                + " because it is currently in use; it can be removed after a restart.");
                        continue;
                    }
                }
                Path folder = Bukkit.getWorldContainer().toPath().resolve(oldName);
                try {
                    deleteWorldFolder(folder);
                    plugin.getLogger().info("Deleted legacy PvPCore arena: " + oldName);
                } catch (IOException error) {
                    plugin.getLogger().warning("Could not delete legacy arena " + oldName
                            + ": " + error.getMessage());
                }
            }
        }
    }

    public void applyPermanentDay(World world) {
        if (world == null) return;

        // Disable the daylight cycle first. Paper 26.2 can expose dimensions
        // without a default world clock; in those dimensions setTime() throws.
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        try {
            world.setTime(1000L);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().fine("World " + world.getName()
                    + " has no default clock; keeping its daylight cycle disabled.");
        }
    }

    public void initializeWorldsOnce() {
        // Never create or synchronously generate arena worlds during onEnable().
        // Bukkit's createWorld() performs spawn-chunk generation on the server thread,
        // which can stall plugin startup on hosted servers and leave the plugin red in /plugins.
        // Arenas are created lazily when a player actually selects one through /rtp.
        migrateArenaSettings();
        cleanupGeneratedWorldsOnce();
        plugin.getLogger().info("Arena world creation deferred until first RTP request.");
    }

    /**
     * One-time cleanup for worlds generated by previous PvPCore builds.
     * Only fixed, known PvPCore world names are eligible; the server's normal
     * worlds and custom worlds are never discovered by prefix or deleted.
     */
    private void cleanupGeneratedWorldsOnce() {
        final String marker = "worlds.generated-worlds-cleaned-v1";
        if (plugin.getConfig().getBoolean(marker, false)) return;

        Set<String> candidates = new LinkedHashSet<>();
        candidates.add("prac_void");
        for (String key : WORLD_KEYS) {
            candidates.add("pvp_" + key + "_arena");
            for (int version = 1; version <= 6; version++) {
                candidates.add("pvp_" + key + "_arena_v" + version);
            }
        }

        // The holding world is only disposable when it is not the configured server spawn.
        String configuredSpawn = plugin.getConfig().getString("spawn.world", "");
        candidates.add("pvpcore_regeneration_holding");

        Path worldContainer = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        boolean complete = true;
        for (String name : candidates) {
            if (!configuredSpawn.isBlank() && name.equalsIgnoreCase(configuredSpawn)) {
                plugin.getLogger().warning("Preserving " + name
                        + " because it is configured as the server spawn world.");
                continue;
            }
            Path folder = worldContainer.resolve(name).normalize();
            if (!worldContainer.equals(folder.getParent())) {
                plugin.getLogger().warning("Skipping unsafe PvPCore world path: " + name);
                complete = false;
                continue;
            }

            World loaded = Bukkit.getWorld(name);
            if (loaded != null) {
                if (!loaded.getPlayers().isEmpty()) {
                    plugin.getLogger().warning("Keeping PvPCore world " + name
                            + " because players are still inside it. Cleanup will retry after a restart.");
                    complete = false;
                    continue;
                }
                if (!Bukkit.unloadWorld(loaded, false)) {
                    plugin.getLogger().warning("Could not unload PvPCore world " + name
                            + ". Cleanup will retry after a restart.");
                    complete = false;
                    continue;
                }
            }

            try {
                if (Files.exists(folder)) {
                    deleteWorldFolder(folder);
                    plugin.getLogger().info("Deleted previous PvPCore world: " + name);
                }
            } catch (IOException error) {
                plugin.getLogger().warning("Could not delete previous PvPCore world "
                        + name + ": " + error.getMessage());
                complete = false;
            }
        }

        if (complete) {
            // Force each saved practice room and its duel background to be rebuilt
            // the next time the player enters. Keep their selected arena and room IDs.
            var players = plugin.getConfig().getConfigurationSection("practice.players");
            if (players != null) {
                for (String uuid : players.getKeys(false)) {
                    plugin.getConfig().set("practice.players." + uuid + ".built", false);
                }
            }
            plugin.getConfig().set("duels.rooms", null);
            plugin.getConfig().set(marker, true);
            plugin.saveConfig();
            plugin.getLogger().info("PvPCore world cleanup completed. All previous test arenas will be generated fresh when used.");
        } else {
            plugin.getLogger().warning("PvPCore world cleanup was incomplete. Remaining folders will be retried on the next server restart.");
        }
    }

    private long worldSeed(String key) {
        long fallback = switch (key) {
            case "plains" -> 731942105L;
            case "desert" -> 284710639L;
            case "snow" -> 918273645L;
            case "badlands" -> 563829174L;
            default -> 731942105L;
        };
        return plugin.getConfig().getLong("worlds." + key + ".seed", fallback);
    }

    private World loadOrCreateWorld(String key) {
        String name = plugin.getConfig().getString("worlds." + key + ".name", "pvp_" + key + "_arena");
        World world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name).environment(World.Environment.NORMAL)
                    .type(WorldType.NORMAL).seed(worldSeed(key)).generator(new PvPGenerator(key)).generateStructures(false).createWorld();
        }
        if (world != null) {
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            applyPermanentDay(world);
        }
        return world;
    }

    private static final String SPAWN_WORLD_NAME = "Spawn";

    private static final class VoidSpawnGenerator extends ChunkGenerator {
        @Override
        public void generateNoise(org.bukkit.generator.WorldInfo worldInfo, Random random,
                                  int chunkX, int chunkZ, ChunkData chunkData) {
            // Deliberately empty: the Spawn world is a true void world.
        }
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }

    /**
     * Creates/loads the dedicated void hub world and pastes High Crown from
     * the JAR resource. Existing normal world "world" is deliberately preserved.
     */
    public void installHighCrownSpawnOnce() {
        World spawnWorld = Bukkit.getWorld(SPAWN_WORLD_NAME);
        if (spawnWorld == null) {
            spawnWorld = new WorldCreator(SPAWN_WORLD_NAME)
                    .environment(World.Environment.NORMAL)
                    .type(WorldType.NORMAL)
                    .generator(new VoidSpawnGenerator())
                    .generateStructures(false)
                    .createWorld();
        }
        if (spawnWorld == null) {
            plugin.getLogger().severe("Could not create the void Spawn world.");
            return;
        }

        spawnWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        spawnWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        applyPermanentDay(spawnWorld);

        if (plugin.getConfig().getBoolean("spawn.void-hub-installed", false)) {
            plugin.getConfig().set("spawn.world", SPAWN_WORLD_NAME);
            plugin.saveConfig();
            return;
        }

        try {
            final int baseY = 64;
            SchematicLoader.paste(plugin, spawnWorld, "HIGH_CROWN_SPAWN",
                    null, 0, baseY, 0);

            // Center platform: local (95, 101, 131), bottom slab top at y=101.5.
            Location center = new Location(spawnWorld, 0.5D, baseY + 101.5D, 0.5D, 0.0F, 0.0F);
            plugin.getConfig().set("spawn.world", SPAWN_WORLD_NAME);
            plugin.getConfig().set("spawn.x", center.getX());
            plugin.getConfig().set("spawn.y", center.getY());
            plugin.getConfig().set("spawn.z", center.getZ());
            plugin.getConfig().set("spawn.yaw", center.getYaw());
            plugin.getConfig().set("spawn.pitch", center.getPitch());
            plugin.getConfig().set("spawn.high-crown-base-y", baseY);
            plugin.getConfig().set("spawn.void-hub-installed", true);
            spawnWorld.setSpawnLocation(0, baseY + 102, 0);
            plugin.saveConfig();

            plugin.getLogger().info("High Crown hub installed in void world 'Spawn' at X=0, Z=0.");
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not install High Crown spawn: " + exception.getMessage());
        }
    }

    public void setSpawn(Player player) {
        Location location = player.getLocation();
        plugin.getConfig().set("spawn.world", location.getWorld().getName());
        plugin.getConfig().set("spawn.x", location.getX());
        plugin.getConfig().set("spawn.y", location.getY());
        plugin.getConfig().set("spawn.z", location.getZ());
        plugin.getConfig().set("spawn.yaw", location.getYaw());
        plugin.getConfig().set("spawn.pitch", location.getPitch());
        plugin.saveConfig();
        plugin.message(player, "&aServer spawn location has been set.");
    }

    public void teleportToSpawn(Player player) {
        if (Bukkit.getWorld(SPAWN_WORLD_NAME) != null) {
            plugin.getConfig().set("spawn.world", SPAWN_WORLD_NAME);
        }
        String configuredWorld = plugin.getConfig().getString("spawn.world");
        World spawnWorld = configuredWorld == null ? null : Bukkit.getWorld(configuredWorld);
        Location destination = null;
        if (spawnWorld != null) {
            destination = new Location(spawnWorld,
                    plugin.getConfig().getDouble("spawn.x"),
                    plugin.getConfig().getDouble("spawn.y"),
                    plugin.getConfig().getDouble("spawn.z"),
                    (float) plugin.getConfig().getDouble("spawn.yaw"),
                    (float) plugin.getConfig().getDouble("spawn.pitch"));
        }
        if (destination == null) {
            spawnWorld = Bukkit.getWorlds().stream()
                    .filter(world -> !isManagedWorldName(world.getName())).findFirst().orElse(null);
            if (spawnWorld == null) {
                spawnWorld = Bukkit.getWorld("pvpcore_regeneration_holding");
                if (spawnWorld == null) spawnWorld = new WorldCreator("pvpcore_regeneration_holding")
                        .environment(World.Environment.NORMAL).type(WorldType.NORMAL).createWorld();
            }
            if (spawnWorld != null) destination = spawnWorld.getSpawnLocation();
        }
        if (destination == null) {
            plugin.message(player, "&cThe server spawn world is unavailable.");
            return;
        }
        player.teleport(destination);
        plugin.message(player, "&aTeleported to spawn.");
    }

    public boolean teleport(Player player, String key) {
        if (!isWorldKey(key)) {
            plugin.message(player, "&cUnknown arena world.");
            return false;
        }
        if (!plugin.beginRtp(player)) return false;
        World world = loadOrCreateWorld(key);
        if (world == null) {
            plugin.message(player, "&cThe world could not be loaded or created.");
            plugin.finishRtp(player);
            return false;
        }
        int radius = Math.max(100, plugin.getConfig().getInt("worlds." + key + ".radius", 5000));
        if (!teleportToCachedLocation(player, world, key)) findSafeRtpLocation(player, world, key, radius, 0);
        return true;
    }

    private void findSafeRtpLocation(Player player, World world, String key, int radius, int attempt) {
        if (!player.isOnline()) {
            plugin.finishRtp(player);
            return;
        }
        if (attempt >= 24) {
            plugin.message(player, "&cNo safe spawn point was found. Please try again.");
            plugin.finishRtp(player);
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int x = random.nextInt(-radius, radius + 1);
        int z = random.nextInt(-radius, radius + 1);
        world.getChunkAtAsync(x >> 4, z >> 4, true).whenComplete((chunk, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (error != null || chunk == null) {
                        findSafeRtpLocation(player, world, key, radius, attempt + 1);
                        return;
                    }

                    Block floor = world.getHighestBlockAt(x, z);
                    Block feet = floor.getRelative(0, 1, 0);
                    Block head = floor.getRelative(0, 2, 0);
                    if (!floor.getType().isSolid() || !feet.isPassable() || !head.isPassable()
                            || floor.isLiquid()) {
                        findSafeRtpLocation(player, world, key, radius, attempt + 1);
                        return;
                    }

                    Location destination = new Location(world, x + 0.5, floor.getY() + 1, z + 0.5);
                    destination.setYaw(player.getLocation().getYaw());
                    player.teleportAsync(destination).thenAccept(success ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (success && player.isOnline()) {
                                    plugin.message(player, "&aTeleported to &f" + key.toUpperCase(Locale.ROOT) + "&a.");
                                    plugin.finishRtp(player);
                                } else if (player.isOnline()) {
                                    findSafeRtpLocation(player, world, key, radius, attempt + 1);
                                } else {
                                    plugin.finishRtp(player);
                                }
                            }));
                }));
    }

    private static final List<String> WORLD_KEYS = List.of("plains", "desert", "snow", "badlands");
    private record ReturnTarget(Player player, String worldKey) {}

    public boolean isWorldKey(String key) {
        return WORLD_KEYS.contains(key.toLowerCase(Locale.ROOT));
    }

    public void randomTeleport(Player player) {
        if (!plugin.beginRtp(player)) return;
        List<String> keys = new ArrayList<>(WORLD_KEYS);
        Collections.shuffle(keys);
        for (String key : keys) {
            World world = loadOrCreateWorld(key);
            if (world != null) {
                int radius = Math.max(100, plugin.getConfig().getInt("worlds." + key + ".radius", 5000));
                if (!teleportToCachedLocation(player, world, key)) findSafeRtpLocation(player, world, key, radius, 0);
                return;
            }
        }
        plugin.message(player, "&cNo arena world is currently available.");
        plugin.finishRtp(player);
    }

    public void startRegenerationCycle() {
        // Keep safe, already-generated destinations ready before players request RTP.
        Bukkit.getScheduler().runTaskTimer(plugin, this::warmRtpCaches, 20L, 10L);
        scheduleNextRegeneration();
    }

    private void warmRtpCaches() {
        for (String key : WORLD_KEYS) {
            Deque<Location> cache = rtpLocationCache.get(key);
            if (cache == null || cache.size() >= RTP_CACHE_TARGET || rtpCacheWarming.contains(key)) continue;
            World world = Bukkit.getWorld(plugin.getConfig().getString("worlds." + key + ".name", "pvp_" + key + "_arena"));
            if (world == null) continue;

            rtpCacheWarming.add(key);
            int radius = Math.max(100, plugin.getConfig().getInt("worlds." + key + ".radius", 5000));
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int x = random.nextInt(-radius, radius + 1);
            int z = random.nextInt(-radius, radius + 1);
            world.getChunkAtAsync(x >> 4, z >> 4, true).whenComplete((chunk, error) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            if (error == null && chunk != null && Bukkit.getWorld(world.getUID()) != null) {
                                Location safe = highestSafeLocationAt(world, x, z);
                                if (safe != null && cache.size() < RTP_CACHE_TARGET) cache.addLast(safe);
                            }
                        } finally {
                            rtpCacheWarming.remove(key);
                        }
                    }));
        }
    }

    private Location highestSafeLocationAt(World world, int x, int z) {
        Block floor = world.getHighestBlockAt(x, z);
        Block feet = floor.getRelative(0, 1, 0);
        Block head = floor.getRelative(0, 2, 0);
        if (!floor.getType().isSolid() || !feet.isPassable() || !head.isPassable() || floor.isLiquid()) return null;
        return new Location(world, x + 0.5, floor.getY() + 1, z + 0.5);
    }

    private boolean teleportToCachedLocation(Player player, World world, String key) {
        Deque<Location> cache = rtpLocationCache.get(key);
        Location destination = cache == null ? null : cache.pollFirst();
        if (destination == null) return false;
        destination.setYaw(player.getLocation().getYaw());
        player.teleportAsync(destination).whenComplete((success, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error == null && Boolean.TRUE.equals(success) && player.isOnline()) {
                        plugin.message(player, "&aTeleported to &f" + key.toUpperCase(Locale.ROOT) + "&a.");
                        // Warm the surrounding area in the background after the player arrives.
                        int centerX = destination.getBlockX() >> 4;
                        int centerZ = destination.getBlockZ() >> 4;
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                if (dx == 0 && dz == 0) continue;
                                world.getChunkAtAsync(centerX + dx, centerZ + dz, true);
                            }
                        }
                    } else if (player.isOnline()) {
                        findSafeRtpLocation(player, world, key,
                                Math.max(100, plugin.getConfig().getInt("worlds." + key + ".radius", 5000)), 0);
                        return;
                    }
                    plugin.finishRtp(player);
                }));
        return true;
    }

    private void scheduleNextRegeneration() {
        if (!plugin.getConfig().getBoolean("worlds.regeneration.enabled", true)) return;
        long intervalTicks = Math.max(60L, plugin.getConfig().getLong("worlds.regeneration.interval-seconds", 300L)) * 20L;
        long countdownTicks = Math.max(1L, plugin.getConfig().getLong("worlds.regeneration.countdown-seconds", 5L)) * 20L;
        long wait = Math.max(1L, intervalTicks - countdownTicks);
        Bukkit.getScheduler().runTaskLater(plugin, () -> showRegenerationCountdown(
                (int) Math.max(1L, countdownTicks / 20L)), wait);
    }

    private void showRegenerationCountdown(int seconds) {
        if (seconds <= 0) {
            regenerateSharedWorlds();
            scheduleNextRegeneration();
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendTitle(plugin.color("&c&l" + seconds), plugin.color("&eArena regeneration"), 0, 20, 0);
        }
        Bukkit.broadcastMessage(plugin.color("&8[&aPvPCore&8] &eShared arenas regenerate in &c" + seconds + "&e..."));
        Bukkit.getScheduler().runTaskLater(plugin, () -> showRegenerationCountdown(seconds - 1), 20L);
    }

    private record PlayerReturn(Player player, World world, int x, int z, float yaw, float pitch,
                                 boolean allowFlight, boolean flying) {}
    private record ChunkTarget(World world, int x, int z) {}

    public boolean resetArenas() {
        if (regeneratingArenas) return false;
        regenerateSharedWorlds();
        return true;
    }

    private void regenerateSharedWorlds() {
        if (regeneratingArenas) return;
        regeneratingArenas = true;
        List<PlayerReturn> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isManagedWorldName(player.getWorld().getName())) continue;
            Location old = player.getLocation();
            players.add(new PlayerReturn(player, old.getWorld(), old.getBlockX(), old.getBlockZ(),
                    old.getYaw(), old.getPitch(), player.getAllowFlight(), player.isFlying()));
        }

        // Move players straight upward in their current arena. Flight is temporarily
        // enabled so they remain safely above the terrain while chunks are rebuilt.
        for (PlayerReturn target : players) {
            Player player = target.player();
            if (!player.isOnline()) continue;
            World world = target.world();
            player.setAllowFlight(true);
            player.setFlying(true);
            Location above = new Location(world, target.x() + 0.5,
                    Math.min(world.getMaxHeight() - 4, Math.max(world.getMinHeight() + 8, world.getMaxHeight() - 8)),
                    target.z() + 0.5, target.yaw(), target.pitch());
            player.teleport(above);
            player.setAllowFlight(true);
            player.setFlying(true);
            plugin.message(player, "&eArena regenerating in place...");
        }

        List<ChunkTarget> chunks = new ArrayList<>();
        for (String key : WORLD_KEYS) {
            String name = plugin.getConfig().getString("worlds." + key + ".name", "pvp_" + key + "_arena");
            World world = Bukkit.getWorld(name);
            if (world == null) {
                plugin.getLogger().warning("Skipping regeneration of unloaded arena " + name + ".");
                continue;
            }
            rtpLocationCache.get(key).clear();
            chunks.addAll(collectStoredChunks(world));
            for (Chunk loaded : world.getLoadedChunks()) {
                chunks.add(new ChunkTarget(world, loaded.getX(), loaded.getZ()));
            }
        }

        // De-duplicate region-file entries and currently loaded chunks.
        Map<String, ChunkTarget> unique = new LinkedHashMap<>();
        for (ChunkTarget target : chunks) {
            String id = target.world().getUID() + ":" + target.x() + ":" + target.z();
            unique.put(id, target);
        }
        Deque<ChunkTarget> queue = new ArrayDeque<>(unique.values());
        final int total = queue.size();
        final int[] completed = {0};

        if (total == 0) {
            finishRegeneration(players);
            regeneratingArenas = false;
            return;
        }

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int budget = 1; // Keep each tick bounded; regenerate every saved chunk, including underground.
            while (budget-- > 0 && !queue.isEmpty()) {
                ChunkTarget target = queue.pollFirst();
                try {
                    if (!target.world().regenerateChunk(target.x(), target.z())) {
                        plugin.getLogger().warning("Could not regenerate chunk " + target.x() + "," + target.z()
                                + " in " + target.world().getName());
                    }
                } catch (Throwable error) {
                    plugin.getLogger().warning("Chunk regeneration failed in " + target.world().getName()
                            + " at " + target.x() + "," + target.z() + ": " + error.getMessage());
                }
                completed[0]++;
            }

            if (completed[0] % 100 == 0 || queue.isEmpty()) {
                Bukkit.broadcastMessage(plugin.color("&8[&aPvPCore&8] &eRegenerating arenas: &f"
                        + completed[0] + "/" + total + " &echunks"));
            }
            if (queue.isEmpty()) {
                task.cancel();
                finishRegeneration(players);
                regeneratingArenas = false;
            }
        }, 1L, 1L);
    }

    private List<ChunkTarget> collectStoredChunks(World world) {
        List<ChunkTarget> chunks = new ArrayList<>();
        Path regionFolder = world.getWorldFolder().toPath().resolve("region");
        if (!Files.isDirectory(regionFolder)) return chunks;
        try (Stream<Path> files = Files.list(regionFolder)) {
            files.filter(path -> path.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.mca"))
                    .forEach(path -> {
                        String[] parts = path.getFileName().toString()
                                .replaceFirst("^r\\.", "").replace(".mca", "").split("\\.");
                        int regionX = Integer.parseInt(parts[0]);
                        int regionZ = Integer.parseInt(parts[1]);
                        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
                            for (int slot = 0; slot < 1024; slot++) {
                                file.seek((long) slot * 4L);
                                if (file.readInt() == 0) continue;
                                int chunkX = regionX * 32 + (slot & 31);
                                int chunkZ = regionZ * 32 + (slot >> 5);
                                chunks.add(new ChunkTarget(world, chunkX, chunkZ));
                            }
                        } catch (IOException error) {
                            plugin.getLogger().warning("Could not read region file " + path + ": " + error.getMessage());
                        }
                    });
        } catch (IOException error) {
            plugin.getLogger().severe("Could not list region files for " + world.getName() + ": " + error.getMessage());
        }
        return chunks;
    }

    private void finishRegeneration(List<PlayerReturn> players) {
        for (PlayerReturn target : players) {
            Player player = target.player();
            if (!player.isOnline()) continue;
            World world = target.world();
            world.getChunkAtAsync(target.x() >> 4, target.z() >> 4, true).whenComplete((chunk, error) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        Location safe = error == null && chunk != null
                                ? highestSafeLocationAt(world, target.x(), target.z()) : null;
                        if (safe == null) safe = highestSafeLocation(world);
                        if (safe != null) {
                            safe.setYaw(target.yaw());
                            safe.setPitch(target.pitch());
                            player.teleport(safe);
                        }
                        player.setFlying(false);
                        player.setAllowFlight(target.allowFlight());
                        if (target.flying() && target.allowFlight()) player.setFlying(true);
                        plugin.message(player, "&aYour arena has been regenerated.");
                    }));
        }
        Bukkit.broadcastMessage(plugin.color("&8[&aPvPCore&8] &aAll saved arena chunks have been regenerated!"));
    }

    private Location highestSafeLocation(World world) {
        Location spawn = world.getSpawnLocation();
        int centerX = spawn.getBlockX();
        int centerZ = spawn.getBlockZ();
        for (int radius = 0; radius <= 16; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    Block floor = world.getHighestBlockAt(x, z);
                    Block feet = floor.getRelative(0, 1, 0);
                    Block head = floor.getRelative(0, 2, 0);
                    if (floor.getType().isSolid() && !floor.isLiquid()
                            && feet.isPassable() && head.isPassable()) {
                        return new Location(world, x + 0.5, floor.getY() + 1, z + 0.5);
                    }
                }
            }
        }
        return null;
    }

    private boolean isManagedWorldName(String name) {
        return "prac_void".equalsIgnoreCase(name)
                || "pvpcore_regeneration_holding".equalsIgnoreCase(name)
                || keyForWorldName(name) != null;
    }

    private String keyForWorldName(String name) {
        for (String key : WORLD_KEYS) {
            String configured = plugin.getConfig().getString("worlds." + key + ".name", "pvp_" + key + "_arena");
            if (configured.equalsIgnoreCase(name)) return key;
        }
        return null;
    }


    private void deleteWorldFolder(Path folder) throws IOException {
        if (!Files.exists(folder)) return;
        try (Stream<Path> paths = Files.walk(folder)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }


}
