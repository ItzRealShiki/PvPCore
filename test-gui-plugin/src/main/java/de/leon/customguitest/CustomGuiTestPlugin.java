package de.leon.customguitest;

import de.leon.customguitest.art.MapArtworkRegistry;
import de.leon.customguitest.command.TestCommand;
import de.leon.customguitest.css.CssGuiEngine;
import de.leon.customguitest.css.CssGuiListener;
import de.leon.customguitest.css.CssUiCommand;
import de.leon.customguitest.gui.CustomMenu;
import de.leon.customguitest.listener.GuiListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomGuiTestPlugin extends JavaPlugin {
    private CustomMenu menu;
    private MapArtworkRegistry artwork;
    private CssGuiEngine cssEngine;
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
        TestCommand testCommand = new TestCommand(this);
        if (getCommand("test") == null || getCommand("cssui") == null) {
            getLogger().severe("Required command is missing from plugin.yml. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getCommand("test").setExecutor(testCommand);
        getCommand("test").setTabCompleter(testCommand);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        cssEngine = new CssGuiEngine(this);
        cssEngine.initialize();
        CssUiCommand cssCommand = new CssUiCommand(this, cssEngine);
        getCommand("cssui").setExecutor(cssCommand);
        getCommand("cssui").setTabCompleter(cssCommand);
        getServer().getPluginManager().registerEvents(new CssGuiListener(), this);

        getLogger().info("CustomGuiTest enabled. Use /test or /cssui to open a menu.");
    }

    public CustomMenu menu() { return menu; }
    public MapArtworkRegistry artwork() { return artwork; }
    public boolean isAlternateTheme() { return alternateTheme; }
    public void toggleTheme() { alternateTheme = !alternateTheme; }
}
