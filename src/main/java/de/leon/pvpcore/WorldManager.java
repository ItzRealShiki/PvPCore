package de.leon.pvpcore;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.stream.Stream;

public final class WorldManager {
    private final PvPCore plugin;
    private static final int RTP_CACHE_TARGET = 12;
    private final Map<String, Deque<Location>> rtpLocationCache = new HashMap<>();
    private final Set<String> rtpCacheWarming = new HashSet<>();

    public WorldManager(PvPCore plugin) {
        this.plugin = plugin;
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
            double n = noise(x,z,150,seed)*2.8 + noise(x,z,55,seed+7)*1.6
                    + noise(x,z,22,seed+19)*0.65;
            return 64 + (int)Math.round(n);
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
            for (int x=0; x<16; x++) for (int z=0; z<16; z++) {
                int wx=chunkX*16+x, wz=chunkZ*16+z;
                int surface=height(wx,wz,seed);
                surfaceHeights[x][z] = surface;
                if (biome != null) biomeGrid.setBiome(x,z,biome);
                data.setBlock(x,minY,z,Material.BEDROCK);
                for (int y=minY+1; y<surface-4; y++) data.setBlock(x,y,z,y <= 0 ? Material.DEEPSLATE : Material.STONE);
                for (int y=surface-4; y<surface; y++) data.setBlock(x,y,z,filler);
                data.setBlock(x,surface,z,top);
                if (terrainKey.equals("snow")) data.setBlock(x,surface+1,z,Material.SNOW);
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

            // Natural trees are 60% less common than the previous generator.
            Random flora = new Random(seed + chunkX * 73428767L + chunkZ * 912931L);
            if (flora.nextInt(100) < 40 && !terrainKey.equals("desert")) {
                int tx=flora.nextInt(8)+4, tz=flora.nextInt(8)+4;
                int wx=chunkX*16+tx, wz=chunkZ*16+tz, sy=height(wx,wz,seed);
                Material log=terrainKey.equals("snow")?Material.SPRUCE_LOG:
                        terrainKey.equals("badlands")?Material.ACACIA_LOG:Material.OAK_LOG;
                Material leaves=terrainKey.equals("snow")?Material.SPRUCE_LEAVES:
                        terrainKey.equals("badlands")?Material.ACACIA_LEAVES:Material.OAK_LEAVES;
                int trunkHeight=5+flora.nextInt(2);
                for(int y=1;y<=trunkHeight;y++) data.setBlock(tx,sy+y,tz,log);
                org.bukkit.block.data.BlockData leafBlock=Bukkit.createBlockData(leaves);
                if(leafBlock instanceof org.bukkit.block.data.type.Leaves leafData) leafData.setPersistent(true);
                for(int layer=0;layer<4;layer++) {
                    int canopyY=sy+trunkHeight-2+layer;
                    int radius=(layer==1||layer==2)?3:2;
                    for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
                        int distance=dx*dx+dz*dz;
                        if(distance>radius*radius+1 || (distance>1 && flora.nextDouble()<0.13)) continue;
                        int lx=tx+dx,lz=tz+dz;
                        if(lx>=0&&lx<16&&lz>=0&&lz<16&&data.getType(lx,canopyY,lz).isAir())
                            data.setBlock(lx,canopyY,lz,leafBlock);
                    }
                }
            }
            if(terrainKey.equals("desert") && flora.nextInt(100)<85) {
                int count=1+flora.nextInt(3);
                for(int i=0;i<count;i++) {
                    int bx=flora.nextInt(16),bz=flora.nextInt(16);
                    int surface=height(chunkX*16+bx,chunkZ*16+bz,seed);
                    if(data.getType(bx,surface,bz)==Material.SAND && data.getType(bx,surface+1,bz).isAir())
                        data.setBlock(bx,surface+1,bz,Material.DEAD_BUSH);
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
        plugin.saveConfig();
    }

    public void initializeWorldsOnce() {
        migrateArenaSettings();
        if (plugin.getConfig().getBoolean("worlds.initialized", false)) return;
        for (String key : WORLD_KEYS) {
            World world = loadOrCreateWorld(key);
            if (world == null) {
                plugin.getLogger().severe("Could not initialize arena world: " + key);
                return;
            }
        }
        plugin.getConfig().set("worlds.initialized", true);
        plugin.saveConfig();
        plugin.getLogger().info("All PvPCore arena worlds were created on first startup.");
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
        if (world != null) world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        return world;
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

    private void regenerateSharedWorlds() {
        World holding = Bukkit.getWorlds().stream()
                .filter(world -> !isManagedWorldName(world.getName()))
                .findFirst().orElse(null);
        if (holding == null) {
            holding = new WorldCreator("pvpcore_regeneration_holding").environment(World.Environment.NORMAL)
                    .type(WorldType.NORMAL).createWorld();
        }
        if (holding == null) {
            plugin.getLogger().severe("Could not find or create a temporary world; arena regeneration was cancelled.");
            return;
        }

        Location safeHoldingLocation = highestSafeLocation(holding);
        if (safeHoldingLocation == null) {
            plugin.getLogger().severe("Could not find a safe highest block in the holding world; arena regeneration was cancelled.");
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isManagedWorldName(player.getWorld().getName())) {
                player.teleport(safeHoldingLocation);
                plugin.message(player, "&eYour arena was regenerated. You were moved to a safe high location.");
            }
        }

        for (String key : WORLD_KEYS) {
            String name = plugin.getConfig().getString("worlds." + key + ".name", "pvp_" + key + "_arena");
            rtpLocationCache.get(key).clear();
            World oldWorld = Bukkit.getWorld(name);
            Path worldFolder = oldWorld != null ? oldWorld.getWorldFolder().toPath()
                    : Bukkit.getWorldContainer().toPath().resolve(name);
            if (oldWorld != null && !Bukkit.unloadWorld(oldWorld, false)) {
                plugin.getLogger().severe("Could not unload " + name + "; skipping its regeneration.");
                continue;
            }
            try {
                deleteWorldFolder(worldFolder);
            } catch (IOException exception) {
                plugin.getLogger().severe("Could not delete old arena folder " + name + ": " + exception.getMessage());
                continue;
            }

            World fresh = new WorldCreator(name).environment(World.Environment.NORMAL)
                    .type(WorldType.NORMAL).seed(worldSeed(key)).generator(new PvPGenerator(key)).generateStructures(false).createWorld();
            if (fresh == null) {
                plugin.getLogger().severe("Could not create regenerated arena " + name);
            } else {
                fresh.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            }
        }

        Bukkit.broadcastMessage(plugin.color("&8[&aPvPCore&8] &aAll shared arena worlds have been regenerated!"));
        // The cache task will immediately begin preparing fresh destinations in the new worlds.
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
        return keyForWorldName(name) != null;
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
