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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class BotManager implements Listener {
    private static final double OWNER_RADIUS_SQUARED = 200.0 * 200.0;

    private final PvPCore plugin;
    private final Map<UUID, FakePlayer> owned = new HashMap<>();
    // Vanilla shield-disable window after an axe hit (100 ticks / 5 seconds).
    private final Map<UUID, Long> shieldDisabledUntil = new HashMap<>();
    private boolean available;
    private boolean warned;

    public BotManager(PvPCore plugin) {
        this.plugin = plugin;
        available = NmsGuard.supported();
        if (!available) {
            plugin.getLogger().warning("Training bots are disabled: this NMS implementation currently supports Minecraft 26.2 on Paper servers.");
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickBots, 1L, 1L);
    }

    public void spawn(Player owner) {
        if (!available) {
            plugin.message(owner, "&cThe player bot is unavailable on this Minecraft/server version.");
            return;
        }

        removeOwned(owner);
        Location at = owner.getLocation().clone();
        if (owner.getWorld().getName().equals("prac_rooms")) at = plugin.practice().botSpawnLocation(owner);

        try {
            String botName = "Training Bot";
            FakePlayer fake = FakePlayer.spawn(at, botName, owner);
            fake.clearSpawnInvulnerability();
            Player bot = fake.bukkit();
            bot.customName(MiniMessage.miniMessage().deserialize("<gradient:#ff0000:#ff8c00>ᴛʀᴀɪɴɪɴɢ ʙᴏᴛ</gradient>"));
            bot.setCustomNameVisible(true);
            bot.setInvulnerable(false);
            bot.setGravity(true);
            owned.put(owner.getUniqueId(), fake);
            boolean stunSlam = owner.getWorld().getName().equals("prac_rooms")
                    && plugin.practice().mode(owner) == PracticeManager.Mode.STUN_SLAM;
            fake.setKnockbackEnabled(!(stunSlam || !option(owner, "knockback", true)));
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
        meta.setUnbreakable(option(owner, "armor-unbreakable", true));
        int protection = Math.max(0, Math.min(4, plugin.getConfig().getInt("bots.options." + owner.getUniqueId() + ".protection-level", 4)));
        if (protection > 0) meta.addEnchant(Enchantment.PROTECTION, protection, true);
        if (option(owner, "blast-" + piece, false)) {
            meta.addEnchant(Enchantment.BLAST_PROTECTION, 4, true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void equip(Player owner, FakePlayer fake) {
        if (owner.getWorld().getName().equals("prac_rooms")) {
            plugin.practice().applyBotKit(fake.bukkit(), owner);
            return;
        }
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

    public boolean hasOwnedBot(Player owner) {
        FakePlayer fake = owned.get(owner.getUniqueId());
        return fake != null && fake.isValid();
    }

    public boolean isOwnedBot(Player owner, Entity entity) {
        FakePlayer fake = owned.get(owner.getUniqueId());
        return fake != null && fake.isValid() && fake.bukkit().equals(entity);
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
                boolean practiceStunSlam = owner.getWorld().getName().equals("prac_rooms")
                        && plugin.practice().mode(owner) == PracticeManager.Mode.STUN_SLAM;
                fake.setKnockbackEnabled(!(practiceStunSlam || !option(owner, "knockback", true)));
                boolean shieldEnabled = practiceStunSlam || option(owner, "shield", false);
                boolean shieldDisabled = shieldDisabledUntil.getOrDefault(ownerId, 0L) > System.currentTimeMillis();
                fake.setShieldRaised(shieldEnabled && !shieldDisabled);
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
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !option(owner, "infinite-totem", true)) return;
        int delay = difficultyTicks(owner, "totem-difficulty");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            FakePlayer fake = owned.get(ownerId);
            Player currentOwner = Bukkit.getPlayer(ownerId);
            if (fake == null || currentOwner == null || !fake.isValid()
                    || !option(currentOwner, "infinite-totem", true)) return;
            Player bot = fake.bukkit();
            if (bot.getInventory().getItemInOffHand().getType().isAir()) {
                bot.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
            }
        }, delay);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        UUID ownerId = ownerOf(event.getEntity());
        if (ownerId == null) return;
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null) return;
        FakePlayer fake = owned.get(ownerId);
        if (fake == null) return;

        if ((owner.getWorld().getName().equals("prac_rooms") && plugin.practice().mode(owner) == PracticeManager.Mode.STUN_SLAM)
                || !option(owner, "knockback", true)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (fake.isValid()) fake.bukkit().setVelocity(new Vector(0, 0, 0));
            });
        }

        boolean shieldEnabled = (owner.getWorld().getName().equals("prac_rooms")
                && plugin.practice().mode(owner) == PracticeManager.Mode.STUN_SLAM)
                || option(owner, "shield", false);
        if (shieldEnabled
                && event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getEntity().equals(fake.bukkit())
                && byEntity.getDamager() instanceof Player attacker) {
            Material weapon = attacker.getInventory().getItemInMainHand().getType();

            // An axe disables the shield for the 1-second cooldown.
            if (weapon.name().endsWith("_AXE")) {
                long disabledUntil = System.currentTimeMillis() + 1000L;
                shieldDisabledUntil.put(ownerId, disabledUntil);
                fake.setShieldRaised(false);
            }
        }
    }

    private int difficultyTicks(Player owner, String key) {
        String defaultDifficulty = "impossible";
        String value = plugin.getConfig().getString("bots.options." + owner.getUniqueId() + "." + key, defaultDifficulty);
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "easy" -> 40;
            case "normal" -> 20;
            case "hard" -> 10;
            default -> 2;
        };
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
        shieldDisabledUntil.remove(ownerId);
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
                "Fake-player NMS signatures do not match this Paper server build. Only /bot is disabled.", error);
        removeAll();
    }
}
