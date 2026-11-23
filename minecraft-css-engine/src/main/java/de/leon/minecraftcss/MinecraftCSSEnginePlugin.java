package de.leon.minecraftcss;

import de.leon.minecraftcss.command.McssCommand;
import de.leon.minecraftcss.gui.CssGuiEngine;
import de.leon.minecraftcss.gui.CssGuiListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class MinecraftCSSEnginePlugin extends JavaPlugin {
    private CssGuiEngine guiEngine;

    @Override
    public void onEnable() {
        exportIntegrationSetup();

        guiEngine = new CssGuiEngine(this);
        guiEngine.initialize();

        PluginCommand command = getCommand("mcss");
        if (command == null) {
            getLogger().severe("Command 'mcss' is missing from plugin.yml. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        McssCommand executor = new McssCommand(this, guiEngine);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        getServer().getPluginManager().registerEvents(new CssGuiListener(guiEngine), this);

        if (getServer().getPluginManager().isPluginEnabled("ItemsAdder")
                || getServer().getPluginManager().isPluginEnabled("Oraxen")) {
            getLogger().info("Custom GUI texture provider detected.");
        } else {
            getLogger().warning("No texture provider found. Install ItemsAdder or Oraxen and apply plugins/MinecraftCSSEngine/setup/ to enable the custom GUI artwork.");
        }
        getLogger().info("Minecraft CSS Engine enabled. Use /mcss to open the sample dashboard.");
    }

    private void exportIntegrationSetup() {
        List<String> resources = List.of(
                "setup/README.txt",
                "setup/itemsadder/contents/mcss/mcss.yml",
                "setup/itemsadder/contents/mcss/textures/gui/dashboard.png",
                "setup/itemsadder/contents/mcss/resourcepack/assets/mcss/items/gui_hidden.json",
                "setup/oraxen/glyphs/mcss.yml",
                "setup/oraxen/pack/assets/mcss/items/gui_hidden.json",
                "setup/oraxen/pack/textures/gui/dashboard.png"
        );

        for (String resource : resources) {
            try {
                saveResource(resource, false);
            } catch (IllegalArgumentException exception) {
                getLogger().warning("Could not export integration setup file '" + resource + "': " + exception.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Minecraft CSS Engine disabled.");
    }
}
