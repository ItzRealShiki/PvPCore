package de.leon.pvpcore;

import org.bukkit.*;
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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class PracticeManager implements Listener {
    public enum Mode { STUN_SLAM, SWORD }

    private final PvPCore plugin;
    private World world;
    private final Map<UUID, Integer> scores = new HashMap<>();
    private final Map<UUID, Integer> selectedHeights = new HashMap<>();
    private final Map<UUID, Integer> swordSwings = new HashMap<>();
    private final Map<UUID, Integer> swordHits = new HashMap<>();
    private final Set<UUID> activeAttempts = new HashSet<>();
    private final Set<UUID> resetting = new HashSet<>();
    private final Set<Integer> activeForceFields = new HashSet<>();

    public PracticeManager(PvPCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateNearbyArenas, 1L, 10L);
    }

    private void updateNearbyArenas() {
        Set<Integer> nearbyRooms = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().getName().equals("prac_rooms")) continue;
            int index = plugin.getConfig().getInt("practice.players." + player.getUniqueId() + ".room", -1);
            if (index < 0) continue;
            int cx = 128 + index * 96, cz = 128;
            boolean nearby = player.getLocation().distanceSquared(new Location(world == null ? player.getWorld() : world, cx + 0.5, player.getLocation().getY(), cz + 0.5)) <= 36.0 * 36.0;
            if (!nearby) {
                plugin.bots().removeOwned(player);
                continue;
            }
            nearbyRooms.add(index);
            if (activeForceFields.add(index)) buildForceField(cx, cz);
            if (plugin.bots().isAvailable() && !plugin.bots().hasOwnedBot(player)) plugin.bots().spawn(player);
        }
        for (int index : new HashSet<>(activeForceFields)) {
            if (nearbyRooms.contains(index)) continue;
            removeForceField(128 + index * 96, 128);
            activeForceFields.remove(index);
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
                plugin.getConfig().getInt("practice.players." + p.getUniqueId() + ".stun-slam-height", 7));
    }

    public void setSelectedHeight(Player p, int height) {
        if (height != 7 && height != 20 && height != 30) return;
        selectedHeights.put(p.getUniqueId(), height);
        plugin.getConfig().set("practice.players." + p.getUniqueId() + ".stun-slam-height", height);
        plugin.saveConfig();
    }

    public int score(Player p) { return scores.getOrDefault(p.getUniqueId(), 0); }

    public void enforceStunSlamFood() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().getName().equals("prac_rooms") || mode(player) != Mode.STUN_SLAM) continue;
            if (player.getFoodLevel() != 20) player.setFoodLevel(20);
            if (player.getSaturation() < 20.0f) player.setSaturation(20.0f);
            if (player.getExhaustion() != 0.0f) player.setExhaustion(0.0f);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.getWorld().getName().equals("prac_rooms") && mode(player) == Mode.STUN_SLAM) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.setExhaustion(0.0f);
        }
    }

    public void enter(Player p) { enter(p, selectedHeight(p)); }

    public void enter(Player p, int height) {
        int challengeHeight = mode(p) == Mode.STUN_SLAM ? height : 0;
        if (challengeHeight > 0) setSelectedHeight(p, challengeHeight);
        if (world == null) {
            world = Bukkit.getWorld("prac_rooms");
            if (world == null) world = new WorldCreator("prac_rooms").type(WorldType.FLAT).createWorld();
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

        int cx = 128 + index * 96, cz = 128;
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
        applyKit(p);
        scores.putIfAbsent(p.getUniqueId(), 0);
        if (mode(p) == Mode.SWORD) {
            swordSwings.put(p.getUniqueId(), 0);
            swordHits.put(p.getUniqueId(), 0);
            updateSwordAccuracy(p);
        }
        if (challengeHeight > 0) activeAttempts.add(p.getUniqueId());

remove immediate bot spawn        if (challengeHeight > 0) {
            plugin.message(p, "&aStun Slam &7- &e" + difficultyName(challengeHeight) + " &7(" + challengeHeight + " blocks).");
            plugin.message(p, "&7Score: &a" + score(p) + " &8| &7Land a hit on the bot to earn a point.");
        } else plugin.message(p, "&aEntered your private Sword practice room.");
    }

    private String difficultyName(int height) {
        return switch (height) { case 7 -> "Easy"; case 20 -> "Normal"; default -> "Hard"; };
    }

    private Location startLocation(Player p, int height) {
        int index = plugin.getConfig().getInt("practice.players." + p.getUniqueId() + ".room", 0);
        int cx = 128 + index * 96, cz = 128;
        int spawnHeight = height > 0 ? height : 20;
        return new Location(world, cx + 0.5, 81 + spawnHeight, cz + 0.5);
    }

    public Location botSpawnLocation(Player owner) {
        int index = plugin.getConfig().getInt("practice.players." + owner.getUniqueId() + ".room", 0);
        int cx = 128 + index * 96, cz = 128;
        return new Location(world, cx + 0.5, 81, cz + 5.5);
    }

    private void buildRoom(int cx, int cz) {
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) {
            // No launch platform: the arena floor stays empty so players fall straight down.
            world.getBlockAt(cx + x, 80, cz + z).setType(Material.AIR, false);
        }
    }

    private void clearPlatform(int cx, int cz) {
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) {
            world.getBlockAt(cx + x, 80, cz + z).setType(Material.AIR, false);
        }
    }

    private void buildForceField(int cx, int cz) {
        if (world == null) return;
        for (int offset = -24; offset <= 24; offset++) {
            for (int y = 81; y <= 110; y++) {
                world.getBlockAt(cx - 24, y, cz + offset).setType(Material.GLASS, false);
                world.getBlockAt(cx + 24, y, cz + offset).setType(Material.GLASS, false);
                world.getBlockAt(cx + offset, y, cz - 24).setType(Material.GLASS, false);
                world.getBlockAt(cx + offset, y, cz + 24).setType(Material.GLASS, false);
            }
        }
    }

    private void removeForceField(int cx, int cz) {
        if (world == null) return;
        for (int offset = -24; offset <= 24; offset++) {
            for (int y = 81; y <= 110; y++) {
                world.getBlockAt(cx - 24, y, cz + offset).setType(Material.AIR, false);
                world.getBlockAt(cx + 24, y, cz + offset).setType(Material.AIR, false);
                world.getBlockAt(cx + offset, y, cz - 24).setType(Material.AIR, false);
                world.getBlockAt(cx + offset, y, cz + 24).setType(Material.AIR, false);
            }
        }
    }

    private void retry(Player p, boolean success) {
        UUID id = p.getUniqueId();
        if (!resetting.add(id)) return;
        activeAttempts.remove(id);
        if (success) {
            scores.merge(id, 1, Integer::sum);
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
                || !player.getWorld().getName().equals("prac_rooms")
                || mode(player) != Mode.SWORD) return;
        swordSwings.merge(player.getUniqueId(), 1, Integer::sum);
        updateSwordAccuracy(player);
    }

    @EventHandler
    public void onBotHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!player.getWorld().getName().equals("prac_rooms")) return;
        if (!plugin.bots().isOwnedBot(player, event.getEntity())) return;

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
        player.sendActionBar(ChatColor.GREEN + "Accuracy: " + ChatColor.YELLOW + accuracy + "%"
                + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Hits: "
                + ChatColor.GREEN + hits + ChatColor.GRAY + "/" + swings);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!p.getWorld().getName().equals("prac_rooms") || event.getTo() == null) return;

        int roomIndex = plugin.getConfig().getInt("practice.players." + p.getUniqueId() + ".room", -1);
        if (roomIndex >= 0) {
            int cx = 128 + roomIndex * 96;
            int cz = 128;
            Location to = event.getTo();
            // The glass wall is at +/-24; teleport anyone who bypasses it back inside.
            if (to.getX() < cx - 23.5 || to.getX() > cx + 23.5
                    || to.getZ() < cz - 23.5 || to.getZ() > cz + 23.5) {
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
        if (!(event.getEntity() instanceof Player p) || !p.getWorld().getName().equals("prac_rooms")) return;
        // Practice players are invulnerable; falling still ends an attempt through onMove().
        event.setCancelled(true);
    }

    @EventHandler public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getWorld().getName().equals("prac_rooms")) event.setCancelled(true);
    }

    @EventHandler public void onPlace(BlockPlaceEvent event) {
        if (event.getBlock().getWorld().getName().equals("prac_rooms")) event.setCancelled(true);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        scores.remove(id); selectedHeights.remove(id); swordSwings.remove(id); swordHits.remove(id);
        activeAttempts.remove(id); resetting.remove(id);
    }

    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        activeAttempts.remove(id); resetting.remove(id);
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
        if (spear == null) {
            throw new IllegalStateException("NETHERITE_SPEAR is not available in the configured Paper API.");
        }
        return spear;
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
