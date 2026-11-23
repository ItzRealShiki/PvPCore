package de.leon.minecraftcss.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class CssGuiListener implements Listener {
    private final CssGuiEngine engine;

    public CssGuiListener(CssGuiEngine engine) {
        this.engine = engine;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CssGuiHolder holder)) return;
        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getInventory().getSize()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        engine.performAction(player, holder.actionAt(rawSlot));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CssGuiHolder) {
            event.setCancelled(true);
        }
    }
}
