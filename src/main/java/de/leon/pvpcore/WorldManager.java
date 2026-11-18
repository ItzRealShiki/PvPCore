package de.leon.pvpcore;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public final class WorldManager {
    private final PvPCore plugin;

    public WorldManager(PvPCore plugin) {
        this.plugin = plugin;
    }

    public boolean teleport(Player player, String key) {
        String base = "worlds." + key + ".";
        String name = plugin.getConfig().getString(base + "name", "pvp_" + key);
        World world = Bukkit.getWorld(name);
        if (world == null) {
            WorldCreator creator = new WorldCreator(name)
                    .environment(World.Environment.NORMAL)
                    .type(WorldType.FLAT)
                    .generateStructures(false)
                    .generatorSettings(generatorSettings(key));
            world = creator.createWorld();
        }
        if (world == null) {
            plugin.message(player, "&cDie Welt konnte nicht geladen oder erstellt werden.");
            return false;
        }

        int radius = Math.max(100, plugin.getConfig().getInt(base + "radius", 5000));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 50; attempt++) {
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
            plugin.message(player, "&aDu wurdest nach &f" + key.toUpperCase() + " &ateleportiert.");
            return true;
        }

        plugin.message(player, "&cEs konnte kein sicherer Spawnpunkt gefunden werden. Bitte erneut versuchen.");
        return false;
    }

    private String generatorSettings(String key) {
        return switch (key.toLowerCase()) {
            case "desert" -> "{\"biome\":\"minecraft:desert\",\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},{\"block\":\"minecraft:sandstone\",\"height\":2},{\"block\":\"minecraft:sand\",\"height\":1}],\"structures\":{}}";
            case "snow" -> "{\"biome\":\"minecraft:snowy_plains\",\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},{\"block\":\"minecraft:dirt\",\"height\":2},{\"block\":\"minecraft:snow_block\",\"height\":1}],\"structures\":{}}";
            case "badlands" -> "{\"biome\":\"minecraft:badlands\",\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},{\"block\":\"minecraft:terracotta\",\"height\":3}],\"structures\":{}}";
            default -> "{\"biome\":\"minecraft:plains\",\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},{\"block\":\"minecraft:dirt\",\"height\":2},{\"block\":\"minecraft:grass_block\",\"height\":1}],\"structures\":{}}";
        };
    }
}
