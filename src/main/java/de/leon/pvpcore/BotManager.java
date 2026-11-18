package de.leon.pvpcore;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

public final class BotManager implements Listener {
    private static final long SHIELD_COOLDOWN_TICKS = 20L;
    private record BotSettings(UUID ownerId, boolean shield, boolean infiniteTotem, boolean knockback) {}

    private final PvPCore plugin;
    private final Map<UUID, Set<NPC>> owned = new HashMap<>();
    private final Map<UUID, BotSettings> settings = new HashMap<>();
    private final Map<UUID, Long> shieldReadyAt = new HashMap<>();

    public BotManager(PvPCore plugin) { this.plugin = plugin; }

    public void spawn(Player owner, boolean shield, boolean infiniteTotem, boolean knockback) {
        removeOwned(owner);

        if (!CitizensAPI.hasImplementation()) {
            plugin.message(owner, "&cCitizens is required to spawn a player-model Training Bot.");
            return;
        }

        Location at = owner.getLocation().clone().add(owner.getLocation().getDirection().multiply(2.5));
        at.setY(owner.getLocation().getY());
        at.setDirection(owner.getLocation().toVector().subtract(at.toVector()));

        NPC npc = CitizensAPI.getNPCRegistry().createNPC(org.bukkit.entity.EntityType.PLAYER, "Training Bot");
        npc.setProtected(false);
        // Copy the owner's actual skin texture, not just their username or a player-head item.
        npc.getOrAddTrait(SkinTrait.class).setSkinPersistent(owner);
        if (!npc.spawn(at)) {
            npc.destroy();
            plugin.message(owner, "&cCould not spawn your Training Bot.");
            return;
        }

        Entity entity = npc.getEntity();
        if (!(entity instanceof Player bot)) {
            npc.destroy();
            plugin.message(owner, "&cCitizens did not create a player entity.");
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
            equipment.setItemInOffHand(shield
                    ? new ItemStack(Material.SHIELD)
                    : new ItemStack(Material.TOTEM_OF_UNDYING));
            equipment.setArmorContentsDropChance(0.0f);
            equipment.setItemInMainHandDropChance(0.0f);
            equipment.setItemInOffHandDropChance(0.0f);
        }

        UUID id = bot.getUniqueId();
        owned.computeIfAbsent(owner.getUniqueId(), key -> new HashSet<>()).add(npc);
        settings.put(id, new BotSettings(owner.getUniqueId(), shield, infiniteTotem, knockback));
        plugin.message(owner, "&aPlayer-model Training Bot spawned. &7Shield: &f" + (shield ? "ON" : "OFF")
                + "&7, Inf. Totem: &f" + (infiniteTotem ? "ON" : "OFF")
                + "&7, Knockback: &f" + (knockback ? "ON" : "OFF"));
    }

    private ItemStack enchantedNetherite(Material material) {
        ItemStack item = new ItemStack(material);
        item.addEnchantment(Enchantment.PROTECTION, 4);
        item.addEnchantment(Enchantment.UNBREAKING, 3);
        item.addEnchantment(Enchantment.MENDING, 1);
        return item;
    }

    @EventHandler
    public void onBotDamage(EntityDamageEvent event) {
        UUID id = event.getEntity().getUniqueId();
        BotSettings cfg = settings.get(id);
        if (cfg == null || !(event.getEntity() instanceof Player bot)) return;

        if (cfg.shield() && event instanceof EntityDamageByEntityEvent hit
                && hit.getDamager() instanceof Player attacker) {
            Vector toward = attacker.getLocation().toVector().subtract(bot.getLocation().toVector());
            toward.setY(0);
            Vector facing = bot.getLocation().getDirection().setY(0);
            if (toward.lengthSquared() > 0 && facing.lengthSquared() > 0
                    && facing.normalize().dot(toward.normalize()) >= 0.15) {
                long now = bot.getWorld().getGameTime();
                long ready = shieldReadyAt.getOrDefault(id, 0L);
                if (now >= ready) {
                    event.setCancelled(true);
                    shieldReadyAt.put(id, now + SHIELD_COOLDOWN_TICKS);
                    bot.swingOffHand();
                    bot.getWorld().playSound(bot.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1, 1);
                    bot.getWorld().spawnParticle(Particle.CRIT, bot.getLocation().add(0, 1, 0),
                            8, .35, .45, .35, .05);
                    return;
                }
            }
        }

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
        shieldReadyAt.remove(id);
        for (Set<NPC> npcs : owned.values()) {
            npcs.removeIf(npc -> npc.getEntity() == null || npc.getEntity().getUniqueId().equals(id));
        }
        owned.values().removeIf(Set::isEmpty);
    }

    public void removeOwned(Player owner) {
        Set<NPC> npcs = owned.remove(owner.getUniqueId());
        if (npcs == null) return;
        for (NPC npc : npcs) removeBot(npc);
    }

    public void removeAll() {
        for (Set<NPC> npcs : owned.values()) {
            for (NPC npc : npcs) removeBot(npc);
        }
        owned.clear();
    }

    private void removeBot(NPC npc) {
        Entity entity = npc.getEntity();
        if (entity != null) {
            settings.remove(entity.getUniqueId());
            shieldReadyAt.remove(entity.getUniqueId());
        }
        npc.destroy();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getConfig().getBoolean("bots.remove-on-quit", true)) removeOwned(event.getPlayer());
    }
}
