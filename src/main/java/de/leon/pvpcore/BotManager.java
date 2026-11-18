package de.leon.pvpcore;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

public final class BotManager implements Listener {
    private static final double OWNER_RADIUS_SQUARED = 200.0 * 200.0;
    private record BotSettings(boolean infiniteTotem, boolean knockback) {}

    private final PvPCore plugin;
    private final Map<UUID, FakePlayer> owned = new HashMap<>();
    private final Map<UUID, BotSettings> settings = new HashMap<>();
    private boolean available;

    public BotManager(PvPCore plugin) {
        this.plugin = plugin;
        available = NmsGuard.supported();
        if (!available) {
            plugin.getLogger().warning("Training bots are disabled: this build supports Paper 26.2 only.");
            return;
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickBots, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::removeOutOfRangeBots, 20L, 20L);
    }

    public void spawn(Player owner, boolean infiniteTotem, boolean knockback) {
        if (!available) {
            plugin.message(owner, "&cTraining bots are only available on Paper 26.2.");
            return;
        }
        removeOwned(owner);
        Location at = owner.getLocation().clone().add(owner.getLocation().getDirection().multiply(2.5));
        at.setY(owner.getLocation().getY());
        at.setDirection(owner.getLocation().toVector().subtract(at.toVector()));
        try {
            FakePlayer fake = FakePlayer.spawn(at, "TrainingBot", owner);
            fake.clearSpawnInvulnerability();
            Player bot = fake.bukkit();
            bot.setCustomName(plugin.color("&eTraining Bot"));
            bot.setCustomNameVisible(true);
            bot.setInvulnerable(false);
            bot.setCollidable(true);
            bot.setCanPickupItems(false);
            bot.setHealth(20.0);
            EntityEquipment equipment = bot.getEquipment();
            if (equipment != null) {
                equipment.setHelmet(enchantedNetherite(Material.NETHERITE_HELMET));
                equipment.setChestplate(enchantedNetherite(Material.NETHERITE_CHESTPLATE));
                equipment.setLeggings(enchantedNetherite(Material.NETHERITE_LEGGINGS));
                equipment.setBoots(enchantedNetherite(Material.NETHERITE_BOOTS));
                equipment.setItemInMainHand(new ItemStack(Material.TOTEM_OF_UNDYING));
                equipment.setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
                equipment.setHelmetDropChance(0.0f);
                equipment.setChestplateDropChance(0.0f);
                equipment.setLeggingsDropChance(0.0f);
                equipment.setBootsDropChance(0.0f);
                equipment.setItemInMainHandDropChance(0.0f);
                equipment.setItemInOffHandDropChance(0.0f);
            }
            owned.put(owner.getUniqueId(), fake);
            settings.put(bot.getUniqueId(), new BotSettings(infiniteTotem, knockback));
            plugin.message(owner, "&aTraining Bot spawned. &7It disappears on logout, world change, or beyond 200 blocks.");
        } catch (LinkageError error) {
            available = false;
            plugin.getLogger().severe("Paper 26.2 NMS signatures do not match this server build; training bots disabled: " + error);
            plugin.message(owner, "&cThe bot system could not start on this Paper build.");
            removeAll();
        } catch (IllegalStateException error) {
            plugin.getLogger().warning(error.getMessage());
            plugin.message(owner, "&cCould not spawn your Training Bot.");
        }
    }

    private ItemStack enchantedNetherite(Material material) {
        ItemStack item = new ItemStack(material);
        item.addEnchantment(Enchantment.PROTECTION, 4);
        item.addEnchantment(Enchantment.UNBREAKING, 3);
        item.addEnchantment(Enchantment.MENDING, 1);
        return item;
    }

    private void tickBots() {
        if (!available) return;
        try {
            for (FakePlayer fake : new ArrayList<>(owned.values())) {
                if (!fake.isValid()) removeByBot(fake);
                else fake.tick();
            }
        } catch (LinkageError error) {
            available = false;
            plugin.getLogger().severe("Training bot tick failed because the Paper NMS API changed: " + error);
            removeAll();
        }
    }

    private void removeOutOfRangeBots() {
        for (Map.Entry<UUID, FakePlayer> entry : new HashMap<>(owned).entrySet()) {
            Player owner = Bukkit.getPlayer(entry.getKey());
            if (owner == null || !owner.isOnline()) {
                removeOwned(entry.getKey());
                continue;
            }
            Entity entity = entry.getValue().bukkit();
            if (!entry.getValue().isValid() || !entity.getWorld().equals(owner.getWorld())
                    || entity.getLocation().distanceSquared(owner.getLocation()) > OWNER_RADIUS_SQUARED) {
                removeOwned(entry.getKey());
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        BotSettings cfg = settings.get(event.getEntity().getUniqueId());
        if (cfg == null || !(event.getEntity() instanceof Player bot)) return;
        if (!cfg.knockback()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (bot.isValid()) bot.setVelocity(new Vector());
            });
        }
        if (cfg.infiniteTotem() && event.getFinalDamage() >= bot.getHealth()) {
            event.setCancelled(true);
            bot.setHealth(1.0);
            bot.getWorld().playSound(bot.getLocation(), Sound.ITEM_TOTEM_USE, 1, 1);
            bot.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                    bot.getLocation().add(0, 1, 0), 30, .5, .8, .5, .1);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        settings.remove(id);
        for (Map.Entry<UUID, FakePlayer> entry : new HashMap<>(owned).entrySet()) {
            if (entry.getValue().bukkit().getUniqueId().equals(id)) removeOwned(entry.getKey());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!available) return;
        for (FakePlayer fake : owned.values()) {
            try {
                fake.showTo(event.getPlayer());
            } catch (LinkageError error) {
                available = false;
                plugin.getLogger().severe("Could not show training bot to joining player: " + error);
                removeAll();
                return;
            }
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { removeOwned(event.getPlayer().getUniqueId()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { removeOwned(event.getPlayer().getUniqueId()); }

    private void removeByBot(FakePlayer bot) {
        for (Map.Entry<UUID, FakePlayer> entry : new HashMap<>(owned).entrySet()) {
            if (entry.getValue() == bot) {
                removeOwned(entry.getKey());
                return;
            }
        }
    }

    public void removeOwned(Player owner) { removeOwned(owner.getUniqueId()); }

    private void removeOwned(UUID ownerId) {
        FakePlayer fake = owned.remove(ownerId);
        if (fake == null) return;
        settings.remove(fake.bukkit().getUniqueId());
        fake.remove();
    }

    public void removeAll() {
        for (UUID ownerId : new ArrayList<>(owned.keySet())) removeOwned(ownerId);
        owned.clear();
        settings.clear();
    }
}
