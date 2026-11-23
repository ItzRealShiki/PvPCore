package de.leon.minecraftcss;

import de.leon.minecraftcss.command.McssCommand;
import de.leon.minecraftcss.gui.CssGuiEngine;
import de.leon.minecraftcss.gui.CssGuiListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MinecraftCSSEnginePlugin extends JavaPlugin {
    private CssGuiEngine guiEngine;

    @Override
    public void onEnable() {
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

        getLogger().info("Minecraft CSS Engine enabled. Use /mcss to open the example GUI.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Minecraft CSS Engine disabled.");
    }
}
