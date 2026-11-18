package de.leon.pvpcore;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Menus implements Listener {
    private final PvPCore plugin;
    private final Map<UUID, Boolean> shieldOptions = new HashMap<>();
    private final Map<UUID, Boolean> totemOptions = new HashMap<>();

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
        boolean shield = shieldOptions.getOrDefault(p.getUniqueId(), false);
        boolean infiniteTotem = totemOptions.getOrDefault(p.getUniqueId(), true);

        Inventory inv = create("pvp", plugin.getConfig().getString("menus.title-pvp", "&8ᴛʀᴀɪɴɪɴɢ ʙᴏᴛ"), 27);
        inv.setItem(11, item(Material.SHIELD,
                shield ? "&aShield: ON" : "&cShield: OFF",
                "&7Blocks frontal attacks.",
                "&7Ready again after 0.1 seconds.",
                "&eClick to toggle"));
        inv.setItem(15, item(Material.TOTEM_OF_UNDYING,
                infiniteTotem ? "&aInf. Totem: ON" : "&cInf. Totem: OFF",
                "&7The NPC never consumes a totem.",
                "&7Enabled by default.",
                "&eClick to toggle"));
        inv.setItem(22, item(Material.ZOMBIE_HEAD, "&6Spawn NPC",
                "&7Spawn a stationary training NPC",
                "&7with the selected options."));
        inv.setItem(26, item(Material.BARRIER, "&cRemove my NPCs",
                "&7Remove all of your active NPCs."));
        p.openInventory(inv);
    }

    public void openRtp(Player p) {
        Inventory inv = create("rtp", plugin.getConfig().getString("menus.title-rtp", "&8ʀᴀɴᴅᴏᴍ ᴛᴇʟᴇᴘᴏʀᴛ"), 27);
        inv.setItem(10, item(Material.GRASS_BLOCK, "&aPlains", "&7Natural Minecraft terrain", "&eClick to teleport"));
        inv.setItem(12, item(Material.SAND, "&6Desert", "&7Natural desert terrain", "&eClick to teleport"));
        inv.setItem(14, item(Material.SNOW_BLOCK, "&bSnow", "&7Natural snowy terrain", "&eClick to teleport"));
        inv.setItem(16, item(Material.TERRACOTTA, "&cBadlands", "&7Natural badlands terrain", "&eClick to teleport"));
        inv.setItem(22, item(Material.BARRIER, "&cClose", "&7Close this menu."));
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
        inv.setItem(10, item(Material.GRASS_BLOCK, "&aSurvival", "&7Set your gamemode."));
        inv.setItem(12, item(Material.DIAMOND_BLOCK, "&bCreative", "&7Set your gamemode."));
        inv.setItem(14, item(Material.IRON_SWORD, "&6Adventure", "&7Set your gamemode."));
        inv.setItem(16, item(Material.ENDER_EYE, "&dSpectator", "&7Set your gamemode."));
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
                UUID id = p.getUniqueId();
                switch (slot) {
                    case 11 -> {
                        shieldOptions.put(id, !shieldOptions.getOrDefault(id, false));
                        openPvp(p);
                    }
                    case 15 -> {
                        totemOptions.put(id, !totemOptions.getOrDefault(id, true));
                        openPvp(p);
                    }
                    case 22 -> {
                        plugin.bots().spawn(p, shieldOptions.getOrDefault(id, false),
                                totemOptions.getOrDefault(id, true));
                        shieldOptions.remove(id);
                        totemOptions.remove(id);
                        p.closeInventory();
                    }
                    case 26 -> {
                        plugin.bots().removeOwned(p);
                        plugin.message(p, "&aYour NPCs were removed.");
                        p.closeInventory();
                    }
                    default -> { }
                }
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
