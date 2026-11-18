package de.leon.pvpcore;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BotManager implements Listener {
    private static final double OWNER_RADIUS_SQUARED = 200.0 * 200.0;

    private final PvPCore plugin;
    private final Map<UUID, Set<Mannequin>> owned = new HashMap<>();
    private final Map<UUID, Long> shieldCooldowns = new HashMap<>();

    public BotManager(PvPCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::removeOutOfRangeBots, 20L, 20L);
    }

    public void spawn(Player owner) {
        removeOwned(owner);
        Location at = owner.getLocation().clone()
                .add(owner.getLocation().getDirection().normalize().multiply(2.5));
        at.setY(owner.getLocation().getY());
        at.setDirection(owner.getLocation().toVector().subtract(at.toVector()));

        Entity spawned = owner.getWorld().spawnEntity(at, EntityType.MANNEQUIN);
        if (!(spawned instanceof Mannequin mannequin)) {
            spawned.remove();
            plugin.message(owner, "&cCould not create the player mannequin.");
            return;
        }

        mannequin.setCustomName(plugin.color("&e" + owner.getName()));
        mannequin.setCustomNameVisible(true);
        mannequin.setInvulnerable(false);
        mannequin.setGravity(false);
        mannequin.setImmovable(!option(owner, "knockback", true));
        mannequin.setPersistent(false);
        mannequin.setProfile(ResolvableProfile.resolvableProfile(Bukkit.createProfile(owner.getUniqueId())));

        EntityEquipment equipment = mannequin.getEquipment();
        equipment.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        equipment.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        equipment.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        equipment.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
        applyOptions(owner, mannequin);

        owned.computeIfAbsent(owner.getUniqueId(), key -> new HashSet<>()).add(mannequin);
        plugin.message(owner, "&aYour player mannequin has spawned. It disappears when you log out, change worlds, or move more than 200 blocks away.");
    }


    private boolean option(Player owner, String key, boolean defaultValue) {
        return plugin.getConfig().getBoolean("bots.options." + owner.getUniqueId() + "." + key, defaultValue);
    }

    private void applyOptions(Player owner, Mannequin mannequin) {
        EntityEquipment equipment = mannequin.getEquipment();
        equipment.setItemInMainHand(option(owner, "infinite-totem", true)
                ? new ItemStack(Material.TOTEM_OF_UNDYING) : new ItemStack(Material.AIR));
        if (option(owner, "shield", false)) {
            ItemStack shield = new ItemStack(Material.SHIELD);
            ItemMeta meta = shield.getItemMeta();
            meta.setUnbreakable(true);
            shield.setItemMeta(meta);
            equipment.setItemInOffHand(shield);
        } else {
            equipment.setItemInOffHand(new ItemStack(Material.AIR));
        }
        mannequin.setImmovable(!option(owner, "knockback", true));
    }

    public void refreshOwned(Player owner) {
        for (Mannequin mannequin : owned.getOrDefault(owner.getUniqueId(), Set.of())) {
            if (mannequin.isValid()) applyOptions(owner, mannequin);
        }
    }

    private UUID ownerOf(Mannequin mannequin) {
        for (Map.Entry<UUID, Set<Mannequin>> entry : owned.entrySet()) {
            if (entry.getValue().contains(mannequin)) return entry.getKey();
        }
        return null;
    }

    @EventHandler
    public void onNpcDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Mannequin mannequin)) return;
        UUID ownerId = ownerOf(mannequin);
        if (ownerId == null) return;
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && option(owner, "infinite-totem", true)) event.setCancelled(true);
    }

    @EventHandler
    public void onNpcHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Mannequin mannequin)) return;
        UUID ownerId = ownerOf(mannequin);
        if (ownerId == null) return;
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null) return;

        if (option(owner, "knockback", true) && event.getDamager() instanceof Player attacker) {
            Vector direction = mannequin.getLocation().toVector().subtract(attacker.getLocation().toVector());
            direction.setY(0);
            if (direction.lengthSquared() > 0.0001) {
                Vector velocity = direction.normalize().multiply(0.45);
                velocity.setY(0.28);
                mannequin.setVelocity(velocity);
            }
        }

        if (!option(owner, "shield", false)) return;
        long now = System.currentTimeMillis();
        if (shieldCooldowns.getOrDefault(mannequin.getUniqueId(), 0L) > now) return;

        if (event.getDamager() instanceof Player attacker
                && attacker.getInventory().getItemInMainHand().getType().name().endsWith("_AXE")) {
            shieldCooldowns.put(mannequin.getUniqueId(), now + 1000L);
            mannequin.getEquipment().setItemInOffHand(new ItemStack(Material.AIR));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!mannequin.isValid()) return;
                UUID currentOwnerId = ownerOf(mannequin);
                Player currentOwner = currentOwnerId == null ? null : Bukkit.getPlayer(currentOwnerId);
                if (currentOwner != null && option(currentOwner, "shield", false)) {
                    ItemStack shield = new ItemStack(Material.SHIELD);
                    ItemMeta meta = shield.getItemMeta();
                    meta.setUnbreakable(true);
                    shield.setItemMeta(meta);
                    mannequin.getEquipment().setItemInOffHand(shield);
                }
                shieldCooldowns.remove(mannequin.getUniqueId());
            }, 20L);
        } else {
            event.setCancelled(true);
        }
    }

    private void removeOutOfRangeBots() {
        for (Map.Entry<UUID, Set<Mannequin>> entry : new HashMap<>(owned).entrySet()) {
            Player owner = Bukkit.getPlayer(entry.getKey());
            if (owner == null || !owner.isOnline()) {
                removeOwned(entry.getKey());
                continue;
            }

            for (Mannequin mannequin : new HashSet<>(entry.getValue())) {
                if (!mannequin.isValid()) {
                    removeBot(entry.getKey(), mannequin);
                    continue;
                }

                Location mannequinLocation = mannequin.getLocation();
                if (mannequinLocation.getWorld() != owner.getWorld()
                        || mannequinLocation.distanceSquared(owner.getLocation()) > OWNER_RADIUS_SQUARED) {
                    removeBot(entry.getKey(), mannequin);
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeOwned(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        removeOwned(event.getPlayer());
    }

    public void removeOwned(Player owner) {
        removeOwned(owner.getUniqueId());
    }

    private void removeOwned(UUID ownerId) {
        Set<Mannequin> mannequins = owned.remove(ownerId);
        if (mannequins == null) return;
        for (Mannequin mannequin : mannequins) {
            shieldCooldowns.remove(mannequin.getUniqueId());
            if (mannequin.isValid()) mannequin.remove();
        }
    }

    public void removeAll() {
        for (UUID ownerId : new HashSet<>(owned.keySet())) removeOwned(ownerId);
        owned.clear();
    }

    private void removeBot(UUID ownerId, Mannequin mannequin) {
        Set<Mannequin> mannequins = owned.get(ownerId);
        if (mannequins != null) {
            mannequins.remove(mannequin);
            if (mannequins.isEmpty()) owned.remove(ownerId);
        }
        if (mannequin.isValid()) mannequin.remove();
    }
}
