package de.leon.pvpcore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protected Spawn lobby and an endless, private three-platform parkour course.
 * Each run chooses one colour family; every platform uses a different block
 * selected from that same family. Only three platforms exist ahead at a time.
 */
public final class ParkourManager implements Listener {
    private static final String SPAWN_WORLD = "Spawn";
    private static final int COURSE_START_Y = 180;
    private static final int PLOT_SPACING = 10_000;

    private static final String[] COLOUR_FAMILIES = {
            "WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY",
            "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"
    };
    private static final String[] BLOCK_VARIANTS = {
            "_CONCRETE", "_TERRACOTTA", "_WOOL", "_GLAZED_TERRACOTTA", "_STAINED_GLASS"
    };

    private final PvPCore plugin;
    private final NamespacedKey entrancePlateKey;
    private final NamespacedKey leaderboardToolKey;
    private static final String LEADERBOARD_TAG = "pvpcore_parkour_leaderboard";
    private TextDisplay leaderboardDisplay;

    private record Leader(String name, int score) {}
    private final Map<UUID, Run> activeRuns = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private record Platform(int x, int y, int z, Material material) {}

    private static final class Run {
        private final int startY;
        private final Material[] palette;
        private final List<Platform> platforms = new ArrayList<>(3);
        private int score;
        private int minimumY;
        private final Random random;

        private Run(int startY, Material[] palette, Random random) {
            this.startY = startY;
            this.minimumY = startY;
            this.palette = palette;
            this.random = random;
        }
    }

