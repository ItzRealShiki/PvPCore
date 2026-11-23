package de.leon.pvpcore;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;

public final class LagCommand implements CommandExecutor {
    private static final DecimalFormat NUMBER = new DecimalFormat("0.0");
    private final PvPCore plugin;

    public LagCommand(PvPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.hasCore(sender, "Admin.Core")) {
            plugin.message(sender, "&cYou do not have permission.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("profile")) {
            boolean started = Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "spark profiler start --timeout 600");
            if (started) {
                plugin.message(sender, "&aSpark profiler started for 5 minutes. Check the server console for its report URL.");
            } else {
                plugin.message(sender, "&cSpark is not available. Paper normally includes it; check your server build.");
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("report")) {
            LagMonitor.Snapshot data = plugin.lag().snapshot();
            sender.sendMessage(plugin.color("&8&m------------------------------------------------"));
            sender.sendMessage(plugin.color("&a&lPRAC &8» &fServer Diagnostics"));
            sender.sendMessage(plugin.color("&7TPS: &f" + NUMBER.format(data.tps1m()) + " &8| &7MSPT: &f"
                    + NUMBER.format(data.windowAverageMspt()) + " &8| &7Peak: &f" + NUMBER.format(data.peakMspt()) + " ms"));
            sender.sendMessage(plugin.color("&7Spikes (60s): &f" + data.spikesOver50() + " &8| &7Worlds: &f"
                    + data.worlds().size() + " &8| &7Chunks: &f" + data.totalChunks() + " &8| &7Entities: &f" + data.totalEntities()));
            sender.sendMessage(plugin.color("&7Heap: &f" + NUMBER.format(data.heapPercent()) + "% &8| &7Threads: &f"
                    + data.threadCount() + " &8| &7Pending tasks: &f" + data.pendingTasks()));
            sender.sendMessage(plugin.color("&8&m------------------------------------------------"));
            if (data.topOperations().isEmpty()) {
                sender.sendMessage(plugin.color("&7No slow Prac operation was recorded in the last 60 seconds."));
            } else {
                sender.sendMessage(plugin.color("&eSlowest measured Prac operations (last 60s):"));
                for (LagMonitor.OperationSample operation : data.topOperations()) {
                    sender.sendMessage(plugin.color("&8• &f" + operation.name() + " &7— &e"
                            + NUMBER.format(operation.durationMillis()) + " ms"));
                }
            }
            return true;
        }

        if (sender instanceof Player player) {
            plugin.lagMenu().openOverview(player);
        } else {
            onCommand(sender, command, label, new String[]{"report"});
        }
        return true;
    }
}
