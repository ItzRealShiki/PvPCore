package de.leon.aiadmin;

import de.leon.aiadmin.ai.AIAnalysis;
import de.leon.aiadmin.ai.OpenAIResponsesClient;
import de.leon.aiadmin.command.AIAdminCommand;
import de.leon.aiadmin.diagnostics.LogMonitor;
import de.leon.aiadmin.diagnostics.ServerSnapshot;
import de.leon.aiadmin.diagnostics.SnapshotService;
import de.leon.aiadmin.proposal.ActionExecutor;
import de.leon.aiadmin.proposal.ActionProposal;
import de.leon.aiadmin.proposal.ProposalStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AIAdminPlugin extends JavaPlugin {
    private final AtomicBoolean analysisRunning = new AtomicBoolean(false);
    private ExecutorService worker;
    private java.nio.file.Path serverRoot;
    private ProposalStore proposalStore;
    private ActionExecutor actionExecutor;
    private SnapshotService snapshotService;
    private LogMonitor logMonitor;
    private OpenAIResponsesClient aiClient;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        serverRoot = getDataFolder().toPath().toAbsolutePath().normalize().getParent().getParent();
        worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "AIAdmin-Worker");
            thread.setDaemon(true);
            return thread;
        });
        proposalStore = new ProposalStore(this);
        actionExecutor = new ActionExecutor(this, serverRoot);
        snapshotService = new SnapshotService(this, serverRoot);
        logMonitor = new LogMonitor(this, serverRoot);
        refreshClient();

        if (getCommand("aiadmin") != null) {
            AIAdminCommand adminCommand = new AIAdminCommand(this);
            getCommand("aiadmin").setExecutor(adminCommand);
            getCommand("aiadmin").setTabCompleter(adminCommand);
        }
        logMonitor.start();
        getLogger().info("AIAdmin enabled. API key configured: " + (hasApiKey() ? "yes" : "no"));
        getLogger().info("Use /aiadmin help. No repair is applied without an explicit administrator approval.");
    }

    @Override
    public void onDisable() {
        if (logMonitor != null) logMonitor.stop();
        if (worker != null) worker.shutdownNow();
    }

    public void reloadServices() {
        reloadConfig();
        refreshClient();
        if (logMonitor != null) logMonitor.start();
    }

    private void refreshClient() {
        String endpoint = getConfig().getString("openai.endpoint", "https://api.openai.com/v1/responses").trim();
        try {
            URI uri = URI.create(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"api.openai.com".equalsIgnoreCase(uri.getHost())) {
                endpoint = "https://api.openai.com/v1/responses";
                getLogger().warning("The configured endpoint was not the official HTTPS API host; using the official endpoint.");
            }
        } catch (IllegalArgumentException exception) {
            endpoint = "https://api.openai.com/v1/responses";
        }
        String model = getConfig().getString("openai.model", "gpt-6.2-astra").trim();
        aiClient = new OpenAIResponsesClient(endpoint, getApiKey(), model,
                getConfig().getInt("openai.timeout-seconds", 50),
                getConfig().getInt("openai.max-output-tokens", 2600));
    }

    private String getApiKey() {
        String environment = System.getenv("OPENAI_API_KEY");
        if (environment != null && !environment.isBlank()) return environment.trim();
        return getConfig().getString("openai.api-key", "").trim();
    }

    public boolean hasApiKey() { return !getApiKey().isBlank(); }

    public void runAutomaticAnalysis(String request) {
        if (!hasApiKey()) {
            getLogger().warning("AI log alert detected, but no OpenAI API key is configured. Add it to plugins/AIAdmin/config.yml.");
            return;
        }
        analyze(null, request, true);
    }

    public void analyze(CommandSender sender, String request, boolean automatic) {
        if (!hasApiKey()) {
            tell(sender, ChatColor.RED + "Es ist kein OpenAI-API-Key eingetragen. Trage ihn in plugins/AIAdmin/config.yml unter openai.api-key ein.");
            return;
        }
        if (!analysisRunning.compareAndSet(false, true)) {
            tell(sender, ChatColor.YELLOW + "Eine KI-Analyse läuft bereits.");
            return;
        }

        final ServerSnapshot snapshot;
        try {
            snapshot = snapshotService.capture();
        } catch (Exception exception) {
            analysisRunning.set(false);
            tell(sender, ChatColor.RED + "Serverdaten konnten nicht gesammelt werden: " + exception.getMessage());
            return;
        }

        tell(sender, ChatColor.GRAY + "AIAdmin analysiert Serverdaten und bereitet nur Vorschläge vor ...");
        CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = request == null || request.isBlank()
                        ? "Führe eine allgemeine Diagnose durch und priorisiere die wichtigsten Probleme." : request;
                return aiClient.analyze(snapshot.toPromptText(), prompt);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }, worker).whenComplete((analysis, failure) -> Bukkit.getScheduler().runTask(this, () -> {
            analysisRunning.set(false);
            if (failure != null) {
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                getLogger().warning("AI analysis failed: " + message);
                tell(sender, ChatColor.RED + "KI-Analyse fehlgeschlagen: " + message);
                return;
            }
            displayAnalysis(sender, analysis, automatic);
        }));
    }

    private void displayAnalysis(CommandSender sender, AIAnalysis analysis, boolean automatic) {
        ChatColor severityColor = switch (analysis.severity().toUpperCase()) {
            case "CRITICAL", "HIGH" -> ChatColor.RED;
            case "MEDIUM" -> ChatColor.GOLD;
            case "LOW" -> ChatColor.YELLOW;
            default -> ChatColor.GREEN;
        };
        sendToAdmins(ChatColor.DARK_AQUA + "[AIAdmin] " + severityColor + analysis.severity()
                + ChatColor.GRAY + " — " + analysis.summary(), automatic);
        if (sender != null) {
            sender.sendMessage(ChatColor.DARK_AQUA + "—— AIAdmin Diagnose ——");
            sender.sendMessage(severityColor + "Priorität: " + analysis.severity());
            sender.sendMessage(ChatColor.WHITE + "Zusammenfassung: " + analysis.summary());
            sender.sendMessage(ChatColor.WHITE + "Wahrscheinliche Ursache: " + analysis.rootCause());
        } else {
            getLogger().info("AI summary: " + analysis.summary());
            getLogger().info("AI likely cause: " + analysis.rootCause());
        }

        int index = 1;
        for (AIAnalysis.Finding finding : analysis.findings()) {
            String message = ChatColor.YELLOW + index++ + ". " + ChatColor.WHITE + finding.title()
                    + ChatColor.GRAY + " | Beleg: " + finding.evidence()
                    + " | Erklärung: " + finding.explanation()
                    + " | Empfehlung: " + finding.recommendation();
            if (sender != null) sender.sendMessage(message);
            else getLogger().info("Finding: " + finding.title() + " — " + finding.explanation() + " — " + finding.recommendation());
        }

        List<ActionProposal> created = proposalStore.addAll(analysis.actions());
        for (ActionProposal proposal : created) {
            String message = ChatColor.GOLD + "Vorschlag " + proposal.id() + ": " + proposal.title()
                    + ChatColor.GRAY + " (Risiko: " + proposal.risk() + "). Bestätigen: /aiadmin approve " + proposal.id();
            if (sender != null) sender.sendMessage(message);
            else getLogger().info("Pending proposal " + proposal.id() + ": " + proposal.title() + " [" + proposal.type() + "]");
        }
        if (created.isEmpty() && sender != null) sender.sendMessage(ChatColor.GREEN + "Keine Reparaturvorschläge erstellt.");
        if (sender != null) sender.sendMessage(ChatColor.GRAY + "AIAdmin hat nichts geändert. Du entscheidest über jeden Vorschlag.");
    }

    private void sendToAdmins(String message, boolean automatic) {
        if (!automatic) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp() || player.hasPermission("aiadmin.admin")) player.sendMessage(message);
        }
    }

    private void tell(CommandSender sender, String message) {
        if (sender == null) getLogger().info(ChatColor.stripColor(message));
        else sender.sendMessage(message);
    }

    public java.nio.file.Path getServerRoot() { return serverRoot; }
    public ProposalStore getProposalStore() { return proposalStore; }
    public ActionExecutor getActionExecutor() { return actionExecutor; }
}
