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

            for (int x=0; x<16; x++) for (int z=0; z<16; z++) {
                int wx=chunkX*16+x, wz=chunkZ*16+z;
                int surface=height(wx,wz,seed);
                if (biome != null) biomeGrid.setBiome(x,z,biome);
                data.setBlock(x,minY,z,Material.BEDROCK);
                for (int y=minY+1; y<surface-4; y++) data.setBlock(x,y,z,Material.STONE);
                for (int y=surface-4; y<surface; y++) data.setBlock(x,y,z,filler);
                data.setBlock(x,surface,z,top);
                if (terrainKey.equals("snow")) data.setBlock(x,surface+1,z,Material.SNOW);
            }

            // Ore veins are placed only inside existing stone, never in open air.
            Random ores = new Random(seed ^ (chunkX * 341873128712L) ^ (chunkZ * 132897987541L));
            Material[] types = {Material.COAL_ORE, Material.IRON_ORE, Material.COPPER_ORE,
                    Material.GOLD_ORE, Material.REDSTONE_ORE, Material.LAPIS_ORE,
                    Material.DIAMOND_ORE, Material.EMERALD_ORE};
            int[] counts = {14, 10, 9, 5, 5, 4, 3, 1};
            int[] sizes = {9, 8, 9, 7, 7, 6, 5, 3};
            int[] ceilings = {120, 64, 48, 32, 16, 24, 16, 24};
            for (int i=0;i<types.length;i++) for (int vein=0;vein<counts[i];vein++) {
                int ox=ores.nextInt(16), oz=ores.nextInt(16);
                int oy=minY+5+ores.nextInt(Math.max(1, ceilings[i]-minY-4));
                for (int j=0;j<sizes[i];j++) {
                    int px=ox+ores.nextInt(3)-1, py=oy+ores.nextInt(3)-1, pz=oz+ores.nextInt(3)-1;
                    if (px<0||px>15||pz<0||pz>15||py<=minY||py>=height(chunkX*16+px,chunkZ*16+pz,seed)-4) continue;
                    if (data.getType(px,py,pz)==Material.STONE) data.setBlock(px,py,pz,types[i]);
                }
            }

            // A rare, naturally placed single tree; no floating blocks or random stone pillars.
            Random flora = new Random(seed + chunkX * 73428767L + chunkZ * 912931L);
            if (flora.nextInt(18)==0 && !terrainKey.equals("desert")) {
                int tx=flora.nextInt(12)+2, tz=flora.nextInt(12)+2;
                int wx=chunkX*16+tx, wz=chunkZ*16+tz, sy=height(wx,wz,seed);
                Material log=terrainKey.equals("snow")?Material.SPRUCE_LOG:
                        terrainKey.equals("badlands")?Material.ACACIA_LOG:Material.OAK_LOG;
                Material leaves=terrainKey.equals("snow")?Material.SPRUCE_LEAVES:
                        terrainKey.equals("badlands")?Material.ACACIA_LEAVES:Material.OAK_LEAVES;
                for(int y=1;y<=4;y++) data.setBlock(tx,sy+y,tz,log);
                for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) {
                    if(Math.abs(dx)+Math.abs(dz)>3) continue;
                    for(int dy=2;dy<=5;dy++) {
                        int lx=tx+dx, lz=tz+dz, ly=sy+dy;
                        if(lx>=0&&lx<16&&lz>=0&&lz<16&&data.getType(lx,ly,lz).isAir())
                            data.setBlock(lx,ly,lz,leaves);
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

    public boolean teleport(Player player, String key) {
        String base="worlds."+key+".";
        String name=plugin.getConfig().getString(base+"name","pvp_"+key+"_arena");
        World world=Bukkit.getWorld(name);
        if(world==null) world=new WorldCreator(name).environment(World.Environment.NORMAL)
                .type(WorldType.NORMAL).generator(new PvPGenerator(key)).generateStructures(false).createWorld();
        if(world==null){plugin.message(player,"&cThe world could not be loaded or created.");return false;}
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        int radius=Math.max(100,plugin.getConfig().getInt(base+"radius",5000));
        ThreadLocalRandom random=ThreadLocalRandom.current();
        for(int attempt=0;attempt<200;attempt++){
            int x=random.nextInt(-radius,radius+1), z=random.nextInt(-radius,radius+1);
            Block floor=world.getHighestBlockAt(x,z), feet=floor.getRelative(0,1,0), head=floor.getRelative(0,2,0);
            if(!floor.getType().isSolid()||!feet.isPassable()||!head.isPassable()||floor.isLiquid())continue;
            Location dest=new Location(world,x+.5,floor.getY()+1,z+.5);
            dest.setYaw(player.getLocation().getYaw()); player.teleport(dest);
            plugin.message(player,"&aTeleported to &f"+key.toUpperCase()+"&a."); return true;
        }
        plugin.message(player,"&cNo safe spawn point was found. Please try again."); return false;
    }

    private static final List<String> WORLD_KEYS = List.of("plains", "desert", "snow", "badlands");
    private record ReturnTarget(Player player, String worldKey) {}

    public boolean isWorldKey(String key) {
        return WORLD_KEYS.contains(key.toLowerCase(Locale.ROOT));
    }

    public void randomTeleport(Player player) {
        List<String> keys = new ArrayList<>(WORLD_KEYS);
        Collections.shuffle(keys);
        for (String key : keys) {
            if (teleport(player, key)) return;
        }
        plugin.message(player, "&cNo arena world is currently available.");
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

        List<ReturnTarget> returnTargets = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String key = keyForWorldName(player.getWorld().getName());
            if (key != null) {
                returnTargets.add(new ReturnTarget(player, key));
                player.teleport(holding.getSpawnLocation());
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
                    .type(WorldType.NORMAL).generator(new PvPGenerator(key)).generateStructures(false).createWorld();
            if (fresh == null) {
                plugin.getLogger().severe("Could not create regenerated arena " + name);
            } else {
                fresh.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            }
        }

        for (ReturnTarget target : returnTargets) {
            Player player = target.player();
            if (player.isOnline()) {
                World regenerated = Bukkit.getWorld(plugin.getConfig().getString(
                        "worlds." + target.worldKey() + ".name", "pvp_" + target.worldKey() + "_arena"));
                if (regenerated != null) teleport(player, target.worldKey());
                else player.teleport(holding.getSpawnLocation());
            }
        }
        Bukkit.broadcastMessage(plugin.color("&8[&aPvPCore&8] &aAll shared arena worlds have been regenerated!"));
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
