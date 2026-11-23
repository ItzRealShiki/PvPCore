package de.leon.pvpcore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class LagCommand implements CommandExecutor, TabCompleter {
    private static final DecimalFormat NUMBER = new DecimalFormat("0.0");
    private static final List<String> SUBCOMMANDS = List.of("copy", "report", "profile");
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

        if (args.length > 0 && args[0].equalsIgnoreCase("copy")) {
            String report = plugin.lag().diagnosticsReport();
            if (sender instanceof Player player) {
                Component copyButton = Component.text("§a§l[PRAC] §fClick here to copy the complete lag report")
                        .clickEvent(ClickEvent.copyToClipboard(report))
                        .hoverEvent(HoverEvent.showText(Component.text(
                                "Copies server, JVM, world, plugin, tick-spike and Prac timing diagnostics.")));
                player.sendMessage(copyButton);
                plugin.message(player, "&7After copying, paste the report into your support message.");
            } else {
                sender.sendMessage(report);
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("profile")) {
            boolean started = Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "spark profiler start --timeout 600");
            if (started) {
                plugin.message(sender, "&aSpark profiler started for 10 minutes. Check the server console for its report URL.");
            } else {
                plugin.message(sender, "&cSpark is not available. Check whether the Spark profiler command is installed.");
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
            sender.sendMessage(plugin.color("&7Use &e/lag copy &7to copy the full diagnostic report."));
            return true;
        }

        if (args.length > 0) {
            plugin.message(sender, "&cUnknown subcommand. Use /lag [copy|report|profile].");
            return true;
        }

        if (sender instanceof Player player) {
            plugin.lagMenu().openOverview(player);
        } else {
            onCommand(sender, command, label, new String[]{"report"});
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!plugin.hasCore(sender, "Admin.Core")) return List.of();
        if (args.length != 1) return List.of();

        String typed = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream()
                .filter(value -> value.startsWith(typed))
                .collect(Collectors.toList());
    }
}
