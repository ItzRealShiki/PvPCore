package de.leon.pvpcore;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BotManager implements Listener {
    private static final double OWNER_RADIUS_SQUARED = 200.0 * 200.0;
    private final PvPCore plugin;
    private final Map<UUID, Mannequin> owned = new HashMap<>();
    private final Map<UUID, Integer> shieldTasks = new HashMap<>();

    public BotManager(PvPCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::removeOutOfRangeBots, 20L, 20L);
    }

    public void spawn(Player owner) {
        // A player can own exactly one bot. Replace the old one before creating the new one.
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
        equip(owner, mannequin);

        owned.put(owner.getUniqueId(), mannequin);
        plugin.message(owner, "&aYour bot has been created. Creating another one replaces this bot.");
    }

    private boolean option(Player owner, String key, boolean defaultValue) {
        return plugin.getConfig().getBoolean("bots.options." + owner.getUniqueId() + "." + key, defaultValue);
    }

    private ItemStack armor(Player owner, Material material, String piece) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        // Keep a visible enchantment glint; Blast Protection is independently configurable per armor piece.
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        if (option(owner, "blast-" + piece, false)) {
            meta.addEnchant(Enchantment.BLAST_PROTECTION, 4, true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack shield() {
        ItemStack item = new ItemStack(Material.SHIELD);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    private void equip(Player owner, Mannequin mannequin) {
        EntityEquipment equipment = mannequin.getEquipment();
        equipment.setHelmet(armor(owner, Material.NETHERITE_HELMET, "helmet"));
        equipment.setChestplate(armor(owner, Material.NETHERITE_CHESTPLATE, "chestplate"));
        equipment.setLeggings(armor(owner, Material.NETHERITE_LEGGINGS, "leggings"));
        equipment.setBoots(armor(owner, Material.NETHERITE_BOOTS, "boots"));
        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
        equipment.setItemInMainHand(option(owner, "shield", false) ? shield() : new ItemStack(Material.AIR));
        equipment.setItemInOffHand(option(owner, "infinite-totem", true)
                ? new ItemStack(Material.TOTEM_OF_UNDYING) : new ItemStack(Material.AIR));
        mannequin.setImmovable(!option(owner, "knockback", true));
    }

    public void refreshOwned(Player owner) {
        Mannequin mannequin = owned.get(owner.getUniqueId());
        if (mannequin != null && mannequin.isValid()) equip(owner, mannequin);
    }

    private UUID ownerOf(Mannequin mannequin) {
        for (Map.Entry<UUID, Mannequin> entry : owned.entrySet()) {
            if (entry.getValue().equals(mannequin)) return entry.getKey();
        }
        return null;
    }

    @EventHandler
    public void onBotDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Mannequin mannequin)) return;
        UUID ownerId = ownerOf(mannequin);
        if (ownerId == null) return;
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !option(owner, "infinite-totem", true)) return;

        // The mannequin has no AI. Its offhand totem protects it from lethal damage.
        if (event.getFinalDamage() >= mannequin.getHealth()) {
            event.setCancelled(true);
            mannequin.getEquipment().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
        }
    }

    @EventHandler
    public void onBotHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Mannequin mannequin)) return;
        UUID ownerId = ownerOf(mannequin);
        if (ownerId == null) return;
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !option(owner, "shield", false)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        if (!attacker.getInventory().getItemInMainHand().getType().name().endsWith("_AXE")) return;

        Integer oldTask = shieldTasks.remove(mannequin.getUniqueId());
        if (oldTask != null) Bukkit.getScheduler().cancelTask(oldTask);
        mannequin.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));

        int task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!mannequin.isValid()) return;
            UUID currentOwnerId = ownerOf(mannequin);
            Player currentOwner = currentOwnerId == null ? null : Bukkit.getPlayer(currentOwnerId);
            if (currentOwner != null && option(currentOwner, "shield", false)) {
                mannequin.getEquipment().setItemInMainHand(shield());
            }
            shieldTasks.remove(mannequin.getUniqueId());
        }, 20L).getTaskId();
        shieldTasks.put(mannequin.getUniqueId(), task);
    }

    private void removeOutOfRangeBots() {
        long started = System.nanoTime();
        try {
        for (Map.Entry<UUID, Mannequin> entry : new HashMap<>(owned).entrySet()) {
            Player owner = Bukkit.getPlayer(entry.getKey());
            Mannequin mannequin = entry.getValue();
            if (owner == null || !owner.isOnline() || !mannequin.isValid()) {
                removeOwned(entry.getKey());
                continue;
            }
            Location location = mannequin.getLocation();
            if (location.getWorld() != owner.getWorld()
                    || location.distanceSquared(owner.getLocation()) > OWNER_RADIUS_SQUARED) {
                removeOwned(entry.getKey());
            }
        }
        } finally {
            plugin.lag().recordOperation("Bot cleanup (" + owned.size() + " active)", System.nanoTime() - started);
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
        Mannequin mannequin = owned.remove(ownerId);
        if (mannequin == null) return;
        Integer task = shieldTasks.remove(mannequin.getUniqueId());
        if (task != null) Bukkit.getScheduler().cancelTask(task);
        if (mannequin.isValid()) mannequin.remove();
    }

    public void removeAll() {
        for (UUID ownerId : new HashSet<>(owned.keySet())) removeOwned(ownerId);
        owned.clear();
    }
}
