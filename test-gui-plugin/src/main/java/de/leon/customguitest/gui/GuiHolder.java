package de.leon.customguitest.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class GuiHolder implements InventoryHolder {
    private final GuiPage page;
    private Inventory inventory;

    public GuiHolder(GuiPage page) {
        this.page = page;
    }

    public GuiPage page() {
        return page;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
