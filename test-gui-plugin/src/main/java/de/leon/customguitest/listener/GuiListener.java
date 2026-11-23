package de.leon.customguitest.listener;

import de.leon.customguitest.CustomGuiTestPlugin;
import de.leon.customguitest.gui.GuiHolder;
import de.leon.customguitest.gui.GuiPage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getRawSlot();
        switch (holder.page()) {
            case HOME -> home(player, slot);
            case DASHBOARD -> dashboard(player, slot);
            case PROFILE -> profile(player, slot);
            case SETTINGS -> settings(player, slot);
            case SHOWCASE -> showcase(player, slot);
            case STATS -> stats(player, slot);
            case LAB -> lab(player, slot);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }

    private void home(Player player, int slot) {
        switch (slot) {
            case 20 -> open(player, GuiPage.PROFILE);
            case 22 -> open(player, GuiPage.DASHBOARD);
            case 24 -> open(player, GuiPage.SETTINGS);
            case 29 -> open(player, GuiPage.SHOWCASE);
            case 31 -> open(player, GuiPage.STATS);
            case 33 -> open(player, GuiPage.LAB);
            case 49 -> close(player);
            default -> click(player);
        }
    }

    private void dashboard(Player player, int slot) {
        switch (slot) {
            case 12 -> {
                player.sendActionBar(Component.text("NOVA input channel: operational", NamedTextColor.AQUA));
                click(player);
            }
            case 14 -> toggleTheme(player);
            case 29 -> open(player, GuiPage.PROFILE);
            case 31 -> open(player, GuiPage.LAB);
            case 33 -> open(player, GuiPage.HOME);
            case 49 -> close(player);
            default -> click(player);
        }
    }

    private void profile(Player player, int slot) {
        if (slot == 49) open(player, GuiPage.HOME);
        else click(player);
    }

    private void settings(Player player, int slot) {
        switch (slot) {
            case 11 -> toggleTheme(player);
            case 13 -> {
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.5f);
                player.sendActionBar(Component.text("NOVA sound preview", NamedTextColor.LIGHT_PURPLE));
            }
            case 15 -> player.sendActionBar(Component.text("This test uses server-rendered map art.", NamedTextColor.GRAY));
            case 33, 49 -> open(player, GuiPage.HOME);
            case 53 -> close(player);
            default -> click(player);
        }
    }

    private void showcase(Player player, int slot) {
        if (slot == 49) open(player, GuiPage.HOME);
        else click(player);
    }

    private void stats(Player player, int slot) {
        if (slot == 31) {
            open(player, GuiPage.STATS);
            player.sendActionBar(Component.text("Live values refreshed", NamedTextColor.GREEN));
        } else if (slot == 33 || slot == 49) {
            open(player, GuiPage.HOME);
        } else {
            click(player);
        }
    }

    private void lab(Player player, int slot) {
        if (slot == 33 || slot == 49) open(player, GuiPage.HOME);
        else if (slot == 53) close(player);
        else click(player);
    }

    private void open(Player player, GuiPage page) {
        plugin.menu().open(player, page);
        click(player);
    }

    private void toggleTheme(Player player) {
        plugin.toggleTheme();
        plugin.menu().refresh(player);
        player.sendActionBar(Component.text(
                plugin.isAlternateTheme() ? "Violet atmosphere enabled" : "Aurora atmosphere enabled",
                NamedTextColor.LIGHT_PURPLE));
        click(player);
    }

    private void close(Player player) {
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.8f);
    }

    private void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.55f, 1.12f);
    }
}
