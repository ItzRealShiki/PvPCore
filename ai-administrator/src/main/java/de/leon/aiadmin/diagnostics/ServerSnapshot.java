package de.leon.aiadmin.diagnostics;

import java.util.List;

public record ServerSnapshot(
        String serverVersion,
        String minecraftVersion,
        String javaVersion,
        int onlinePlayers,
        int maxPlayers,
        long usedMemoryMb,
        long maxMemoryMb,
        List<String> plugins,
        String recentLogs,
        String latestCrashReport
) {
    public String toPromptText() {
        StringBuilder out = new StringBuilder(8000);
        out.append("Server version: ").append(serverVersion).append('\n');
        out.append("Minecraft version: ").append(minecraftVersion).append('\n');
        out.append("Java version: ").append(javaVersion).append('\n');
        out.append("Players online: ").append(onlinePlayers).append('/').append(maxPlayers).append('\n');
        out.append("Memory used/max (MiB): ").append(usedMemoryMb).append('/').append(maxMemoryMb).append('\n');
        out.append("Installed plugins (names and versions only):\n");
        plugins.forEach(plugin -> out.append("- ").append(plugin).append('\n'));
        if (recentLogs != null && !recentLogs.isBlank()) {
            out.append("\nRecent sanitized log excerpt:\n").append(recentLogs).append('\n');
        }
        if (latestCrashReport != null && !latestCrashReport.isBlank()) {
            out.append("\nLatest crash-report excerpt (sanitized):\n").append(latestCrashReport).append('\n');
        }
        return out.toString();
    }
}
