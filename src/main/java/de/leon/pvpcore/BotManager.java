package de.leon.pvpcore;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.*;

public final class BotManager implements Listener {
    private static final long SHIELD_COOLDOWN_MS = 100L;

    private record BotSettings(boolean shield, boolean infiniteTotem) {}

    private final PvPCore plugin;
    private final Map<UUID, Set<UUID>> owned = new HashMap<>();
    private final Map<UUID, BotSettings> settings = new HashMap<>();
    private final Map<UUID, Long> shieldReadyAt = new HashMap<>();

    public BotManager(PvPCore plugin) {
        this.plugin = plugin;
    }

    public void spawn(Player owner, boolean shield, boolean infiniteTotem) {
        int max = Math.max(1, plugin.getConfig().getInt("bots.max-per-player", 2));
        Set<UUID> ids = owned.computeIfAbsent(owner.getUniqueId(), key -> new HashSet<>());
        ids.removeIf(id -> Bukkit.getEntity(id) == null);
        if (ids.size() >= max) {
            plugin.message(owner, "&cYou have reached your training-bot limit.");
            return;
        }

        Location at = owner.getLocation().clone().add(owner.getLocation().getDirection().multiply(2.5));
        at.setY(owner.getLocation().getY());
        at.setDirection(owner.getLocation().toVector().subtract(at.toVector()));

        Zombie bot = owner.getWorld().spawn(at, Zombie.class, zombie -> {
            zombie.setCustomName(plugin.color("&eTRAINING NPC"));
            zombie.setCustomNameVisible(true);
            zombie.setCanPickupItems(false);
            zombie.setRemoveWhenFarAway(false);
            zombie.setPersistent(true);
            zombie.setAI(false);
            zombie.setGravity(false);
            zombie.setVelocity(new Vector(0, 0, 0));
            zombie.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40.0);
            zombie.setHealth(40.0);
            zombie.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.0);
            if (zombie.getAttribute(Attribute.KNOCKBACK_RESISTANCE) != null) {
                zombie.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
            }

            EntityEquipment equipment = zombie.getEquipment();
            if (equipment != null) {
                equipment.setHelmet(new ItemStack(Material.IRON_HELMET));
                equipment.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                equipment.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
                equipment.setBoots(new ItemStack(Material.IRON_BOOTS));
                equipment.setItemInMainHand(new ItemStack(Material.TOTEM_OF_UNDYING));
                if (shield) equipment.setItemInOffHand(new ItemStack(Material.SHIELD));
            }
        });

        UUID botId = bot.getUniqueId();
        ids.add(botId);
        settings.put(botId, new BotSettings(shield, infiniteTotem));
        plugin.message(owner, "&aTraining NPC spawned. &7Shield: &f" + (shield ? "ON" : "OFF")
                + "&7, Inf. Totem: &f" + (infiniteTotem ? "ON" : "OFF"));
    }

    @EventHandler
    public void onBotDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Zombie bot)) return;
        BotSettings botSettings = settings.get(bot.getUniqueId());
        if (botSettings == null) return;

        // Keep the dummy fixed in place even when it is attacked.
        bot.setVelocity(new Vector(0, 0, 0));

        if (botSettings.infiniteTotem() && event.getFinalDamage() >= bot.getHealth()) {
            event.setCancelled(true);
            bot.setHealth(1.0);
            bot.getWorld().playSound(bot.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
            bot.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                    bot.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.1);
            return;
        }

        if (!botSettings.shield() || !(event instanceof EntityDamageByEntityEvent damageEvent)
                || !(damageEvent.getDamager() instanceof Player attacker)) {
            return;
        }

        Vector towardAttacker = attacker.getLocation().toVector().subtract(bot.getLocation().toVector());
        towardAttacker.setY(0);
        Vector facing = bot.getLocation().getDirection().setY(0);
        if (towardAttacker.lengthSquared() == 0 || facing.lengthSquared() == 0
                || facing.normalize().dot(towardAttacker.normalize()) < 0.15) {
            return;
        }

        long now = System.currentTimeMillis();
        long readyAt = shieldReadyAt.getOrDefault(bot.getUniqueId(), 0L);
        if (now < readyAt) return;

        event.setCancelled(true);
        shieldReadyAt.put(bot.getUniqueId(), now + SHIELD_COOLDOWN_MS);
        bot.getWorld().playSound(bot.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
        bot.getWorld().spawnParticle(Particle.CRIT, bot.getLocation().add(0, 1, 0),
                8, 0.35, 0.45, 0.35, 0.05);
    }

    @EventHandler
    public void onBotDeath(EntityDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        settings.remove(id);
        shieldReadyAt.remove(id);
        for (Set<UUID> ids : owned.values()) ids.remove(id);
    }

    public void removeOwned(Player owner) {
        Set<UUID> ids = owned.remove(owner.getUniqueId());
        if (ids == null) return;
        for (UUID id : ids) removeBot(id);
    }

    public void removeAll() {
        for (Set<UUID> ids : owned.values()) {
            for (UUID id : ids) removeBot(id);
        }
        owned.clear();
    }

    private void removeBot(UUID id) {
        Entity entity = Bukkit.getEntity(id);
        if (entity != null) entity.remove();
        settings.remove(id);
        shieldReadyAt.remove(id);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getConfig().getBoolean("bots.remove-on-quit", true)) {
            removeOwned(event.getPlayer());
        }
    }
}
