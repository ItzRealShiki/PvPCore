package de.leon.pvpcore;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.stream.Stream;

public final class WorldManager {
    private final PvPCore plugin;
    public WorldManager(PvPCore plugin) { this.plugin = plugin; }

    private void migrateArenaSettings() {
        if (plugin.getConfig().getBoolean("worlds.fixed-format-migrated", false)) return;
        String[][] arenas = {
                {"plains", "pvp_plains_arena_v2", "pvp_plains_arena_v3", "731942105"},
                {"desert", "pvp_desert_arena_v2", "pvp_desert_arena_v3", "284710639"},
                {"snow", "pvp_snow_arena_v2", "pvp_snow_arena_v3", "918273645"},
                {"badlands", "pvp_badlands_arena_v2", "pvp_badlands_arena_v3", "563829174"}
        };
        for (String[] arena : arenas) {
            String path = "worlds." + arena[0] + ".name";
            String configuredName = plugin.getConfig().getString(path, arena[1]);
            if (configuredName.equalsIgnoreCase(arena[1])) {
                plugin.getConfig().set(path, arena[2]);
            }
            String seedPath = "worlds." + arena[0] + ".seed";
            if (!plugin.getConfig().contains(seedPath)) {
                plugin.getConfig().set(seedPath, Long.parseLong(arena[3]));
            }
        }
        plugin.getConfig().set("worlds.regeneration.enabled", false);
        plugin.getConfig().set("worlds.fixed-format-migrated", true);
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
                    .type(WorldType.NORMAL).seed(worldSeed(key)).generateStructures(true).createWorld();
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
            World oldWorld = Bukkit.getWorld(name);
            if (oldWorld != null) {
                oldWorld.save();
                if (!Bukkit.unloadWorld(oldWorld, false)) {
                    plugin.getLogger().severe("Could not unload " + name + "; skipping its regeneration.");
                    continue;
                }
            }

            World fresh = new WorldCreator(name).environment(World.Environment.NORMAL)
                    .type(WorldType.NORMAL).seed(worldSeed(key)).generateStructures(true).createWorld();
            if (fresh == null) {
                plugin.getLogger().severe("Could not create regenerated arena " + name);
            } else {
                fresh.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            }
        }

        Bukkit.broadcastMessage(plugin.color("&8[&aPvPCore&8] &aAll shared arena worlds have been regenerated!"));
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
