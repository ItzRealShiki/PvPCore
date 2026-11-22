package de.leon.aiadmin.proposal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.leon.aiadmin.ai.AIAnalysis;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ProposalStore {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, ActionProposal> proposals = new LinkedHashMap<>();

    public ProposalStore(JavaPlugin plugin) {
        this.file = plugin.getDataFolder().toPath().resolve("proposals.json");
        load();
    }

    public synchronized List<ActionProposal> addAll(List<AIAnalysis.ProposedActionData> actions) {
        List<ActionProposal> created = new ArrayList<>();
        for (AIAnalysis.ProposedActionData action : actions) {
            String id;
            do {
                id = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
            } while (proposals.containsKey(id));
            ActionProposal proposal = new ActionProposal(id, action.type(), action.title(), action.target(),
                    action.value(), action.reason(), action.risk(), action.requiresRestart(), "PENDING", Instant.now().toString());
            proposals.put(id, proposal);
            created.add(proposal);
        }
        save();
        return List.copyOf(created);
    }

    public synchronized Optional<ActionProposal> find(String id) {
        return Optional.ofNullable(proposals.get(id.toUpperCase(Locale.ROOT)));
    }

    public synchronized List<ActionProposal> pending() {
        return proposals.values().stream().filter(p -> p.status().equals("PENDING")).toList();
    }

    public synchronized void updateStatus(String id, String status) {
        ActionProposal old = proposals.get(id.toUpperCase(Locale.ROOT));
        if (old == null) return;
        proposals.put(old.id(), new ActionProposal(old.id(), old.type(), old.title(), old.target(), old.value(),
                old.reason(), old.risk(), old.requiresRestart(), status, old.createdAt()));
        save();
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonArray()) return;
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject json = element.getAsJsonObject();
                ActionProposal proposal = new ActionProposal(
                        string(json, "id"), string(json, "type"), string(json, "title"), string(json, "target"),
                        string(json, "value"), string(json, "reason"), string(json, "risk"),
                        json.has("requiresRestart") && json.get("requiresRestart").getAsBoolean(),
                        string(json, "status"), string(json, "createdAt"));
                if (!proposal.id().isBlank()) proposals.put(proposal.id(), proposal);
            }
        } catch (Exception ignored) {
            // Keep the plugin available even if the proposal cache is damaged.
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            JsonArray array = new JsonArray();
            for (ActionProposal proposal : proposals.values()) {
                JsonObject object = new JsonObject();
                object.addProperty("id", proposal.id());
                object.addProperty("type", proposal.type());
                object.addProperty("title", proposal.title());
                object.addProperty("target", proposal.target());
                object.addProperty("value", proposal.value());
                object.addProperty("reason", proposal.reason());
                object.addProperty("risk", proposal.risk());
                object.addProperty("requiresRestart", proposal.requiresRestart());
                object.addProperty("status", proposal.status());
                object.addProperty("createdAt", proposal.createdAt());
                array.add(object);
            }
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                gson.toJson(array, writer);
            }
        } catch (IOException ignored) { }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }
}
