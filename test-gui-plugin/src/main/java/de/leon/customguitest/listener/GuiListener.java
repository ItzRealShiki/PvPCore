package de.leon.customguitest.listener;

import de.leon.customguitest.CustomGuiTestPlugin;
import de.leon.customguitest.gui.GuiHolder;
import de.leon.customguitest.gui.GuiPage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class GuiListener implements Listener {
    private final CustomGuiTestPlugin plugin;

    public GuiListener(CustomGuiTestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        int slot = event.getRawSlot();
        if (holder.page() == GuiPage.MAIN) {
            handleMainClick(player, slot);
        } else {
            handleInfoClick(player, slot);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }

    private void handleMainClick(Player player, int slot) {
        switch (slot) {
            case 20 -> {
                player.sendMessage(Component.text("Hello, " + player.getName()
                        + "! The custom GUI button works.", NamedTextColor.AQUA));
                clickSound(player);
            }
            case 22 -> {
                plugin.menu().openInfo(player);
                clickSound(player);
            }
            case 24 -> {
                plugin.toggleTheme();
                plugin.menu().refresh(player);
                player.sendMessage(Component.text("GUI theme changed.", NamedTextColor.LIGHT_PURPLE));
                clickSound(player);
            }
            case 31 -> {
                player.sendActionBar(Component.text("Custom inventory interaction works!", NamedTextColor.GREEN));
                clickSound(player);
            }
            case 49 -> {
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.8f);
            }
            default -> {
                // Decorative slots intentionally do nothing.
            }
        }
    }

    private void handleInfoClick(Player player, int slot) {
        switch (slot) {
            case 49 -> {
                plugin.menu().openMain(player);
                clickSound(player);
            }
            case 53 -> {
                player.closeInventory();
                clickSound(player);
            }
            default -> {
                // Information cards are display-only.
            }
        }
    }

    private void clickSound(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.65f, 1.15f);
    }
}
