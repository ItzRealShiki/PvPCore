package de.leon.pvpcore;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

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
