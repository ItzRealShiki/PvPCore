package de.leon.pvpcore;

import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Score;
import io.papermc.paper.scoreboard.numbers.NumberFormat;

import java.util.*;

public final class PracticeManager implements Listener {
    public enum Mode { STUN_SLAM, SWORD }

    private static final int ROOM_SPACING = 1024;
    private static final String PRACTICE_WORLD = "prac_void";

    private final PvPCore plugin;
    private World world;
    private final Map<UUID, Integer> scores = new HashMap<>();
    private final Map<UUID, Integer> selectedHeights = new HashMap<>();
    private final Map<UUID, Integer> swordSwings = new HashMap<>();
    private final Map<UUID, Integer> swordHits = new HashMap<>();
    private final Set<UUID> activeAttempts = new HashSet<>();
    private final Set<UUID> resetting = new HashSet<>();
    private final Set<Integer> activeForceFields = new HashSet<>();
    private final Map<Integer, Integer> forceFieldHeights = new HashMap<>();
    private final Map<Integer, org.bukkit.scheduler.BukkitTask> leafDecayTasks = new HashMap<>();
    private final Map<UUID, Scoreboard> practiceBoards = new HashMap<>();
    private final Map<UUID, Scoreboard> previousBoards = new HashMap<>();
    private boolean warnedSpearFallback;

