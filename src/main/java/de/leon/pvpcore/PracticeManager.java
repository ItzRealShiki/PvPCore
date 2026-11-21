package de.leon.pvpcore;

import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
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
    public enum Mode { STUN_SLAM, SWORD, CRYSTAL_ANCHOR }

    private static final int ROOM_SPACING = 1024;
    private static final int DUEL_ARENA_RADIUS = 48;
    private static final int PRACTICE_ARENA_RADIUS = 24;
    private static final String PRACTICE_WORLD = "prac_void";

    private final PvPCore plugin;
    private World world;
    private final Map<UUID, Integer> stunCombos = new HashMap<>();
    private final Map<UUID, Integer> swordCombos = new HashMap<>();
    private final Map<UUID, Long> lastSwordHitTicks = new HashMap<>();
    private final Map<UUID, Integer> swordSwingSequence = new HashMap<>();
    private final Map<UUID, Integer> swordHitSwingSequence = new HashMap<>();
    private final Map<UUID, Integer> selectedHeights = new HashMap<>();
    private final Map<UUID, Integer> swordSwings = new HashMap<>();
    private final Map<UUID, Integer> swordHits = new HashMap<>();
    private final Map<UUID, Long> crystalAnchorStartTicks = new HashMap<>();
    private final Map<UUID, Location> crystalAnchorLocations = new HashMap<>();
    private final Set<UUID> activeAttempts = new HashSet<>();
    private final Set<UUID> resetting = new HashSet<>();
    private final Set<Integer> activeForceFields = new HashSet<>();
    private final Map<Integer, Integer> forceFieldHeights = new HashMap<>();
    private final Map<Integer, Integer> forceFieldRadii = new HashMap<>();
    private final Map<UUID, Integer> fightRooms = new HashMap<>();
    private final Map<UUID, Integer> fightSpawnOffsets = new HashMap<>();
    private final Map<UUID, Mode> fightModes = new HashMap<>();
    private final Map<UUID, Location> fightReturnLocations = new HashMap<>();
    private final Map<UUID, Integer> adminBoxVisits = new HashMap<>();
    private final Map<Integer, org.bukkit.scheduler.BukkitTask> leafDecayTasks = new HashMap<>();
    private final Set<Integer> foundationBuildingRooms = new HashSet<>();
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
            if (other.equals(ownBot) || isFightOpponent(player, other) || adminBoxVisits.containsKey(player.getUniqueId())) player.showPlayer(plugin, other);
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
            int index = fightRooms.getOrDefault(player.getUniqueId(), adminBoxVisits.getOrDefault(player.getUniqueId(),
                    plugin.getConfig().getInt("practice.players." + player.getUniqueId() + ".room", -1)));
            if (index < 0) continue;
            int cx = roomCenterX(index), cz = 128;
            boolean nearby = player.getLocation().distanceSquared(new Location(world == null ? player.getWorld() : world, cx + 0.5, player.getLocation().getY(), cz + 0.5)) <= 60.0 * 60.0;
            if (!nearby) {
                plugin.bots().removeOwned(player);
                continue;
            }
            nearbyRooms.add(index);
            int glassHeight = mode(player) == Mode.SWORD ? 30 : selectedHeight(player) + 10;
            int arenaRadius = isFighting(player) ? DUEL_ARENA_RADIUS : PRACTICE_ARENA_RADIUS;
            Integer previousGlassHeight = forceFieldHeights.get(index);
            if (!activeForceFields.contains(index)) {
                // Clear the previous full-height forcefield from older plugin versions.
                removeForceField(cx, cz, world.getMaxHeight() - 81, DUEL_ARENA_RADIUS);
                buildForceField(cx, cz, glassHeight, arenaRadius);
                activeForceFields.add(index);
                forceFieldHeights.put(index, glassHeight);
                forceFieldRadii.put(index, arenaRadius);
            } else if (previousGlassHeight == null || previousGlassHeight != glassHeight) {
                removeForceField(cx, cz, previousGlassHeight == null ? world.getMaxHeight() - 81 : previousGlassHeight,
                        forceFieldRadii.getOrDefault(index, PRACTICE_ARENA_RADIUS));
                buildForceField(cx, cz, glassHeight, arenaRadius);
                forceFieldHeights.put(index, glassHeight);
                forceFieldRadii.put(index, arenaRadius);
            }
            if (!isFighting(player) && !adminBoxVisits.containsKey(player.getUniqueId())
                    && mode(player) != Mode.CRYSTAL_ANCHOR && plugin.bots().isAvailable() && !plugin.bots().hasOwnedBot(player)) plugin.bots().spawn(player);
            if (mode(player) == Mode.SWORD) recoverSwordBotAtEdge(player, cx, cz);
            updatePracticeBoard(player);
            updatePlayerVisibility(player);
        }
        for (int index : new HashSet<>(activeForceFields)) {
            if (nearbyRooms.contains(index)) continue;
            removeForceField(roomCenterX(index), 128, forceFieldHeights.getOrDefault(index, 40),
                    forceFieldRadii.getOrDefault(index, PRACTICE_ARENA_RADIUS));
            activeForceFields.remove(index);
            forceFieldHeights.remove(index);
            forceFieldRadii.remove(index);
        }
    }

    public Mode mode(Player p) {
        Mode fightMode = fightModes.get(p.getUniqueId());
        if (fightMode != null) return fightMode;
        try { return Mode.valueOf(plugin.getConfig().getString("practice.players." + p.getUniqueId() + ".mode", "STUN_SLAM")); }
        catch (IllegalArgumentException ex) { return Mode.STUN_SLAM; }
    }

    public boolean isFighting(Player player) { return fightRooms.containsKey(player.getUniqueId()); }

    public void teleportToBox(Player admin, Player owner) {
        if (world == null) {
            world = Bukkit.getWorld(PRACTICE_WORLD);
            if (world == null) world = createVoidWorld();
        }
        if (world == null) {
            plugin.message(admin, "&cPractice world could not be loaded.");
            return;
        }
        String base = "practice.players." + owner.getUniqueId();
        int index = plugin.getConfig().getInt(base + ".room", -1);
        if (index < 0) {
            plugin.message(admin, "&cThat player has not created a practice box yet.");
            return;
        }
        if (!plugin.getConfig().getBoolean(base + ".built", false)) {
            buildRoom(roomCenterX(index), 128);
            plugin.getConfig().set(base + ".built", true);
            plugin.saveConfig();
        }
        adminBoxVisits.put(admin.getUniqueId(), index);
        int height = owner.getGameMode() == GameMode.SPECTATOR ? 0
                : (mode(owner) == Mode.SWORD ? 30 : selectedHeight(owner) + 10);
        if (!activeForceFields.contains(index)) {
            removeForceField(roomCenterX(index), 128, world.getMaxHeight() - 81, DUEL_ARENA_RADIUS);
            buildForceField(roomCenterX(index), 128, height, PRACTICE_ARENA_RADIUS);
            activeForceFields.add(index);
            forceFieldHeights.put(index, height);
            forceFieldRadii.put(index, PRACTICE_ARENA_RADIUS);
        }
        admin.teleport(new Location(world, roomCenterX(index) + 0.5, 82.0, 128.5));
        Bukkit.getScheduler().runTask(plugin, () -> updatePlayerVisibility(admin));
        plugin.message(admin, "&aTeleported to &f" + owner.getName() + "&a's box.");
    }

    private boolean isFightOpponent(Player player, Player other) {
        return isFighting(player) && Objects.equals(fightRooms.get(player.getUniqueId()), fightRooms.get(other.getUniqueId()))
                && isFighting(other);
    }

    public void startFight(Player challenger, Player opponent) {
        if (world == null) {
            world = Bukkit.getWorld(PRACTICE_WORLD);
            if (world == null) world = createVoidWorld();
        }
        if (world == null) {
            plugin.message(challenger, "&cPractice world could not be loaded.");
            plugin.message(opponent, "&cPractice world could not be loaded.");
            return;
        }
        String base = "practice.players." + challenger.getUniqueId();
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
        clearPlatform(cx, cz, DUEL_ARENA_RADIUS);
        buildDuelObstacles(cx, cz, index);
        buildDuelFoundation(cx, cz, index);
        buildBackgroundLandscape(cx, cz, index);
        plugin.bots().removeOwned(challenger);
        plugin.bots().removeOwned(opponent);
        activeAttempts.remove(challenger.getUniqueId());
        activeAttempts.remove(opponent.getUniqueId());
        resetting.remove(challenger.getUniqueId());
        resetting.remove(opponent.getUniqueId());
        Mode selected = mode(challenger);
        fightReturnLocations.put(challenger.getUniqueId(), challenger.getLocation().clone());
        fightReturnLocations.put(opponent.getUniqueId(), opponent.getLocation().clone());
        fightRooms.put(challenger.getUniqueId(), index);
        fightRooms.put(opponent.getUniqueId(), index);
        fightSpawnOffsets.put(challenger.getUniqueId(), -3);
        fightSpawnOffsets.put(opponent.getUniqueId(), 3);
        fightModes.put(challenger.getUniqueId(), selected);
        fightModes.put(opponent.getUniqueId(), selected);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime(1000L);
        for (Player fighter : List.of(challenger, opponent)) {
            fighter.setGameMode(GameMode.SURVIVAL);
            fighter.setFallDistance(0);
            fighter.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            fighter.teleport(fightSpawnLocation(index, fightSpawnOffsets.get(fighter.getUniqueId())));
            applyFightKit(fighter, selected);
            fighter.setHealth(20.0);
            fighter.setFoodLevel(20);
            fighter.setSaturation(20);
            Bukkit.getScheduler().runTask(plugin, () -> updatePlayerVisibility(fighter));
        }
        plugin.message(challenger, "&a1v1 started against &f" + opponent.getName() + "&a. Kit: &e" + selected.name() + "&a.");
        plugin.message(opponent, "&a1v1 started against &f" + challenger.getName() + "&a. Kit: &e" + selected.name() + "&a.");
    }

    private Location fightSpawnLocation(int room, int offset) {
        return new Location(world, roomCenterX(room) + 0.5 + offset, 81.0, 128.5, offset < 0 ? -90.0f : 90.0f, 0.0f);
    }

    public void endFight(Player player, String reason) {
        Integer room = fightRooms.remove(player.getUniqueId());
        if (room == null) return;
        List<UUID> participants = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : fightRooms.entrySet()) {
            if (entry.getValue().equals(room)) participants.add(entry.getKey());
        }
        fightSpawnOffsets.remove(player.getUniqueId());
        fightModes.remove(player.getUniqueId());
        returnFighter(player);
        for (UUID id : participants) {
            fightRooms.remove(id);
            fightSpawnOffsets.remove(id);
            fightModes.remove(id);
            Player other = Bukkit.getPlayer(id);
            if (other != null) {
                plugin.message(other, "&eThe 1v1 has ended. " + reason);
                returnFighter(other);
            } else fightReturnLocations.remove(id);
        }
        plugin.message(player, "&eThe 1v1 has ended. " + reason);
        restorePlayerVisibility(player);
        for (UUID id : participants) {
            Player other = Bukkit.getPlayer(id);
            if (other != null) restorePlayerVisibility(other);
        }
    }

    private void returnFighter(Player fighter) {
        Location destination = fightReturnLocations.get(fighter.getUniqueId());
        if (destination == null) return;
        if (fighter.isDead()) return;
        if (fighter.getWorld().equals(world)) fighter.teleport(destination);
        fightReturnLocations.remove(fighter.getUniqueId());
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

    public int score(Player p) { return stunCombos.getOrDefault(p.getUniqueId(), 0); }

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
            lines.add("&7Mode: " + (mode(player) == Mode.SWORD ? "&bSword" : mode(player) == Mode.CRYSTAL_ANCHOR ? "&6Crystal Anchor" : "&dStun Slam"));
        }
        if (scoreboardOption(player, "sword")) {
            lines.add("&b&lSWORD");
            lines.add("&7Accuracy: &a" + accuracy + "%");
            lines.add("&7Combo: &f" + swordCombos.getOrDefault(id, 0));
            if (scoreboardOption(player, "personal-bests")) lines.add("&7Personal Best: &e" + highScore(player, Mode.SWORD) + "%");
        }
        if (scoreboardOption(player, "stun-slam")) {
            lines.add("&d&lSTUN SLAM");
            lines.add("&7Current Combo: &f" + score(player));
            if (scoreboardOption(player, "personal-bests")) lines.add("&7Personal Best: &e" + highScore(player, Mode.STUN_SLAM));
        }
        if (mode(player) == Mode.CRYSTAL_ANCHOR) {
            long bestTicks = plugin.getConfig().getLong("practice.highscores." + id + ".crystal-anchor-best-ticks", 0L);
            long currentTicks = crystalAnchorStartTicks.containsKey(id)
                    ? Math.max(0L, world.getGameTime() - crystalAnchorStartTicks.get(id)) : 0L;
            lines.add("&6&lCRYSTAL ANCHOR");
            lines.add("&7Current: &f" + (currentTicks == 0L ? "--" : String.format(Locale.US, "%.2fs", currentTicks / 20.0)));
            lines.add("&7Best: &e" + (bestTicks == 0L ? "--" : String.format(Locale.US, "%.2fs", bestTicks / 20.0)));
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
        clearPlatform(cx, cz, PRACTICE_ARENA_RADIUS);
        plugin.bots().removeOwned(p);
        p.setGameMode(GameMode.SURVIVAL);
        p.setFallDistance(0);
        p.teleport(startLocation(p, challengeHeight));
        Bukkit.getScheduler().runTask(plugin, () -> updatePlayerVisibility(p));
        applyKit(p);
        stunCombos.putIfAbsent(p.getUniqueId(), 0);
        swordCombos.putIfAbsent(p.getUniqueId(), 0);
        if (mode(p) == Mode.CRYSTAL_ANCHOR) {
            crystalAnchorStartTicks.remove(p.getUniqueId());
            crystalAnchorLocations.remove(p.getUniqueId());
        }
        if (mode(p) == Mode.SWORD) {
            swordSwings.put(p.getUniqueId(), 0);
            swordHits.put(p.getUniqueId(), 0);
            updateSwordAccuracy(p);
        }
        if (challengeHeight > 0) activeAttempts.add(p.getUniqueId());

        if (challengeHeight > 0) {
            plugin.message(p, "&aStun Slam &7- &e" + difficultyName(challengeHeight) + " &7(" + challengeHeight + " blocks).");
            plugin.message(p, "&7Score: &a" + score(p) + " &8| &7Land a hit on the bot to earn a point.");
        } else if (mode(p) == Mode.CRYSTAL_ANCHOR) {
            plugin.message(p, "&aEntered Crystal Anchor practice. &7Place an anchor, then explode it to record your time.");
            updatePracticeBoard(p);
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
        buildArenaPlatform(cx, cz, PRACTICE_ARENA_RADIUS);
    }

    private void clearPlatform(int cx, int cz, int radius) {
        // Remove the old 49x49 glass enclosure when upgrading existing rooms.
        for (int x = -DUEL_ARENA_RADIUS; x <= DUEL_ARENA_RADIUS; x++) {
            for (int z = -DUEL_ARENA_RADIUS; z <= DUEL_ARENA_RADIUS; z++) {
                for (int y = 81; y < world.getMaxHeight(); y++) {
                    Block block = world.getBlockAt(cx + x, y, cz + z);
                    Material type = block.getType();
                    if (type == Material.GLASS || type.name().endsWith("_STAINED_GLASS")) block.setType(Material.AIR, false);
                }
            }
        }
        buildArenaPlatform(cx, cz, radius);
        int roomIndex = Math.floorDiv(cx - 128, ROOM_SPACING);
        activeForceFields.remove(roomIndex);
        forceFieldHeights.remove(roomIndex);
        forceFieldRadii.remove(roomIndex);
    }

    private void buildDuelObstacles(int cx, int cz, int roomIndex) {
        Material[] pillars = {Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.POLISHED_ANDESITE, Material.COBBLED_DEEPSLATE};
        int[][] positions = {
                {-14, -12, 3}, {14, -12, 2}, {-14, 12, 2}, {14, 12, 3},
                {-25, -2, 2}, {25, 2, 2}, {-3, -22, 2}, {3, 22, 2}
        };
        for (int i = 0; i < positions.length; i++) {
            int[] pos = positions[i];
            Material block = pillars[Math.floorMod(roomIndex + i, pillars.length)];
            for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++)
                for (int y = 1; y <= pos[2]; y++)
                    world.getBlockAt(cx + pos[0] + dx, 80 + y, cz + pos[1] + dz).setType(block, false);
        }
        // Low cover walls on the outer lanes; keep the central spawn-to-spawn lane open.
        for (int i = -4; i <= 4; i++) {
            world.getBlockAt(cx - 21 + i, 81, cz - 15).setType(Material.STONE_BRICKS, false);
            world.getBlockAt(cx - 21 + i, 82, cz - 15).setType(Material.STONE_BRICKS, false);
            world.getBlockAt(cx + 21 + i, 81, cz + 15).setType(Material.POLISHED_ANDESITE, false);
            world.getBlockAt(cx + 21 + i, 82, cz + 15).setType(Material.POLISHED_ANDESITE, false);
        }
        // Raised two-block stepping platforms add vertical movement without blocking combat.
        for (int x : new int[]{-8, 8}) for (int z : new int[]{-28, 28}) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                world.getBlockAt(cx + x + dx, 81, cz + z + dz).setType(Material.CUT_COPPER, false);
        }
    }

    private void buildDuelFoundation(int cx, int cz, int roomIndex) {
        if (world == null || !plugin.getConfig().getBoolean("duels.foundation.enabled", true)) return;
        String path = "duels.rooms." + roomIndex + ".foundation-built";
        if (plugin.getConfig().getBoolean(path, false) || !foundationBuildingRooms.add(roomIndex)) return;
        int stoneDepth = Math.max(1, Math.min(64, plugin.getConfig().getInt("duels.foundation.stone-depth", 64)));
        int deepslateDepth = Math.max(1, Math.min(128, plugin.getConfig().getInt("duels.foundation.deepslate-depth", 64)));
        List<int[]> columns = new ArrayList<>((DUEL_ARENA_RADIUS * 2 + 1) * (DUEL_ARENA_RADIUS * 2 + 1));
        for (int x = -DUEL_ARENA_RADIUS; x <= DUEL_ARENA_RADIUS; x++) for (int z = -DUEL_ARENA_RADIUS; z <= DUEL_ARENA_RADIUS; z++) columns.add(new int[]{cx + x, cz + z});
        int[] cursor = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int processed = 0;
            while (cursor[0] < columns.size() && processed++ < 24) {
                int[] col = columns.get(cursor[0]++);
                for (int y = 80 - stoneDepth; y < 80; y++) world.getBlockAt(col[0], y, col[1]).setType(Material.STONE, false);
                for (int y = 80 - stoneDepth - deepslateDepth; y < 80 - stoneDepth; y++) world.getBlockAt(col[0], y, col[1]).setType(Material.DEEPSLATE, false);
                world.getBlockAt(col[0], 80 - stoneDepth - deepslateDepth - 1, col[1]).setType(Material.BEDROCK, false);
            }
            if (cursor[0] >= columns.size()) {
                plugin.getConfig().set(path, true); plugin.saveConfig();
                foundationBuildingRooms.remove(roomIndex); task.cancel();
            }
        }, 1L, 1L);
    }

    private void buildBackgroundLandscape(int cx, int cz, int roomIndex) {
        if (world == null || !plugin.getConfig().getBoolean("duels.landscape.enabled", true)) return;
        String path = "duels.rooms." + roomIndex + ".landscape-built";
        if (plugin.getConfig().getBoolean(path, false)) return;
        Random random = new Random(731942L + roomIndex * 7919L);
        for (int hill = 0; hill < 22; hill++) {
            double angle = Math.PI * 2.0 * hill / 22.0 + random.nextDouble() * 0.18;
            int distance = 78 + random.nextInt(52);
            int hx = cx + (int) Math.round(Math.cos(angle) * distance);
            int hz = cz + (int) Math.round(Math.sin(angle) * distance);
            int radius = 8 + random.nextInt(10), peak = 4 + random.nextInt(9);
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                double norm = Math.sqrt(dx * dx + dz * dz) / radius;
                if (norm > 1.0) continue;
                int surface = 77 + Math.max(1, (int) Math.round(peak * (1.0 - norm * norm)));
                for (int y = 76; y < surface - 1; y++) world.getBlockAt(hx + dx, y, hz + dz).setType(Material.STONE, false);
                world.getBlockAt(hx + dx, surface - 1, hz + dz).setType(Material.DIRT, false);
                world.getBlockAt(hx + dx, surface, hz + dz).setType(Material.GRASS_BLOCK, false);
            }
            if (hill % 2 == 0) {
                int tx = hx + random.nextInt(Math.max(1, radius / 2)) - radius / 4;
                int tz = hz + random.nextInt(Math.max(1, radius / 2)) - radius / 4;
                int ground = 77 + peak, trunk = 5 + random.nextInt(3);
                for (int y = 1; y <= trunk; y++) world.getBlockAt(tx, ground + y, tz).setType(Material.SPRUCE_LOG, false);
                for (int layer = 0; layer < 4; layer++) {
                    int spread = 3 - layer / 2;
                    for (int dx = -spread; dx <= spread; dx++) for (int dz = -spread; dz <= spread; dz++)
                        if (Math.abs(dx) + Math.abs(dz) <= spread + 1)
                            world.getBlockAt(tx + dx, ground + trunk - 2 + layer, tz + dz).setType(Material.SPRUCE_LEAVES, false);
                }
            }
        }
        plugin.getConfig().set(path, true); plugin.saveConfig();
    }

    public String arenaTheme(Player player) {
        return plugin.getConfig().getString("practice.players." + player.getUniqueId() + ".box-theme", "PLAINS").toUpperCase(Locale.ROOT);
    }

    private String arenaTheme(int roomIndex) {
        var players = plugin.getConfig().getConfigurationSection("practice.players");
        if (players != null) {
            for (String uuid : players.getKeys(false)) {
                if (plugin.getConfig().getInt("practice.players." + uuid + ".room", -1) == roomIndex) {
                    return plugin.getConfig().getString("practice.players." + uuid + ".box-theme", "PLAINS").toUpperCase(Locale.ROOT);
                }
            }
        }
        return "PLAINS";
    }

    public void setArenaTheme(Player player, String theme) {
        String normalized = theme.toUpperCase(Locale.ROOT);
        if (!List.of("DESERT", "PLAINS", "END", "NETHER", "BADLANDS", "PALE_GARDEN",
                "CHERRY_BLOSSOM", "WARPED", "CRIMSON", "FLOWER_PLAINS").contains(normalized)) return;
        String base = "practice.players." + player.getUniqueId();
        plugin.getConfig().set(base + ".box-theme", normalized);
        plugin.saveConfig();
        if (world == null) world = Bukkit.getWorld(PRACTICE_WORLD);
        int room = plugin.getConfig().getInt(base + ".room", -1);
        if (world != null && room >= 0) buildArenaPlatform(roomCenterX(room), 128,
                isFighting(player) ? DUEL_ARENA_RADIUS : PRACTICE_ARENA_RADIUS);
        plugin.message(player, "&aYour box design is now &e" + normalized.replace('_', ' ') + "&a.");
    }

    private Material[] arenaPalette(int roomIndex) {
        return switch (arenaTheme(roomIndex)) {
            case "DESERT" -> new Material[]{Material.SAND, Material.SANDSTONE, Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE, Material.DEAD_BUSH};
            case "END" -> new Material[]{Material.END_STONE, Material.END_STONE, Material.END_STONE, Material.END_STONE, Material.CHORUS_FLOWER};
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

    private void clearEndDecorativeShulkers(int roomIndex) {
        String tag = "pvpcore_end_shulker_" + roomIndex;
        for (org.bukkit.entity.Entity entity : world.getEntities()) {
            if (entity.getScoreboardTags().contains(tag)) entity.remove();
        }
    }

    private void buildEndRuin(int x, int z, int roomIndex, int variant) {
        // Small 5x5 End-stone-brick outposts with purpur details.
        int baseY = 81;
        Material brick = Material.END_STONE_BRICKS;
        Material accent = Material.PURPUR_PILLAR;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                if (!corner) world.getBlockAt(x + dx, baseY, z + dz).setType(brick, false);
            }
        }
        for (int[] corner : new int[][]{{-2,-2},{-2,2},{2,-2},{2,2}}) {
            int px = x + corner[0], pz = z + corner[1];
            for (int y = 1; y <= 3; y++)
                world.getBlockAt(px, baseY + y, pz).setType(accent, false);
            world.getBlockAt(px, baseY + 4, pz).setType(Material.PURPUR_BLOCK, false);
        }
        world.getBlockAt(x, baseY + 1, z).setType(Material.PURPUR_BLOCK, false);
        world.getBlockAt(x, baseY + 2, z).setType(Material.PURPUR_PILLAR, false);
        world.getBlockAt(x, baseY + 3, z).setType(Material.PURPUR_BLOCK, false);
        // Each ruin has one stationary, invulnerable shulker as decoration.
        Location spawn = new Location(world, x + 0.5, baseY + 1.0, z + 0.5);
        Shulker shulker = world.spawn(spawn, Shulker.class, entity -> {
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setGravity(false);
            entity.addScoreboardTag("pvpcore_end_shulker_" + roomIndex);
            entity.setPeek((byte) 0);
            entity.setColor(org.bukkit.DyeColor.PURPLE);
        });
    }

    private void buildArenaPlatform(int cx, int cz, int arenaRadius) {
        if (world == null) return;
        int roomIndex = Math.floorDiv(cx - 128, ROOM_SPACING);
        String theme = arenaTheme(roomIndex);
        Material[] palette = arenaPalette(roomIndex);
        Random decorationRandom = new Random();
        List<Block> timedLeaves = new ArrayList<>();

        // Cancel the previous timer when a room is rebuilt or its theme changes.
        org.bukkit.scheduler.BukkitTask oldDecay = leafDecayTasks.remove(roomIndex);
        if (oldDecay != null) oldDecay.cancel();

        for (int x = -DUEL_ARENA_RADIUS; x <= DUEL_ARENA_RADIUS; x++) {
            for (int z = -DUEL_ARENA_RADIUS; z <= DUEL_ARENA_RADIUS; z++) {
                int edge = Math.max(Math.abs(x), Math.abs(z));
                double radius = Math.hypot(x, z);

                // Keep the central PvP zone level. Small, clustered height changes
                // are restricted to the outer ring so movement and combos stay fair.
                double terrainNoise = Math.sin(x * 0.37) + Math.cos(z * 0.33)
                        + Math.sin((x + z) * 0.21) * 0.8;
                int height = 0;
                if (radius >= arenaRadius * 0.54 && radius <= arenaRadius * 0.90) {
                    if (terrainNoise > 2.05) height = 2;
                    else if (terrainNoise > 1.15) height = 1;
                }

                // Never touch the outermost ring: it contains the forcefield wall.
                // Clearing y=81..85 there would remove the bottom glass rows.
                if (edge > arenaRadius) {
                    for (int y = 80; y <= 90; y++) world.getBlockAt(cx + x, y, cz + z).setType(Material.AIR, false);
                    continue;
                }
                if (edge == arenaRadius) continue;

                // Clear old decoration and raised terrain before rebuilding the tile.
                // Clear the full decoration height, including persistent cherry leaves and logs
                // from the previous theme. The interior bounds keep the glass forcefield untouched.
                for (int y = 81; y <= 90; y++) {
                    world.getBlockAt(cx + x, y, cz + z).setType(Material.AIR, false);
                }

                int topY = 80 + height;
                world.getBlockAt(cx + x, topY, cz + z).setType(palette[0], false);
                world.getBlockAt(cx + x, topY - 1, cz + z).setType(palette[1], false);
                world.getBlockAt(cx + x, topY - 2, cz + z).setType(palette[2], false);
                world.getBlockAt(cx + x, topY - 3, cz + z).setType(palette[3], false);

                // Add varied ground cover from the mid-ring outward. The central
                // combat zone stays clear, while plants are scattered in small clusters.
                if (radius >= arenaRadius * 0.33 && radius <= arenaRadius * 0.94) {
                    int plantRoll = decorationRandom.nextInt(17);
                    Material groundDetail = null;
                    switch (theme) {
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
                    if ((theme.equals("PLAINS") || theme.equals("FLOWER_PLAINS")
                            || theme.equals("CHERRY_BLOSSOM"))
                            && Math.floorMod(x * 43 + z * 67, 31) == 0) {
                        world.getBlockAt(cx + x, topY + 1, cz + z).setType(Material.MOSS_CARPET, false);
                    }
                }

                // Compact ornamental cherry trees with a tapered, rounded canopy.
                // They stay near the perimeter and their leaves are temporary accents.
                if (theme.equals("CHERRY_BLOSSOM")
                        && edge >= arenaRadius * 0.75 && edge <= arenaRadius * 0.92
                        && decorationRandom.nextInt(173) == 0) {
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

        // The END theme gets a few compact ruins around the perimeter. The central
        // combat area remains completely open and the shulkers are display-only.
        if (theme.equals("END")) {
            clearEndDecorativeShulkers(roomIndex);
            buildEndRuin(cx - 15, cz - 14, roomIndex, 0);
            buildEndRuin(cx + 15, cz - 14, roomIndex, 1);
            buildEndRuin(cx - 15, cz + 14, roomIndex, 2);
            buildEndRuin(cx + 15, cz + 14, roomIndex, 3);
        } else {
            clearEndDecorativeShulkers(roomIndex);
        }

        // Decorative frame inside the glass enclosure, using the selected theme's accent block.
        Material trim = palette[3];
        for (int offset = -arenaRadius + 1; offset <= arenaRadius - 1; offset++) {
            world.getBlockAt(cx - arenaRadius + 1, 80, cz + offset).setType(trim, false);
            world.getBlockAt(cx + arenaRadius - 1, 80, cz + offset).setType(trim, false);
            world.getBlockAt(cx + offset, 80, cz - arenaRadius + 1).setType(trim, false);
            world.getBlockAt(cx + offset, 80, cz + arenaRadius - 1).setType(trim, false);
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

    private void buildForceField(int cx, int cz, int glassHeight, int arenaRadius) {
        if (world == null) return;
        int roofY = 80 + glassHeight;
        for (int offset = -arenaRadius; offset <= arenaRadius; offset++) {
            for (int y = 81; y <= roofY; y++) {
                world.getBlockAt(cx - arenaRadius, y, cz + offset).setType(forceFieldMaterial(), false);
                world.getBlockAt(cx + arenaRadius, y, cz + offset).setType(forceFieldMaterial(), false);
                world.getBlockAt(cx + offset, y, cz - arenaRadius).setType(forceFieldMaterial(), false);
                world.getBlockAt(cx + offset, y, cz + arenaRadius).setType(forceFieldMaterial(), false);
            }
        }

        // Close the arena with a complete glass roof at the calculated mode-specific height.
        for (int x = -arenaRadius; x <= arenaRadius; x++) {
            for (int z = -arenaRadius; z <= arenaRadius; z++) {
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
            buildForceField(cx, cz, forceFieldHeights.getOrDefault(index, 40), forceFieldRadii.getOrDefault(index, PRACTICE_ARENA_RADIUS));
        }
    }

    private void removeForceField(int cx, int cz, int glassHeight, int arenaRadius) {
        if (world == null) return;
        int roofY = 80 + glassHeight;
        for (int offset = -arenaRadius; offset <= arenaRadius; offset++) {
            for (int y = 81; y <= roofY; y++) {
                world.getBlockAt(cx - arenaRadius, y, cz + offset).setType(Material.AIR, false);
                world.getBlockAt(cx + arenaRadius, y, cz + offset).setType(Material.AIR, false);
                world.getBlockAt(cx + offset, y, cz - arenaRadius).setType(Material.AIR, false);
                world.getBlockAt(cx + offset, y, cz + arenaRadius).setType(Material.AIR, false);
            }
        }
        for (int x = -arenaRadius; x <= arenaRadius; x++) {
            for (int z = -arenaRadius; z <= arenaRadius; z++) {
                world.getBlockAt(cx + x, roofY, cz + z).setType(Material.AIR, false);
            }
        }
    }

    private void retry(Player p, boolean success) {
        UUID id = p.getUniqueId();
        if (!resetting.add(id)) return;
        activeAttempts.remove(id);
        if (success) {
            stunCombos.merge(id, 1, Integer::sum);
            updateHighScore(p, Mode.STUN_SLAM, score(p));
            updatePracticeBoard(p);
            plugin.message(p, "&a+1 combo! &7Combo: &e" + score(p));
        } else {
            stunCombos.put(id, 0);
            updatePracticeBoard(p);
            plugin.message(p, "&cMissed! &7Combo reset. Current combo: &e" + score(p));
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
                || mode(player) != Mode.SWORD
                || !player.getInventory().getItemInMainHand().getType().name().endsWith("_SWORD")) return;
        UUID id = player.getUniqueId();
        swordSwings.merge(id, 1, Integer::sum);
        int swing = swordSwingSequence.merge(id, 1, Integer::sum);
        long swingTick = world.getGameTime();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !player.getWorld().equals(world)) return;
            // Paper may deliver the entity-damage event before the arm-swing animation
            // event for a successful hit. Allow the hit event to arrive first, including
            // when both events happen during the same server tick.
            Long lastHitTick = lastSwordHitTicks.get(id);
            boolean hitThisSwing = lastHitTick != null && lastHitTick >= swingTick;
            if (swordSwingSequence.getOrDefault(id, 0) == swing && !hitThisSwing) {
                resetSwordCombo(player);
            }
        }, 2L);
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
            if (!player.getInventory().getItemInMainHand().getType().name().endsWith("_SWORD")) return;
            UUID id = player.getUniqueId();
            swordHits.merge(id, 1, Integer::sum);
            swordHitSwingSequence.put(id, swordSwingSequence.getOrDefault(id, 0));
            long now = world.getGameTime();
            Long previousHit = lastSwordHitTicks.get(id);
            int nextCombo = previousHit == null || now - previousHit > 200L ? 1 : swordCombos.getOrDefault(id, 0) + 1;
            swordCombos.put(id, nextCombo);
            lastSwordHitTicks.put(id, now);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                Long last = lastSwordHitTicks.get(id);
                if (last != null && last == now && world.getGameTime() - last >= 200L) resetSwordCombo(player);
            }, 200L);
            updateSwordAccuracy(player);
            updatePracticeBoard(player);
            return;
        }

        if (!activeAttempts.contains(player.getUniqueId())) return;
        // Stun Slam only scores a real mace hit; other weapons still deal normal damage.
        if (player.getInventory().getItemInMainHand().getType() != Material.MACE) return;
        retry(player, true);
    }

    private void resetSwordCombo(Player player) {
        UUID id = player.getUniqueId();
        swordCombos.put(id, 0);
        lastSwordHitTicks.remove(id);
        updatePracticeBoard(player);
        player.sendActionBar(ChatColor.RED + "Sword combo missed! " + ChatColor.GRAY + "Combo: " + ChatColor.YELLOW + "0");
    }

    private void updateSwordAccuracy(Player player) {
        UUID id = player.getUniqueId();
        int swings = swordSwings.getOrDefault(id, 0);
        int hits = swordHits.getOrDefault(id, 0);
        int accuracy = swings == 0 ? 0 : Math.min(100, Math.round(hits * 100.0f / swings));
        updateHighScore(player, Mode.SWORD, accuracy);
        player.sendActionBar(ChatColor.GREEN + "Accuracy: " + ChatColor.YELLOW + accuracy + "%"
                + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Combo: "
                + ChatColor.AQUA + swordCombos.getOrDefault(id, 0)
                + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Hits: "
                + ChatColor.GREEN + hits + ChatColor.GRAY + "/" + swings);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!p.getWorld().equals(world) || event.getTo() == null) return;

        int roomIndex = fightRooms.getOrDefault(p.getUniqueId(), adminBoxVisits.getOrDefault(p.getUniqueId(),
                plugin.getConfig().getInt("practice.players." + p.getUniqueId() + ".room", -1)));
        if (roomIndex >= 0) {
            int cx = roomCenterX(roomIndex);
            int cz = 128;
            int arenaRadius = isFighting(p) ? DUEL_ARENA_RADIUS : PRACTICE_ARENA_RADIUS;
            Location to = event.getTo();
            // Only teleport after the player's position has passed beyond the outer edge
            // of the glass wall (at +/-48), not while approaching or touching its inner face.
            if (to.getX() < cx - arenaRadius - 0.5 || to.getX() > cx + arenaRadius + 0.5
                    || to.getZ() < cz - arenaRadius - 0.5 || to.getZ() > cz + arenaRadius + 0.5) {
                p.setFallDistance(0);
                p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                if (isFighting(p)) p.teleport(fightSpawnLocation(roomIndex, fightSpawnOffsets.getOrDefault(p.getUniqueId(), 0)));
                else if (adminBoxVisits.containsKey(p.getUniqueId())) p.teleport(new Location(world, cx + 0.5, 82.0, cz + 0.5));
                else {
                    int height = mode(p) == Mode.STUN_SLAM && activeAttempts.contains(p.getUniqueId())
                            ? selectedHeight(p) : 0;
                    p.teleport(startLocation(p, height));
                }
                return;
            }
        }

        if (!activeAttempts.contains(p.getUniqueId())) return;
        if (event.getTo().getY() >= event.getFrom().getY()) return;
        if (event.getTo().getY() <= 81.0) retry(p, false);
    }

    @EventHandler
    public void onFightDeath(PlayerDeathEvent event) {
        Player defeated = event.getEntity();
        if (world != null && defeated.getWorld().equals(world)) {
            // Practice rooms and duels are kit-based: never leave dropped gear or XP behind.
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepInventory(true);
            event.setKeepLevel(true);
        }
        if (isFighting(defeated)) endFight(defeated, "&a" + defeated.getName() + " was defeated.");
    }


    @EventHandler
    public void onFightRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Location destination = fightReturnLocations.remove(event.getPlayer().getUniqueId());
        if (destination != null) event.setRespawnLocation(destination);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p) || !p.getWorld().equals(world)) return;
        // Human-vs-human damage is enabled only for participants in an accepted 1v1.
        // In Crystal Anchor practice, players must take respawn-anchor explosion damage.
        if (isFighting(p) || plugin.bots().isPracticeBot(p)) return;
        if (mode(p) == Mode.CRYSTAL_ANCHOR
                && event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onAnchorTotemResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player) || world == null
                || !player.getWorld().equals(world) || mode(player) != Mode.CRYSTAL_ANCHOR
                || isFighting(player)) return;
        // Replenish the offhand after the totem has been consumed by resurrection.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !player.getWorld().equals(world)
                    || mode(player) != Mode.CRYSTAL_ANCHOR || isFighting(player)) return;
            player.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
        });
    }

    @EventHandler public void onBreak(BlockBreakEvent event) {
        if (!event.getBlock().getWorld().equals(world)) return;
        boolean duelBreaking = isFighting(event.getPlayer())
                && plugin.getConfig().getBoolean("duels.block-breaking.enabled", false);
        if (!duelBreaking) event.setCancelled(true);
    }

    @EventHandler public void onPlace(BlockPlaceEvent event) {
        if (!event.getBlock().getWorld().equals(world)) return;
        Player player = event.getPlayer();
        if (!isFighting(player) && mode(player) == Mode.CRYSTAL_ANCHOR
                && event.getBlockPlaced().getType() == Material.RESPAWN_ANCHOR) {
            UUID id = player.getUniqueId();
            crystalAnchorStartTicks.put(id, world.getGameTime());
            crystalAnchorLocations.put(id, event.getBlockPlaced().getLocation());
            // Refill the placing hand after Bukkit applies the normal item decrement.
            // This keeps the practice anchor available indefinitely.
            org.bukkit.inventory.EquipmentSlot hand = event.getHand();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || mode(player) != Mode.CRYSTAL_ANCHOR) return;
                ItemStack held = hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND
                        ? player.getInventory().getItemInOffHand()
                        : player.getInventory().getItemInMainHand();
                if (held == null || held.getType() != Material.RESPAWN_ANCHOR) {
                    held = gear(Material.RESPAWN_ANCHOR);
                    if (hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND)
                        player.getInventory().setItemInOffHand(held);
                    else player.getInventory().setItemInMainHand(held);
                } else if (held.getAmount() < 64) {
                    held.setAmount(64);
                }
            });
            player.sendActionBar(ChatColor.GOLD + "Anchor placed! " + ChatColor.GRAY + "Timer started.");
            updatePracticeBoard(player);
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onAnchorExplode(BlockExplodeEvent event) {
        if (world == null || !event.getBlock().getWorld().equals(world)
                || event.getBlock().getType() != Material.RESPAWN_ANCHOR) return;
        // Respawn anchors are practice equipment, never loot.
        event.setYield(0.0f);
        Location exploded = event.getBlock().getLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            Location tracked = crystalAnchorLocations.get(id);
            Long started = crystalAnchorStartTicks.get(id);
            if (tracked == null || started == null || !tracked.equals(exploded)
                    || !player.getWorld().equals(world) || mode(player) != Mode.CRYSTAL_ANCHOR) continue;
            long elapsed = Math.max(1L, world.getGameTime() - started);
            String path = "practice.highscores." + id + ".crystal-anchor-best-ticks";
            long best = plugin.getConfig().getLong(path, 0L);
            if (best == 0L || elapsed < best) {
                plugin.getConfig().set(path, elapsed);
                plugin.saveConfig();
                player.sendMessage(ChatColor.GREEN + "New Crystal Anchor best: "
                        + String.format(Locale.US, "%.2f", elapsed / 20.0) + "s");
            } else {
                player.sendMessage(ChatColor.GOLD + "Crystal Anchor time: "
                        + String.format(Locale.US, "%.2f", elapsed / 20.0) + "s"
                        + ChatColor.GRAY + " | Best: " + String.format(Locale.US, "%.2f", best / 20.0) + "s");
            }
            crystalAnchorStartTicks.remove(id);
            crystalAnchorLocations.remove(id);
            updatePracticeBoard(player);
            break;
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        restorePracticeBoard(event.getPlayer());
        restorePlayerVisibility(event.getPlayer());
        if (isFighting(event.getPlayer())) endFight(event.getPlayer(), "&cA player left.");
        adminBoxVisits.remove(id);
        fightReturnLocations.remove(id);
        stunCombos.remove(id); swordCombos.remove(id); lastSwordHitTicks.remove(id);
        crystalAnchorStartTicks.remove(id); crystalAnchorLocations.remove(id);
        swordSwingSequence.remove(id); swordHitSwingSequence.remove(id);
        selectedHeights.remove(id); swordSwings.remove(id); swordHits.remove(id);
        activeAttempts.remove(id); resetting.remove(id);
    }

    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        activeAttempts.remove(id); resetting.remove(id);
        if (isFighting(event.getPlayer()) && !event.getPlayer().getWorld().equals(world)) {
            endFight(event.getPlayer(), "&cA player left the arena.");
        }
        if (!event.getPlayer().getWorld().equals(world)) {
            adminBoxVisits.remove(id);
            restorePracticeBoard(event.getPlayer());
            restorePlayerVisibility(event.getPlayer());
        } else Bukkit.getScheduler().runTask(plugin, () -> updatePlayerVisibility(event.getPlayer()));
    }

    public void applyKit(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[]{gear(Material.DIAMOND_BOOTS),gear(Material.DIAMOND_LEGGINGS),gear(Material.DIAMOND_CHESTPLATE),gear(Material.DIAMOND_HELMET)});
        if (mode(p) == Mode.CRYSTAL_ANCHOR) {
            p.getInventory().setItem(0, gear(Material.RESPAWN_ANCHOR));
            p.getInventory().setItem(1, new ItemStack(Material.GLOWSTONE, 64));
        } else if (mode(p) == Mode.SWORD) p.getInventory().setItem(0, gear(Material.DIAMOND_SWORD));
        else {
            p.getInventory().setItem(0, enchanted(Material.NETHERITE_AXE, "sharpness", 5));
            ItemStack mace = enchanted(Material.MACE, "density", 5); enchant(mace, "wind_burst", 3);
            p.getInventory().setItem(1, mace);
            p.getInventory().setItem(2, enchanted(spearMaterial(), "lunge", 3));
        }
        loadSavedKit(p, p.getInventory());
        if (mode(p) == Mode.CRYSTAL_ANCHOR) {
            p.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
        }
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
        if (mode(player) == Mode.CRYSTAL_ANCHOR) {
            editor.setItem(0, gear(Material.RESPAWN_ANCHOR));
            editor.setItem(1, new ItemStack(Material.GLOWSTONE, 64));
        } else if (mode(player) == Mode.SWORD) editor.setItem(0, gear(Material.DIAMOND_SWORD));
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

    public void applyDuelKitToInventory(Player player, Mode mode, Inventory editor) {
        editor.clear();
        if (mode == Mode.SWORD) {
            editor.setItem(0, gear(Material.DIAMOND_SWORD));
        } else {
            editor.setItem(0, enchanted(Material.NETHERITE_AXE, "sharpness", 5));
            ItemStack mace = enchanted(Material.MACE, "density", 5);
            enchant(mace, "wind_burst", 3);
            editor.setItem(1, mace);
            editor.setItem(2, enchanted(spearMaterial(), "lunge", 3));
        }
        editor.setItem(45, gear(Material.DIAMOND_HELMET));
        editor.setItem(46, gear(Material.DIAMOND_CHESTPLATE));
        editor.setItem(47, gear(Material.DIAMOND_LEGGINGS));
        editor.setItem(48, gear(Material.DIAMOND_BOOTS));
        loadDuelKit(player, mode, editor);
    }

    public void saveDuelKit(Player player, Mode mode, Inventory editor) {
        String path = "practice.players." + player.getUniqueId() + ".duel-kits." + mode.name();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = editor.getItem(slot);
            plugin.getConfig().set(path + ".contents." + slot,
                    item == null || item.getType().isAir() ? null : item.clone());
        }
        plugin.getConfig().set(path + ".helmet", editor.getItem(45));
        plugin.getConfig().set(path + ".chestplate", editor.getItem(46));
        plugin.getConfig().set(path + ".leggings", editor.getItem(47));
        plugin.getConfig().set(path + ".boots", editor.getItem(48));
        plugin.saveConfig();
    }

    private void loadDuelKit(Player player, Mode mode, PlayerInventory inventory) {
        String personalPath = "practice.players." + player.getUniqueId() + ".duel-kits." + mode.name();
        String path = plugin.getConfig().contains(personalPath + ".contents")
                ? personalPath : "practice.admin-kits." + mode.name();
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

    private void loadDuelKit(Player player, Mode mode, Inventory inventory) {
        String personalPath = "practice.players." + player.getUniqueId() + ".duel-kits." + mode.name();
        String path = plugin.getConfig().contains(personalPath + ".contents")
                ? personalPath : "practice.admin-kits." + mode.name();
        if (!plugin.getConfig().contains(path + ".contents")) return;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = plugin.getConfig().getItemStack(path + ".contents." + slot);
            inventory.setItem(slot, item == null ? null : item.clone());
        }
        inventory.setItem(45, plugin.getConfig().getItemStack(path + ".helmet"));
        inventory.setItem(46, plugin.getConfig().getItemStack(path + ".chestplate"));
        inventory.setItem(47, plugin.getConfig().getItemStack(path + ".leggings"));
        inventory.setItem(48, plugin.getConfig().getItemStack(path + ".boots"));
    }

    private void applyFightKit(Player player, Mode mode) {
        player.getInventory().clear();
        if (mode == Mode.SWORD) {
            player.getInventory().setArmorContents(new ItemStack[]{gear(Material.DIAMOND_BOOTS), gear(Material.DIAMOND_LEGGINGS), gear(Material.DIAMOND_CHESTPLATE), gear(Material.DIAMOND_HELMET)});
            player.getInventory().setItem(0, gear(Material.DIAMOND_SWORD));
        } else {
            player.getInventory().setArmorContents(new ItemStack[]{gear(Material.DIAMOND_BOOTS), gear(Material.DIAMOND_LEGGINGS), gear(Material.DIAMOND_CHESTPLATE), gear(Material.DIAMOND_HELMET)});
            player.getInventory().setItem(0, enchanted(Material.NETHERITE_AXE, "sharpness", 5));
            ItemStack mace = enchanted(Material.MACE, "density", 5);
            enchant(mace, "wind_burst", 3);
            player.getInventory().setItem(1, mace);
            player.getInventory().setItem(2, enchanted(spearMaterial(), "lunge", 3));
        }
        loadDuelKit(player, mode, player.getInventory());
        player.getInventory().setHeldItemSlot(0);
    }

    public void applyAdminKitToInventory(Mode mode, Inventory editor) {
        editor.clear();
        if (mode == Mode.SWORD) {
            editor.setItem(0, gear(Material.DIAMOND_SWORD));
        } else {
            editor.setItem(0, enchanted(Material.NETHERITE_AXE, "sharpness", 5));
            ItemStack mace = enchanted(Material.MACE, "density", 5);
            enchant(mace, "wind_burst", 3);
            editor.setItem(1, mace);
            editor.setItem(2, enchanted(spearMaterial(), "lunge", 3));
        }
        editor.setItem(45, gear(Material.DIAMOND_HELMET));
        editor.setItem(46, gear(Material.DIAMOND_CHESTPLATE));
        editor.setItem(47, gear(Material.DIAMOND_LEGGINGS));
        editor.setItem(48, gear(Material.DIAMOND_BOOTS));
        String path = "practice.admin-kits." + mode.name();
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

    public void saveAdminKit(Mode mode, Inventory editor) {
        String path = "practice.admin-kits." + mode.name();
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

    private void loadAdminKit(Mode mode, PlayerInventory inventory) {
        String path = "practice.admin-kits." + mode.name();
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
