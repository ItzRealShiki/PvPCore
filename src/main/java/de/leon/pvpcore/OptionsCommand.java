package de.leon.pvpcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class OptionsCommand implements CommandExecutor, Listener {
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private final PvPCore plugin;

    public OptionsCommand(PvPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return plugin.playerOnly(sender);
        if (!plugin.hasCore(player, "Member.Core")) {
            plugin.message(player, "&cYou do not have permission.");
            return true;
        }
        open(player);
        return true;
    }

    private ItemStack item(Material material, String title, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(plugin.color(title));
        meta.setLore(java.util.Arrays.stream(lore).map(plugin::color).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private void open(Player player) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.color("&8✦ ᴀʀᴇɴᴀ ɪɴꜰᴏ"));
        holder.inventory = inventory;

        String arena = plugin.practice().arenaMap(player);
        Material icon = switch (arena) {
            case "TINY_END_COLISEUM" -> Material.PURPUR_PILLAR;
            default -> Material.CHISELED_SANDSTONE;
        };
        inventory.setItem(11, item(icon, "&aSelected Arena: &f" + arena.replace('_', ' '),
                "&7This preset controls the entire arena:",
                "&7Floor, walls, trim and decorations.",
                "&7Change it from &f/prac &7→ Arena Selector."));

        int activationDistance = Math.max(2,
                plugin.getConfig().getInt("practice.forcefield.activation-distance", 6));
        inventory.setItem(15, item(Material.LIGHT_BLUE_STAINED_GLASS,
                "&bBoundary Forcefield",
                "&7A cyan particle barrier appears near the arena edge.",
                "&7Activation distance: &f" + activationDistance + " blocks",
                "&7The barrier prevents leaving the arena."));
        inventory.setItem(26, item(Material.BARRIER, "&cClose", "&7Close this menu."));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)
                || !(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getSlot() == 26) event.getWhoClicked().closeInventory();
    }
}