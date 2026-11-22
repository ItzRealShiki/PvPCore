package de.leon.pvpcore;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AdminCommand implements CommandExecutor, TabCompleter {
    private final PvPCore plugin;
    private final NamespacedKey barrierViewerKey;

    public AdminCommand(PvPCore plugin) {
        this.plugin = plugin;
        this.barrierViewerKey = new NamespacedKey(plugin, "barrier_viewer");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return plugin.playerOnly(sender);
        if (!plugin.hasCore(player, "Admin.Core")) {
            plugin.message(player, "&cYou do not have permission.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("barrier")) {
            toggleBarrierViewer(player);
            return true;
        }
        if (args.length > 0 && (args[0].equalsIgnoreCase("practiceblocks")
                || args[0].equalsIgnoreCase("blocks"))) {
            plugin.menus().openPracticeBlockSettings(player);
            return true;
        }

        plugin.menus().openAdmin(player);
        return true;
    }

    private void toggleBarrierViewer(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack mainHand = inventory.getItemInMainHand();

        // The client only renders invisible barrier blocks while the Barrier item
        // is actively held. Keeping it in a random inventory slot is not enough.
        if (isViewer(mainHand)) {
            inventory.setItemInMainHand(null);
            plugin.message(player, "&eBarrier visibility disabled.");
            return;
        }

        ItemStack viewer = inventory.getItemInOffHand();
        boolean fromOffhand = isViewer(viewer);
        int fromSlot = fromOffhand ? -1 : findViewerSlot(inventory);

        if (fromOffhand) {
            inventory.setItemInOffHand(null);
        } else if (fromSlot >= 0) {
            viewer = inventory.getItem(fromSlot).clone();
            inventory.setItem(fromSlot, null);
        } else {
            viewer = createViewer();
        }

        if (mainHand != null && !mainHand.getType().isAir()) {
            int emptySlot = inventory.firstEmpty();
            if (emptySlot >= 0) {
                inventory.setItem(emptySlot, mainHand.clone());
            } else if (inventory.getItemInOffHand().getType().isAir()) {
                inventory.setItemInOffHand(mainHand.clone());
            } else {
                // Put the viewer back where we found it if there is no safe place
                // for the currently held item.
                if (fromOffhand) inventory.setItemInOffHand(viewer);
                else if (fromSlot >= 0) inventory.setItem(fromSlot, viewer);
                plugin.message(player, "&cMake one inventory slot free before enabling Barrier visibility.");
                return;
            }
        }

        inventory.setItemInMainHand(viewer);
        player.updateInventory();
        plugin.message(player, "&aBarrier viewer enabled. Keep the Barrier in your main hand to see Barrier blocks. Use /admin barrier again to disable it.");
    }

    private ItemStack createViewer() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bBarrier Visibility");
        meta.getPersistentDataContainer().set(barrierViewerKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isViewer(ItemStack item) {
        return item != null && item.getType() == Material.BARRIER && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(barrierViewerKey, PersistentDataType.BYTE);
    }

    private int findViewerSlot(PlayerInventory inventory) {
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isViewer(contents[slot])) return slot;
        }
        return -1;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !plugin.hasCore(player, "Admin.Core")) return List.of();
        if (args.length != 1) return List.of();
        String input = args[0].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        for (String option : List.of("barrier", "practiceblocks")) {
            if (option.startsWith(input)) suggestions.add(option);
        }
        return suggestions;
    }
}
