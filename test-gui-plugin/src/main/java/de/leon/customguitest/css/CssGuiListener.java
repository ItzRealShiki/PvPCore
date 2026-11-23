package de.leon.customguitest.css;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class CssGuiListener implements Listener {
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CssGuiHolder holder)) return;
        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getInventory().getSize()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String action = holder.actionAt(rawSlot);
        if (action == null || action.isBlank()) return;

        if (action.equalsIgnoreCase("close")) {
            player.closeInventory();
        } else if (action.regionMatches(true, 0, "message:", 0, 8)) {
            player.sendMessage(action.substring(8));
        } else if (action.regionMatches(true, 0, "open:", 0, 5)) {
            String target = action.substring(5).trim();
            if (target.matches("[a-zA-Z0-9_-]+")) player.performCommand("cssui open " + target);
        } else if (action.regionMatches(true, 0, "command:", 0, 8)) {
            String command = action.substring(8).trim();
            if (command.matches("[a-zA-Z0-9_:/ .-]+")) player.performCommand(command);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CssGuiHolder) event.setCancelled(true);
    }
}
