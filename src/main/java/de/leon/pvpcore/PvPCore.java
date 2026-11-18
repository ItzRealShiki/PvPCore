package de.leon.pvpcore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PvPCore extends JavaPlugin implements Listener {
    private Menus menus;
    private BotManager bots;
    private WorldManager worlds;
    private ItemEditCommand itemEdit;
    private final Set<UUID> rtpLoading = ConcurrentHashMap.newKeySet();

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

    public boolean beginRtp(Player player) {
        if (!rtpLoading.add(player.getUniqueId())) {
            message(player, "&cYour previous RTP is still loading. Please wait.");
            return false;
        }
        message(player, "&eYour random teleport is loading. Please wait...");
        return true;
    }

    public void finishRtp(Player player) {
        if (rtpLoading.remove(player.getUniqueId()) && player.isOnline()) {
            message(player, "&aRTP is ready. You can use /rtp again!");
        }
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
