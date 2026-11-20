package de.leon.pvpcore;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public final class Menus implements Listener {
    private final PvPCore plugin;

    private static final class MenuHolder implements InventoryHolder {
        private final String key;
        private Inventory inventory;
        private MenuHolder(String key) { this.key = key; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public Menus(PvPCore plugin) { this.plugin = plugin; }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(plugin.color(name));
        if (lore.length > 0) meta.setLore(Arrays.stream(lore).map(plugin::color).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private Inventory create(String key, String title, int size) {
        MenuHolder holder = new MenuHolder(key);
        Inventory inv = Bukkit.createInventory(holder, size, plugin.color(title));
        holder.inventory = inv;
        return inv;
    }

    public void openPvp(Player p) {
        Inventory inv = create("pvp", plugin.getConfig().getString("menus.title-pvp", "&8ᴛʀᴀɪɴɪɴɢ ʙᴏᴛ"), 54);
        boolean totem = botOption(p, "infinite-totem", true);
        boolean knockback = botOption(p, "knockback", true);
        boolean shield = botOption(p, "shield", false);
        inv.setItem(10, item(Material.IRON_CHESTPLATE, "&aSurvival Player", "&7Real server-side player entity.", "&7Vanilla gravity, collisions and movement."));
        inv.setItem(13, item(totem ? Material.TOTEM_OF_UNDYING : Material.GRAY_DYE, (totem ? "&a" : "&c") + "Infinite Totem", "&7Uses vanilla totem resurrection.", "&7Automatically replaces a used totem.", "&7Status: " + (totem ? "&aEnabled" : "&cDisabled")));
        inv.setItem(16, item(knockback ? Material.IRON_CHESTPLATE : Material.GRAY_DYE, (knockback ? "&a" : "&c") + "Knockback", "&7Allows normal player knockback.", "&7Status: " + (knockback ? "&aEnabled" : "&cDisabled")));
        inv.setItem(22, item(Material.SHIELD, (shield ? "&a" : "&c") + "Shield", "&7Makes the bot hold its shield up.", "&7Status: " + (shield ? "&aEnabled" : "&cDisabled"), "&eClick to toggle"));
        inv.setItem(28, blastItem(p, "helmet", Material.NETHERITE_HELMET, "Helmet"));
        inv.setItem(30, blastItem(p, "chestplate", Material.NETHERITE_CHESTPLATE, "Chestplate"));
        inv.setItem(32, blastItem(p, "leggings", Material.NETHERITE_LEGGINGS, "Leggings"));
        inv.setItem(34, blastItem(p, "boots", Material.NETHERITE_BOOTS, "Boots"));
        inv.setItem(37, item(botOption(p, "armor-unbreakable", true) ? Material.ANVIL : Material.DAMAGED_ANVIL,
                "&eUnbreakable Armor", "&7Status: " + (botOption(p, "armor-unbreakable", true) ? "&aEnabled" : "&cDisabled"), "&eClick to toggle"));
        inv.setItem(39, item(Material.ENCHANTED_BOOK, "&dProtection Level: " + plugin.getConfig().getInt("bots.options." + p.getUniqueId() + ".protection-level", 4),
                "&7Click to cycle Protection 0–IV."));
        inv.setItem(45, item(Material.BARRIER, "&cRemove Bot", "&7Remove your active bot."));
        inv.setItem(49, item(Material.PLAYER_HEAD, "&aCreate Bot", "&7Creates your survival player bot with", "&7unbreakable netherite armor.", "&7Your previous bot is replaced automatically."));
        p.openInventory(inv);
    }

    private ItemStack blastItem(Player p, String piece, Material material, String label) {
        boolean enabled = botOption(p, "blast-" + piece, false);
        return item(material, (enabled ? "&a" : "&c") + label + " - Blast Protection", "&7Unbreakable netherite armor.", "&7Blast Protection IV: " + (enabled ? "&aEnabled" : "&cDisabled"), "&eClick to toggle");
    }

    private boolean botOption(Player p, String option, boolean defaultValue) {
        return plugin.getConfig().getBoolean("bots.options." + p.getUniqueId() + "." + option, defaultValue);
    }

    private void toggleBotOption(Player p, String option, boolean defaultValue) {
        String path = "bots.options." + p.getUniqueId() + "." + option;
        plugin.getConfig().set(path, !plugin.getConfig().getBoolean(path, defaultValue));
        plugin.saveConfig();
        plugin.bots().refreshOwned(p);
        openPvp(p);
    }

    public void openRtp(Player p) {
        Inventory inv = create("rtp", plugin.getConfig().getString("menus.title-rtp", "&8ʀᴀɴᴅᴏᴍ ᴛᴇʟᴇᴘᴏʀᴛ"), 27);
        inv.setItem(10, item(Material.GRASS_BLOCK, "&aPlains", "&7Natural Minecraft terrain", "&eClick to teleport"));
        inv.setItem(12, item(Material.SAND, "&6Desert", "&7Natural desert terrain", "&eClick to teleport"));
        inv.setItem(14, item(Material.SNOW_BLOCK, "&bSnow", "&7Natural snowy terrain", "&eClick to teleport"));
        inv.setItem(16, item(Material.TERRACOTTA, "&cBadlands", "&7Natural badlands terrain", "&eClick to teleport"));
        inv.setItem(22, item(Material.COMPASS, "&dRandom", "&7Choose a random arena world", "&eClick to teleport"));
        inv.setItem(26, item(Material.BARRIER, "&cClose", "&7Close this menu."));
        p.openInventory(inv);
    }

    public void openAdmin(Player p) {
        Inventory inv = create("admin", plugin.getConfig().getString("menus.title-admin", "&8ᴘᴠᴘᴄᴏʀᴇ ᴀᴅᴍɪɴ"), 27);
        inv.setItem(10, item(Material.PLAYER_HEAD, "&aPlayer Management", "&7Open player tools."));
        inv.setItem(12, item(Material.ZOMBIE_HEAD, "&eBot Management", "&7Remove all training NPCs."));
        inv.setItem(14, item(Material.COMMAND_BLOCK, "&6Gamemode Management", "&7Open gamemode selector."));
        inv.setItem(16, item(Material.COMPARATOR, "&bPlugin Settings", "&7Reload PvPCore configuration."));
        inv.setItem(22, item(Material.BARRIER, "&cClose", "&7Close this menu."));
        p.openInventory(inv);
    }

    public void openGamemode(Player p) {
        Inventory inv = create("gm", "&8ɢᴀᴍᴇᴍᴏᴅᴇ", 27);
        inv.setItem(10, item(Material.GRASS_BLOCK, "&aSurvival", "&7Set your game mode."));
        inv.setItem(12, item(Material.DIAMOND_BLOCK, "&bCreative", "&7Set your game mode."));
        inv.setItem(14, item(Material.IRON_SWORD, "&6Adventure", "&7Set your game mode."));
        inv.setItem(16, item(Material.ENDER_EYE, "&dSpectator", "&7Set your game mode."));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;

        int slot = e.getSlot();
        switch (holder.key) {
            case "pvp" -> {
                switch (slot) {
                    case 13 -> toggleBotOption(p, "infinite-totem", true);
                    case 16 -> toggleBotOption(p, "knockback", true);
                    case 22 -> toggleBotOption(p, "shield", false);
                    case 37 -> toggleBotOption(p, "armor-unbreakable", true);
                    case 39 -> {
                        String path = "bots.options." + p.getUniqueId() + ".protection-level";
                        int level = plugin.getConfig().getInt(path, 4);
                        plugin.getConfig().set(path, (level + 1) % 5);
                        plugin.saveConfig();
                        plugin.bots().refreshOwned(p);
                        openPvp(p);
                    }
                    case 28 -> toggleBotOption(p, "blast-helmet", false);
                    case 30 -> toggleBotOption(p, "blast-chestplate", false);
                    case 32 -> toggleBotOption(p, "blast-leggings", false);
                    case 34 -> toggleBotOption(p, "blast-boots", false);
                    case 45 -> { plugin.bots().removeOwned(p); plugin.message(p, "&aYour bot was removed."); p.closeInventory(); }
                    case 49 -> { plugin.bots().spawn(p); p.closeInventory(); }
                    default -> { }
                }
            }
            case "rtp" -> {
                switch (slot) {
                    case 10 -> { p.closeInventory(); plugin.worlds().teleport(p, "plains"); }
                    case 12 -> { p.closeInventory(); plugin.worlds().teleport(p, "desert"); }
                    case 14 -> { p.closeInventory(); plugin.worlds().teleport(p, "snow"); }
                    case 16 -> { p.closeInventory(); plugin.worlds().teleport(p, "badlands"); }
                    case 22 -> { p.closeInventory(); plugin.worlds().randomTeleport(p); }
                    case 26 -> p.closeInventory();
                    default -> { }
                }
            }
            case "admin" -> {
                if (!plugin.hasCore(p, "Admin.Core")) return;
                switch (slot) {
                    case 10 -> plugin.message(p, "&6Player tools are planned for the next module.");
                    case 12 -> { plugin.bots().removeAll(); plugin.message(p, "&aAll training NPCs removed."); }
                    case 14 -> { openGamemode(p); return; }
                    case 16 -> { plugin.reloadConfig(); plugin.message(p, "&aConfiguration reloaded."); }
                    case 22 -> { p.closeInventory(); return; }
                    default -> { return; }
                }
            }
            case "gm" -> {
                if (!plugin.hasCore(p, "Admin.Core")) return;
                switch (slot) {
                    case 10 -> p.setGameMode(GameMode.SURVIVAL);
                    case 12 -> p.setGameMode(GameMode.CREATIVE);
                    case 14 -> p.setGameMode(GameMode.ADVENTURE);
                    case 16 -> p.setGameMode(GameMode.SPECTATOR);
                    default -> { return; }
                }
                plugin.message(p, "&aGamemode changed to &f" + p.getGameMode().name() + "&a.");
                p.closeInventory();
            }
        }
    }
}
