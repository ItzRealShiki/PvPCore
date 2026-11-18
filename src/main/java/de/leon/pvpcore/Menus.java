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
        Inventory inv = create("pvp", plugin.getConfig().getString("menus.title-pvp", "&8ᴘᴠᴘ ᴛʀᴀɪɴɪɴɢ"), 27);
        inv.setItem(10, item(Material.END_CRYSTAL, "&cCrystal PvP", "&7Spawn a basic training opponent."));
        inv.setItem(12, item(Material.MACE, "&6Mace PvP", "&7Practice against a training opponent."));
        inv.setItem(14, item(Material.DIAMOND_SWORD, "&bSword PvP", "&7Practice sword combat."));
        inv.setItem(16, item(Material.NETHERITE_AXE, "&eAxe PvP", "&7Practice axe combat."));
        inv.setItem(22, item(Material.BARRIER, "&cRemove my bots", "&7Remove your active training opponents."));
        p.openInventory(inv);
    }
    public void openRtp(Player p) {
        Inventory inv = create("rtp", plugin.getConfig().getString("menus.title-rtp", "&8ʀᴀɴᴅᴏᴍ ᴛᴇʟᴇᴘᴏʀᴛ"), 27);
        inv.setItem(10, item(Material.GRASS_BLOCK, "&aPlains", "&7Flat grass world", "&eClick to teleport"));
        inv.setItem(12, item(Material.SAND, "&6Desert", "&7Flat sand world", "&eClick to teleport"));
        inv.setItem(14, item(Material.SNOW_BLOCK, "&bSnow", "&7Flat snow world", "&eClick to teleport"));
        inv.setItem(16, item(Material.TERRACOTTA, "&cBadlands", "&7Flat terracotta world", "&eClick to teleport"));
        inv.setItem(22, item(Material.BARRIER, "&cClose", "&7Close this menu."));
        p.openInventory(inv);
    }
    public void openAdmin(Player p) {
        Inventory inv = create("admin", plugin.getConfig().getString("menus.title-admin", "&8ᴘᴠᴘᴄᴏʀᴇ ᴀᴅᴍɪɴ"), 27);
        inv.setItem(10, item(Material.PLAYER_HEAD, "&aPlayer Management", "&7Open player tools."));
        inv.setItem(12, item(Material.ZOMBIE_HEAD, "&eBot Management", "&7Remove all training bots."));
        inv.setItem(14, item(Material.COMMAND_BLOCK, "&6Gamemode Management", "&7Open gamemode selector."));
        inv.setItem(16, item(Material.COMPARATOR, "&bPlugin Settings", "&7Reload PvPCore configuration."));
        inv.setItem(22, item(Material.BARRIER, "&cClose", "&7Close this menu."));
        p.openInventory(inv);
    }
    public void openGamemode(Player p) {
        Inventory inv = create("gm", "&8ɢᴀᴍᴇᴍᴏᴅᴇ", 27);
        inv.setItem(10, item(Material.GRASS_BLOCK, "&aSurvival", "&7Set your gamemode."));
        inv.setItem(12, item(Material.DIAMOND_BLOCK, "&bCreative", "&7Set your gamemode."));
        inv.setItem(14, item(Material.IRON_SWORD, "&6Adventure", "&7Set your gamemode."));
        inv.setItem(16, item(Material.ENDER_EYE, "&dSpectator", "&7Set your gamemode."));
        p.openInventory(inv);
    }

    @EventHandler public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;

        int slot = e.getSlot();
        switch (holder.key) {
            case "pvp" -> {
                switch (slot) {
                    case 10 -> plugin.bots().spawn(p, "CRYSTAL");
                    case 12 -> plugin.bots().spawn(p, "MACE");
                    case 14 -> plugin.bots().spawn(p, "SWORD");
                    case 16 -> plugin.bots().spawn(p, "AXE");
                    case 22 -> { plugin.bots().removeOwned(p); plugin.message(p, "&aYour training bots were removed."); }
                    default -> { return; }
                }
                p.closeInventory();
            }
            case "rtp" -> {
                switch (slot) {
                    case 10 -> { p.closeInventory(); plugin.worlds().teleport(p, "plains"); }
                    case 12 -> { p.closeInventory(); plugin.worlds().teleport(p, "desert"); }
                    case 14 -> { p.closeInventory(); plugin.worlds().teleport(p, "snow"); }
                    case 16 -> { p.closeInventory(); plugin.worlds().teleport(p, "badlands"); }
                    case 22 -> p.closeInventory();
                    default -> { }
                }
            }
            case "admin" -> {
                if (!plugin.hasCore(p, "Admin.Core")) return;
                switch (slot) {
                    case 10 -> plugin.message(p, "&6Player tools are planned for the next module.");
                    case 12 -> { plugin.bots().removeAll(); plugin.message(p, "&aAll training bots removed."); }
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
