package de.leon.pvpcore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.World;
import org.bukkit.GameRule;
import org.bukkit.event.world.WorldLoadEvent;
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
    private PracticeManager practice;
    private final Set<UUID> rtpLoading = ConcurrentHashMap.newKeySet();

    @Override public void onEnable() {
        saveDefaultConfig();
        worlds = new WorldManager(this);
        for (World world : Bukkit.getWorlds()) worlds.applyPermanentDay(world);
        menus = new Menus(this);
        bots = new BotManager(this);
        practice = new PracticeManager(this);
        Bukkit.getPluginManager().registerEvents(practice, this);
        Bukkit.getScheduler().runTaskTimer(this, practice::enforceStunSlamFood, 1L, 1L);
        PracticeCommand practiceCommand = new PracticeCommand(this);
        register("prac", practiceCommand);
        OptionsCommand optionsCommand = new OptionsCommand(this);
        register("options", optionsCommand);
        Bukkit.getPluginManager().registerEvents(optionsCommand, this);
        Bukkit.getPluginManager().registerEvents(practiceCommand, this);
        register("spawn", new SpawnCommand(this));
        register("setspawn", new SetSpawnCommand(this));
        register("admin", new AdminCommand(this));
        register("gm", new GamemodeCommand(this));
        itemEdit = new ItemEditCommand(this);
        register("it", itemEdit);
        logCompatibilityDiagnostics();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(bots, this);
        Bukkit.getPluginManager().registerEvents(itemEdit, this);
        getLogger().info("PvPCore enabled.");
    }


    @org.bukkit.event.EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        worlds.applyPermanentDay(event.getWorld());
    }

    private void logCompatibilityDiagnostics() {
        String serverName = Bukkit.getName();
        String serverVersion = Bukkit.getBukkitVersion();
        String normalizedName = serverName == null ? "" : serverName.toLowerCase(java.util.Locale.ROOT);
        boolean paper = normalizedName.contains("paper");
        String minecraftVersion = Bukkit.getMinecraftVersion();
        boolean supportedNmsVersion = "26.2".equals(minecraftVersion);

        boolean lungeAvailable = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("lunge")) != null;

        getLogger().info("Server platform: " + serverName);
        getLogger().info("Server version: " + serverVersion + " | Minecraft: " + minecraftVersion);
        getLogger().info("Paper server detected: " + paper);
        getLogger().info("Training bot NMS version supported: " + supportedNmsVersion);
        getLogger().info("Lunge enchantment registered: " + lungeAvailable);

        if (!paper) {
            getLogger().warning("This server is not identified as Paper. Plugin compatibility is not guaranteed.");
        }
        if (paper) {
            getLogger().info("Paper detected. PvPCore targets the Paper API.");
        }
        if (!lungeAvailable) {
            getLogger().warning("The Lunge enchantment is not registered on this server version.");
        }
    }

    // Retained only for the legacy world manager's internal compilation; /rtp is no longer registered.
    public boolean beginRtp(Player player) {
        if (!rtpLoading.add(player.getUniqueId())) return false;
        return true;
    }
    public void finishRtp(Player player) { rtpLoading.remove(player.getUniqueId()); }

    private void register(String name, CommandExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Missing command " + name);
        command.setExecutor(executor);
    }
    public Menus menus() { return menus; }
    public BotManager bots() { return bots; }
    public WorldManager worlds() { return worlds; }
    public PracticeManager practice() { return practice; }
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