    public PracticeManager(PvPCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateNearbyArenas, 1L, 10L);
    }

    private int roomCenterX(int index) { return 128 + index * ROOM_SPACING; }

    private World createVoidWorld() {
        ChunkGenerator voidGenerator = new ChunkGenerator() {
            @Override
            public void generateNoise(WorldInfo worldInfo, java.util.Random random, int chunkX, int chunkZ, ChunkData chunkData) {
                // Intentionally leave every generated chunk completely empty.
            }
        };
        return new WorldCreator(PRACTICE_WORLD).generator(voidGenerator).createWorld();
    }

    private void updatePlayerVisibility(Player player) {
        if (!player.getWorld().equals(world)) return;
        Player ownBot = plugin.bots().ownedBot(player);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            if (other.equals(ownBot)) player.showPlayer(plugin, other);
            else player.hidePlayer(plugin, other);
        }
    }

    private void restorePlayerVisibility(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) player.showPlayer(plugin, other);
        }
    }

    private void updateNearbyArenas() {
        Set<Integer> nearbyRooms = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(world)) continue;
            int index = plugin.getConfig().getInt("practice.players." + player.getUniqueId() + ".room", -1);
            if (index < 0) continue;
            int cx = roomCenterX(index), cz = 128;
            boolean nearby = player.getLocation().distanceSquared(new Location(world == null ? player.getWorld() : world, cx + 0.5, player.getLocation().getY(), cz + 0.5)) <= 36.0 * 36.0;
            if (!nearby) {
                plugin.bots().removeOwned(player);
                continue;
            }
            nearbyRooms.add(index);
            int glassHeight = (mode(player) == Mode.SWORD ? 30 : selectedHeight(player)) + 10;
            Integer previousGlassHeight = forceFieldHeights.get(index);
            if (!activeForceFields.contains(index)) {
                buildForceField(cx, cz, glassHeight);
                activeForceFields.add(index);
                forceFieldHeights.put(index, glassHeight);
            } else if (previousGlassHeight == null || previousGlassHeight != glassHeight) {
                removeForceField(cx, cz, previousGlassHeight == null ? glassHeight : previousGlassHeight);
                buildForceField(cx, cz, glassHeight);
                forceFieldHeights.put(index, glassHeight);
            }
            if (plugin.bots().isAvailable() && !plugin.bots().hasOwnedBot(player)) plugin.bots().spawn(player);
            if (mode(player) == Mode.SWORD) recoverSwordBotAtEdge(player, cx, cz);
            updatePracticeBoard(player);
            updatePlayerVisibility(player);
        }
        for (int index : new HashSet<>(activeForceFields)) {
            if (nearbyRooms.contains(index)) continue;
            removeForceField(roomCenterX(index), 128, forceFieldHeights.getOrDefault(index, 40));
            activeForceFields.remove(index);
            forceFieldHeights.remove(index);
        }
    }

    public Mode mode(Player p) {
        try { return Mode.valueOf(plugin.getConfig().getString("practice.players." + p.getUniqueId() + ".mode", "STUN_SLAM")); }
        catch (IllegalArgumentException ex) { return Mode.STUN_SLAM; }
    }

    public void setMode(Player p, Mode mode) {
        activeAttempts.remove(p.getUniqueId());
        resetting.remove(p.getUniqueId());
        plugin.getConfig().set("practice.players." + p.getUniqueId() + ".mode", mode.name());
        plugin.saveConfig();
    }

    public int selectedHeight(Player p) {
        return selectedHeights.getOrDefault(p.getUniqueId(),
                plugin.getConfig().getInt("practice.players." + p.getUniqueId() + ".stun-slam-height", 10));
    }

    public void setSelectedHeight(Player p, int height) {
        if (height != 10 && height != 20 && height != 30 && height != 100) return;
        selectedHeights.put(p.getUniqueId(), height);
        plugin.getConfig().set("practice.players." + p.getUniqueId() + ".stun-slam-height", height);
        plugin.saveConfig();
    }

    public int score(Player p) { return scores.getOrDefault(p.getUniqueId(), 0); }

    private String highScorePath(Player player, Mode mode) {
        return "practice.highscores." + player.getUniqueId() + "." + (mode == Mode.SWORD ? "sword-accuracy" : "stun-slam");
    }

    private int highScore(Player player, Mode mode) {
        return plugin.getConfig().getInt(highScorePath(player, mode), 0);
    }

    private void updateHighScore(Player player, Mode mode, int value) {
        String path = highScorePath(player, mode);
        if (value <= plugin.getConfig().getInt(path, 0)) return;
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
    }

    private String scoreboardPath(Player player, String option) {
        return "practice.scoreboard." + player.getUniqueId() + "." + option;
    }

    public boolean scoreboardOption(Player player, String option) {
        return plugin.getConfig().getBoolean(scoreboardPath(player, option), true);
    }

    public void toggleScoreboardOption(Player player, String option) {
        String path = scoreboardPath(player, option);
        plugin.getConfig().set(path, !scoreboardOption(player, option));
        plugin.saveConfig();
        refreshScoreboard(player);
    }

    public void refreshScoreboard(Player player) {
        if (world != null && player.getWorld().equals(world)) updatePracticeBoard(player);
    }

    private void updatePracticeBoard(Player player) {
        UUID id = player.getUniqueId();
        if (!scoreboardOption(player, "enabled")) {
            restorePracticeBoard(player);
            return;
        }
        Scoreboard board = practiceBoards.get(id);
        if (board == null || player.getScoreboard() != board) {
            if (board == null) previousBoards.put(id, player.getScoreboard());
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            practiceBoards.put(id, board);
            player.setScoreboard(board);
        }
        Objective oldObjective = board.getObjective("practice");
        if (oldObjective != null) oldObjective.unregister();
        Objective objective = board.registerNewObjective("practice", Criteria.DUMMY,
                net.kyori.adventure.text.Component.text("✦ ᴘᴠᴘᴄᴏʀᴇ"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());

        int swings = swordSwings.getOrDefault(id, 0);
        int hits = swordHits.getOrDefault(id, 0);
        int accuracy = swings == 0 ? 0 : Math.min(100, Math.round(hits * 100.0f / swings));
        List<String> lines = new ArrayList<>();
        lines.add("&8&m----------------");
        if (scoreboardOption(player, "mode")) {
            lines.add("&7Mode: " + (mode(player) == Mode.SWORD ? "&bSword" : "&dStun Slam"));
        }
        if (scoreboardOption(player, "sword")) {
            lines.add("&b&lSWORD");
            lines.add("&7Accuracy: &a" + accuracy + "%");
            if (scoreboardOption(player, "personal-bests")) lines.add("&7Personal Best: &e" + highScore(player, Mode.SWORD) + "%");
        }
        if (scoreboardOption(player, "stun-slam")) {
            lines.add("&d&lSTUN SLAM");
            lines.add("&7Current Score: &f" + score(player));
            if (scoreboardOption(player, "personal-bests")) lines.add("&7Personal Best: &e" + highScore(player, Mode.STUN_SLAM));
        }
        if (scoreboardOption(player, "footer")) {
            lines.add("&8&m----------------");
            lines.add("&7Keep improving!");
        }
        if (lines.size() == 1) lines.add("&7Customize with &f/scoreboard");
        for (int i = 0; i < lines.size(); i++) {
            String entry = org.bukkit.ChatColor.COLOR_CHAR + Integer.toHexString(i) + org.bukkit.ChatColor.COLOR_CHAR + "r";
            Score lineScore = objective.getScore(entry);
            lineScore.setScore(lines.size() - i);
            String rendered = org.bukkit.ChatColor.translateAlternateColorCodes('&', lines.get(i));
            lineScore.customName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(rendered));
        }
    }
    private void restorePracticeBoard(Player player) {
        UUID id = player.getUniqueId();
        Scoreboard current = practiceBoards.remove(id);
        Scoreboard previous = previousBoards.remove(id);
        if (current != null && player.getScoreboard() == current) {
            player.setScoreboard(previous != null ? previous : Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private void recoverSwordBotAtEdge(Player owner, int centerX, int centerZ) {
        Player bot = plugin.bots().ownedBot(owner);
        if (bot == null || !bot.isValid() || bot.getWorld() != owner.getWorld()) return;
        Location location = bot.getLocation();
        double edgeX = Math.abs(location.getX() - centerX);
        double edgeZ = Math.abs(location.getZ() - centerZ);
        org.bukkit.util.Vector velocity = bot.getVelocity();
        if ((edgeX < 22.0 && edgeZ < 22.0)
                || Math.hypot(velocity.getX(), velocity.getZ()) > 0.10) return;
        org.bukkit.util.Vector towardCenter = new org.bukkit.util.Vector(
                centerX + 0.5 - location.getX(), 0.0, centerZ + 0.5 - location.getZ());
        if (towardCenter.lengthSquared() < 0.01) return;
        towardCenter.normalize().multiply(1.35).setY(0.42);
        bot.setVelocity(towardCenter);
    }

    public void enforcePracticeFood() {
        // Keep hunger disabled globally, including worlds outside the practice arenas.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getFoodLevel() != 20) player.setFoodLevel(20);
            if (player.getSaturation() < 20.0f) player.setSaturation(20.0f);
            if (player.getExhaustion() != 0.0f) player.setExhaustion(0.0f);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        // Cancel every hunger change so sprinting, jumping and time cannot drain food.
        event.setCancelled(true);
        if (player.getFoodLevel() != 20) player.setFoodLevel(20);
        if (player.getSaturation() < 20.0f) player.setSaturation(20.0f);
        if (player.getExhaustion() != 0.0f) player.setExhaustion(0.0f);
    }

    public void enter(Player p) { enter(p, selectedHeight(p)); }

    public void enter(Player p, int height) {
        int challengeHeight = mode(p) == Mode.STUN_SLAM ? height : 0;
        if (challengeHeight > 0) setSelectedHeight(p, challengeHeight);
        if (world == null) {
            world = Bukkit.getWorld(PRACTICE_WORLD);
            if (world == null) world = createVoidWorld();
        }
        if (world == null) { plugin.message(p, "&cPractice world could not be loaded."); return; }

        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime(1000L);

        String base = "practice.players." + p.getUniqueId();
        int index;
        if (plugin.getConfig().contains(base + ".room")) index = plugin.getConfig().getInt(base + ".room");
        else {
            index = plugin.getConfig().getInt("practice.next-room", 0);
            plugin.getConfig().set("practice.next-room", index + 1);
            plugin.getConfig().set(base + ".room", index);
            plugin.saveConfig();
        }

        int cx = roomCenterX(index), cz = 128;
        if (!plugin.getConfig().getBoolean(base + ".built", false)) {
            buildRoom(cx, cz);
            plugin.getConfig().set(base + ".built", true);
            plugin.saveConfig();
        }
        clearPlatform(cx, cz);
        plugin.bots().removeOwned(p);
        p.setGameMode(GameMode.SURVIVAL);
        p.setFallDistance(0);
        p.teleport(startLocation(p, challengeHeight));
        Bukkit.getScheduler().runTask(plugin, () -> updatePlayerVisibility(p));
        applyKit(p);
        scores.putIfAbsent(p.getUniqueId(), 0);
        if (mode(p) == Mode.SWORD) {
            swordSwings.put(p.getUniqueId(), 0);
            swordHits.put(p.getUniqueId(), 0);
            updateSwordAccuracy(p);
        }
        if (challengeHeight > 0) activeAttempts.add(p.getUniqueId());

        if (challengeHeight > 0) {
            plugin.message(p, "&aStun Slam &7- &e" + difficultyName(challengeHeight) + " &7(" + challengeHeight + " blocks).");
            plugin.message(p, "&7Score: &a" + score(p) + " &8| &7Land a hit on the bot to earn a point.");
        } else plugin.message(p, "&aEntered your private Sword practice room.");
    }

    private String difficultyName(int height) {
        return switch (height) { case 10 -> "Easy"; case 20 -> "Normal"; case 30 -> "Hard"; default -> "Impossible"; };
    }

    private Location startLocation(Player p, int height) {
        int index = plugin.getConfig().getInt("practice.players." + p.getUniqueId() + ".room", 0);
        int cx = roomCenterX(index), cz = 128;
        // Sword practice starts directly on the arena floor; only Stun Slam uses an elevated spawn.
        int spawnHeight = height > 0 ? height : 0;
        return new Location(world, cx + 0.5, 81 + spawnHeight, cz + 0.5, 0.0f, height > 0 ? 90.0f : 0.0f);
    }

    public Location botSpawnLocation(Player owner) {
        int index = plugin.getConfig().getInt("practice.players." + owner.getUniqueId() + ".room", 0);
        int cx = roomCenterX(index), cz = 128;
        return new Location(world, cx + 0.5, 81, cz + 0.5);
    }

    private void buildRoom(int cx, int cz) {
        buildArenaPlatform(cx, cz);
    }

    private void clearPlatform(int cx, int cz) {
        // Restore the selected arena theme while keeping the surrounding glass box intact.
        buildArenaPlatform(cx, cz);
    }

    public String arenaTheme() {
        return plugin.getConfig().getString("practice.arena-theme", "PLAINS").toUpperCase(Locale.ROOT);
    }

    public void setArenaTheme(String theme) {
        String normalized = theme.toUpperCase(Locale.ROOT);
        if (!List.of("DESERT", "PLAINS", "END", "NETHER", "BADLANDS", "PALE_GARDEN",
                "CHERRY_BLOSSOM", "WARPED", "CRIMSON", "FLOWER_PLAINS").contains(normalized)) return;
        plugin.getConfig().set("practice.arena-theme", normalized);
        plugin.saveConfig();

        // Update every allocated private room immediately, including rooms whose owners are offline.
        if (world == null) world = Bukkit.getWorld(PRACTICE_WORLD);
        if (world != null) {
            int rooms = plugin.getConfig().getInt("practice.next-room", 0);
            for (int index = 0; index < rooms; index++) {
                buildArenaPlatform(roomCenterX(index), 128);
            }
        }
    }

    private Material[] arenaPalette() {
        return switch (arenaTheme()) {
            case "DESERT" -> new Material[]{Material.SAND, Material.SANDSTONE, Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE, Material.DEAD_BUSH};
            case "END" -> new Material[]{Material.END_STONE, Material.END_STONE, Material.PURPUR_BLOCK, Material.END_STONE_BRICKS, Material.CHORUS_FLOWER};
            case "NETHER" -> new Material[]{Material.NETHERRACK, Material.NETHERRACK, Material.BASALT, Material.BLACKSTONE, Material.FIRE};
            case "BADLANDS" -> new Material[]{Material.RED_SAND, Material.RED_SANDSTONE, Material.TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.DEAD_BUSH};
            case "PALE_GARDEN" -> new Material[]{Material.PALE_MOSS_BLOCK, Material.PALE_MOSS_BLOCK, Material.PALE_OAK_PLANKS, Material.PALE_OAK_LOG, Material.PALE_HANGING_MOSS};
            case "CHERRY_BLOSSOM" -> new Material[]{Material.GRASS_BLOCK, Material.DIRT, Material.CHERRY_LOG, Material.CHERRY_PLANKS, Material.PINK_PETALS};
            case "WARPED" -> new Material[]{Material.WARPED_NYLIUM, Material.NETHERRACK, Material.WARPED_STEM, Material.WARPED_WART_BLOCK, Material.WARPED_FUNGUS};
            case "CRIMSON" -> new Material[]{Material.CRIMSON_NYLIUM, Material.NETHERRACK, Material.CRIMSON_STEM, Material.NETHER_WART_BLOCK, Material.CRIMSON_FUNGUS};
            case "FLOWER_PLAINS" -> new Material[]{Material.GRASS_BLOCK, Material.DIRT, Material.ROOTED_DIRT, Material.MOSS_BLOCK, Material.PINK_PETALS};
            default -> new Material[]{Material.GRASS_BLOCK, Material.DIRT, Material.DIRT, Material.STONE, Material.SHORT_GRASS};
        };
    }

    private void buildArenaPlatform(int cx, int cz) {
        if (world == null) return;
        Material[] palette = arenaPalette();
        int roomIndex = Math.floorDiv(cx - 128, ROOM_SPACING);
        List<Block> timedLeaves = new ArrayList<>();

        // Cancel the previous timer when a room is rebuilt or its theme changes.
        org.bukkit.scheduler.BukkitTask oldDecay = leafDecayTasks.remove(roomIndex);
        if (oldDecay != null) oldDecay.cancel();

        for (int x = -24; x <= 24; x++) {
            for (int z = -24; z <= 24; z++) {
                int edge = Math.max(Math.abs(x), Math.abs(z));
                double radius = Math.hypot(x, z);

                // Keep the central PvP zone level. Small, clustered height changes
                // are restricted to the outer ring so movement and combos stay fair.
                double terrainNoise = Math.sin(x * 0.37) + Math.cos(z * 0.33)
                        + Math.sin((x + z) * 0.21) * 0.8;
                int height = 0;
                if (radius >= 13.0 && radius <= 21.5) {
                    if (terrainNoise > 2.05) height = 2;
                    else if (terrainNoise > 1.15) height = 1;
                }

                // Never touch the outermost ring: it contains the forcefield wall.
                // Clearing y=81..85 there would remove the bottom glass rows.
                if (edge == 24) continue;

                // Clear old decoration and raised terrain before rebuilding the tile.
                for (int y = 81; y <= 85; y++) {
                    world.getBlockAt(cx + x, y, cz + z).setType(Material.AIR, false);
                }

                int topY = 80 + height;
                world.getBlockAt(cx + x, topY, cz + z).setType(palette[0], false);
                world.getBlockAt(cx + x, topY - 1, cz + z).setType(palette[1], false);
                world.getBlockAt(cx + x, topY - 2, cz + z).setType(palette[2], false);
                world.getBlockAt(cx + x, topY - 3, cz + z).setType(palette[3], false);

                // Add varied ground cover from the mid-ring outward. The central
                // combat zone stays clear, while plants are scattered in small clusters.
                if (radius >= 8.0 && radius <= 22.0) {
                    int plantRoll = Math.floorMod(x * 73 + z * 151 + x * z * 19, 17);
                    Material groundDetail = null;
                    switch (arenaTheme()) {
                        case "CHERRY_BLOSSOM" -> {
                            if (plantRoll <= 3) groundDetail = Material.PINK_PETALS;
                            else if (plantRoll == 4) groundDetail = Material.SHORT_GRASS;
                            else if (plantRoll == 5) groundDetail = Material.FERN;
                            else if (plantRoll == 6) groundDetail = Material.MOSS_CARPET;
                        }
                        case "FLOWER_PLAINS" -> {
                            if (plantRoll <= 2) groundDetail = Material.POPPY;
                            else if (plantRoll <= 4) groundDetail = Material.DANDELION;
                            else if (plantRoll == 5) groundDetail = Material.AZURE_BLUET;
                            else if (plantRoll == 6) groundDetail = Material.OXEYE_DAISY;
                            else if (plantRoll == 7) groundDetail = Material.CORNFLOWER;
                            else if (plantRoll == 8) groundDetail = Material.SHORT_GRASS;
                            else if (plantRoll == 9) groundDetail = Material.FERN;
                            else if (plantRoll == 10) groundDetail = Material.MOSS_CARPET;
                        }
                        case "PLAINS" -> {
                            if (plantRoll <= 4) groundDetail = Material.SHORT_GRASS;
                            else if (plantRoll <= 6) groundDetail = Material.FERN;
                            else if (plantRoll == 7) groundDetail = Material.POPPY;
                            else if (plantRoll == 8) groundDetail = Material.DANDELION;
                            else if (plantRoll == 9) groundDetail = Material.MOSS_CARPET;
                        }
                        case "PALE_GARDEN" -> {
                            if (plantRoll <= 3) groundDetail = Material.PALE_MOSS_CARPET;
                            else if (plantRoll <= 5) groundDetail = Material.PALE_HANGING_MOSS;
                            else if (plantRoll == 6) groundDetail = Material.FERN;
                            else if (plantRoll == 7) groundDetail = Material.SHORT_GRASS;
                        }
                        case "WARPED" -> {
                            if (plantRoll <= 2) groundDetail = Material.WARPED_ROOTS;
                            else if (plantRoll <= 4) groundDetail = Material.NETHER_SPROUTS;
                            else if (plantRoll == 5) groundDetail = Material.WARPED_FUNGUS;
                        }
                        case "CRIMSON" -> {
                            if (plantRoll <= 2) groundDetail = Material.CRIMSON_ROOTS;
                            else if (plantRoll <= 4) groundDetail = Material.NETHER_SPROUTS;
                            else if (plantRoll == 5) groundDetail = Material.CRIMSON_FUNGUS;
                        }
                        case "NETHER" -> {
                            if (plantRoll <= 2) groundDetail = Material.NETHER_SPROUTS;
                            else if (plantRoll == 3) groundDetail = Material.CRIMSON_ROOTS;
                            else if (plantRoll == 4) groundDetail = Material.WARPED_ROOTS;
                        }
                        case "END" -> {
                            if (plantRoll <= 2) groundDetail = Material.CHORUS_FLOWER;
                        }
                        case "DESERT", "BADLANDS" -> {
                            if (plantRoll == 0) groundDetail = Material.DEAD_BUSH;
                        }
                    }
                    if (groundDetail != null) {
                        world.getBlockAt(cx + x, topY + 1, cz + z).setType(groundDetail, false);
                    }

                    // A few subtle ground patches add texture between the plants.
                    if ((arenaTheme().equals("PLAINS") || arenaTheme().equals("FLOWER_PLAINS")
                            || arenaTheme().equals("CHERRY_BLOSSOM"))
                            && Math.floorMod(x * 43 + z * 67, 31) == 0) {
                        world.getBlockAt(cx + x, topY + 1, cz + z).setType(Material.MOSS_CARPET, false);
                    }
                }

                // Compact ornamental cherry trees with a tapered, rounded canopy.
                // They stay near the perimeter and their leaves are temporary accents.
                if (arenaTheme().equals("CHERRY_BLOSSOM")
                        && edge >= 18 && edge <= 21
                        && Math.floorMod(x * 47 + z * 31 + x * z, 173) == 0) {
                    int trunkBase = topY + 1;
                    for (int trunkY = 0; trunkY < 3; trunkY++) {
                        world.getBlockAt(cx + x, trunkBase + trunkY, cz + z)
                                .setType(Material.CHERRY_LOG, false);
                    }

                    // Broad lower canopy with clipped corners.
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                            addTimedCherryLeaves(cx + x + dx, trunkBase + 3, cz + z + dz, timedLeaves);
                        }
                    }
                    // Smaller upper canopy creates a soft, rounded crown.
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            addTimedCherryLeaves(cx + x + dx, trunkBase + 4, cz + z + dz, timedLeaves);
                        }
                    }
                    addTimedCherryLeaves(cx + x, trunkBase + 5, cz + z, timedLeaves);
                }
            }
        }

        // Decorative frame inside the glass enclosure, using the selected theme's accent block.
        Material trim = palette[3];
        for (int offset = -23; offset <= 23; offset++) {
            world.getBlockAt(cx - 23, 80, cz + offset).setType(trim, false);
            world.getBlockAt(cx + 23, 80, cz + offset).setType(trim, false);
            world.getBlockAt(cx + offset, 80, cz - 23).setType(trim, false);
            world.getBlockAt(cx + offset, 80, cz + 23).setType(trim, false);
        }

        if (!timedLeaves.isEmpty()) {
            leafDecayTasks.put(roomIndex, Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Block leaf : timedLeaves) {
                    if (leaf.getType() == Material.CHERRY_LEAVES) leaf.setType(Material.AIR, false);
                }
                leafDecayTasks.remove(roomIndex);
            }, 1200L));
        }
    }

    private void addTimedCherryLeaves(int x, int y, int z, List<Block> timedLeaves) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.CHERRY_LEAVES, false);
        Leaves leaves = (Leaves) block.getBlockData();
        leaves.setPersistent(true);
        block.setBlockData(leaves, false);
        timedLeaves.add(block);
    }

    private void buildForceField(int cx, int cz, int glassHeight) {
        if (world == null) return;
        int roofY = 80 + glassHeight;
        for (int offset = -24; offset <= 24; offset++) {
            for (int y = 81; y <= roofY; y++) {
                world.getBlockAt(cx - 24, y, cz + offset).setType(forceFieldMaterial(), false);
                world.getBlockAt(cx + 24, y, cz + offset).setType(forceFieldMaterial(), false);
                world.getBlockAt(cx + offset, y, cz - 24).setType(forceFieldMaterial(), false);
                world.getBlockAt(cx + offset, y, cz + 24).setType(forceFieldMaterial(), false);
            }
        }

        // Close the arena with a complete glass roof at the calculated mode-specific height.
        for (int x = -24; x <= 24; x++) {
            for (int z = -24; z <= 24; z++) {
                world.getBlockAt(cx + x, roofY, cz + z).setType(forceFieldMaterial(), false);
            }
        }
    }

    public Material forceFieldMaterial() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("practice.forcefield.material", "GLASS"));
        return material != null && (material == Material.GLASS || material.name().endsWith("_STAINED_GLASS"))
                ? material : Material.GLASS;
    }

    public void setForceFieldMaterial(Material material) {
        if (material != Material.GLASS && !material.name().endsWith("_STAINED_GLASS")) return;
        plugin.getConfig().set("practice.forcefield.material", material.name());
        plugin.saveConfig();
        for (int index : activeForceFields) {
            int cx = roomCenterX(index), cz = 128;
            buildForceField(cx, cz, forceFieldHeights.getOrDefault(index, 40));
        }
    }

    private void removeForceField(int cx, int cz, int glassHeight) {
        if (world == null) return;
        int roofY = 80 + glassHeight;
        for (int offset = -24; offset <= 24; offset++) {
            for (int y = 81; y <= roofY; y++) {
                world.getBlockAt(cx - 24, y, cz + offset).setType(Material.AIR, false);
                world.getBlockAt(cx + 24, y, cz + offset).setType(Material.AIR, false);
                world.getBlockAt(cx + offset, y, cz - 24).setType(Material.AIR, false);
                world.getBlockAt(cx + offset, y, cz + 24).setType(Material.AIR, false);
            }
        }
        for (int x = -24; x <= 24; x++) {
            for (int z = -24; z <= 24; z++) {
                world.getBlockAt(cx + x, roofY, cz + z).setType(Material.AIR, false);
            }
        }
    }

    private void retry(Player p, boolean success) {
        UUID id = p.getUniqueId();
        if (!resetting.add(id)) return;
        activeAttempts.remove(id);
        if (success) {
            scores.merge(id, 1, Integer::sum);
            updateHighScore(p, Mode.STUN_SLAM, score(p));
            plugin.message(p, "&a+1 point! &7Score: &e" + score(p));
        } else {
            plugin.message(p, "&cMissed! &7Try again. Score: &e" + score(p));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            resetting.remove(id);
            if (!p.isOnline() || p.getWorld() != world) return;
            p.setFallDistance(0);
            p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            p.teleport(startLocation(p, selectedHeight(p)));
            activeAttempts.add(id);
        }, 10L);
    }

    @EventHandler
    public void onSwordSwing(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING
                || !player.getWorld().equals(world)
                || mode(player) != Mode.SWORD) return;
        swordSwings.merge(player.getUniqueId(), 1, Integer::sum);
        updateSwordAccuracy(player);
    }

    @EventHandler
    public void onBotHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!player.getWorld().equals(world)) return;
        if (!plugin.bots().isOwnedBot(player, event.getEntity())) return;
        // Shield-blocked mace swings trigger damage events too, but must never award a point.
        if (event.isCancelled() || event.getFinalDamage() <= 0.0) return;

        if (mode(player) == Mode.SWORD) {
            swordHits.merge(player.getUniqueId(), 1, Integer::sum);
            updateSwordAccuracy(player);
            return;
        }

        if (!activeAttempts.contains(player.getUniqueId())) return;
        // Stun Slam only scores a real mace hit; other weapons still deal normal damage.
        if (player.getInventory().getItemInMainHand().getType() != Material.MACE) return;
        retry(player, true);
    }

    private void updateSwordAccuracy(Player player) {
        UUID id = player.getUniqueId();
        int swings = swordSwings.getOrDefault(id, 0);
        int hits = swordHits.getOrDefault(id, 0);
        int accuracy = swings == 0 ? 0 : Math.min(100, Math.round(hits * 100.0f / swings));
        updateHighScore(player, Mode.SWORD, accuracy);
        player.sendActionBar(ChatColor.GREEN + "Accuracy: " + ChatColor.YELLOW + accuracy + "%"
                + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Hits: "
                + ChatColor.GREEN + hits + ChatColor.GRAY + "/" + swings);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!p.getWorld().equals(world) || event.getTo() == null) return;

        int roomIndex = plugin.getConfig().getInt("practice.players." + p.getUniqueId() + ".room", -1);
        if (roomIndex >= 0) {
            int cx = roomCenterX(roomIndex);
            int cz = 128;
            Location to = event.getTo();
            // Only teleport after the player's position has passed beyond the outer edge
            // of the glass wall (at +/-24), not while approaching or touching its inner face.
            if (to.getX() < cx - 24.5 || to.getX() > cx + 24.5
                    || to.getZ() < cz - 24.5 || to.getZ() > cz + 24.5) {
                int height = mode(p) == Mode.STUN_SLAM && activeAttempts.contains(p.getUniqueId())
                        ? selectedHeight(p) : 0;
                p.setFallDistance(0);
                p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                p.teleport(startLocation(p, height));
                return;
            }
        }

        if (!activeAttempts.contains(p.getUniqueId())) return;
        if (event.getTo().getY() >= event.getFrom().getY()) return;
        if (event.getTo().getY() <= 81.0) retry(p, false);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p) || !p.getWorld().equals(world)) return;
        // Fake-player bots must remain damageable; only the human practice player is protected.
        if (plugin.bots().isPracticeBot(p)) return;
        event.setCancelled(true);
    }

    @EventHandler public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getWorld().equals(world)) event.setCancelled(true);
    }

    @EventHandler public void onPlace(BlockPlaceEvent event) {
        if (event.getBlock().getWorld().equals(world)) event.setCancelled(true);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        restorePracticeBoard(event.getPlayer());
        restorePlayerVisibility(event.getPlayer());
        scores.remove(id); selectedHeights.remove(id); swordSwings.remove(id); swordHits.remove(id);
        activeAttempts.remove(id); resetting.remove(id);
    }

    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        activeAttempts.remove(id); resetting.remove(id);
        if (!event.getPlayer().getWorld().equals(world)) {
            restorePracticeBoard(event.getPlayer());
            restorePlayerVisibility(event.getPlayer());
        } else Bukkit.getScheduler().runTask(plugin, () -> updatePlayerVisibility(event.getPlayer()));
    }

    public void applyKit(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[]{gear(Material.DIAMOND_BOOTS),gear(Material.DIAMOND_LEGGINGS),gear(Material.DIAMOND_CHESTPLATE),gear(Material.DIAMOND_HELMET)});
        if (mode(p) == Mode.SWORD) p.getInventory().setItem(0, gear(Material.DIAMOND_SWORD));
        else {
            p.getInventory().setItem(0, enchanted(Material.NETHERITE_AXE, "sharpness", 5));
            ItemStack mace = enchanted(Material.MACE, "density", 5); enchant(mace, "wind_burst", 3);
            p.getInventory().setItem(1, mace);
            p.getInventory().setItem(2, enchanted(spearMaterial(), "lunge", 3));
        }
        loadSavedKit(p, p.getInventory());
        p.getInventory().setHeldItemSlot(0);
        p.setHealth(20); p.setFoodLevel(20); p.setSaturation(20); p.setFireTicks(0);
    }

    public void applyBotKit(Player bot, Player owner) {
        bot.getInventory().clear();
        if (mode(owner) == Mode.SWORD) {
            bot.getInventory().setArmorContents(new ItemStack[]{gear(Material.DIAMOND_BOOTS), gear(Material.DIAMOND_LEGGINGS), gear(Material.DIAMOND_CHESTPLATE), gear(Material.DIAMOND_HELMET)});
            bot.getInventory().setItemInMainHand(gear(Material.DIAMOND_SWORD));
            return;
        }
        bot.getInventory().setArmorContents(new ItemStack[]{
                botArmor(Material.NETHERITE_BOOTS), botArmor(Material.NETHERITE_LEGGINGS),
                botArmor(Material.NETHERITE_CHESTPLATE), botArmor(Material.NETHERITE_HELMET)});
        bot.getInventory().setItemInMainHand(gear(Material.SHIELD));
        bot.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
        bot.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
    }

    private ItemStack botArmor(Material type) {
        ItemStack item = gear(type);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.PROTECTION, 4, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        item.setItemMeta(meta);
        return item;
    }

    public void applyKitToInventory(Player player, Inventory editor) {
        editor.clear();
        if (mode(player) == Mode.SWORD) editor.setItem(0, gear(Material.DIAMOND_SWORD));
        else {
            editor.setItem(0, enchanted(Material.NETHERITE_AXE, "sharpness", 5));
            ItemStack mace = enchanted(Material.MACE, "density", 5); enchant(mace, "wind_burst", 3);
            editor.setItem(1, mace);
            editor.setItem(2, enchanted(spearMaterial(), "lunge", 3));
        }
        editor.setItem(45, gear(Material.DIAMOND_HELMET));
        editor.setItem(46, gear(Material.DIAMOND_CHESTPLATE));
        editor.setItem(47, gear(Material.DIAMOND_LEGGINGS));
        editor.setItem(48, gear(Material.DIAMOND_BOOTS));
        String path = "practice.players." + player.getUniqueId() + ".kits." + mode(player).name();
        if (!plugin.getConfig().contains(path + ".contents")) return;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = plugin.getConfig().getItemStack(path + ".contents." + slot);
            editor.setItem(slot, item == null ? null : item.clone());
        }
        editor.setItem(45, plugin.getConfig().getItemStack(path + ".helmet"));
        editor.setItem(46, plugin.getConfig().getItemStack(path + ".chestplate"));
        editor.setItem(47, plugin.getConfig().getItemStack(path + ".leggings"));
        editor.setItem(48, plugin.getConfig().getItemStack(path + ".boots"));
    }

    public void saveKit(Player player, Inventory editor) {
        String path = "practice.players." + player.getUniqueId() + ".kits." + mode(player).name();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = editor.getItem(slot);
            plugin.getConfig().set(path + ".contents." + slot, item == null || item.getType().isAir() ? null : item.clone());
        }
        plugin.getConfig().set(path + ".helmet", editor.getItem(45));
        plugin.getConfig().set(path + ".chestplate", editor.getItem(46));
        plugin.getConfig().set(path + ".leggings", editor.getItem(47));
        plugin.getConfig().set(path + ".boots", editor.getItem(48));
        plugin.saveConfig();
    }

    private void loadSavedKit(Player player, PlayerInventory inventory) {
        String path = "practice.players." + player.getUniqueId() + ".kits." + mode(player).name();
        if (!plugin.getConfig().contains(path + ".contents")) return;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = plugin.getConfig().getItemStack(path + ".contents." + slot);
            inventory.setItem(slot, item == null ? null : item.clone());
        }
        inventory.setHelmet(plugin.getConfig().getItemStack(path + ".helmet"));
        inventory.setChestplate(plugin.getConfig().getItemStack(path + ".chestplate"));
        inventory.setLeggings(plugin.getConfig().getItemStack(path + ".leggings"));
        inventory.setBoots(plugin.getConfig().getItemStack(path + ".boots"));
    }

    private void setSingleSpear(Inventory inventory) {
        Material spear = spearMaterial();
        for (int slot = 0; slot < Math.min(36, inventory.getSize()); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() == spear) inventory.setItem(slot, null);
        }
        inventory.setItem(2, enchanted(spear, "lunge", 3));
    }

    private Material spearMaterial() {
        Material spear = Material.matchMaterial("NETHERITE_SPEAR");
        if (spear != null) return spear;
        if (!warnedSpearFallback) {
            warnedSpearFallback = true;
            plugin.getLogger().warning("NETHERITE_SPEAR is not available in this Paper API; using TRIDENT as a compatibility fallback.");
        }
        return Material.TRIDENT;
    }

    private ItemStack gear(Material type) {
        ItemStack item = new ItemStack(type); ItemMeta meta = item.getItemMeta(); meta.setUnbreakable(true); item.setItemMeta(meta); return item;
    }
    private ItemStack enchanted(Material type, String key, int level) {
        ItemStack item = gear(type); enchant(item,key,level); return item;
    }
    private void enchant(ItemStack item, String key, int level) {
        Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        if (e == null) { plugin.getLogger().warning("Missing enchantment: " + key); return; }
        ItemMeta meta = item.getItemMeta(); meta.addEnchant(e,level,true); item.setItemMeta(meta);
    }
}
