package de.leon.pvpcore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class PvPCore extends JavaPlugin {
    private Menus menus;
    private BotManager bots;
    private WorldManager worlds;
    private ItemEditCommand itemEdit;

    @Override public void onEnable() {
        saveDefaultConfig();
        worlds = new WorldManager(this);
        worlds.startRegenerationCycle();
        menus = new Menus(this);
        bots = new BotManager(this);
        register("bot", new PvpCommand(this));
        register("rtp", new RtpCommand(this));
        register("admin", new AdminCommand(this));
        register("gm", new GamemodeCommand(this));
        itemEdit = new ItemEditCommand(this);
        register("it", itemEdit);
        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(bots, this);
        Bukkit.getPluginManager().registerEvents(itemEdit, this);
        getLogger().info("PvPCore enabled.");
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
