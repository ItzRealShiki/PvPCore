package de.leon.pvpcore;

import de.leon.pvpcore.bot.NmsGuard;
import de.leon.pvpcore.bot.nms.FakePlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class BotManager implements Listener {
    private static final double OWNER_RADIUS_SQUARED = 200.0 * 200.0;

    private final PvPCore plugin;
    private final Map<UUID, FakePlayer> owned = new HashMap<>();
    private boolean available;
    private boolean warned;

    public BotManager(PvPCore plugin) {
        this.plugin = plugin;
        available = NmsGuard.supported();
        if (!available) {
            plugin.getLogger().warning("Training bots are disabled: this implementation currently supports Paper 26.2 only.");
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickBots, 1L, 1L);
    }

    public void spawn(Player owner) {
        if (!available) {
            plugin.message(owner, "&cThe player bot is unavailable on this Paper version.");
            return;
        }

        removeOwned(owner);
        Location at = owner.getLocation().clone()
                .add(owner.getLocation().getDirection().normalize().multiply(2.5));
        at.setY(owner.getLocation().getY());
        at.setDirection(owner.getLocation().toVector().subtract(at.toVector()));

        try {
            String botName = "Training_Bot";
            FakePlayer fake = FakePlayer.spawn(at, botName, owner);
            fake.clearSpawnInvulnerability();
            Player bot = fake.bukkit();
            bot.setCustomName(plugin.color("&eᴛʀᴀɪɴɪɴɢ ʙᴏᴛ"));
            bot.setCustomNameVisible(true);
            bot.setInvulnerable(false);
            bot.setGravity(true);
            owned.put(owner.getUniqueId(), fake);
            equip(owner, fake);
            plugin.message(owner, "&aYour survival player bot has been created.");
        } catch (LinkageError error) {
            disableFeature(error);
            plugin.message(owner, "&cThe bot could not start because this Paper build has incompatible internal signatures.");
        } catch (IllegalStateException error) {
            plugin.getLogger().log(Level.WARNING, "Could not spawn training bot.", error);
            plugin.message(owner, "&cThe bot could not be spawned. Check the server log.");
        }
    }

    private boolean option(Player owner, String key, boolean defaultValue) {
        return plugin.getConfig().getBoolean("bots.options." + owner.getUniqueId() + "." + key, defaultValue);
    }

    private ItemStack armor(Player owner, Material material, String piece) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        if (option(owner, "blast-" + piece, false)) {
            meta.addEnchant(Enchantment.BLAST_PROTECTION, 4, true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void equip(Player owner, FakePlayer fake) {
        PlayerInventory inventory = fake.bukkit().getInventory();
        inventory.setHelmet(armor(owner, Material.NETHERITE_HELMET, "helmet"));
        inventory.setChestplate(armor(owner, Material.NETHERITE_CHESTPLATE, "chestplate"));
        inventory.setLeggings(armor(owner, Material.NETHERITE_LEGGINGS, "leggings"));
        inventory.setBoots(armor(owner, Material.NETHERITE_BOOTS, "boots"));

        if (option(owner, "shield", false)) {
            ItemStack shield = new ItemStack(Material.SHIELD);
            ItemMeta shieldMeta = shield.getItemMeta();
            shieldMeta.setUnbreakable(true);
            shield.setItemMeta(shieldMeta);
            inventory.setItemInMainHand(shield);
        } else {
            inventory.setItemInMainHand(new ItemStack(Material.AIR));
        }

        inventory.setItemInOffHand(option(owner, "infinite-totem", true)
                ? new ItemStack(Material.TOTEM_OF_UNDYING) : new ItemStack(Material.AIR));
        fake.setShieldRaised(option(owner, "shield", false));
    }

    public void refreshOwned(Player owner) {
        FakePlayer fake = owned.get(owner.getUniqueId());
        if (fake != null && fake.isValid()) equip(owner, fake);
    }

    private UUID ownerOf(Entity entity) {
        for (Map.Entry<UUID, FakePlayer> entry : owned.entrySet()) {
            if (entry.getValue().isValid() && entry.getValue().bukkit().equals(entity)) return entry.getKey();
        }
        return null;
    }

    private void tickBots() {
        if (!available) return;
        try {
            for (Map.Entry<UUID, FakePlayer> entry : new HashMap<>(owned).entrySet()) {
                UUID ownerId = entry.getKey();
                FakePlayer fake = entry.getValue();
                Player owner = Bukkit.getPlayer(ownerId);

                if (owner == null || !owner.isOnline() || !fake.isValid()) {
                    removeOwned(ownerId);
                    continue;
                }

                Location location = fake.bukkit().getLocation();
                if (location.getWorld() != owner.getWorld()
                        || location.distanceSquared(owner.getLocation()) > OWNER_RADIUS_SQUARED) {
                    removeOwned(ownerId);
                    continue;
                }

                Player bot = fake.bukkit();
                if (option(owner, "infinite-totem", true)
                        && bot.getInventory().getItemInOffHand().getType().isAir()) {
                    bot.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
                }
                fake.setShieldRaised(option(owner, "shield", false));
                fake.tick();
            }
        } catch (LinkageError error) {
            disableFeature(error);
        }
    }

    @EventHandler
    public void onResurrect(EntityResurrectEvent event) {
        UUID ownerId = ownerOf(event.getEntity());
        if (ownerId == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            FakePlayer fake = owned.get(ownerId);
            Player owner = Bukkit.getPlayer(ownerId);
            if (fake == null || owner == null || !fake.isValid()
                    || !option(owner, "infinite-totem", true)) return;
            Player bot = fake.bukkit();
            if (bot.getInventory().getItemInOffHand().getType().isAir()) {
                bot.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
            }
        });
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        UUID ownerId = ownerOf(event.getEntity());
        if (ownerId == null) return;
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || option(owner, "knockback", true)) return;
        FakePlayer fake = owned.get(ownerId);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (fake != null && fake.isValid()) fake.bukkit().setVelocity(new Vector(0, 0, 0));
        });
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        UUID ownerId = ownerOf(event.getEntity());
        if (ownerId != null) removeOwned(ownerId);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!available) return;
        try {
            for (FakePlayer fake : owned.values()) if (fake.isValid()) fake.showTo(event.getPlayer());
        } catch (LinkageError error) {
            disableFeature(error);
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { removeOwned(event.getPlayer()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { removeOwned(event.getPlayer()); }

    public void removeOwned(Player owner) { removeOwned(owner.getUniqueId()); }

    private void removeOwned(UUID ownerId) {
        FakePlayer fake = owned.remove(ownerId);
        if (fake != null) {
            try {
                fake.remove();
            } catch (LinkageError error) {
                disableFeature(error);
            }
        }
    }

    public void removeAll() {
        for (UUID ownerId : new HashSet<>(owned.keySet())) removeOwned(ownerId);
        owned.clear();
    }

    private void disableFeature(LinkageError error) {
        if (warned) return;
        warned = true;
        available = false;
        plugin.getLogger().log(Level.WARNING,
                "Fake-player NMS signatures do not match this Paper build. Only /bot is disabled.", error);
        removeAll();
    }
}
