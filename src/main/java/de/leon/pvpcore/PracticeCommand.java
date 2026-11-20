package de.leon.pvpcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Arrays;

public final class PracticeCommand implements CommandExecutor, Listener {
    private static final class Holder implements InventoryHolder {
        final String kind;
        final PracticeManager.Mode mode;
        final boolean duelKit;
        Inventory inv;
        Holder(String kind) { this(kind, null, false); }
        Holder(String kind, PracticeManager.Mode mode, boolean duelKit) {
            this.kind = kind; this.mode = mode; this.duelKit = duelKit;
        }
        public Inventory getInventory() { return inv; }
    }

    private final PvPCore plugin;
    public PracticeCommand(PvPCore plugin) { this.plugin = plugin; }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return plugin.playerOnly(sender);
        if (!plugin.hasCore(p, "Member.Core")) { plugin.message(p, "&cYou do not have permission."); return true; }
        if (plugin.practice().isFighting(p)) { plugin.message(p, "&cYou cannot open practice while a 1v1 is active."); return true; }
        if (command.getName().equalsIgnoreCase("kits")) { kit(p); return true; }
        if (args.length == 0) { main(p); return true; }
        if (args[0].equalsIgnoreCase("kit")) { kit(p); return true; }
        if (args[0].equalsIgnoreCase("join")) { plugin.practice().enter(p); return true; }
        plugin.message(p, "&eUsage: /prac [kit|join]");
        return true;
    }

    private ItemStack item(Material type, String name, String... lore) {
        ItemStack i = new ItemStack(type);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(plugin.color(name));
        m.setLore(Arrays.stream(lore).map(plugin::color).toList());
        i.setItemMeta(m);
        return i;
    }

    private Inventory create(String title, String kind, int size) {
        return create(title, new Holder(kind), size);
    }
    private Inventory create(String title, Holder holder, int size) {
        holder.inv = Bukkit.createInventory(holder, size, plugin.color(title));
        return holder.inv;
    }

    private void main(Player p) {
        Inventory i = create("&8✦ ᴘʀᴀᴄᴛɪᴄᴇ", "main", 27);
        i.setItem(11, item(Material.MACE, "&6Stun Slam", "&7Choose a difficulty before entering."));
        i.setItem(13, item(Material.GRASS_BLOCK, "&aBox Design", "&7Choose your own arena theme."));
        i.setItem(15, item(Material.DIAMOND_SWORD, "&bSword", "&7Diamond sword vs diamond sword.", "&eClick to enter"));
        i.setItem(22, item(Material.ANVIL, "&aKit Editor", "&7Edit your Practice and Duel kits."));
        p.openInventory(i);
    }

    private void difficulty(Player p) {
        Inventory i = create("&8✦ ꜱᴛᴜɴ ꜱʟᴀᴍ ᴅɪꜰꜰɪᴄᴜʟᴛʏ", "difficulty", 27);
        i.setItem(10, item(Material.LIME_CONCRETE, "&aEasy", "&710-block height", "&eClick to play"));
        i.setItem(12, item(Material.YELLOW_CONCRETE, "&eNormal", "&720-block height", "&eClick to play"));
        i.setItem(14, item(Material.ORANGE_CONCRETE, "&cHard", "&730-block height", "&eClick to play"));
        i.setItem(16, item(Material.RED_CONCRETE, "&4Impossible", "&7100-block height", "&eClick to play"));
        i.setItem(22, item(Material.ARROW, "&cBack"));
        p.openInventory(i);
    }

    private void kit(Player p) {
        Inventory i = create("&8✦ ᴋɪᴛ ᴇᴅɪᴛᴏʀ", "selector", 54);
        i.setItem(10, item(Material.NETHERITE_AXE, "&6Practice · Stun Slam", "&7Edit your personal practice loadout."));
        i.setItem(12, item(Material.DIAMOND_SWORD, "&bPractice · Sword", "&7Edit your personal practice loadout."));
        i.setItem(14, item(Material.NETHERITE_AXE, "&6Duel · Stun Slam", "&7Edit the kit you receive in /duel."));
        i.setItem(16, item(Material.DIAMOND_SWORD, "&bDuel · Sword", "&7Edit the kit you receive in /duel."));
        i.setItem(31, item(Material.BOOK, "&ePersonal Kits", "&7Duel kits are saved separately from Practice kits.", "&7If no personal Duel kit exists, the Admin kit is used."));
        i.setItem(49, item(Material.ARROW, "&cBack"));
        p.openInventory(i);
    }

    private void edit(Player p, PracticeManager.Mode mode, boolean duelKit) {
        Holder holder = new Holder("editor", mode, duelKit);
        Inventory inv = create("&8✦ " + (duelKit ? "ᴅᴜᴇʟ" : "ᴘʀᴀᴄᴛɪᴄᴇ") + " ᴋɪᴛ · " + mode.name(), holder, 54);
        if (duelKit) plugin.practice().applyDuelKitToInventory(p, mode, inv);
        else plugin.practice().applyKitToInventory(p, inv);
        for (int s = 36; s <= 44; s++) inv.setItem(s, item(Material.GRAY_STAINED_GLASS_PANE, "&8 "));
        inv.setItem(49, item(Material.BOOK, "&eKit Editor", "&7Move items within this menu.", "&7Armor slots: 45–48.", "&7Your changes save when you close."));
        inv.setItem(53, item(Material.EMERALD, "&aSave & Close", "&7Save your kit and return."));
        p.openInventory(inv);
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        if (h.kind.equals("editor")) {
            if (e.isShiftClick()) { e.setCancelled(true); return; }
            if (e.getClickedInventory() == e.getView().getTopInventory()) {
                int s = e.getSlot();
                if (s == 53) {
                    save(p, e.getView().getTopInventory(), h);
                    p.closeInventory();
                    plugin.message(p, "&a" + (h.duelKit ? "Duel" : "Practice") + " kit saved.");
                    return;
                }
                if ((s >= 36 && s <= 44) || (s >= 49 && s <= 52)) { e.setCancelled(true); return; }
                e.setCancelled(false);
            } else e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int s = e.getSlot();
        if (h.kind.equals("main")) {
            if (s == 11) difficulty(p);
            else if (s == 13) plugin.menus().openBoxThemes(p);
            else if (s == 15) { plugin.practice().setMode(p, PracticeManager.Mode.SWORD); p.closeInventory(); plugin.practice().enter(p); }
            else if (s == 22) kit(p);
        } else if (h.kind.equals("difficulty")) {
            if (s == 10 || s == 12 || s == 14 || s == 16) {
                int height = s == 10 ? 10 : s == 12 ? 20 : s == 14 ? 30 : 100;
                plugin.practice().setMode(p, PracticeManager.Mode.STUN_SLAM);
                p.closeInventory();
                plugin.practice().enter(p, height);
            } else if (s == 22) main(p);
        } else if (h.kind.equals("selector")) {
            if (s == 10) edit(p, PracticeManager.Mode.STUN_SLAM, false);
            else if (s == 12) edit(p, PracticeManager.Mode.SWORD, false);
            else if (s == 14) edit(p, PracticeManager.Mode.STUN_SLAM, true);
            else if (s == 16) edit(p, PracticeManager.Mode.SWORD, true);
            else if (s == 49) main(p);
        }
    }

    private void save(Player p, Inventory inventory, Holder holder) {
        if (holder.duelKit) plugin.practice().saveDuelKit(p, holder.mode, inventory);
        else {
            plugin.practice().setMode(p, holder.mode);
            plugin.practice().saveKit(p, inventory);
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p) || !(e.getInventory().getHolder() instanceof Holder h) || !h.kind.equals("editor")) return;
        save(p, e.getInventory(), h);
    }
}