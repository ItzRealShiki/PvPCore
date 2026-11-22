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
    private static final int ARENA_1_FORCE_FIELD_RADIUS = 74;
    private static final int ARENA_3_FORCE_FIELD_RADIUS = 34;
    private static final int ARENA_FORCE_FIELD_HEIGHT = 200;
    private static final Material FORCE_FIELD_BLOCK = Material.BARRIER;
    private static final int LEGACY_GLASS_HEIGHT = 12;
    // Horizontal centers of the nine tall ice-spike groups detected in Arena 1's schematic.
    // The wall is interpolated smoothly between these anchors, keeping it centered between spikes.
    private static final int[][] ARENA_1_ICICLE_POINTS = {
            {-46, -39}, {-10, -66}, {24, -64}, {64, -48}, {80, 0},
            {60, 49}, {16, 64}, {-24, 50}, {-54, 9}
    };
    private static final List<String> ARENA_MAPS = List.of("ARENA_1", "ARENA_3");
    private static final String PRACTICE_WORLD = "prac_void";
    private static final int SCHEMATIC_BUILD_VERSION = 3;

    private final PvPCore plugin;
    private World world;
    private final Map<UUID, Integer> stunCombos = new HashMap<>();
    private final Map<UUID, Integer> swordCombos = new HashMap<>();
    private final Map<UUID, Long> lastSwordHitTicks = new HashMap<>();
    private final Map<UUID, Long> pendingBotHitTicks = new HashMap<>();
    private final Map<UUID, Mode> pendingBotHitModes = new HashMap<>();
    private final Map<UUID, Long> lastBotComboScoreTicks = new HashMap<>();
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
    private final Map<Integer, Integer> arenaFloorCache = new HashMap<>();
    private final Map<UUID, Long> forceFieldRenderTicks = new HashMap<>();
    private final Map<UUID, Integer> fightRooms = new HashMap<>();
    private final Map<UUID, Integer> fightSpawnOffsets = new HashMap<>();
    private final Map<UUID, Mode> fightModes = new HashMap<>();
    private final Map<UUID, Location> fightReturnLocations = new HashMap<>();
    private final Map<UUID, Integer> adminBoxVisits = new HashMap<>();
    private final Map<UUID, Scoreboard> practiceBoards = new HashMap<>();
    private final Map<UUID, Scoreboard> previousBoards = new HashMap<>();
    private boolean warnedSpearFallback;

    public PracticeManager(PvPCore plugin) {
        this.plugin = plugin;
        this.world = Bukkit.getWorld(PRACTICE_WORLD);
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateNearbyArenas, 1L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateCrystalAnchorTimers, 1L, 5L);
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
            int arenaRadius = arenaForceFieldRadius(index);
            boolean nearby = player.getLocation().distanceSquared(
                    new Location(world, cx + 0.5, player.getLocation().getY(), cz + 0.5)) <= 100.0 * 100.0;
            if (!nearby) {
                plugin.bots().removeOwned(player);
                continue;
            }

            nearbyRooms.add(index);
            Integer previousGlassHeight = forceFieldHeights.get(index);
            Integer previousRadius = forceFieldRadii.get(index);
            if (!activeForceFields.contains(index)) {
                removeForceField(cx, cz, ARENA_1_FORCE_FIELD_RADIUS);
                removeForceField(cx, cz, ARENA_3_FORCE_FIELD_RADIUS);
                buildForceField(cx, cz, ARENA_FORCE_FIELD_HEIGHT, arenaRadius);
                activeForceFields.add(index);
                forceFieldHeights.put(index, ARENA_FORCE_FIELD_HEIGHT);
                forceFieldRadii.put(index, arenaRadius);
            } else if (previousGlassHeight == null || previousGlassHeight != ARENA_FORCE_FIELD_HEIGHT
                    || previousRadius == null || previousRadius != arenaRadius) {
                removeForceField(cx, cz, previousRadius == null ? arenaRadius : previousRadius);
                buildForceField(cx, cz, ARENA_FORCE_FIELD_HEIGHT, arenaRadius);
                forceFieldHeights.put(index, ARENA_FORCE_FIELD_HEIGHT);
                forceFieldRadii.put(index, arenaRadius);
            }

            renderNearbyForceField(player, cx, cz, arenaRadius);
            if (!isFighting(player) && !adminBoxVisits.containsKey(player.getUniqueId())
                    && mode(player) != Mode.CRYSTAL_ANCHOR && plugin.bots().isAvailable()
                    && !plugin.bots().hasOwnedBot(player)) plugin.bots().spawn(player);
            if (mode(player) != Mode.CRYSTAL_ANCHOR) recoverBotAtBarrier(player, cx, cz);
            updatePracticeBoard(player);
            updatePlayerVisibility(player);
        }

        for (int index : new HashSet<>(activeForceFields)) {
            if (nearbyRooms.contains(index)) continue;
            removeForceField(roomCenterX(index), 128,
                    forceFieldRadii.getOrDefault(index, arenaForceFieldRadius(index)));
            activeForceFields.remove(index);
            forceFieldHeights.remove(index);
            forceFieldRadii.remove(index);
        }
    }

    private void updateCrystalAnchorTimers() {
        if (world == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(world) || mode(player) != Mode.CRYSTAL_ANCHOR) continue;
            Long started = crystalAnchorStartTicks.get(player.getUniqueId());
            if (started == null) continue;
            long elapsed = Math.max(0L, world.getGameTime() - started);
            player.sendActionBar(ChatColor.GOLD + "Crystal Anchor: "
                    + String.format(Locale.US, "%.2fs", elapsed / 20.0));
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

        ensureRoomBuilt(base, index, arenaMap(owner));
        adminBoxVisits.put(admin.getUniqueId(), index);
        int radius = arenaForceFieldRadius(index);
        if (!activeForceFields.contains(index)) {
            removeForceField(roomCenterX(index), 128, ARENA_1_FORCE_FIELD_RADIUS);
            removeForceField(roomCenterX(index), 128, ARENA_3_FORCE_FIELD_RADIUS);
            buildForceField(roomCenterX(index), 128, ARENA_FORCE_FIELD_HEIGHT, radius);
            activeForceFields.add(index);
            forceFieldHeights.put(index, ARENA_FORCE_FIELD_HEIGHT);
            forceFieldRadii.put(index, radius);
        }
        admin.teleport(arenaSpawnLocation(index, 0, 0));
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
        clearPlatform(cx, cz);
        ensureRoomBuilt(base, index, arenaMap(challenger));
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
        Location spawn = arenaSpawnLocation(room, offset, 0);
        spawn.setYaw(offset < 0 ? -90.0f : 90.0f);
        spawn.setPitch(0.0f);
        return spawn;
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

    private void recoverBotAtBarrier(Player owner, int centerX, int centerZ) {
        Player bot = plugin.bots().ownedBot(owner);
        if (bot == null || !bot.isValid() || bot.getWorld() != owner.getWorld()) return;

        int roomIndex = plugin.getConfig().getInt("practice.players." + owner.getUniqueId() + ".room", -1);
        if (roomIndex < 0) return;

        Location location = bot.getLocation();
        double relativeX = location.getX() - (centerX + 0.5);
        double relativeZ = location.getZ() - 128.5;
        double distance = Math.hypot(relativeX, relativeZ);
        boolean touchingBarrier;

        if (arenaMap(roomIndex).equals("ARENA_1")) {
            touchingBarrier = distanceToArenaOneBarrier(relativeX, relativeZ) <= 1.0;
        } else {
            int radius = arenaForceFieldRadius(roomIndex);
            touchingBarrier = distance >= radius - 1.0;
        }
        if (!touchingBarrier) return;

        org.bukkit.util.Vector velocity = bot.getVelocity();
        org.bukkit.util.Vector towardCenter = new org.bukkit.util.Vector(-relativeX, 0.0, -relativeZ);
        if (towardCenter.lengthSquared() < 0.01) return;
        towardCenter.normalize();

        // Only intervene at the actual barrier collision line, and only if the bot is
        // trying to move outward or has become stuck against the invisible blocks.
        double outwardSpeed = velocity.getX() * -towardCenter.getX()
                + velocity.getZ() * -towardCenter.getZ();
        double horizontalSpeedSquared = velocity.getX() * velocity.getX()
                + velocity.getZ() * velocity.getZ();
        if (outwardSpeed < 0.02 && horizontalSpeedSquared > 0.06) return;

        towardCenter.multiply(1.25).setY(velocity.getY());
        bot.setVelocity(towardCenter);
    }

    private double distanceToArenaOneBarrier(double x, double z) {
        double closest = Double.MAX_VALUE;
        for (int i = 0; i < ARENA_1_ICICLE_POINTS.length; i++) {
            int[] start = ARENA_1_ICICLE_POINTS[i];
            int[] end = ARENA_1_ICICLE_POINTS[(i + 1) % ARENA_1_ICICLE_POINTS.length];
            closest = Math.min(closest, distanceToSegment(x, z, start[0], start[1], end[0], end[1]));
        }
        return closest;
    }

    private double distanceToSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared < 0.0001) return Math.hypot(px - ax, pz - az);
        double t = Math.max(0.0, Math.min(1.0,
                ((px - ax) * dx + (pz - az) * dz) / lengthSquared));
        return Math.hypot(px - (ax + t * dx), pz - (az + t * dz));
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
        clearPlatform(cx, cz);
        ensureRoomBuilt(base, index, arenaMap(p));
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

    private Location startLocation(Player player, int height) {
        int index = plugin.getConfig().getInt("practice.players." + player.getUniqueId() + ".room", 0);
        Location spawn = arenaSpawnLocation(index, 0, 0);
        if (height > 0) spawn.add(0, height, 0);
        spawn.setYaw(0.0f);
        spawn.setPitch(height > 0 ? 90.0f : 0.0f);
        return spawn;
    }

    public Location botSpawnLocation(Player owner) {
        int index = plugin.getConfig().getInt("practice.players." + owner.getUniqueId() + ".room", 0);
        return arenaSpawnLocation(index, 0, 0);
    }

    private int highestArenaSurfaceY(int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        while (y > world.getMinHeight()) {
            Material type = world.getBlockAt(x, y, z).getType();
            if (type != Material.BARRIER && type != Material.LIGHT_BLUE_STAINED_GLASS) break;
            y--;
        }
        return y;
    }

    private Location arenaSpawnLocation(int roomIndex, int offsetX, int offsetZ) {
        int targetX = roomCenterX(roomIndex) + offsetX;
        int targetZ = 128 + offsetZ;

        for (int radius = 0; radius <= 16; radius++) {
            int bestX = targetX;
            int bestZ = targetZ;
            int bestY = Integer.MIN_VALUE;
            int bestDistance = Integer.MAX_VALUE;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = targetX + dx;
                    int z = targetZ + dz;
                    int floorY = highestArenaSurfaceY(x, z);
                    if (floorY < world.getMinHeight() || floorY + 2 >= world.getMaxHeight()) continue;

                    Block floor = world.getBlockAt(x, floorY, z);
                    Block feet = world.getBlockAt(x, floorY + 1, z);
                    Block head = world.getBlockAt(x, floorY + 2, z);
                    if (!floor.getType().isSolid() || !feet.isPassable() || !head.isPassable()) continue;

                    int distance = dx * dx + dz * dz;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestX = x;
                        bestZ = z;
                        bestY = floorY;
                    }
                }
            }

            if (bestY != Integer.MIN_VALUE) {
                return new Location(world, bestX + 0.5, bestY + 1.0, bestZ + 0.5);
            }
        }

        int floorY = highestArenaSurfaceY(targetX, targetZ);
        return new Location(world, targetX + 0.5, floorY + 1.0, targetZ + 0.5);
    }

    private void ensureRoomBuilt(String base, int roomIndex, String selectedMap) {
        String builtMap = plugin.getConfig().getString(base + ".built-map", "");
        boolean built = plugin.getConfig().getBoolean(base + ".built", false);
        int builtVersion = plugin.getConfig().getInt(base + ".built-schematic-version", 0);
        if (built && builtVersion == SCHEMATIC_BUILD_VERSION && selectedMap.equals(builtMap)) return;

        // Older versions may have generated fallback geometry instead of pasting Arena 1.
        // Preserve a real previous schematic ID so the loader can clear the complete old footprint.
        String previousMap = built
                ? (builtVersion < SCHEMATIC_BUILD_VERSION && "ARENA_1".equals(builtMap)
                        ? "LEGACY_GENERATED"
                        : ARENA_MAPS.contains(builtMap) ? builtMap : "LEGACY_GENERATED")
                : "";

        if (buildRoom(roomCenterX(roomIndex), 128, selectedMap, previousMap)) {
            plugin.getConfig().set(base + ".built", true);
            plugin.getConfig().set(base + ".built-map", selectedMap);
            plugin.getConfig().set(base + ".built-schematic-version", SCHEMATIC_BUILD_VERSION);
        } else {
            // Keep the old version marker so a later entry retries loading the imported schematic.
            plugin.getConfig().set(base + ".built-schematic-version", 0);
        }
        plugin.saveConfig();
    }

    private boolean buildRoom(int cx, int cz, String map, String previousMap) {
        try {
            SchematicLoader.paste(plugin, world, map, previousMap, cx, 80, cz);
            plugin.getLogger().info("Loaded " + map + " schematic at " + cx + ", 80, " + cz + ".");
            return true;
        } catch (Exception exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not load arena schematic " + map + ". Loading will be retried on next entry.", exception);
            return false;
        }
    }

    private void clearPlatform(int cx, int cz) {
        // Remove only our dedicated forcefield glass. Map glass and other decorations stay intact.
        removeForceField(cx, cz, ARENA_1_FORCE_FIELD_RADIUS);
        removeForceField(cx, cz, ARENA_3_FORCE_FIELD_RADIUS);
        int roomIndex = Math.floorDiv(cx - 128, ROOM_SPACING);
        activeForceFields.remove(roomIndex);
        forceFieldHeights.remove(roomIndex);
        forceFieldRadii.remove(roomIndex);
        arenaFloorCache.remove(roomIndex);
    }

    public String arenaMap(Player player) {
        return normalizeArenaMap(plugin.getConfig().getString(
                "practice.players." + player.getUniqueId() + ".arena-map", "ARENA_1"));
    }

    private String arenaMap(int roomIndex) {
        var players = plugin.getConfig().getConfigurationSection("practice.players");
        if (players != null) {
            for (String uuid : players.getKeys(false)) {
                if (plugin.getConfig().getInt("practice.players." + uuid + ".room", -1) == roomIndex) {
                    return normalizeArenaMap(plugin.getConfig().getString(
                            "practice.players." + uuid + ".arena-map", "ARENA_1"));
                }
            }
        }
        return "ARENA_1";
    }

    private String normalizeArenaMap(String value) {
        String normalized = value == null ? "ARENA_1" : value.toUpperCase(Locale.ROOT);
        return normalized.equals("ARENA_3") ? "ARENA_3" : "ARENA_1";
    }

    public void setArenaMap(Player player, String map) {
        if (!ARENA_MAPS.contains(map)) return;
        plugin.getConfig().set("practice.players." + player.getUniqueId() + ".arena-map", map);
        plugin.saveConfig();
        plugin.message(player, "&aArena selected: &e" + map.replace('_', ' ') + "&a.");
    }

    private int arenaForceFieldRadius(int roomIndex) {
        return arenaMap(roomIndex).equals("ARENA_3")
                ? ARENA_3_FORCE_FIELD_RADIUS : ARENA_1_FORCE_FIELD_RADIUS;
    }

    private int arenaFloorY(int roomIndex) {
        return arenaFloorCache.computeIfAbsent(roomIndex,
                index -> arenaSpawnLocation(index, 0, 0).getBlockY() - 1);
    }

    private void buildForceField(int cx, int cz, int ignoredHeight, int arenaRadius) {
        if (world == null) return;
        int roomIndex = Math.floorDiv(cx - 128, ROOM_SPACING);
        int floorY = arenaFloorY(roomIndex);
        int topY = Math.min(world.getMaxHeight() - 1, floorY + ARENA_FORCE_FIELD_HEIGHT);

        if (arenaMap(roomIndex).equals("ARENA_1")) {
            for (int i = 0; i < ARENA_1_ICICLE_POINTS.length; i++) {
                int[] start = ARENA_1_ICICLE_POINTS[i];
                int[] end = ARENA_1_ICICLE_POINTS[(i + 1) % ARENA_1_ICICLE_POINTS.length];
                int steps = Math.max(1, (int) Math.ceil(Math.hypot(end[0] - start[0], end[1] - start[1]) * 2.0));
                for (int step = 0; step <= steps; step++) {
                    double t = step / (double) steps;
                    int x = cx + (int) Math.round(start[0] + (end[0] - start[0]) * t);
                    int z = cz + (int) Math.round(start[1] + (end[1] - start[1]) * t);
                    for (int y = floorY + 1; y <= topY; y++) {
                        world.getBlockAt(x, y, z).setType(FORCE_FIELD_BLOCK, false);
                    }
                }
            }
            int minX = Arrays.stream(ARENA_1_ICICLE_POINTS).mapToInt(point -> point[0]).min().orElse(-80);
            int maxX = Arrays.stream(ARENA_1_ICICLE_POINTS).mapToInt(point -> point[0]).max().orElse(80);
            int minZ = Arrays.stream(ARENA_1_ICICLE_POINTS).mapToInt(point -> point[1]).min().orElse(-66);
            int maxZ = Arrays.stream(ARENA_1_ICICLE_POINTS).mapToInt(point -> point[1]).max().orElse(66);
            for (int dx = minX; dx <= maxX; dx++) {
                for (int dz = minZ; dz <= maxZ; dz++) {
                    if (insideArenaOneBarrierPolygon(dx, dz)) {
                        world.getBlockAt(cx + dx, topY, cz + dz).setType(FORCE_FIELD_BLOCK, false);
                    }
                }
            }
            return;
        }

        int steps = Math.max(240, arenaRadius * 12);
        for (int i = 0; i < steps; i++) {
            double angle = (Math.PI * 2.0 * i) / steps;
            int x = cx + (int) Math.round(Math.cos(angle) * arenaRadius);
            int z = cz + (int) Math.round(Math.sin(angle) * arenaRadius);
            for (int y = floorY + 1; y <= topY; y++) world.getBlockAt(x, y, z).setType(FORCE_FIELD_BLOCK, false);
        }
        int radiusSquared = arenaRadius * arenaRadius;
        for (int dx = -arenaRadius; dx <= arenaRadius; dx++) for (int dz = -arenaRadius; dz <= arenaRadius; dz++) {
            if (dx * dx + dz * dz <= radiusSquared) world.getBlockAt(cx + dx, topY, cz + dz).setType(FORCE_FIELD_BLOCK, false);
        }
    }

    private boolean insideArenaOneBarrierPolygon(int x, int z) {
        boolean inside = false;
        for (int i = 0, j = ARENA_1_ICICLE_POINTS.length - 1; i < ARENA_1_ICICLE_POINTS.length; j = i++) {
            int xi = ARENA_1_ICICLE_POINTS[i][0], zi = ARENA_1_ICICLE_POINTS[i][1];
            int xj = ARENA_1_ICICLE_POINTS[j][0], zj = ARENA_1_ICICLE_POINTS[j][1];
            boolean crosses = (zi > z) != (zj > z);
            if (crosses && x < (xj - xi) * (z - zi) / (double) (zj - zi) + xi) inside = !inside;
        }
        return inside;
    }

    private void renderNearbyForceField(Player player, int cx, int cz, int arenaRadius) {
        if (world == null || !player.isOnline()) return;
        long now = world.getGameTime();
        long previous = forceFieldRenderTicks.getOrDefault(player.getUniqueId(), Long.MIN_VALUE / 2);
        if (now - previous < 5L) return;
        forceFieldRenderTicks.put(player.getUniqueId(), now);

        double relativeX = player.getLocation().getX() - (cx + 0.5);
        double relativeZ = player.getLocation().getZ() - (cz + 0.5);
        double distance = Math.hypot(relativeX, relativeZ);
        double remaining = arenaRadius - 1.5 - distance;
        double activationDistance = Math.max(2.0,
                plugin.getConfig().getDouble("practice.forcefield.activation-distance", 6.0));
        if (remaining > activationDistance) return;

        int floorY = arenaFloorY(Math.floorDiv(cx - 128, ROOM_SPACING));
        Particle.DustOptions energy = new Particle.DustOptions(Color.fromRGB(65, 205, 255), 1.15f);
        double angle = Math.atan2(relativeZ, relativeX);
        double arcHalfWidth = 8.0 / Math.max(1.0, arenaRadius);
        int particleBottom = Math.max(floorY + 1, player.getLocation().getBlockY() - 2);
        int particleTop = Math.min(floorY + ARENA_FORCE_FIELD_HEIGHT,
                player.getLocation().getBlockY() + 3);
        for (int step = -8; step <= 8; step++) {
            double arcAngle = angle + arcHalfWidth * step / 8.0;
            int x = cx + (int) Math.round(Math.cos(arcAngle) * arenaRadius);
            int z = cz + (int) Math.round(Math.sin(arcAngle) * arenaRadius);
            for (int y = particleBottom; y <= particleTop; y++) {
                world.spawnParticle(Particle.DUST, List.of(player), player,
                        x + 0.5, y + 0.5, z + 0.5, 1, 0, 0, 0, 0, energy);
            }
        }
        world.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.0, 0),
                2, 0.25, 0.35, 0.25, 0.01, player);
    }

    private void removeForceField(int cx, int cz, int arenaRadius) {
        if (world == null) return;
        int roomIndex = Math.floorDiv(cx - 128, ROOM_SPACING);
        int floorY = arenaFloorY(roomIndex);
        int topY = Math.min(world.getMaxHeight() - 1, floorY + ARENA_FORCE_FIELD_HEIGHT);
        int steps = Math.max(240, arenaRadius * 12);
        for (int i = 0; i < steps; i++) {
            double angle = (Math.PI * 2.0 * i) / steps;
            int x = cx + (int) Math.round(Math.cos(angle) * arenaRadius);
            int z = cz + (int) Math.round(Math.sin(angle) * arenaRadius);
            for (int y = floorY + 1; y <= topY; y++) {
                Block block = world.getBlockAt(x, y, z);
                if (block.getType() == Material.BARRIER || block.getType() == Material.LIGHT_BLUE_STAINED_GLASS) block.setType(Material.AIR, false);
            }
        }

        // Also clear the custom icicle-to-icicle contour, regardless of which arena
        // is selected now; this removes it when switching from Arena 1 to Arena 3.
        for (int i = 0; i < ARENA_1_ICICLE_POINTS.length; i++) {
            int[] start = ARENA_1_ICICLE_POINTS[i];
            int[] end = ARENA_1_ICICLE_POINTS[(i + 1) % ARENA_1_ICICLE_POINTS.length];
            int segmentSteps = Math.max(1, (int) Math.ceil(Math.hypot(end[0] - start[0], end[1] - start[1]) * 2.0));
            for (int step = 0; step <= segmentSteps; step++) {
                double t = step / (double) segmentSteps;
                int x = cx + (int) Math.round(start[0] + (end[0] - start[0]) * t);
                int z = cz + (int) Math.round(start[1] + (end[1] - start[1]) * t);
                for (int y = floorY + 1; y <= topY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.BARRIER) block.setType(Material.AIR, false);
                }
            }
        }
        int minX = Arrays.stream(ARENA_1_ICICLE_POINTS).mapToInt(point -> point[0]).min().orElse(-80);
        int maxX = Arrays.stream(ARENA_1_ICICLE_POINTS).mapToInt(point -> point[0]).max().orElse(80);
        int minZ = Arrays.stream(ARENA_1_ICICLE_POINTS).mapToInt(point -> point[1]).min().orElse(-66);
        int maxZ = Arrays.stream(ARENA_1_ICICLE_POINTS).mapToInt(point -> point[1]).max().orElse(66);
        for (int dx = minX; dx <= maxX; dx++) for (int dz = minZ; dz <= maxZ; dz++) {
            if (!insideArenaOneBarrierPolygon(dx, dz)) continue;
            Block block = world.getBlockAt(cx + dx, topY, cz + dz);
            if (block.getType() == Material.BARRIER) block.setType(Material.AIR, false);
        }

        // Remove any old 12-block blue-glass circular wall from earlier plugin versions.
        int legacyTop = Math.min(world.getMaxHeight() - 1, floorY + LEGACY_GLASS_HEIGHT);
        for (int dx = -arenaRadius - 2; dx <= arenaRadius + 2; dx++) {
            for (int dz = -arenaRadius - 2; dz <= arenaRadius + 2; dz++) {
                if (Math.abs(Math.hypot(dx, dz) - arenaRadius) > 1.75) continue;
                for (int y = floorY + 1; y <= legacyTop; y++) {
                    Block block = world.getBlockAt(cx + dx, y, cz + dz);
                    if (block.getType() == Material.LIGHT_BLUE_STAINED_GLASS) block.setType(Material.AIR, false);
                }
            }
        }

        int radiusSquared = arenaRadius * arenaRadius;
        for (int dx = -arenaRadius; dx <= arenaRadius; dx++) for (int dz = -arenaRadius; dz <= arenaRadius; dz++) {
            if (dx * dx + dz * dz > radiusSquared) continue;
            Block block = world.getBlockAt(cx + dx, topY, cz + dz);
            if (block.getType() == Material.BARRIER) block.setType(Material.AIR, false);
        }
    }

    private void retry(Player p, boolean success) {
        UUID id = p.getUniqueId();
        if (!resetting.add(id)) return;
        activeAttempts.remove(id);
        if (success) {
            if (world != null) lastBotComboScoreTicks.put(id, world.getGameTime());
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
            boolean hitThisSwing = lastHitTick != null && lastHitTick >= swingTick - 2L;
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

        UUID id = player.getUniqueId();
        Mode hitMode = mode(player);
        Material weapon = player.getInventory().getItemInMainHand().getType();
        if (hitMode == Mode.SWORD && weapon.name().endsWith("_SWORD")) {
            pendingBotHitTicks.put(id, world.getGameTime());
            pendingBotHitModes.put(id, Mode.SWORD);
        } else if (hitMode == Mode.STUN_SLAM && weapon == Material.MACE) {
            pendingBotHitTicks.put(id, world.getGameTime());
            pendingBotHitModes.put(id, Mode.STUN_SLAM);
        } else {
            pendingBotHitTicks.remove(id);
            pendingBotHitModes.remove(id);
        }

        // A totem can resurrect the bot even when the damage event reports zero final damage.
        // Keep the attempted weapon/tick above so the resurrection event can safely award it once.
        if (event.isCancelled() || event.getFinalDamage() <= 0.0) {
            long attemptedTick = world.getGameTime();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (Objects.equals(pendingBotHitTicks.get(id), attemptedTick)) {
                    pendingBotHitTicks.remove(id);
                    pendingBotHitModes.remove(id);
                }
            }, 3L);
            return;
        }
        pendingBotHitTicks.remove(id);
        pendingBotHitModes.remove(id);
        if (hitMode == Mode.SWORD) {
            if (!weapon.name().endsWith("_SWORD")) return;
            swordHits.merge(id, 1, Integer::sum);
            swordHitSwingSequence.put(id, swordSwingSequence.getOrDefault(id, 0));
            long now = world.getGameTime();
            Long previousHit = lastSwordHitTicks.get(id);
            int nextCombo = previousHit == null || now - previousHit > 200L ? 1 : swordCombos.getOrDefault(id, 0) + 1;
            swordCombos.put(id, nextCombo);
            lastSwordHitTicks.put(id, now);
            lastBotComboScoreTicks.put(id, now);
            scheduleSwordComboReset(player, id, now);
            updateSwordAccuracy(player);
            updatePracticeBoard(player);
            return;
        }

        if (!activeAttempts.contains(id) || weapon != Material.MACE) return;
        retry(player, true);
    }

    private void scheduleSwordComboReset(Player player, UUID id, long hitTick) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Long last = lastSwordHitTicks.get(id);
            if (last != null && last == hitTick && world.getGameTime() - last >= 200L) resetSwordCombo(player);
        }, 200L);
    }

    public void onPracticeBotTotemPop(Player owner) {
        if (world == null || !owner.isOnline() || !owner.getWorld().equals(world) || isFighting(owner)) return;
        UUID id = owner.getUniqueId();
        Long hitTick = pendingBotHitTicks.get(id);
        Mode hitMode = pendingBotHitModes.get(id);
        if (hitTick == null || hitMode == null) return;

        long now = world.getGameTime();
        if (now < hitTick || now - hitTick > 2L) {
            pendingBotHitTicks.remove(id);
            pendingBotHitModes.remove(id);
            return;
        }
        pendingBotHitTicks.remove(id);
        pendingBotHitModes.remove(id);
        lastBotComboScoreTicks.put(id, now);

        if (hitMode == Mode.STUN_SLAM && mode(owner) == Mode.STUN_SLAM
                && activeAttempts.contains(id)) {
            retry(owner, true);
            return;
        }

        if (hitMode == Mode.SWORD && mode(owner) == Mode.SWORD) {
            swordHits.merge(id, 1, Integer::sum);
            swordHitSwingSequence.put(id, swordSwingSequence.getOrDefault(id, 0));
            Long previousHit = lastSwordHitTicks.get(id);
            int nextCombo = previousHit == null || now - previousHit > 200L
                    ? 1 : swordCombos.getOrDefault(id, 0) + 1;
            swordCombos.put(id, nextCombo);
            lastSwordHitTicks.put(id, now);
            scheduleSwordComboReset(owner, id, now);
            updateSwordAccuracy(owner);
            updatePracticeBoard(owner);
        }
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
            int arenaRadius = arenaForceFieldRadius(roomIndex);
            Location to = event.getTo();
            double relativeX = to.getX() - (cx + 0.5);
            double relativeZ = to.getZ() - (cz + 0.5);

            renderNearbyForceField(p, cx, cz, arenaRadius);

            // Physical barrier blocks the outer edge; movement inside the arena stays unrestricted.
        }

        if (!activeAttempts.contains(p.getUniqueId())) return;
        if (event.getTo().getY() >= event.getFrom().getY()) return;
        int attemptRoom = plugin.getConfig().getInt("practice.players." + p.getUniqueId() + ".room", -1);
        if (attemptRoom < 0) return;
        double arenaFloorSpawnY = arenaFloorY(attemptRoom) + 1.0;
        if (event.getTo().getY() <= arenaFloorSpawnY) retry(p, false);
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
        if (!isFighting(player) && mode(player) == Mode.CRYSTAL_ANCHOR) {
            Material placed = event.getBlockPlaced().getType();
            org.bukkit.inventory.EquipmentSlot hand = event.getHand();
            if (placed == Material.RESPAWN_ANCHOR) {
                UUID id = player.getUniqueId();
                crystalAnchorStartTicks.put(id, world.getGameTime());
                crystalAnchorLocations.put(id, event.getBlockPlaced().getLocation());
                refillCrystalPracticeItem(player, hand, Material.RESPAWN_ANCHOR);
                player.sendActionBar(ChatColor.GOLD + "Anchor placed! " + ChatColor.GRAY + "Timer started.");
                updatePracticeBoard(player);
                return;
            }
            if (placed == Material.GLOWSTONE) {
                refillCrystalPracticeItem(player, hand, Material.GLOWSTONE);
                return;
            }
        }
        event.setCancelled(true);
    }

    private void refillCrystalPracticeItem(Player player, org.bukkit.inventory.EquipmentSlot hand, Material type) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || mode(player) != Mode.CRYSTAL_ANCHOR || isFighting(player)) return;
            ItemStack held = hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND
                    ? player.getInventory().getItemInOffHand()
                    : player.getInventory().getItemInMainHand();
            // Each training stack starts at 64, so the placed block leaves a matching stack to refill.
            if (held == null || held.getType() != type) return;
            if (held.getAmount() < 64) held.setAmount(64);
        });
    }

    @EventHandler
    public void onAnchorExplode(BlockExplodeEvent event) {
        if (world == null || !event.getBlock().getWorld().equals(world)) return;
        // Respawn anchors are practice equipment, never loot.
        event.setYield(0.0f);
        Location exploded = event.getBlock().getLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            Location tracked = crystalAnchorLocations.get(id);
            Long started = crystalAnchorStartTicks.get(id);
            if (tracked == null || started == null
                    || !tracked.getWorld().equals(exploded.getWorld())
                    || tracked.getBlockX() != exploded.getBlockX()
                    || tracked.getBlockY() != exploded.getBlockY()
                    || tracked.getBlockZ() != exploded.getBlockZ()
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
        pendingBotHitTicks.remove(id); pendingBotHitModes.remove(id); lastBotComboScoreTicks.remove(id);
        crystalAnchorStartTicks.remove(id); crystalAnchorLocations.remove(id);
        swordSwingSequence.remove(id); swordHitSwingSequence.remove(id);
        selectedHeights.remove(id); swordSwings.remove(id); swordHits.remove(id);
        activeAttempts.remove(id); resetting.remove(id);
        forceFieldRenderTicks.remove(id);
    }

    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        activeAttempts.remove(id); resetting.remove(id);
        forceFieldRenderTicks.remove(id);
        if (isFighting(event.getPlayer()) && !event.getPlayer().getWorld().equals(world)) {
            endFight(event.getPlayer(), "&cA player left the arena.");
        }
        if (!event.getPlayer().getWorld().equals(world)) {
            adminBoxVisits.remove(id);
            pendingBotHitTicks.remove(id); pendingBotHitModes.remove(id); lastBotComboScoreTicks.remove(id);
            restorePracticeBoard(event.getPlayer());
            restorePlayerVisibility(event.getPlayer());
        } else Bukkit.getScheduler().runTask(plugin, () -> updatePlayerVisibility(event.getPlayer()));
    }

    public void applyKit(Player p) {
        p.getInventory().clear();
        if (mode(p) == Mode.CRYSTAL_ANCHOR) {
            p.getInventory().setArmorContents(crystalAnchorArmor());
            p.getInventory().setItem(0, new ItemStack(Material.RESPAWN_ANCHOR, 64));
            p.getInventory().setItem(1, new ItemStack(Material.GLOWSTONE, 64));
        } else {
            p.getInventory().setArmorContents(new ItemStack[]{gear(Material.DIAMOND_BOOTS),gear(Material.DIAMOND_LEGGINGS),gear(Material.DIAMOND_CHESTPLATE),gear(Material.DIAMOND_HELMET)});
        }
        if (mode(p) == Mode.SWORD) p.getInventory().setItem(0, gear(Material.DIAMOND_SWORD));
        else if (mode(p) != Mode.CRYSTAL_ANCHOR) {
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
        applyKitToInventory(player, mode(player), editor);
    }

    public void applyKitToInventory(Player player, Mode selectedMode, Inventory editor) {
        editor.clear();
        if (selectedMode == Mode.CRYSTAL_ANCHOR) {
            editor.setItem(0, new ItemStack(Material.RESPAWN_ANCHOR, 64));
            editor.setItem(1, new ItemStack(Material.GLOWSTONE, 64));
        } else if (selectedMode == Mode.SWORD) editor.setItem(0, gear(Material.DIAMOND_SWORD));
        else {
            editor.setItem(0, enchanted(Material.NETHERITE_AXE, "sharpness", 5));
            ItemStack mace = enchanted(Material.MACE, "density", 5); enchant(mace, "wind_burst", 3);
            editor.setItem(1, mace);
            editor.setItem(2, enchanted(spearMaterial(), "lunge", 3));
        }
        if (selectedMode == Mode.CRYSTAL_ANCHOR) {
            ItemStack[] armor = crystalAnchorArmor();
            editor.setItem(45, armor[3]);
            editor.setItem(46, armor[2]);
            editor.setItem(47, armor[1]);
            editor.setItem(48, armor[0]);
        } else {
            editor.setItem(45, gear(Material.DIAMOND_HELMET));
            editor.setItem(46, gear(Material.DIAMOND_CHESTPLATE));
            editor.setItem(47, gear(Material.DIAMOND_LEGGINGS));
            editor.setItem(48, gear(Material.DIAMOND_BOOTS));
        }
        String path = "practice.players." + player.getUniqueId() + ".kits." + selectedMode.name();
        if (!plugin.getConfig().contains(path + ".contents")) return;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = plugin.getConfig().getItemStack(path + ".contents." + slot);
            editor.setItem(slot, item == null ? null : item.clone());
        }
        editor.setItem(45, plugin.getConfig().getItemStack(path + ".helmet"));
        editor.setItem(46, plugin.getConfig().getItemStack(path + ".chestplate"));
        editor.setItem(47, plugin.getConfig().getItemStack(path + ".leggings"));
        editor.setItem(48, plugin.getConfig().getItemStack(path + ".boots"));
        if (selectedMode == Mode.STUN_SLAM) migrateLegacyStunSlamSwordKit(editor);
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
        Mode selectedMode = mode(player);
        String path = "practice.players." + player.getUniqueId() + ".kits." + selectedMode.name();
        if (!plugin.getConfig().contains(path + ".contents")) return;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = plugin.getConfig().getItemStack(path + ".contents." + slot);
            inventory.setItem(slot, item == null ? null : item.clone());
        }
        inventory.setHelmet(plugin.getConfig().getItemStack(path + ".helmet"));
        inventory.setChestplate(plugin.getConfig().getItemStack(path + ".chestplate"));
        inventory.setLeggings(plugin.getConfig().getItemStack(path + ".leggings"));
        inventory.setBoots(plugin.getConfig().getItemStack(path + ".boots"));
        if (selectedMode == Mode.STUN_SLAM) migrateLegacyStunSlamSwordKit(inventory);
    }

    private void migrateLegacyStunSlamSwordKit(Inventory inventory) {
        ItemStack first = inventory.getItem(0);
        ItemStack second = inventory.getItem(1);
        ItemStack third = inventory.getItem(2);
        if (first == null || first.getType() != Material.DIAMOND_SWORD
                || (second != null && !second.getType().isAir())
                || (third != null && !third.getType().isAir())) return;

        // The old kit-editor bug stored the currently selected Sword kit under STUN_SLAM.
        // Repair only that unmistakable one-sword pattern, preserving all other custom kits.
        inventory.setItem(0, enchanted(Material.NETHERITE_AXE, "sharpness", 5));
        ItemStack mace = enchanted(Material.MACE, "density", 5);
        enchant(mace, "wind_burst", 3);
        inventory.setItem(1, mace);
        inventory.setItem(2, enchanted(spearMaterial(), "lunge", 3));
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

    private ItemStack[] crystalAnchorArmor() {
        return new ItemStack[]{
                anchorArmor(Material.NETHERITE_BOOTS, true),
                anchorArmor(Material.NETHERITE_LEGGINGS, true),
                anchorArmor(Material.NETHERITE_CHESTPLATE, false),
                anchorArmor(Material.NETHERITE_HELMET, false)
        };
    }

    private ItemStack anchorArmor(Material material, boolean blastProtection) {
        ItemStack item = gear(material);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.PROTECTION, 4, true);
        if (blastProtection) meta.addEnchant(Enchantment.BLAST_PROTECTION, 4, true);
        item.setItemMeta(meta);
        return item;
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
