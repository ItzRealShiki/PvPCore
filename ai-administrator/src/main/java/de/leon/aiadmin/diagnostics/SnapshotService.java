package de.leon.aiadmin.diagnostics;

import de.leon.aiadmin.AIAdminPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SnapshotService {
    private final AIAdminPlugin plugin;
    private final Path serverRoot;

    public SnapshotService(AIAdminPlugin plugin, Path serverRoot) {
        this.plugin = plugin;
        this.serverRoot = serverRoot;
    }

    public ServerSnapshot capture() {
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long max = runtime.maxMemory() / (1024 * 1024);
        List<String> plugins = new ArrayList<>();
        for (Plugin installed : Bukkit.getPluginManager().getPlugins()) {
            plugins.add(installed.getName() + " v" + installed.getDescription().getVersion()
                    + (installed.isEnabled() ? " [enabled]" : " [disabled]"));
        }
        plugins.sort(String.CASE_INSENSITIVE_ORDER);

        int logLimit = plugin.getConfig().getInt("privacy.max-log-characters", 16000);
        String logs = plugin.getConfig().getBoolean("privacy.include-logs", true)
                ? LogReader.readRecentLogs(serverRoot, Math.max(2000, Math.min(logLimit, 30000))) : "Log transfer disabled in AIAdmin/config.yml.";
        String crash = plugin.getConfig().getBoolean("privacy.include-crash-report", true)
                ? LogReader.readLatestCrashReport(serverRoot, 10000) : "Crash report transfer disabled in AIAdmin/config.yml.";

        String minecraftVersion = Bukkit.getBukkitVersion();
        return new ServerSnapshot(Bukkit.getVersion(), minecraftVersion,
                System.getProperty("java.version", "unknown"), Bukkit.getOnlinePlayers().size(),
                Bukkit.getMaxPlayers(), used, max, List.copyOf(plugins), logs, crash);
    }
}
