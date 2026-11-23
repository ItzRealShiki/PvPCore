package de.leon.customguitest.css;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.entity.Player;

public final class CssGuiListener implements Listener {
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CssGuiHolder holder)) return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) return;
        String action = holder.actionAt(event.getRawSlot());
        if (action == null || action.isBlank() || !(event.getWhoClicked() instanceof Player player)) return;
        if (action.equalsIgnoreCase("close")) player.closeInventory();
        else if (action.startsWith("message:")) player.sendMessage(action.substring("message:".length()));
        else if (action.startsWith("open:")) {
            player.performCommand("cssui open " + action.substring("open:".length()));
        } else if (action.startsWith("command:")) {
            player.performCommand(action.substring("command:".length()));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CssGuiHolder) event.setCancelled(true);
    }
}
