package de.leon.aiadmin.proposal;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ActionExecutor {
    private static final Pattern PLUGIN_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern YAML_PATH = Pattern.compile("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*");
    private static final Pattern WEATHER = Pattern.compile("(?i)weather (clear|rain|thunder)");
    private static final Pattern DIFFICULTY = Pattern.compile("(?i)difficulty (peaceful|easy|normal|hard)");
    private static final Pattern TIME = Pattern.compile("(?i)time set (day|night|noon|midnight)");
    private static final Pattern GAMERULE = Pattern.compile("(?i)gamerule (keepInventory|doDaylightCycle|doWeatherCycle|mobGriefing|doMobSpawning|doFireTick) (true|false)");

    private final JavaPlugin plugin;
    private final Path serverRoot;

    public ActionExecutor(JavaPlugin plugin, Path serverRoot) {
        this.plugin = plugin;
        this.serverRoot = serverRoot;
    }

    public String execute(ActionProposal proposal) throws IOException {
        return switch (proposal.type()) {
            case "SET_PLUGIN_CONFIG" -> setPluginConfig(proposal);
            case "RUN_SAFE_COMMAND" -> runSafeCommand(proposal);
            case "MANUAL_STEP" -> throw new IllegalArgumentException("This is a manual instruction and cannot be executed by the plugin.");
            default -> throw new IllegalArgumentException("Unknown action type; nothing was changed.");
        };
    }

    private String setPluginConfig(ActionProposal proposal) throws IOException {
        if (!plugin.getConfig().getBoolean("security.allow-approved-config-changes", true)) {
            throw new IllegalStateException("Approved config changes are disabled in AIAdmin/config.yml.");
        }
        String[] target = proposal.target().split(":", 2);
        if (target.length != 2 || !PLUGIN_NAME.matcher(target[0]).matches() || !YAML_PATH.matcher(target[1]).matches()) {
            throw new IllegalArgumentException("Invalid config target. Expected PluginName:yaml.path.");
        }
        Path pluginFolder = serverRoot.resolve("plugins").resolve(target[0]).normalize();
        Path allowedFolder = serverRoot.resolve("plugins").normalize();
        if (!pluginFolder.startsWith(allowedFolder)) throw new IllegalArgumentException("Config target is outside the plugins directory.");
        Path configPath = pluginFolder.resolve("config.yml").normalize();
        if (!configPath.startsWith(pluginFolder) || !Files.isRegularFile(configPath)) {
            throw new IllegalArgumentException("The target plugin's existing config.yml was not found.");
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configPath.toFile());
        if (!yaml.contains(target[1])) {
            throw new IllegalArgumentException("That setting does not already exist. AIAdmin will not invent new config keys.");
        }
        Object scalar = parseScalar(proposal.value());
        String backupName = "config.yml.aiadmin-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path backup = pluginFolder.resolve(backupName);
        Files.copy(configPath, backup, StandardCopyOption.COPY_ATTRIBUTES);
        yaml.set(target[1], scalar);
        yaml.save(configPath.toFile());
        return "Updated " + target[0] + ":" + target[1] + ". Backup: " + backup.getFileName();
    }

    private String runSafeCommand(ActionProposal proposal) {
        if (!plugin.getConfig().getBoolean("security.allow-approved-safe-commands", true)) {
            throw new IllegalStateException("Approved safe commands are disabled in AIAdmin/config.yml.");
        }
        String command = proposal.target().trim();
        if (!isSafeCommand(command)) throw new IllegalArgumentException("The command is not on AIAdmin's safe command allowlist.");
        boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (!success) throw new IllegalStateException("The server did not accept the command.");
        return "Executed approved command: /" + command;
    }

    private static boolean isSafeCommand(String command) {
        return command.equalsIgnoreCase("save-all")
                || WEATHER.matcher(command).matches()
                || DIFFICULTY.matcher(command).matches()
                || TIME.matcher(command).matches()
                || GAMERULE.matcher(command).matches();
    }

    private static Object parseScalar(String value) {
        if (value == null || value.length() > 300 || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException("Only short, single-line scalar values are allowed.");
        }
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        if (value.matches("-?[0-9]{1,9}")) return Integer.parseInt(value);
        if (value.matches("-?[0-9]{1,9}\\.[0-9]{1,6}")) return Double.parseDouble(value);
        return value;
    }
}
