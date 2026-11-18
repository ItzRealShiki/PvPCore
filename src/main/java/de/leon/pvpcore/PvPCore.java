package de.leon.pvpcore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class PvPCore extends JavaPlugin implements Listener {
    private Menus menus;
    private BotManager bots;
    private WorldManager worlds;
    private ItemEditCommand itemEdit;

    @Override public void onEnable() {
        saveDefaultConfig();
        worlds = new WorldManager(this);
        worlds.initializeWorldsOnce();
        worlds.startRegenerationCycle();
        menus = new Menus(this);
        bots = new BotManager(this);
        register("bot", new PvpCommand(this));
        register("rtp", new RtpCommand(this));
        register("spawn", new SpawnCommand(this));
        register("setspawn", new SetSpawnCommand(this));
        register("admin", new AdminCommand(this));
        register("gm", new GamemodeCommand(this));
        itemEdit = new ItemEditCommand(this);
        register("it", itemEdit);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(bots, this);
        Bukkit.getPluginManager().registerEvents(itemEdit, this);
        getLogger().info("PvPCore enabled.");
    }

    public boolean startRtpCooldown(Player player) {
        String path = "rtp.cooldowns." + player.getUniqueId();
        long now = System.currentTimeMillis();
        long expiresAt = getConfig().getLong(path, 0L);
        if (expiresAt > now) {
            message(player, "&cYou can use /rtp again in &f" + formatDuration(expiresAt - now) + "&c.");
            return false;
        }
        long duration = Math.max(0L, getConfig().getLong("rtp.cooldown-seconds", 300L)) * 1000L;
        if (duration <= 0L) return true;
        expiresAt = now + duration;
        getConfig().set(path, expiresAt);
        saveConfig();
        scheduleRtpReadyMessage(player.getUniqueId(), expiresAt);
        message(player, "&eRTP cooldown started. You can use /rtp again in &f" + formatDuration(duration) + "&e.");
        return true;
    }

    private void scheduleRtpReadyMessage(java.util.UUID uuid, long expiresAt) {
        long delay = Math.max(1L, (expiresAt - System.currentTimeMillis() + 49L) / 50L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            String path = "rtp.cooldowns." + uuid;
            long currentExpiry = getConfig().getLong(path, 0L);
            if (currentExpiry != expiresAt || currentExpiry > System.currentTimeMillis()) return;
            getConfig().set(path, null);
            saveConfig();
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) message(player, "&aYour RTP cooldown has expired. You can use /rtp again!");
        }, delay);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String path = "rtp.cooldowns." + event.getPlayer().getUniqueId();
        long expiresAt = getConfig().getLong(path, 0L);
        if (expiresAt <= 0L) return;
        if (expiresAt <= System.currentTimeMillis()) {
            getConfig().set(path, null);
            saveConfig();
            message(event.getPlayer(), "&aYour RTP cooldown has expired. You can use /rtp again!");
        } else {
            scheduleRtpReadyMessage(event.getPlayer().getUniqueId(), expiresAt);
        }
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(1L, (millis + 999L) / 1000L);
        long minutes = seconds / 60L;
        long remainder = seconds % 60L;
        return minutes > 0 ? minutes + "m " + remainder + "s" : remainder + "s";
    }

    private void register(String name, CommandExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Missing command " + name);
        command.setExecutor(executor);
    }
    public Menus menus() { return menus; }
    public BotManager bots() { return bots; }
    public WorldManager worlds() { return worlds; }
    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', SmallCaps.of(text));
    }
    public void message(CommandSender sender, String text) {
        sender.sendMessage(color(getConfig().getString("style.prefix", "&8[&aPvPCore&8] ") + text));
    }
    public boolean hasCore(CommandSender sender, String permission) {
        return sender.hasPermission("Owner.Core") || sender.hasPermission(permission);
    }
    public boolean playerOnly(CommandSender sender) {
        if (sender instanceof Player) return true;
        message(sender, "&cThis command can only be used in-game.");
        return false;
    }
    @Override public void onDisable() { if (bots != null) bots.removeAll(); }
}
