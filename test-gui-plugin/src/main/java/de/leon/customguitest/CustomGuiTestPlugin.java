package de.leon.customguitest;

import de.leon.customguitest.art.MapArtworkRegistry;
import de.leon.customguitest.command.TestCommand;
import de.leon.customguitest.gui.CustomMenu;
import de.leon.customguitest.listener.GuiListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomGuiTestPlugin extends JavaPlugin {
    private CustomMenu menu;
    private MapArtworkRegistry artwork;
    private boolean alternateTheme;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            artwork = new MapArtworkRegistry(this);
            artwork.initialize();
        } catch (Exception exception) {
            getLogger().severe("Could not initialize custom map artwork: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        menu = new CustomMenu(this);
        TestCommand command = new TestCommand(this);

        if (getCommand("test") == null) {
            getLogger().severe("Command 'test' is missing from plugin.yml. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getCommand("test").setExecutor(command);
        getCommand("test").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        getLogger().info("CustomGuiTest enabled. Use /test to open the fully textured map-art GUI.");
    }

    public CustomMenu menu() {
        return menu;
    }

    public MapArtworkRegistry artwork() {
        return artwork;
    }

    public boolean isAlternateTheme() {
        return alternateTheme;
    }

    public void toggleTheme() {
        alternateTheme = !alternateTheme;
    }
}