    public ParkourManager(PvPCore plugin) {
        this.plugin = plugin;
        this.entrancePlateKey = new NamespacedKey(plugin, "parkour_entrance_plate");
        this.leaderboardToolKey = new NamespacedKey(plugin, "parkour_leaderboard_tool");
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshLeaderboard, 20L, 100L);
        // Polling makes the entrance reliable even if another plugin cancels or
        // modifies movement/physical-interaction events for weighted pressure plates.
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickParkour, 1L, 2L);
    }

    public ItemStack createEntrancePlate() {
        ItemStack item = new ItemStack(Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.color("&6Parkour Entrance"));
        meta.setLore(List.of(
                plugin.color("&7Place this in the Spawn world."),
                plugin.color("&7Players step on it to enter their"),
                plugin.color("&7own private endless parkour course.")
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

    public void giveLeaderboardTool(Player player) {
        ItemStack item = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.color("&6Parkour Leaderboard"));
        meta.setLore(List.of(
                plugin.color("&7Place this gold block in Spawn."),
                plugin.color("&7The hologram appears above it."),
                plugin.color("&7Updates automatically with highscores."),
                plugin.color("&8Uses Text Display - no Armor Stands.")
        ));
        meta.getPersistentDataContainer().set(leaderboardToolKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        plugin.message(player, "&aParkour leaderboard tool added to your inventory.");
    }

    private boolean isLeaderboardTool(ItemStack item) {
        return item != null && item.getType() == Material.GOLD_BLOCK && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                .has(leaderboardToolKey, PersistentDataType.BYTE);
    }

    private void removeLeaderboardDisplayOnly(World world, Location location) {
        if (world == null || location == null) return;
        for (Entity entity : world.getNearbyEntities(location, 2.0D, 4.0D, 2.0D)) {
            if (entity instanceof TextDisplay display && display.getScoreboardTags().contains(LEADERBOARD_TAG)) {
                display.remove();
            }
        }
        leaderboardDisplay = null;
    }

    private void removeLeaderboardAtConfiguredLocation() {
        String worldName = plugin.getConfig().getString("parkour.leaderboard.world", "");
        World world = worldName.isBlank() ? null : Bukkit.getWorld(worldName);
        if (world != null && plugin.getConfig().contains("parkour.leaderboard.x")) {
            int x = plugin.getConfig().getInt("parkour.leaderboard.x");
            int y = plugin.getConfig().getInt("parkour.leaderboard.y");
            int z = plugin.getConfig().getInt("parkour.leaderboard.z");
            world.getChunkAt(x >> 4, z >> 4);
            Block oldBlock = world.getBlockAt(x, y, z);
            Location hologramLocation = oldBlock.getLocation().add(0.5D, 1.15D, 0.5D);
            removeLeaderboardDisplayOnly(world, hologramLocation);
            if (oldBlock.getType() == Material.GOLD_BLOCK) oldBlock.setType(Material.AIR, false);
        }
        plugin.getConfig().set("parkour.leaderboard", null);
        plugin.saveConfig();
        leaderboardDisplay = null;
    }

    public void refreshLeaderboard() {
        String worldName = plugin.getConfig().getString("parkour.leaderboard.world", "");
        if (!SPAWN_WORLD.equalsIgnoreCase(worldName)
                || !plugin.getConfig().contains("parkour.leaderboard.x")) return;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        int blockX = plugin.getConfig().getInt("parkour.leaderboard.x");
        int blockY = plugin.getConfig().getInt("parkour.leaderboard.y");
        int blockZ = plugin.getConfig().getInt("parkour.leaderboard.z");
        world.getChunkAt(blockX >> 4, blockZ >> 4);
        Block baseBlock = world.getBlockAt(blockX, blockY, blockZ);
        Location location = baseBlock.getLocation().add(0.5D, 1.15D, 0.5D);

        // The hologram is only active while its gold-block base still exists.
        if (baseBlock.getType() != Material.GOLD_BLOCK) {
            removeLeaderboardDisplayOnly(world, location);
            return;
        }

        if (leaderboardDisplay == null || !leaderboardDisplay.isValid()) {
            for (Entity entity : world.getNearbyEntities(location, 2.0D, 4.0D, 2.0D)) {
                if (entity instanceof TextDisplay display && display.getScoreboardTags().contains(LEADERBOARD_TAG)) {
                    leaderboardDisplay = display;
                    break;
                }
            }
        }
        if (leaderboardDisplay == null || !leaderboardDisplay.isValid()) {
            leaderboardDisplay = world.spawn(location, TextDisplay.class, display -> {
                display.addScoreboardTag(LEADERBOARD_TAG);
                display.setPersistent(true);
                display.setInvulnerable(true);
                display.setGravity(false);
                display.setSilent(true);
                display.setBillboard(Display.Billboard.CENTER);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setLineWidth(360);
                display.setSeeThrough(true);
                display.setShadowed(true);
                display.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            });
        }
        leaderboardDisplay.teleport(location);
        leaderboardDisplay.text(buildLeaderboardText());
    }

    private Component buildLeaderboardText() {
        List<Leader> leaders = new ArrayList<>();
        var playersSection = plugin.getConfig().getConfigurationSection("parkour.players");
        if (playersSection != null) {
            for (String uuidText : playersSection.getKeys(false)) {
                String path = "parkour.players." + uuidText;
                int score = plugin.getConfig().getInt(path + ".highscore", 0);
                if (score <= 0) continue;
                String name = plugin.getConfig().getString(path + ".name");
                if (name == null || name.isBlank()) {
                    try {
                        name = Bukkit.getOfflinePlayer(UUID.fromString(uuidText)).getName();
                    } catch (IllegalArgumentException ignored) {
                        name = null;
                    }
                }
                if (name == null || name.isBlank()) name = "unknown";
                leaders.add(new Leader(name, score));
            }
        }
        leaders.sort(Comparator.comparingInt(Leader::score).reversed()
                .thenComparing(leader -> leader.name().toLowerCase(Locale.ROOT)));

        Component text = Component.text(SmallCaps.of("parkour leaderboard"), NamedTextColor.YELLOW)
                .decorate(TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text("-----", NamedTextColor.DARK_GRAY));

        TextColor silver = TextColor.fromHexString("#C0C0C0");
        TextColor bronze = TextColor.fromHexString("#83543B");
        for (int index = 0; index < 10; index++) {
            Leader leader = index < leaders.size() ? leaders.get(index) : new Leader("---", 0);
            TextColor rankColor = switch (index) {
                case 0 -> NamedTextColor.GOLD;
                case 1 -> silver == null ? NamedTextColor.WHITE : silver;
                case 2 -> bronze == null ? NamedTextColor.WHITE : bronze;
                default -> NamedTextColor.WHITE;
            };
            Component row = Component.text("#" + (index + 1), rankColor)
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text(SmallCaps.of(leader.name()), NamedTextColor.YELLOW))
                    .append(Component.text(": ", NamedTextColor.YELLOW))
                    .append(Component.text(Integer.toString(leader.score()), NamedTextColor.DARK_RED));
            text = text.append(Component.newline()).append(row);
        }
        return text;
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
        Player player = event.getPlayer();

        // The special gold-block item is the leaderboard placement tool.
        if (isLeaderboardTool(event.getItemInHand())) {
            if (!plugin.hasCore(player, "Admin.Core")) {
                event.setCancelled(true);
                plugin.message(player, "&cOnly admins can place the leaderboard.");
                return;
            }
            if (!isSpawnWorld(event.getBlockPlaced().getWorld())) {
                event.setCancelled(true);
                plugin.message(player, "&cThe leaderboard must be placed in Spawn.");
                return;
            }

            removeLeaderboardAtConfiguredLocation();
            Block placed = event.getBlockPlaced();
            plugin.getConfig().set("parkour.leaderboard.world", SPAWN_WORLD);
            plugin.getConfig().set("parkour.leaderboard.x", placed.getX());
            plugin.getConfig().set("parkour.leaderboard.y", placed.getY());
            plugin.getConfig().set("parkour.leaderboard.z", placed.getZ());
            plugin.saveConfig();
            leaderboardDisplay = null;
            Bukkit.getScheduler().runTask(plugin, this::refreshLeaderboard);
            plugin.message(player, "&aParkour leaderboard placed above the gold block.");
            return;
        }

        if (!isSpawnWorld(event.getBlockPlaced().getWorld())) return;

        // Accept an admin-placed weighted gold pressure plate for the parkour entrance.
        boolean weightedPlate = event.getBlockPlaced().getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE;
        if (weightedPlate) {
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
            plugin.message(player, "&aParkour entrance set. Step on the gold plate to start.");
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnBlockBreak(BlockBreakEvent event) {
        if (!isSpawnWorld(event.getBlock().getWorld())) return;
        if (isConfiguredLeaderboardBlock(event.getBlock().getLocation())) {
            if (!plugin.hasCore(event.getPlayer(), "Admin.Core")) {
                event.setCancelled(true);
                return;
            }
            Location hologramLocation = event.getBlock().getLocation().add(0.5D, 1.15D, 0.5D);
            removeLeaderboardDisplayOnly(event.getBlock().getWorld(), hologramLocation);
            plugin.getConfig().set("parkour.leaderboard", null);
            plugin.saveConfig();
            plugin.message(event.getPlayer(), "&eParkour leaderboard removed.");
            return;
        }
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

    private boolean isConfiguredLeaderboardBlock(Location location) {
        return SPAWN_WORLD.equalsIgnoreCase(plugin.getConfig().getString("parkour.leaderboard.world", ""))
                && location.getBlockX() == plugin.getConfig().getInt("parkour.leaderboard.x", Integer.MIN_VALUE)
                && location.getBlockY() == plugin.getConfig().getInt("parkour.leaderboard.y", Integer.MIN_VALUE)
                && location.getBlockZ() == plugin.getConfig().getInt("parkour.leaderboard.z", Integer.MIN_VALUE);
    }

    private boolean isConfiguredPlate(Location location) {
        return SPAWN_WORLD.equalsIgnoreCase(plugin.getConfig().getString("spawn.parkour-plate.world", ""))
                && location.getBlockX() == plugin.getConfig().getInt("spawn.parkour-plate.x", Integer.MIN_VALUE)
                && location.getBlockY() == plugin.getConfig().getInt("spawn.parkour-plate.y", Integer.MIN_VALUE)
                && location.getBlockZ() == plugin.getConfig().getInt("spawn.parkour-plate.z", Integer.MIN_VALUE);
    }

    private void tickParkour() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Run run = activeRuns.get(player.getUniqueId());
            if (run != null) {
                tickActiveRun(player, run);
            } else if (isAtEntrance(player)) {
                startPersonalParkour(player);
            }
        }
    }

    private boolean isAtEntrance(Player player) {
        if (!isSpawnWorld(player.getWorld()) || !plugin.getConfig().contains("spawn.parkour-plate.x")) {
            return false;
        }
        int x = plugin.getConfig().getInt("spawn.parkour-plate.x", Integer.MIN_VALUE);
        int y = plugin.getConfig().getInt("spawn.parkour-plate.y", Integer.MIN_VALUE);
        int z = plugin.getConfig().getInt("spawn.parkour-plate.z", Integer.MIN_VALUE);
        Block plate = player.getWorld().getBlockAt(x, y, z);
        if (plate.getType() != Material.LIGHT_WEIGHTED_PRESSURE_PLATE) return false;

        Location feet = player.getLocation();
        if (feet.getBlockX() != x || feet.getBlockZ() != z || !player.isOnGround()) return false;
        int feetBlockY = feet.getBlockY();
        return feetBlockY == y || feetBlockY == y + 1;
    }

    private void tickActiveRun(Player player, Run run) {
        if (!isSpawnWorld(player.getWorld())) {
            finishRun(player, run, false, false);
            return;
        }
        if (player.getLocation().getY() < run.minimumY - 7.0D) {
            finishRun(player, run, true, true);
            return;
        }

        if (!player.isOnGround() || run.platforms.size() != 3) return;
        Platform second = run.platforms.get(1);
        Platform last = run.platforms.get(2);
        Location feet = player.getLocation();
        int supportY = (int) Math.floor(feet.getY() - 0.01D);
        if (feet.getBlockX() != second.x() || feet.getBlockZ() != second.z()
                || supportY != second.y()) return;

        // Reaching the middle (second) platform rolls the course forward:
        // remove the platform behind the player and add one beyond the old last.
        Platform oldest = run.platforms.remove(0);
        Block oldBlock = player.getWorld().getBlockAt(oldest.x(), oldest.y(), oldest.z());
        if (oldBlock.getType() == oldest.material()) oldBlock.setType(Material.AIR, false);

        Platform next = createNextPlatform(last, run);
        player.getWorld().getChunkAt(next.x() >> 4, next.z() >> 4);
        player.getWorld().getBlockAt(next.x(), next.y(), next.z()).setType(next.material(), false);
        run.platforms.add(next);
        run.score++;
        run.minimumY = Math.min(run.minimumY, next.y());

        String scorePath = "parkour.players." + player.getUniqueId() + ".highscore";
        int best = plugin.getConfig().getInt(scorePath, 0);
        if (run.score > best) {
            best = run.score;
            plugin.getConfig().set(scorePath, best);
            plugin.getConfig().set("parkour.players." + player.getUniqueId() + ".name", player.getName());
            plugin.saveConfig();
            player.sendMessage(plugin.color("&dNew Parkour highscore: &f" + best + " jumps!"));
        }
        player.sendActionBar(Component.text("Parkour  •  " + run.score + "  |  Highscore  •  " + best,
                NamedTextColor.LIGHT_PURPLE));
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
            plugin.saveConfig();
        }

        int baseX = 1024 + plot * PLOT_SPACING;
        int baseZ = 1024;
        Random runRandom = new Random(random.nextLong() ^ uuid.getMostSignificantBits() ^ System.nanoTime());
        Material[] palette = chooseRandomColourFamily(runRandom);
        Run run = new Run(COURSE_START_Y, palette, runRandom);

        Platform first = new Platform(baseX, COURSE_START_Y, baseZ, randomBlock(palette, runRandom));
        run.platforms.add(first);
        Platform second = createNextPlatform(first, run);
        Platform third = createNextPlatform(second, run);
        run.platforms.add(second);
        run.platforms.add(third);

        for (Platform platform : run.platforms) {
            spawn.getChunkAt(platform.x() >> 4, platform.z() >> 4);
            spawn.getBlockAt(platform.x(), platform.y(), platform.z()).setType(platform.material(), false);
        }

        // Clear any three leftover platforms from an interrupted previous run at this plot.
        activeRuns.put(uuid, run);
        Location start = new Location(spawn, baseX + 0.5D, COURSE_START_Y + 1.0D, baseZ + 0.5D);
        start.setYaw(-90.0F);
        start.setPitch(0.0F);
        player.teleport(start);
        int best = plugin.getConfig().getInt(path + ".highscore", 0);
        plugin.message(player, "&dParkour started! &7Colour: &f" + paletteName(palette)
                + " &8| &7Highscore: &f" + best);
    }

    private Material[] chooseRandomColourFamily(Random random) {
        String family = COLOUR_FAMILIES[random.nextInt(COLOUR_FAMILIES.length)];
        List<Material> palette = new ArrayList<>();
        for (String variant : BLOCK_VARIANTS) {
            Material material = Material.matchMaterial("minecraft:" + family.toLowerCase(java.util.Locale.ROOT) + variant.toLowerCase(java.util.Locale.ROOT));
            if (material != null && material.isBlock()) palette.add(material);
        }
        if (palette.isEmpty()) return new Material[]{Material.WHITE_CONCRETE, Material.WHITE_TERRACOTTA, Material.WHITE_WOOL};
        return palette.toArray(Material[]::new);
    }

    private String paletteName(Material[] palette) {
        String name = palette[0].name();
        int suffix = name.indexOf("_CONCRETE");
        if (suffix < 0) suffix = name.indexOf("_TERRACOTTA");
        if (suffix < 0) suffix = name.indexOf("_WOOL");
        if (suffix < 0) suffix = name.indexOf("_STAINED_GLASS");
        if (suffix < 0) suffix = name.indexOf("_GLAZED_TERRACOTTA");
        return suffix < 0 ? name : name.substring(0, suffix);
    }

    private Material randomBlock(Material[] palette, Random random) {
        return palette[random.nextInt(palette.length)];
    }

    private Platform createNextPlatform(Platform previous, Run run) {
        // Distances 2, 3 and 4 block coordinates correspond to 1, 2 and 3
        // empty blocks. The four-block jump is rare and always straight + level.
        int roll = run.random.nextInt(20);
        int distance = roll < 11 ? 2 : roll < 18 ? 3 : 4;
        int x = previous.x() + distance;
        int z = previous.z();
        int y = previous.y();

        if (distance == 4) {
            // No diagonal movement and no height change on the demanding jump.
        } else {
            z += run.random.nextInt(3) - 1;
            y += run.random.nextInt(3) - 1;
        }

        Material block = randomBlock(run.palette, run.random);
        return new Platform(x, y, z, block);
    }

    private void finishRun(Player player, Run run, boolean fell, boolean returnToSpawn) {
        if (!activeRuns.remove(player.getUniqueId(), run)) return;
        World spawn = Bukkit.getWorld(SPAWN_WORLD);
        if (spawn != null) {
            for (Platform platform : run.platforms) {
                Block block = spawn.getBlockAt(platform.x(), platform.y(), platform.z());
                if (block.getType() == platform.material()) block.setType(Material.AIR, false);
            }
        }
        if (returnToSpawn && player.isOnline()) {
            plugin.worlds().teleportToSpawn(player);
            if (fell) plugin.message(player, "&eYou fell! Back to spawn. &7Score: &f" + run.score);
        }
    }

    public boolean leaveToSpawn(Player player) {
        Run run = activeRuns.get(player.getUniqueId());
        if (run == null) return false;
        finishRun(player, run, false, true);
        plugin.message(player, "&aYou left your private parkour. &7Score: &f" + run.score);
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Run run = activeRuns.get(event.getPlayer().getUniqueId());
        if (run != null) finishRun(event.getPlayer(), run, false, false);
    }
}
