package de.leon.pvpcore;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.util.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.stream.Stream;
import java.util.concurrent.ThreadLocalRandom;

public final class WorldManager {
    private final PvPCore plugin;
    public WorldManager(PvPCore plugin) { this.plugin = plugin; }

    private static final class PvPGenerator extends ChunkGenerator {
        private final Biome biome;
        private final String terrainKey;
        private final LagMonitor monitor;
        private PvPGenerator(String key, LagMonitor monitor) {
            terrainKey = key.toLowerCase();
            this.monitor = monitor;
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
            long generationStarted = System.nanoTime();
            try {
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

            for (int x=0; x<16; x++) for (int z=0; z<16; z++) {
                int wx=chunkX*16+x, wz=chunkZ*16+z;
                int surface=height(wx,wz,seed);
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
                    if (px<0||px>15||pz<0||pz>15||py<=minY||py>=height(chunkX*16+px,chunkZ*16+pz,seed)-4) continue;
                    Material base = data.getType(px,py,pz);
                    if (base == Material.STONE || base == Material.DEEPSLATE) {
                        data.setBlock(px,py,pz,py <= 0 ? deepslateTypes[i] : types[i]);
                    }
                }
            }

            // Natural trees have a 5% spawn chance per generated non-desert chunk.
            Random flora = new Random(seed + chunkX * 73428767L + chunkZ * 912931L);
            if (flora.nextInt(100) < 5 && !terrainKey.equals("desert")) {
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
            } finally {
                monitor.recordOperation("Chunk generation [" + terrainKey + "]", System.nanoTime() - generationStarted);
            }
        }
        @Override public boolean shouldGenerateNoise(){return false;}
        @Override public boolean shouldGenerateSurface(){return false;}
        @Override public boolean shouldGenerateCaves(){return false;}
        @Override public boolean shouldGenerateDecorations(){return false;}
        @Override public boolean shouldGenerateMobs(){return false;}
        @Override public boolean shouldGenerateStructures(){return false;}
    }

    public void initializeWorldsOnce() {
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

    private World loadOrCreateWorld(String key) {
        long started = System.nanoTime();
        try {
            String name = plugin.getConfig().getString("worlds." + key + ".name", "pvp_" + key + "_arena");
            World world = Bukkit.getWorld(name);
            if (world == null) {
                world = new WorldCreator(name).environment(World.Environment.NORMAL)
                        .type(WorldType.NORMAL).generator(new PvPGenerator(key, plugin.lag())).generateStructures(false).createWorld();
            }
            if (world != null) world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            return world;
        } finally {
            plugin.lag().recordOperation("World load/create [" + key + "]", System.nanoTime() - started);
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
        findSafeRtpLocation(player, world, key, radius, 0);
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
                findSafeRtpLocation(player, world, key, radius, 0);
                return;
            }
        }
        plugin.message(player, "&cNo arena world is currently available.");
        plugin.finishRtp(player);
    }

    public void startRegenerationCycle() {
        scheduleNextRegeneration();
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
        long regenerationStarted = System.nanoTime();
        try {
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

        Location serverSpawn = holding.getSpawnLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isManagedWorldName(player.getWorld().getName())) {
                player.teleport(serverSpawn);
                plugin.message(player, "&eYour arena was regenerated. You were sent to spawn.");
            }
        }

        for (String key : WORLD_KEYS) {
            String name = plugin.getConfig().getString("worlds." + key + ".name", "pvp_" + key + "_arena");
            World oldWorld = Bukkit.getWorld(name);
            if (oldWorld != null) {
                oldWorld.save();
                if (!Bukkit.unloadWorld(oldWorld, false)) {
                    plugin.getLogger().severe("Could not unload " + name + "; skipping its regeneration.");
                    continue;
                }
            }

            Path folder = Bukkit.getWorldContainer().toPath().resolve(name);
            try {
                deleteWorldFolder(folder);
            } catch (IOException exception) {
                plugin.getLogger().severe("Could not delete old arena " + name + ": " + exception.getMessage());
                continue;
            }

            World fresh = new WorldCreator(name).environment(World.Environment.NORMAL)
                    .type(WorldType.NORMAL).generator(new PvPGenerator(key, plugin.lag())).generateStructures(false).createWorld();
            if (fresh == null) {
                plugin.getLogger().severe("Could not create regenerated arena " + name);
            } else {
                fresh.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            }
        }

        Bukkit.broadcastMessage(plugin.color("&8[&aPvPCore&8] &aAll shared arena worlds have been regenerated!"));
        } finally {
            plugin.lag().recordOperation("Scheduled arena regeneration (all worlds)", System.nanoTime() - regenerationStarted);
        }
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
