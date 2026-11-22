package de.leon.aiadmin.diagnostics;

import de.leon.aiadmin.AIAdminPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LogMonitor {
    private final AIAdminPlugin plugin;
    private final Path latestLog;
    private final AtomicBoolean triggered = new AtomicBoolean(false);
    private BukkitTask task;
    private long lastSize;
    private long lastTriggeredAt;

    public LogMonitor(AIAdminPlugin plugin, Path serverRoot) {
        this.plugin = plugin;
        this.latestLog = serverRoot.resolve("logs").resolve("latest.log");
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("monitoring.enabled", true)) return;
        try { lastSize = Files.isRegularFile(latestLog) ? Files.size(latestLog) : 0L; }
        catch (IOException ignored) { lastSize = 0L; }
        int seconds = Math.max(15, Math.min(300, plugin.getConfig().getInt("monitoring.interval-seconds", 30)));
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::poll, seconds * 20L, seconds * 20L);
        plugin.getLogger().info("Automatic log monitoring enabled (every " + seconds + " seconds).");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void poll() {
        if (!Files.isRegularFile(latestLog)) return;
        try {
            long size = Files.size(latestLog);
            if (size < lastSize) lastSize = 0L;
            if (size <= lastSize) return;
            long start = lastSize;
            if (size - start > 256 * 1024L) start = Math.max(0L, size - 256 * 1024L);
            int length = (int) Math.min(Integer.MAX_VALUE, size - start);
            ByteBuffer buffer = ByteBuffer.allocate(length);
            try (FileChannel channel = FileChannel.open(latestLog, StandardOpenOption.READ)) {
                channel.position(start);
                while (buffer.hasRemaining() && channel.read(buffer) > 0) { }
            }
            lastSize = size;
            buffer.flip();
            String appended = LogReader.sanitize(StandardCharsets.UTF_8.decode(buffer).toString());
            List<String> errors = new ArrayList<>();
            for (String line : appended.split("\\R")) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.contains("[error]") || lower.contains("[severe]") || lower.contains("exception")
                        || lower.contains("caused by:") || lower.contains("fatal")) errors.add(line);
            }
            if (errors.isEmpty()) return;
            long now = System.currentTimeMillis();
            long cooldown = Math.max(60, plugin.getConfig().getLong("monitoring.cooldown-seconds", 300)) * 1000L;
            if (now - lastTriggeredAt < cooldown || !triggered.compareAndSet(false, true)) return;
            lastTriggeredAt = now;
            int maxLines = Math.max(5, Math.min(80, plugin.getConfig().getInt("monitoring.max-trigger-lines", 35)));
            String excerpt = String.join("\n", errors.subList(Math.max(0, errors.size() - maxLines), errors.size()));
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    plugin.runAutomaticAnalysis("Neue Fehler in logs/latest.log erkannt. Bitte ordne die folgenden Zeilen ein:\n" + excerpt);
                } finally {
                    triggered.set(false);
                }
            });
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not inspect latest.log: " + exception.getClass().getSimpleName());
        }
    }
}
