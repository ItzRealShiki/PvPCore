package de.leon.pvpcore;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class LagMonitor implements Listener {
    private static final int MAX_TICK_SAMPLES = 1_200;
    private static final int MAX_SPIKES = 24;
    private static final int MAX_OPERATIONS = 180;
    private static final long WINDOW_MILLIS = 60_000L;

    private final PvPCore plugin;
    private final Object lock = new Object();
    private final Deque<TickSample> tickSamples = new ArrayDeque<>();
    private final Deque<SpikeSample> spikes = new ArrayDeque<>();
    private final Deque<OperationSample> operations = new ArrayDeque<>();

    private volatile List<WorldSnapshot> worldSnapshots = List.of();
    private volatile long worldSnapshotAt;
    private int worldSamplerTask = -1;

    public LagMonitor(PvPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        sampleWorlds();
        worldSamplerTask = Bukkit.getScheduler()
                .runTaskTimer(plugin, this::sampleWorlds, 200L, 200L).getTaskId();
    }

    public void stop() {
        if (worldSamplerTask != -1) {
            Bukkit.getScheduler().cancelTask(worldSamplerTask);
            worldSamplerTask = -1;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerTickEnd(ServerTickEndEvent event) {
        long now = System.currentTimeMillis();
        double duration = event.getTickDuration();
        TickSample sample = new TickSample(now, duration, event.getTimeRemaining());

        synchronized (lock) {
            tickSamples.addLast(sample);
            while (tickSamples.size() > MAX_TICK_SAMPLES) tickSamples.removeFirst();

            if (duration >= 50.0) {
                spikes.addLast(new SpikeSample(now, event.getTickNumber(), duration, event.getTimeRemaining()));
                while (spikes.size() > MAX_SPIKES) spikes.removeFirst();
            }
        }
    }

    /**
     * Records a named operation without logging every call. Calls below 2 ms are ignored
     * to keep the diagnostics overhead low.
     */
    public void recordOperation(String name, long durationNanos) {
        if (durationNanos < 2_000_000L) return;

        long now = System.currentTimeMillis();
        double durationMillis = durationNanos / 1_000_000.0;
        OperationSample sample = new OperationSample(
                name,
                now - Math.max(0L, Math.round(durationMillis)),
                now,
                durationMillis
        );

        synchronized (lock) {
            operations.addLast(sample);
            while (operations.size() > MAX_OPERATIONS) operations.removeFirst();
        }
    }

    private void sampleWorlds() {
        List<WorldSnapshot> next = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            next.add(new WorldSnapshot(
                    world.getName(),
                    world.getPlayerCount(),
                    world.getChunkCount(),
                    world.getEntityCount(),
                    world.getTileEntityCount(),
                    world.getTickableTileEntityCount()
            ));
        }
        worldSnapshots = List.copyOf(next);
        worldSnapshotAt = System.currentTimeMillis();
    }

    public Snapshot snapshot() {
        long now = System.currentTimeMillis();
        List<TickSample> recentTicks = new ArrayList<>();
        List<SpikeSample> recentSpikes = new ArrayList<>();
        List<OperationSample> recentOperations = new ArrayList<>();

        synchronized (lock) {
            for (TickSample sample : tickSamples) {
                if (now - sample.timestampMillis() <= WINDOW_MILLIS) recentTicks.add(sample);
            }
            for (SpikeSample spike : spikes) {
                if (now - spike.timestampMillis() <= WINDOW_MILLIS) recentSpikes.add(spike);
            }
            for (OperationSample operation : operations) {
                if (now - operation.endMillis() <= WINDOW_MILLIS) recentOperations.add(operation);
            }
        }

        double sum = 0.0;
        double peak = 0.0;
        int over50 = 0;
        int over100 = 0;
        int over250 = 0;
        double[] sorted = new double[recentTicks.size()];
        for (int i = 0; i < recentTicks.size(); i++) {
            double value = recentTicks.get(i).durationMillis();
            sorted[i] = value;
            sum += value;
            peak = Math.max(peak, value);
            if (value >= 50.0) over50++;
            if (value >= 100.0) over100++;
            if (value >= 250.0) over250++;
        }
        java.util.Arrays.sort(sorted);
        double average = sorted.length == 0 ? 0.0 : sum / sorted.length;
        double p95 = sorted.length == 0 ? 0.0 : sorted[Math.min(sorted.length - 1,
                Math.max(0, (int) Math.ceil(sorted.length * 0.95) - 1))];

        double[] tps = Bukkit.getTPS();
        double usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        double committedMemory = Runtime.getRuntime().totalMemory();
        double maxMemory = Runtime.getRuntime().maxMemory();

        long gcCount = 0L;
        long gcTime = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (bean.getCollectionCount() > 0) gcCount += bean.getCollectionCount();
            if (bean.getCollectionTime() > 0) gcTime += bean.getCollectionTime();
        }

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int threadCount = threadBean.getThreadCount();

        recentOperations.sort(Comparator.comparingDouble(OperationSample::durationMillis).reversed());
        List<OperationSample> topOperations = recentOperations.stream().limit(8).toList();

        return new Snapshot(
                now,
                valueAt(tps, 0), valueAt(tps, 1), valueAt(tps, 2),
                Bukkit.getAverageTickTime(),
                average,
                p95,
                peak,
                recentTicks.size(),
                over50,
                over100,
                over250,
                List.copyOf(recentSpikes),
                topOperations,
                worldSnapshots,
                worldSnapshotAt,
                usedMemory,
                committedMemory,
                maxMemory,
                gcCount,
                gcTime,
                threadCount,
                Bukkit.getOnlinePlayers().size(),
                Bukkit.getPluginManager().getPlugins().length,
                Bukkit.getScheduler().getPendingTasks().size()
        );
    }


    /**
     * Creates a compact, shareable report from the last 60 seconds of captured
     * telemetry plus a point-in-time snapshot of JVM, server, worlds and plugins.
     * It intentionally excludes player names, UUIDs, IP addresses and file paths.
     */
    public String diagnosticsReport() {
        Snapshot data = snapshot();
        StringBuilder out = new StringBuilder(8_000);
        DateTimeFormatter clock = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                .withZone(ZoneId.systemDefault());
        Runtime runtime = Runtime.getRuntime();
        java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();

        out.append("=== PRAC LAG DIAGNOSTICS ===\n");
        out.append("Captured: ").append(clock.format(Instant.ofEpochMilli(data.capturedAtMillis()))).append("\n");
        out.append("Report window: last 60 seconds; world counts are sampled every 10 seconds.\n\n");

        out.append("[SERVER]\n");
        out.append("Platform: ").append(Bukkit.getName()).append("\n");
        out.append("Bukkit/Paper version: ").append(Bukkit.getBukkitVersion()).append("\n");
        out.append("Server implementation version: ").append(Bukkit.getVersion()).append("\n");
        out.append("Minecraft version: ").append(Bukkit.getMinecraftVersion()).append("\n");
        out.append("Online players: ").append(data.onlinePlayers()).append("\n");
        out.append("Loaded plugins: ").append(data.pluginCount()).append("\n");
        out.append("Uptime: ").append(formatDuration(ManagementFactory.getRuntimeMXBean().getUptime())).append("\n");
        out.append("Java: ").append(System.getProperty("java.version", "unknown"))
                .append(" (").append(System.getProperty("java.vendor", "unknown")).append(")\n");
        out.append("OS: ").append(os.getName()).append(" ").append(os.getVersion())
                .append(" / ").append(os.getArch()).append("\n");
        out.append("Available processors: ").append(runtime.availableProcessors()).append("\n");
        out.append("System load average: ").append(number(os.getSystemLoadAverage())).append("\n");
        if (os instanceof com.sun.management.OperatingSystemMXBean extended) {
            out.append("Process CPU load: ").append(percent(extended.getProcessCpuLoad())).append("\n");
            out.append("System CPU load: ").append(percent(extended.getCpuLoad())).append("\n");
        } else {
            out.append("Process/System CPU load: unavailable from this JVM.\n");
        }
        out.append("\n");

        out.append("[TICK PERFORMANCE]\n");
        out.append("TPS 1m/5m/15m: ").append(number(data.tps1m())).append(" / ")
                .append(number(data.tps5m())).append(" / ").append(number(data.tps15m())).append("\n");
        out.append("Paper average tick time: ").append(number(data.paperAverageMspt())).append(" ms\n");
        out.append("Sampled average MSPT (60s): ").append(number(data.windowAverageMspt())).append(" ms\n");
        out.append("P95 MSPT (60s): ").append(number(data.p95Mspt())).append(" ms\n");
        out.append("Peak MSPT (60s): ").append(number(data.peakMspt())).append(" ms\n");
        out.append("Ticks sampled (60s): ").append(data.sampledTicks()).append("\n");
        out.append("Ticks >=50/100/250 ms: ").append(data.spikesOver50()).append(" / ")
                .append(data.spikesOver100()).append(" / ").append(data.spikesOver250()).append("\n");
        out.append("\n");

        out.append("[JVM MEMORY]\n");
        out.append("Heap used: ").append(megabytes(data.usedMemoryBytes())).append(" MB (")
                .append(number(data.heapPercent())).append("% of max)\n");
        out.append("Heap committed: ").append(megabytes(data.committedMemoryBytes())).append(" MB\n");
        out.append("Heap max: ").append(megabytes(data.maxMemoryBytes())).append(" MB\n");
        java.lang.management.MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        out.append("Non-heap used: ").append(megabytes(nonHeap.getUsed())).append(" MB\n");
        out.append("GC total collections/time: ").append(data.gcCollections()).append(" / ")
                .append(data.gcTimeMillis()).append(" ms\n");
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            out.append("GC ").append(bean.getName()).append(": count=")
                    .append(bean.getCollectionCount()).append(", time=")
                    .append(bean.getCollectionTime()).append(" ms\n");
        }
        out.append("JVM threads: ").append(data.threadCount())
                .append(" (peak ").append(threads.getPeakThreadCount()).append(")\n");
        Map<Thread.State, Integer> threadStates = new EnumMap<>(Thread.State.class);
        for (long id : threads.getAllThreadIds()) {
            ThreadInfo info = threads.getThreadInfo(id);
            if (info != null) threadStates.merge(info.getThreadState(), 1, Integer::sum);
        }
        out.append("Thread states: ").append(threadStates).append("\n");
        out.append("Pending scheduler tasks: ").append(data.pendingTasks()).append("\n");
        out.append("\n");

        out.append("[LOADED WORLDS] (sampled ").append(clock.format(Instant.ofEpochMilli(data.worldSnapshotAt()))).append(")\n");
        out.append("World count: ").append(data.worlds().size()).append("\n");
        for (WorldSnapshot world : data.worlds()) {
            out.append("- ").append(world.name())
                    .append(": players=").append(world.players())
                    .append(", chunks=").append(world.chunks())
                    .append(", entities=").append(world.entities())
                    .append(", blockEntities=").append(world.tileEntities())
                    .append(", tickableBlockEntities=").append(world.tickableTileEntities())
                    .append("\n");
        }
        out.append("Totals: chunks=").append(data.totalChunks())
                .append(", entities=").append(data.totalEntities())
                .append(", blockEntities=").append(data.totalTileEntities())
                .append(", tickableBlockEntities=").append(data.totalTickableTileEntities())
                .append("\n\n");

        out.append("[RECENT TICK SPIKES] (up to ").append(data.spikes().size()).append(" captured)\n");
        if (data.spikes().isEmpty()) {
            out.append("No tick >=50 ms captured in the current 60-second window.\n");
        } else {
            for (SpikeSample spike : data.spikes()) {
                out.append("- ").append(number(spike.durationMillis())).append(" ms")
                        .append(" | tick=").append(spike.tickNumber())
                        .append(" | time=").append(clock.format(Instant.ofEpochMilli(spike.timestampMillis())))
                        .append(" | timeRemaining=")
                        .append(number(spike.timeRemainingNanos() / 1_000_000.0)).append(" ms\n");
            }
        }
        out.append("\n");

        out.append("[SLOW PRAC OPERATIONS] (slowest recorded in current 60-second window)\n");
        if (data.topOperations().isEmpty()) {
            out.append("No instrumented Prac operation >=2 ms was recorded. This does not rule out uninstrumented code.\n");
        } else {
            for (OperationSample operation : data.topOperations()) {
                out.append("- ").append(operation.name())
                        .append(": ").append(number(operation.durationMillis())).append(" ms")
                        .append(" | ended=").append(clock.format(Instant.ofEpochMilli(operation.endMillis())))
                        .append("\n");
            }
        }
        out.append("\n");

        out.append("[PLUGINS]\n");
        for (org.bukkit.plugin.Plugin loadedPlugin : Bukkit.getPluginManager().getPlugins()) {
            out.append("- ").append(loadedPlugin.getName())
                    .append(" ").append(loadedPlugin.getPluginMeta().getVersion())
                    .append(" | enabled=").append(loadedPlugin.isEnabled())
                    .append("\n");
        }
        out.append("\n");
        out.append("[NOTES]\n");
        out.append("- A tick over 50 ms misses the 20 TPS target.\n");
        out.append("- A time-correlated Prac operation is a clue, not proof of root cause.\n");
        out.append("- For a deeper profile during a spike, run /lag profile and include the Spark URL.\n");
        out.append("- This report contains no player names, UUIDs, IP addresses or server file paths.\n");

        return out.toString();
    }

    private static String number(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.2f", value) : "unavailable";
    }

    private static String percent(double value) {
        return value < 0.0 ? "unavailable" : number(value * 100.0) + "%";
    }

    private static String megabytes(double bytes) {
        return bytes < 0.0 ? "unavailable" : number(bytes / 1_048_576.0);
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long remainder = seconds % 60L;
        return days + "d " + hours + "h " + minutes + "m " + remainder + "s";
    }

    private static double valueAt(double[] values, int index) {
        return values != null && values.length > index ? values[index] : 0.0;
    }

    public record TickSample(long timestampMillis, double durationMillis, long timeRemainingNanos) {}
    public record SpikeSample(long timestampMillis, int tickNumber, double durationMillis, long timeRemainingNanos) {}
    public record OperationSample(String name, long startMillis, long endMillis, double durationMillis) {}
    public record WorldSnapshot(String name, int players, int chunks, int entities,
                                int tileEntities, int tickableTileEntities) {}

    public record Snapshot(
            long capturedAtMillis,
            double tps1m, double tps5m, double tps15m,
            double paperAverageMspt,
            double windowAverageMspt, double p95Mspt, double peakMspt,
            int sampledTicks, int spikesOver50, int spikesOver100, int spikesOver250,
            List<SpikeSample> spikes,
            List<OperationSample> topOperations,
            List<WorldSnapshot> worlds,
            long worldSnapshotAt,
            double usedMemoryBytes, double committedMemoryBytes, double maxMemoryBytes,
            long gcCollections, long gcTimeMillis,
            int threadCount, int onlinePlayers, int pluginCount, int pendingTasks
    ) {
        public int totalChunks() {
            return worlds.stream().mapToInt(WorldSnapshot::chunks).sum();
        }
        public int totalEntities() {
            return worlds.stream().mapToInt(WorldSnapshot::entities).sum();
        }
        public int totalTileEntities() {
            return worlds.stream().mapToInt(WorldSnapshot::tileEntities).sum();
        }
        public int totalTickableTileEntities() {
            return worlds.stream().mapToInt(WorldSnapshot::tickableTileEntities).sum();
        }
        public double heapPercent() {
            return maxMemoryBytes <= 0.0 ? 0.0 : usedMemoryBytes / maxMemoryBytes * 100.0;
        }
    }
}
