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
        String name = plugin.getConfig().getString(base + "name", "pvp_" + key + "_natural");
        World world = Bukkit.getWorld(name);
        if (world == null) {
            // Use Minecraft's normal terrain generator: mountains, caves, ores,
            // natural biome transitions, trees and the standard -64 world bottom.
            WorldCreator creator = new WorldCreator(name)
                    .environment(World.Environment.NORMAL)
                    .type(WorldType.NORMAL)
                    .generateStructures(true);
            world = creator.createWorld();
        }
        if (world == null) {
            plugin.message(player, "&cThe world could not be loaded or created.");
            return false;
        }

        int radius = Math.max(100, plugin.getConfig().getInt(base + "radius", 5000));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 60; attempt++) {
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
