package de.leon.customguitest.css;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.HashMap;
import java.util.Map;

public final class CssGuiHolder implements InventoryHolder {
    private final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;

    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    public void setAction(int slot, String action) { if (action != null && !action.isBlank()) actions.put(slot, action); }
    public String actionAt(int slot) { return actions.get(slot); }
    @Override public Inventory getInventory() { return inventory; }
}
