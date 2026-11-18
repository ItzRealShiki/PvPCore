package de.leon.pvpcore;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;

import java.util.*;

public final class BotManager implements Listener {
    private final PvPCore plugin;
    private final Map<UUID, Set<UUID>> owned = new HashMap<>();
    public BotManager(PvPCore plugin) { this.plugin = plugin; }

    public void spawn(Player owner, String style) {
        int max = Math.max(1, plugin.getConfig().getInt("bots.max-per-player", 2));
        Set<UUID> ids = owned.computeIfAbsent(owner.getUniqueId(), k -> new HashSet<>());
        ids.removeIf(id -> Bukkit.getEntity(id) == null);
        if (ids.size() >= max) { plugin.message(owner, "&cYou have reached your training-bot limit."); return; }
        Location at = owner.getLocation().add(owner.getLocation().getDirection().multiply(2.5));
        at.setY(owner.getLocation().getY());
        Zombie bot = owner.getWorld().spawn(at, Zombie.class, z -> {
            z.setCustomName(plugin.color("&c" + style + " &7[&eTRAINING&7]"));
            z.setCustomNameVisible(true);
            z.setCanPickupItems(false);
            z.setRemoveWhenFarAway(false);
            z.setPersistent(true);
            z.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40.0);
            z.setHealth(40.0);
            z.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.24);
            EntityEquipment eq = z.getEquipment();
            if (eq != null) {
                eq.setHelmet(new ItemStack(Material.IRON_HELMET));
                eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                eq.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
                eq.setBoots(new ItemStack(Material.IRON_BOOTS));
                eq.setItemInMainHand(new ItemStack(style.equals("CRYSTAL") ? Material.END_CRYSTAL : Material.IRON_SWORD));
            }
            z.setTarget(owner);
        });
        ids.add(bot.getUniqueId());
        plugin.message(owner, "&aSpawned a &f" + style + " &atraining opponent.");
    }
    public void removeOwned(Player owner) {
        Set<UUID> ids = owned.remove(owner.getUniqueId());
        if (ids != null) ids.forEach(id -> { Entity e = Bukkit.getEntity(id); if (e != null) e.remove(); });
    }
    public void removeAll() {
        owned.values().forEach(ids -> ids.forEach(id -> { Entity e = Bukkit.getEntity(id); if (e != null) e.remove(); }));
        owned.clear();
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        if (plugin.getConfig().getBoolean("bots.remove-on-quit", true)) removeOwned(event.getPlayer());
    }
}