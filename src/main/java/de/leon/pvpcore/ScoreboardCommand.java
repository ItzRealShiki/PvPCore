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

import java.util.Arrays;

public final class ScoreboardCommand implements CommandExecutor, Listener {
    private static final class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private final PvPCore plugin;

    public ScoreboardCommand(PvPCore plugin) { this.plugin = plugin; }

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

    private ItemStack item(Player player, String option, Material enabledMaterial, String title, String description) {
        boolean enabled = plugin.practice().scoreboardOption(player, option);
        ItemStack stack = new ItemStack(enabled ? enabledMaterial : Material.GRAY_DYE);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(plugin.color((enabled ? "&a" : "&c") + title));
        meta.setLore(Arrays.asList(plugin.color("&7" + description),
                plugin.color("&7Status: " + (enabled ? "&aEnabled" : "&cDisabled")),
                plugin.color("&eClick to toggle")));
        stack.setItemMeta(meta);
        return stack;
    }

    private void open(Player player) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.color("&8✦ ꜱᴄᴏʀᴇʙᴏᴀʀᴅ"));
        holder.inventory = inventory;
        inventory.setItem(10, item(player, "enabled", Material.LIME_DYE, "Scoreboard", "Show or hide the complete sidebar."));
        inventory.setItem(11, item(player, "mode", Material.NAME_TAG, "Mode", "Display your current practice mode."));
        inventory.setItem(12, item(player, "sword", Material.DIAMOND_SWORD, "Sword", "Show sword accuracy and statistics."));
        inventory.setItem(14, item(player, "stun-slam", Material.MACE, "Stun Slam", "Show your Stun Slam score."));
        inventory.setItem(15, item(player, "personal-bests", Material.GOLD_INGOT, "Personal Bests", "Show personal best values."));
        inventory.setItem(16, item(player, "footer", Material.PAPER, "Footer", "Show the decorative footer."));
        inventory.setItem(22, item(player, "enabled", Material.BOOK, "Live Preview", "Changes apply immediately."));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        String option = switch (event.getSlot()) {
            case 10 -> "enabled";
            case 11 -> "mode";
            case 12 -> "sword";
            case 14 -> "stun-slam";
            case 15 -> "personal-bests";
            case 16 -> "footer";
            default -> null;
        };
        if (option == null) return;
        plugin.practice().toggleScoreboardOption(player, option);
        open(player);
    }
}
