package de.leon.aiadmin.command;

import de.leon.aiadmin.AIAdminPlugin;
import de.leon.aiadmin.diagnostics.LogReader;
import de.leon.aiadmin.proposal.ActionProposal;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class AIAdminCommand implements CommandExecutor, TabCompleter {
    private final AIAdminPlugin plugin;

    public AIAdminCommand(AIAdminPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !player.hasPermission("aiadmin.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use AIAdmin.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender);
            case "analyze", "analyse", "ask" -> {
                String request = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                        : "Prüfe den Serverzustand, die letzten Logs und bekannte Fehler. Nenne konkrete Ursachen und priorisierte Lösungen.";
                plugin.analyze(sender, request, false);
            }
            case "logs" -> showLogs(sender);
            case "proposals", "vorschlaege", "vorschläge" -> showProposals(sender);
            case "approve", "bestätigen", "bestaetigen" -> approve(sender, args);
            case "reject", "ablehnen" -> reject(sender, args);
            case "monitor" -> monitor(sender, args);
            case "reload" -> {
                plugin.reloadServices();
                sender.sendMessage(ChatColor.GREEN + "AIAdmin-Konfiguration neu geladen.");
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_AQUA + "—— AIAdmin ——");
        sender.sendMessage(ChatColor.AQUA + "/aiadmin status" + ChatColor.GRAY + " — Server- und API-Status");
        sender.sendMessage(ChatColor.AQUA + "/aiadmin analyze [Frage]" + ChatColor.GRAY + " — KI-Diagnose starten");
        sender.sendMessage(ChatColor.AQUA + "/aiadmin logs" + ChatColor.GRAY + " — Letzte Logzeilen anzeigen");
        sender.sendMessage(ChatColor.AQUA + "/aiadmin proposals" + ChatColor.GRAY + " — Offene Reparaturvorschläge");
        sender.sendMessage(ChatColor.AQUA + "/aiadmin approve <ID>" + ChatColor.GRAY + " — Vorschlag bestätigen");
        sender.sendMessage(ChatColor.AQUA + "/aiadmin reject <ID>" + ChatColor.GRAY + " — Vorschlag ablehnen");
        sender.sendMessage(ChatColor.AQUA + "/aiadmin monitor <on|off>" + ChatColor.GRAY + " — Automatische Logüberwachung");
        sender.sendMessage(ChatColor.AQUA + "/aiadmin reload" + ChatColor.GRAY + " — Konfiguration neu laden");
    }

    private void status(CommandSender sender) {
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long max = runtime.maxMemory() / (1024 * 1024);
        sender.sendMessage(ChatColor.DARK_AQUA + "—— AIAdmin Status ——");
        sender.sendMessage(ChatColor.GRAY + "Server: " + ChatColor.WHITE + Bukkit.getVersion());
        sender.sendMessage(ChatColor.GRAY + "Spieler: " + ChatColor.WHITE + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers());
        sender.sendMessage(ChatColor.GRAY + "Java: " + ChatColor.WHITE + System.getProperty("java.version"));
        sender.sendMessage(ChatColor.GRAY + "Heap: " + ChatColor.WHITE + used + " / " + max + " MiB");
        sender.sendMessage(ChatColor.GRAY + "OpenAI API-Key: " + (plugin.hasApiKey() ? ChatColor.GREEN + "konfiguriert" : ChatColor.RED + "fehlt"));
        sender.sendMessage(ChatColor.GRAY + "Log-Monitoring: " + (plugin.getConfig().getBoolean("monitoring.enabled", true) ? ChatColor.GREEN + "aktiv" : ChatColor.YELLOW + "aus"));
        sender.sendMessage(ChatColor.GRAY + "Offene Vorschläge: " + ChatColor.WHITE + plugin.getProposalStore().pending().size());
        sender.sendMessage(ChatColor.GRAY + "Automatische Änderungen: " + ChatColor.GREEN + "keine; Bestätigung erforderlich");
    }

    private void showLogs(CommandSender sender) {
        String content = LogReader.readRecentLogs(plugin.getServerRoot(), 10000);
        String[] lines = content.split("\\R");
        sender.sendMessage(ChatColor.DARK_AQUA + "—— Letzte Server-Logzeilen ——");
        int start = Math.max(0, lines.length - 15);
        for (int i = start; i < lines.length; i++) {
            String line = lines[i];
            if (line.length() > 240) line = line.substring(0, 240) + "…";
            sender.sendMessage(ChatColor.GRAY + line);
        }
    }

    private void showProposals(CommandSender sender) {
        List<ActionProposal> pending = plugin.getProposalStore().pending();
        sender.sendMessage(ChatColor.DARK_AQUA + "—— Offene AIAdmin-Vorschläge ——");
        if (pending.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "Keine offenen Vorschläge.");
            return;
        }
        for (ActionProposal proposal : pending) {
            sender.sendMessage(ChatColor.GOLD + proposal.id() + ChatColor.WHITE + " — " + proposal.title()
                    + ChatColor.GRAY + " | Typ: " + proposal.type() + " | Risiko: " + proposal.risk());
            sender.sendMessage(ChatColor.GRAY + "  " + proposal.reason());
        }
    }

    private void approve(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Verwendung: /aiadmin approve <ID>");
            return;
        }
        ActionProposal proposal = plugin.getProposalStore().find(args[1]).orElse(null);
        if (proposal == null || !proposal.status().equals("PENDING")) {
            sender.sendMessage(ChatColor.RED + "Dieser offene Vorschlag wurde nicht gefunden.");
            return;
        }
        if (proposal.type().equals("MANUAL_STEP")) {
            sender.sendMessage(ChatColor.YELLOW + "Das ist ein manueller Arbeitsschritt und wird nicht automatisch ausgeführt:");
            sender.sendMessage(ChatColor.WHITE + proposal.reason());
            return;
        }
        try {
            String result = plugin.getActionExecutor().execute(proposal);
            plugin.getProposalStore().updateStatus(proposal.id(), "APPLIED");
            sender.sendMessage(ChatColor.GREEN + "Bestätigter Vorschlag " + proposal.id() + " umgesetzt.");
            sender.sendMessage(ChatColor.GRAY + result);
            if (proposal.requiresRestart()) sender.sendMessage(ChatColor.YELLOW + "Hinweis: Laut KI ist danach möglicherweise ein Neustart nötig. AIAdmin startet den Server nicht selbst neu.");
        } catch (Exception exception) {
            plugin.getProposalStore().updateStatus(proposal.id(), "FAILED");
            sender.sendMessage(ChatColor.RED + "Änderung wurde nicht ausgeführt: " + exception.getMessage());
        }
    }

    private void reject(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Verwendung: /aiadmin reject <ID>");
            return;
        }
        ActionProposal proposal = plugin.getProposalStore().find(args[1]).orElse(null);
        if (proposal == null || !proposal.status().equals("PENDING")) {
            sender.sendMessage(ChatColor.RED + "Dieser offene Vorschlag wurde nicht gefunden.");
            return;
        }
        plugin.getProposalStore().updateStatus(proposal.id(), "REJECTED");
        sender.sendMessage(ChatColor.GREEN + "Vorschlag " + proposal.id() + " abgelehnt.");
    }

    private void monitor(CommandSender sender, String[] args) {
        if (args.length < 2 || !(args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
            sender.sendMessage(ChatColor.RED + "Verwendung: /aiadmin monitor <on|off>");
            return;
        }
        boolean enabled = args[1].equalsIgnoreCase("on");
        plugin.getConfig().set("monitoring.enabled", enabled);
        plugin.saveConfig();
        plugin.reloadServices();
        sender.sendMessage(ChatColor.GREEN + "Automatische Logüberwachung " + (enabled ? "aktiviert" : "deaktiviert") + ".");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return startsWith(List.of("help", "status", "analyze", "ask", "logs", "proposals", "approve", "reject", "monitor", "reload"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("approve") || args[0].equalsIgnoreCase("reject"))) {
            return startsWith(plugin.getProposalStore().pending().stream().map(ActionProposal::id).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("monitor")) return startsWith(List.of("on", "off"), args[1]);
        return List.of();
    }

    private static List<String> startsWith(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
