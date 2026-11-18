package de.leon.pvpcore;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

public final class BotManager implements Listener {
    private static final double OWNER_RADIUS_SQUARED = 200.0 * 200.0;
    private record BotSettings(UUID ownerId, boolean infiniteTotem, boolean knockback) {}

    private final PvPCore plugin;
    private final Map<UUID, Set<NPC>> owned = new HashMap<>();
    private final Map<UUID, BotSettings> settings = new HashMap<>();

    public BotManager(PvPCore plugin) {
        this.plugin = plugin;
        // Check once per second. Bots are removed when their owner logs out,
        // changes world, or gets more than 200 blocks away.
        Bukkit.getScheduler().runTaskTimer(plugin, this::removeOutOfRangeBots, 20L, 20L);
    }

    public void spawn(Player owner, boolean infiniteTotem, boolean knockback) {
        removeOwned(owner);

        if (!CitizensAPI.hasImplementation()) {
            plugin.message(owner, "&cThe player-model NPC service is unavailable.");
            return;
        }

        Location at = owner.getLocation().clone().add(owner.getLocation().getDirection().multiply(2.5));
        at.setY(owner.getLocation().getY());
        at.setDirection(owner.getLocation().toVector().subtract(at.toVector()));

        NPC npc = CitizensAPI.getNPCRegistry().createNPC(org.bukkit.entity.EntityType.PLAYER, "Training Bot");
        npc.setProtected(false);
        npc.getOrAddTrait(SkinTrait.class).setSkinPersistent(owner);
        if (!npc.spawn(at)) {
            npc.destroy();
            plugin.message(owner, "&cCould not spawn your Training Bot.");
            return;
        }

        Entity entity = npc.getEntity();
        if (!(entity instanceof Player bot)) {
            npc.destroy();
            plugin.message(owner, "&cCould not create the player-model bot.");
            return;
        }

        bot.setCustomName(plugin.color("&eTraining Bot"));
        bot.setCustomNameVisible(true);
        bot.setInvulnerable(false);
        bot.setCollidable(true);
        bot.setCanPickupItems(false);

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

        owned.computeIfAbsent(owner.getUniqueId(), key -> new HashSet<>()).add(npc);
        settings.put(bot.getUniqueId(), new BotSettings(owner.getUniqueId(), infiniteTotem, knockback));
        plugin.message(owner, "&aTraining Bot spawned. &7It disappears when you leave, change world, or move more than 200 blocks away.");
    }

    private ItemStack enchantedNetherite(Material material) {
        ItemStack item = new ItemStack(material);
        item.addEnchantment(Enchantment.PROTECTION, 4);
        item.addEnchantment(Enchantment.UNBREAKING, 3);
        item.addEnchantment(Enchantment.MENDING, 1);
        return item;
    }

    private void removeOutOfRangeBots() {
        for (Map.Entry<UUID, Set<NPC>> entry : new HashMap<>(owned).entrySet()) {
            Player owner = Bukkit.getPlayer(entry.getKey());
            if (owner == null || !owner.isOnline()) {
                removeOwned(entry.getKey());
                continue;
            }

            for (NPC npc : new HashSet<>(entry.getValue())) {
                Entity entity = npc.getEntity();
                if (entity == null || !entity.isValid()) {
                    removeBot(entry.getKey(), npc);
                    continue;
                }
                Location botLocation = entity.getLocation();
                if (!botLocation.getWorld().equals(owner.getWorld())
                        || botLocation.distanceSquared(owner.getLocation()) > OWNER_RADIUS_SQUARED) {
                    removeBot(entry.getKey(), npc);
                }
            }
        }
    }

    @EventHandler
    public void onBotDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        UUID id = event.getEntity().getUniqueId();
        BotSettings cfg = settings.get(id);
        if (cfg == null || !(event.getEntity() instanceof Player bot)) return;

        if (!cfg.knockback()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (bot.isValid()) bot.setVelocity(new Vector());
            });
        }

        if (cfg.infiniteTotem() && event.getFinalDamage() >= bot.getHealth()) {
            event.setCancelled(true);
            bot.setHealth(1);
            bot.getWorld().playSound(bot.getLocation(), Sound.ITEM_TOTEM_USE, 1, 1);
            bot.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                    bot.getLocation().add(0, 1, 0), 30, .5, .8, .5, .1);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        settings.remove(id);
        for (Map.Entry<UUID, Set<NPC>> entry : new HashMap<>(owned).entrySet()) {
            for (NPC npc : new HashSet<>(entry.getValue())) {
                Entity entity = npc.getEntity();
                if (entity == null || entity.getUniqueId().equals(id)) removeBot(entry.getKey(), npc);
            }
        }
    }

    public void removeOwned(Player owner) {
        removeOwned(owner.getUniqueId());
    }

    private void removeOwned(UUID ownerId) {
        Set<NPC> npcs = owned.remove(ownerId);
        if (npcs == null) return;
        for (NPC npc : npcs) removeBot(ownerId, npc);
    }

    public void removeAll() {
        for (Map.Entry<UUID, Set<NPC>> entry : new HashMap<>(owned).entrySet()) {
            removeOwned(entry.getKey());
        }
        owned.clear();
        settings.clear();
    }

    private void removeBot(UUID ownerId, NPC npc) {
        Entity entity = npc.getEntity();
        if (entity != null) settings.remove(entity.getUniqueId());
        Set<NPC> npcs = owned.get(ownerId);
        if (npcs != null) {
            npcs.remove(npc);
            if (npcs.isEmpty()) owned.remove(ownerId);
        }
        npc.destroy();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Always remove immediately on logout; the config cannot override this rule.
        removeOwned(event.getPlayer());
    }
}
