package de.leon.pvpcore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawn protection plus per-player, randomly generated floating parkour routes.
 * Parkour courses share the Spawn world but are built in isolated plots per UUID.
 */
public final class ParkourManager implements Listener {
    private static final String SPAWN_WORLD = "Spawn";
    private static final int COURSE_START_Y = 180;
    private static final int COURSE_JUMPS = 32;
    private static final int PLOT_SPACING = 512;

    private static final Material[] COLOUR_PALETTE = {
            Material.WHITE_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.GRAY_CONCRETE,
            Material.BLACK_CONCRETE, Material.BROWN_CONCRETE, Material.RED_CONCRETE,
            Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE,
            Material.GREEN_CONCRETE, Material.CYAN_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
            Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE, Material.MAGENTA_CONCRETE,
            Material.PINK_CONCRETE
    };

    private final PvPCore plugin;
    private final NamespacedKey entrancePlateKey;
    private final Map<UUID, Run> activeRuns = new ConcurrentHashMap<>();

    private record Run(int startY, int finishX, int finishY, int finishZ) {}

    public ParkourManager(PvPCore plugin) {
        this.plugin = plugin;
        this.entrancePlateKey = new NamespacedKey(plugin, "parkour_entrance_plate");
    }

    public ItemStack createEntrancePlate() {
        ItemStack item = new ItemStack(Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.color("&6Parkour Entrance"));
        meta.setLore(java.util.List.of(
                plugin.color("&7Place this in the Spawn world."),
                plugin.color("&7Players step on it to enter their"),
                plugin.color("&7own private parkour course.")
        ));
        meta.getPersistentDataContainer().set(entrancePlateKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public void giveEntrancePlate(Player player) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(createEntrancePlate());
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        plugin.message(player, "&aParkour entrance plate added to your inventory.");
    }

    private boolean isEntrancePlate(ItemStack item) {
        return item != null && item.getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                .has(entrancePlateKey, PersistentDataType.BYTE);
    }

    private boolean isSpawnWorld(World world) {
        return world != null && SPAWN_WORLD.equalsIgnoreCase(world.getName());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnBlockPlace(BlockPlaceEvent event) {
        if (!isSpawnWorld(event.getBlockPlaced().getWorld())) return;

        if (isEntrancePlate(event.getItemInHand())) {
            Player player = event.getPlayer();
            if (!plugin.hasCore(player, "Admin.Core")) {
                event.setCancelled(true);
                plugin.message(player, "&cOnly admins can place a Parkour entrance.");
                return;
            }
            Location placed = event.getBlockPlaced().getLocation();
            plugin.getConfig().set("spawn.parkour-plate.world", SPAWN_WORLD);
            plugin.getConfig().set("spawn.parkour-plate.x", placed.getBlockX());
            plugin.getConfig().set("spawn.parkour-plate.y", placed.getBlockY());
            plugin.getConfig().set("spawn.parkour-plate.z", placed.getBlockZ());
            plugin.saveConfig();
            plugin.message(player, "&aParkour entrance set. Players can now step on the plate.");
            return;
        }

        // Spawn remains an interaction-only lobby: buttons, doors, NPCs and menus
        // still work because PlayerInteractEvent is intentionally not cancelled.
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnBlockBreak(BlockBreakEvent event) {
        if (!isSpawnWorld(event.getBlock().getWorld())) return;
        if (isConfiguredPlate(event.getBlock().getLocation())
                && plugin.hasCore(event.getPlayer(), "Admin.Core")) {
            plugin.getConfig().set("spawn.parkour-plate", null);
            plugin.saveConfig();
            plugin.message(event.getPlayer(), "&eParkour entrance removed.");
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isSpawnWorld(event.getPlayer().getWorld())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnBucketFill(PlayerBucketFillEvent event) {
        if (isSpawnWorld(event.getPlayer().getWorld())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isSpawnWorld(player.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnDrop(PlayerDropItemEvent event) {
        if (isSpawnWorld(event.getPlayer().getWorld())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnExplosion(EntityExplodeEvent event) {
        if (isSpawnWorld(event.getLocation().getWorld())) event.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnBlockExplosion(BlockExplodeEvent event) {
        if (isSpawnWorld(event.getBlock().getWorld())) event.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnBlockBurn(BlockBurnEvent event) {
        if (isSpawnWorld(event.getBlock().getWorld())) event.setCancelled(true);
    }

    private boolean isConfiguredPlate(Location location) {
        return SPAWN_WORLD.equalsIgnoreCase(plugin.getConfig().getString("spawn.parkour-plate.world", ""))
                && location.getBlockX() == plugin.getConfig().getInt("spawn.parkour-plate.x", Integer.MIN_VALUE)
                && location.getBlockY() == plugin.getConfig().getInt("spawn.parkour-plate.y", Integer.MIN_VALUE)
                && location.getBlockZ() == plugin.getConfig().getInt("spawn.parkour-plate.z", Integer.MIN_VALUE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        Player player = event.getPlayer();
        Run run = activeRuns.get(player.getUniqueId());

        if (run != null) {
            if (!isSpawnWorld(to.getWorld())) {
                activeRuns.remove(player.getUniqueId());
                return;
            }
            if (to.getY() < run.startY() - 9) {
                activeRuns.remove(player.getUniqueId());
                plugin.worlds().teleportToSpawn(player);
                plugin.message(player, "&eYou fell. Back to spawn!");
                return;
            }
            if (Math.abs(to.getX() - (run.finishX() + 0.5D)) <= 1.2D
                    && Math.abs(to.getZ() - (run.finishZ() + 0.5D)) <= 1.2D
                    && to.getY() >= run.finishY() + 0.8D
                    && to.getY() <= run.finishY() + 3.0D) {
                activeRuns.remove(player.getUniqueId());
                plugin.worlds().teleportToSpawn(player);
                plugin.message(player, "&aParkour completed! Welcome back to spawn.");
            }
            return;
        }

        if (!isSpawnWorld(to.getWorld()) || !isStandingOnConfiguredPlate(to)) return;
        startPersonalParkour(player);
    }

    private boolean isStandingOnConfiguredPlate(Location feet) {
        if (!plugin.getConfig().contains("spawn.parkour-plate.x")) return false;
        Location currentBlock = new Location(feet.getWorld(),
                feet.getBlockX(), feet.getBlockY(), feet.getBlockZ());
        Location blockBelow = currentBlock.clone().subtract(0, 1, 0);
        return isConfiguredPlate(currentBlock) || isConfiguredPlate(blockBelow);
    }

    private void startPersonalParkour(Player player) {
        World spawn = Bukkit.getWorld(SPAWN_WORLD);
        if (spawn == null) {
            plugin.message(player, "&cThe Spawn world is not available.");
            return;
        }

        UUID uuid = player.getUniqueId();
        String path = "parkour.players." + uuid;
        int plot = plugin.getConfig().getInt(path + ".plot", -1);
        if (plot < 0) {
            plot = plugin.getConfig().getInt("parkour.next-plot", 0);
            plugin.getConfig().set(path + ".plot", plot);
            plugin.getConfig().set("parkour.next-plot", plot + 1);
        }

        int baseX = 512 + (plot % 100) * PLOT_SPACING;
        int baseZ = 512 + (plot / 100) * PLOT_SPACING;
        int startY = COURSE_START_Y;
        int finishX;
        int finishY;
        int finishZ;

        if (!plugin.getConfig().getBoolean(path + ".built", false)
                || spawn.getBlockAt(baseX, startY, baseZ).getType().isAir()) {
            int[] finish = buildCourse(spawn, baseX, baseZ, startY, uuid);
            finishX = finish[0];
            finishY = finish[1];
            finishZ = finish[2];
            plugin.getConfig().set(path + ".built", true);
            plugin.getConfig().set(path + ".finish-x", finishX);
            plugin.getConfig().set(path + ".finish-y", finishY);
            plugin.getConfig().set(path + ".finish-z", finishZ);
            plugin.saveConfig();
        } else {
            finishX = plugin.getConfig().getInt(path + ".finish-x");
            finishY = plugin.getConfig().getInt(path + ".finish-y");
            finishZ = plugin.getConfig().getInt(path + ".finish-z");
        }

        Location start = new Location(spawn, baseX + 0.5D, startY + 1.0D, baseZ + 0.5D, -90.0F, 0.0F);
        spawn.getChunkAt(start.getBlockX() >> 4, start.getBlockZ() >> 4);
        activeRuns.put(uuid, new Run(startY, finishX, finishY, finishZ));
        player.teleport(start);
        plugin.message(player, "&dYour private parkour has started. Good luck!");
    }

    private int[] buildCourse(World world, int baseX, int baseZ, int baseY, UUID uuid) {
        Random random = new Random(uuid.getMostSignificantBits()
                ^ uuid.getLeastSignificantBits() ^ System.nanoTime());
        makePlatform(world, baseX, baseY, baseZ, 3, random);

        int x = baseX;
        int z = baseZ;
        int y = baseY;
        for (int jump = 0; jump < COURSE_JUMPS; jump++) {
            // Gap of four empty blocks is intentionally rare (one entry in eight).
            int[] weightedGaps = {1, 1, 2, 2, 2, 3, 3, 4};
            int gap = weightedGaps[random.nextInt(weightedGaps.length)];
            x += gap + (jump == 0 ? 2 : 1);
            z = Math.max(baseZ - 12, Math.min(baseZ + 12, z + random.nextInt(5) - 2));
            // Each next platform is at most three blocks higher than the previous one.
            y = Math.min(baseY + 48, y + random.nextInt(4));
            world.getChunkAt(x >> 4, z >> 4);
            world.getBlockAt(x, y, z).setType(COLOUR_PALETTE[random.nextInt(COLOUR_PALETTE.length)], false);
        }

        // The final landing is widened into a small finish platform.
        makePlatform(world, x, y, z, 3, random);
        return new int[]{x, y, z};
    }

    private void makePlatform(World world, int centerX, int y, int centerZ, int size, Random random) {
        int radius = size / 2;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                world.getChunkAt(x >> 4, z >> 4);
                world.getBlockAt(x, y, z)
                        .setType(COLOUR_PALETTE[random.nextInt(COLOUR_PALETTE.length)], false);
            }
        }
    }

    public boolean leaveToSpawn(Player player) {
        if (activeRuns.remove(player.getUniqueId()) == null) return false;
        plugin.worlds().teleportToSpawn(player);
        plugin.message(player, "&aYou left your private parkour.");
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        activeRuns.remove(event.getPlayer().getUniqueId());
    }
}
