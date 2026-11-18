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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.*;

public final class BotManager implements Listener {
    private static final long SHIELD_COOLDOWN_TICKS = 20L;
    private record BotSettings(boolean shield, boolean infiniteTotem, boolean knockback) {}
    private final PvPCore plugin;
    private final Map<UUID, Set<UUID>> owned = new HashMap<>();
    private final Map<UUID, BotSettings> settings = new HashMap<>();
    private final Map<UUID, Long> shieldReadyAt = new HashMap<>();

    public BotManager(PvPCore plugin) { this.plugin=plugin; }

    public void spawn(Player owner, boolean shield, boolean infiniteTotem, boolean knockback) {
        // Each player may have exactly one active Training Bot. Replace the old one first.
        removeOwned(owner);
        Set<UUID> ids=owned.computeIfAbsent(owner.getUniqueId(),k->new HashSet<>());
        Location at=owner.getLocation().clone().add(owner.getLocation().getDirection().multiply(2.5));
        at.setY(owner.getLocation().getY()); at.setDirection(owner.getLocation().toVector().subtract(at.toVector()));
        Zombie bot=owner.getWorld().spawn(at,Zombie.class,z->{
            z.setCustomName(plugin.color("&eTraining Bot")); z.setCustomNameVisible(true);
            z.setCanPickupItems(false); z.setRemoveWhenFarAway(false); z.setPersistent(true);
            z.setAI(false); z.setGravity(false); z.setVelocity(new Vector());
            z.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40); z.setHealth(40);
            z.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0);
            if(z.getAttribute(Attribute.KNOCKBACK_RESISTANCE)!=null)z.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(knockback?0:1);
            EntityEquipment eq=z.getEquipment();
            if(eq!=null){
                eq.setHelmet(owner.getInventory().getHelmet()==null?new ItemStack(Material.IRON_HELMET):owner.getInventory().getHelmet().clone());
                eq.setChestplate(owner.getInventory().getChestplate()==null?new ItemStack(Material.IRON_CHESTPLATE):owner.getInventory().getChestplate().clone());
                eq.setLeggings(owner.getInventory().getLeggings()==null?new ItemStack(Material.IRON_LEGGINGS):owner.getInventory().getLeggings().clone());
                eq.setBoots(owner.getInventory().getBoots()==null?new ItemStack(Material.IRON_BOOTS):owner.getInventory().getBoots().clone());
                ItemStack head=new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta=(SkullMeta)head.getItemMeta(); meta.setOwningPlayer(owner); head.setItemMeta(meta);
                eq.setHelmet(head);
                eq.setItemInMainHand(new ItemStack(Material.TOTEM_OF_UNDYING));
                eq.setItemInOffHand(shield?new ItemStack(Material.SHIELD):null);
            }
        });
        UUID id=bot.getUniqueId(); ids.add(id); settings.put(id,new BotSettings(shield,infiniteTotem,knockback));
        plugin.message(owner,"&aTraining Bot spawned. &7Shield: &f"+(shield?"ON":"OFF")+"&7, Inf. Totem: &f"+(infiniteTotem?"ON":"OFF")+"&7, Knockback: &f"+(knockback?"ON":"OFF"));
    }

    @EventHandler public void onBotDamage(EntityDamageEvent event){
        if(!(event.getEntity() instanceof Zombie bot))return;
        BotSettings cfg=settings.get(bot.getUniqueId()); if(cfg==null)return;
        if(!cfg.knockback()) bot.setVelocity(new Vector());
        if(cfg.infiniteTotem()&&event.getFinalDamage()>=bot.getHealth()){
            event.setCancelled(true); bot.setHealth(1);
            bot.getWorld().playSound(bot.getLocation(),Sound.ITEM_TOTEM_USE,1,1);
            bot.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,bot.getLocation().add(0,1,0),30,.5,.8,.5,.1); return;
        }
        if(!cfg.shield()||!(event instanceof EntityDamageByEntityEvent hit)||!(hit.getDamager() instanceof Player attacker))return;
        Vector toward=attacker.getLocation().toVector().subtract(bot.getLocation().toVector()); toward.setY(0);
        Vector facing=bot.getLocation().getDirection().setY(0);
        if(toward.lengthSquared()==0||facing.lengthSquared()==0||facing.normalize().dot(toward.normalize())<.15)return;
        long now=bot.getWorld().getGameTime(), ready=shieldReadyAt.getOrDefault(bot.getUniqueId(),0L);
        if(now<ready)return;
        event.setCancelled(true); shieldReadyAt.put(bot.getUniqueId(),now+SHIELD_COOLDOWN_TICKS);
        bot.swingOffHand(); bot.getWorld().playSound(bot.getLocation(),Sound.ITEM_SHIELD_BLOCK,1,1);
        bot.getWorld().spawnParticle(Particle.CRIT,bot.getLocation().add(0,1,0),8,.35,.45,.35,.05);
    }

    @EventHandler public void onDeath(EntityDeathEvent e){
        UUID id=e.getEntity().getUniqueId();settings.remove(id);shieldReadyAt.remove(id);
        for(Set<UUID> ids:owned.values())ids.remove(id);
    }
    public void removeOwned(Player owner){Set<UUID> ids=owned.remove(owner.getUniqueId());if(ids!=null)for(UUID id:ids)removeBot(id);}
    public void removeAll(){for(Set<UUID> ids:owned.values())for(UUID id:ids)removeBot(id);owned.clear();}
    private void removeBot(UUID id){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();settings.remove(id);shieldReadyAt.remove(id);}
    @EventHandler public void onQuit(PlayerQuitEvent e){if(plugin.getConfig().getBoolean("bots.remove-on-quit",true))removeOwned(e.getPlayer());}
}
