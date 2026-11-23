package de.leon.pvpcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

public final class LagMenu implements Listener {
    private static final DecimalFormat NUMBER = new DecimalFormat("0.0");
    private final PvPCore plugin;

    private enum Page { OVERVIEW, WORLDS, SPIKES, ADVICE }

    private static final class Holder implements InventoryHolder {
        private final Page page;
        private Inventory inventory;

        private Holder(Page page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public LagMenu(PvPCore plugin) {
        this.plugin = plugin;
    }

    public void openOverview(Player player) {
        open(player, Page.OVERVIEW);
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(plugin.color(name));
        if (lore.length > 0) meta.setLore(Arrays.stream(lore).map(plugin::color).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private Inventory create(Page page, String title) {
        Holder holder = new Holder(page);
        Inventory inventory = Bukkit.createInventory(holder, 54, plugin.color(title));
        holder.inventory = inventory;

        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, "&0 ");
        for (int i = 0; i < 54; i++) inventory.setItem(i, filler);
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, item(Material.GRAY_STAINED_GLASS_PANE, "&8 "));
            inventory.setItem(45 + i, item(Material.GRAY_STAINED_GLASS_PANE, "&8 "));
        }
        return inventory;
    }

    private void open(Player player, Page page) {
        LagMonitor.Snapshot data = plugin.lag().snapshot();
        Inventory inventory = switch (page) {
            case OVERVIEW -> overview(data);
            case WORLDS -> worlds(data);
            case SPIKES -> spikes(data);
            case ADVICE -> advice(data);
        };
        player.openInventory(inventory);
    }

    private Inventory overview(LagMonitor.Snapshot data) {
        Inventory inv = create(Page.OVERVIEW, "&8&lPRAC &8» &0LAG MONITOR");
        inv.setItem(4, item(Material.BEACON, "&a&lPRAC SERVER TELEMETRY",
                "&7Live server health and recent lag history.",
                "&8Last refresh: " + time(data.capturedAtMillis())));

        String tpsColor = data.tps1m() >= 19.5 ? "&a" : data.tps1m() >= 18.0 ? "&e" : "&c";
        inv.setItem(10, item(Material.CLOCK, "&b&lTICK RATE",
                "&7TPS (1m): " + tpsColor + NUMBER.format(data.tps1m()),
                "&7TPS (5m): &f" + NUMBER.format(data.tps5m()),
                "&7TPS (15m): &f" + NUMBER.format(data.tps15m()),
                "&8Target: 20 TPS"));
        String msptColor = data.windowAverageMspt() < 40.0 ? "&a"
                : data.windowAverageMspt() < 50.0 ? "&e" : "&c";
        inv.setItem(12, item(Material.REPEATER, "&e&lTICK COST / MSPT",
                "&7Prac 60s average: " + msptColor + NUMBER.format(data.windowAverageMspt()) + " ms",
                "&7P95: &f" + NUMBER.format(data.p95Mspt()) + " ms",
                "&7Peak: " + (data.peakMspt() >= 100.0 ? "&c" : "&f") + NUMBER.format(data.peakMspt()) + " ms",
                "&7Paper average: &f" + NUMBER.format(data.paperAverageMspt()) + " ms",
                "&820 TPS target: below 50 ms"));
        inv.setItem(14, item(Material.REDSTONE_BLOCK, "&c&lJVM MEMORY",
                "&7Used heap: &f" + mb(data.usedMemoryBytes()) + " MB",
                "&7Committed: &f" + mb(data.committedMemoryBytes()) + " MB",
                "&7Maximum: &f" + mb(data.maxMemoryBytes()) + " MB",
                "&7Heap usage: " + heapColor(data.heapPercent()) + NUMBER.format(data.heapPercent()) + "%"));
        inv.setItem(16, item(Material.COMPARATOR, "&d&lRUNTIME",
                "&7GC collections: &f" + data.gcCollections(),
                "&7GC time: &f" + data.gcTimeMillis() + " ms",
                "&7JVM threads: &f" + data.threadCount(),
                "&7Loaded plugins: &f" + data.pluginCount(),
                "&7Pending scheduler tasks: &f" + data.pendingTasks()));

        inv.setItem(20, item(Material.GRASS_BLOCK, "&a&lWORLD LOAD",
                "&7Loaded worlds: &f" + data.worlds().size(),
                "&7Loaded chunks: &f" + data.totalChunks(),
                "&7Entities: &f" + data.totalEntities(),
                "&7Tile entities: &f" + data.totalTileEntities(),
                "&8Click to inspect each world."));
        inv.setItem(22, item(Material.IRON_GOLEM_SPAWN_EGG, "&6&lENTITY & BLOCK LOAD",
                "&7Entities: &f" + data.totalEntities(),
                "&7Tickable block entities: &f" + data.totalTickableTileEntities(),
                "&7Pending tasks: &f" + data.pendingTasks(),
                "&8High counts can increase tick work."));
        inv.setItem(24, item(Material.PLAYER_HEAD, "&b&lPLAYER LOAD",
                "&7Online players: &f" + data.onlinePlayers(),
                "&7World snapshots refresh every 10 seconds.",
                "&8Player count alone does not prove lag."));

        inv.setItem(29, item(Material.FILLED_MAP, "&b&lWORLD BREAKDOWN", "&7Chunks, entities and block entities per world.", "&eClick to open"));
        inv.setItem(31, item(data.spikesOver50() == 0 ? Material.LIME_DYE : Material.REDSTONE,
                "&c&lSPIKE HISTORY",
                "&7Ticks sampled: &f" + data.sampledTicks(),
                "&7Over 50 ms: &f" + data.spikesOver50(),
                "&7Over 100 ms: &f" + data.spikesOver100(),
                "&7Over 250 ms: &f" + data.spikesOver250(),
                "&eClick to inspect recent spikes."));
        inv.setItem(33, item(Material.REDSTONE_TORCH, "&e&lDIAGNOSIS & SUGGESTIONS",
                "&7Suggestions based on measured metrics.",
                "&7Correlation is shown separately from proof.",
                "&eClick to open"));

        inv.setItem(45, item(Material.SPECTRAL_ARROW, "&f&lREFRESH",
                "&7Take a fresh snapshot and update this panel."));
        inv.setItem(49, item(Material.SPYGLASS, "&d&lSTART SPARK PROFILE",
                "&7Runs Paper's Spark profiler for 10 minutes.",
                "&7The report URL appears in the server console.",
                "&cUse while the lag is actually happening.",
                "&eClick to start"));
        inv.setItem(53, item(Material.BARRIER, "&c&lCLOSE", "&7Close this panel."));
        return inv;
    }

    private Inventory worlds(LagMonitor.Snapshot data) {
        Inventory inv = create(Page.WORLDS, "&8&lPRAC &8» &0WORLD DIAGNOSTICS");
        inv.setItem(4, item(Material.FILLED_MAP, "&a&lPER-WORLD LOAD",
                "&7World data is cached every 10 seconds.",
                "&8Captured: " + time(data.worldSnapshotAt())));
        int[] slots = {20, 22, 24, 26, 29, 31, 33, 35};
        int slotIndex = 0;
        for (LagMonitor.WorldSnapshot world : data.worlds()) {
            if (slotIndex >= slots.length) break;
            inv.setItem(slots[slotIndex++], item(Material.GRASS_BLOCK, "&b&l" + world.name(),
                    "&7Players: &f" + world.players(),
                    "&7Loaded chunks: &f" + world.chunks(),
                    "&7Entities: &f" + world.entities(),
                    "&7Tile entities: &f" + world.tileEntities(),
                    "&7Tickable tile entities: &f" + world.tickableTileEntities()));
        }
        inv.setItem(45, item(Material.ARROW, "&f&lBACK", "&7Return to overview."));
        inv.setItem(49, item(Material.SPECTRAL_ARROW, "&f&lREFRESH", "&7Refresh world metrics."));
        inv.setItem(53, item(Material.BARRIER, "&c&lCLOSE"));
        return inv;
    }

    private Inventory spikes(LagMonitor.Snapshot data) {
        Inventory inv = create(Page.SPIKES, "&8&lPRAC &8» &0TICK SPIKE HISTORY");
        inv.setItem(4, item(Material.CLOCK, "&c&lRECENT TICK SPIKES",
                "&7Monitoring window: last 60 seconds",
                "&7Over 50 ms: &f" + data.spikesOver50(),
                "&7Over 100 ms: &f" + data.spikesOver100(),
                "&7Over 250 ms: &f" + data.spikesOver250(),
                "&8A tick longer than 50 ms misses the 20 TPS target."));
        int slot = 10;
        for (int i = data.spikes().size() - 1; i >= 0 && slot <= 16; i--) {
            LagMonitor.SpikeSample spike = data.spikes().get(i);
            inv.setItem(slot++, item(spike.durationMillis() >= 250.0 ? Material.REDSTONE_BLOCK : Material.ORANGE_DYE,
                    "&c&l" + NUMBER.format(spike.durationMillis()) + " ms",
                    "&7Tick: &f" + spike.tickNumber(),
                    "&7Time: &f" + time(spike.timestampMillis()),
                    "&7Time remaining: &f" + (spike.timeRemainingNanos() < 0 ? "over budget" : "within budget")));
        }

        int operationSlot = 28;
        for (LagMonitor.OperationSample operation : data.topOperations()) {
            if (operationSlot > 34) break;
            inv.setItem(operationSlot++, item(Material.COMPARATOR, "&e" + operation.name(),
                    "&7Duration: &f" + NUMBER.format(operation.durationMillis()) + " ms",
                    "&7Ended: &f" + time(operation.endMillis()),
                    "&8Measured Prac operation; correlation is not proof."));
        }
        if (data.spikes().isEmpty()) {
            inv.setItem(13, item(Material.LIME_DYE, "&aNo recent spikes captured",
                    "&7Keep monitoring while reproducing the lag."));
        }
        if (data.topOperations().isEmpty()) {
            inv.setItem(31, item(Material.PAPER, "&7No slow Prac operation recorded",
                    "&7No measured Prac method exceeded 2 ms in this window."));
        }
        inv.setItem(45, item(Material.ARROW, "&f&lBACK", "&7Return to overview."));
        inv.setItem(49, item(Material.SPYGLASS, "&d&lSPARK PROFILE", "&7Start a deeper server profile.", "&eClick to start"));
        inv.setItem(53, item(Material.BARRIER, "&c&lCLOSE"));
        return inv;
    }

    private Inventory advice(LagMonitor.Snapshot data) {
        Inventory inv = create(Page.ADVICE, "&8&lPRAC &8» &0DIAGNOSIS");
        inv.setItem(4, item(Material.REDSTONE_TORCH, "&e&lMEASURED FINDINGS",
                "&7Based on the last 60 seconds of captured metrics.",
                "&8A suggestion is not a definitive root-cause claim."));
        int slot = 19;
        if (data.sampledTicks() < 20) {
            inv.setItem(slot++, item(Material.YELLOW_STAINED_GLASS_PANE, "&eNot enough samples yet",
                    "&7Let the monitor collect more tick samples,",
                    "&7then reproduce the lag spike."));
        } else if (data.spikesOver50() == 0) {
            inv.setItem(slot++, item(Material.LIME_STAINED_GLASS_PANE, "&aNo spike captured",
                    "&7No tick exceeded 50 ms during the current 60-second window.",
                    "&7Open this page immediately after a spike."));
        } else {
            inv.setItem(slot++, item(Material.RED_STAINED_GLASS_PANE, "&cTick overruns detected",
                    "&7Captured over-50-ms ticks: &f" + data.spikesOver50(),
                    "&7Worst recent tick: &f" + NUMBER.format(data.peakMspt()) + " ms",
                    "&7P95: &f" + NUMBER.format(data.p95Mspt()) + " ms"));
        }

        boolean correlated = false;
        if (!data.spikes().isEmpty() && !data.topOperations().isEmpty()) {
            LagMonitor.SpikeSample latestSpike = data.spikes().get(data.spikes().size() - 1);
            for (LagMonitor.OperationSample operation : data.topOperations()) {
                long distance = Math.abs(operation.endMillis() - latestSpike.timestampMillis());
                if (distance <= 2_000L) {
                    correlated = true;
                    inv.setItem(slot++, item(Material.COMPARATOR, "&6Possible Prac correlation",
                            "&7A measured Prac operation ended close to",
                            "&7the latest captured tick spike:",
                            "&f" + operation.name(),
                            "&7Duration: &f" + NUMBER.format(operation.durationMillis()) + " ms",
                            "&8This is a time correlation, not proof."));
                    break;
                }
            }
        }

        if (!correlated) {
            inv.setItem(slot++, item(Material.PAPER, "&eNo direct Prac correlation found",
                    "&7No measured Prac operation ended close to",
                    "&7the latest recorded spike.",
                    "&7The cause may be vanilla, another plugin,",
                    "&7disk/CPU/GC pressure or unmeasured code."));
        }

        if (data.totalChunks() > 2_000) {
            inv.setItem(slot++, item(Material.MAP, "&eHigh loaded-chunk count",
                    "&7Currently loaded chunks: &f" + data.totalChunks(),
                    "&7Review view/simulation distance and chunk tickets.",
                    "&8Count is a load indicator, not proof."));
        }
        if (data.totalEntities() > 1_000) {
            inv.setItem(slot++, item(Material.SPAWNER, "&eHigh entity count",
                    "&7Entities across loaded worlds: &f" + data.totalEntities(),
                    "&7Inspect entity-heavy areas and unnecessary entities."));
        }
        if (data.heapPercent() >= 85.0) {
            inv.setItem(slot++, item(Material.REDSTONE_BLOCK, "&cHigh JVM heap usage",
                    "&7Heap usage: &f" + NUMBER.format(data.heapPercent()) + "%",
                    "&7Check allocation rate and GC activity before",
                    "&7changing the configured heap size."));
        }
        if (slot <= 34) {
            inv.setItem(slot, item(Material.SPYGLASS, "&dRecommended next step: Spark",
                    "&7A sampler can identify hot methods and plugins",
                    "&7while the problem is actively happening.",
                    "&eClick the profiler button below."));
        }

        inv.setItem(45, item(Material.ARROW, "&f&lBACK", "&7Return to overview."));
        inv.setItem(49, item(Material.SPYGLASS, "&d&lSTART SPARK PROFILE",
                "&7Runs for 10 minutes. Check the console for the report URL.",
                "&eClick to start"));
        inv.setItem(53, item(Material.BARRIER, "&c&lCLOSE"));
        return inv;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != top) return;

        int slot = event.getRawSlot();
        if (slot == 53) {
            player.closeInventory();
            return;
        }
        if (slot == 45) {
            open(player, holder.page == Page.OVERVIEW ? Page.OVERVIEW : Page.OVERVIEW);
            return;
        }
        if (slot == 49 && (holder.page == Page.OVERVIEW || holder.page == Page.SPIKES || holder.page == Page.ADVICE)) {
            startSpark(player);
            return;
        }
        if (holder.page == Page.OVERVIEW) {
            if (slot == 29) open(player, Page.WORLDS);
            else if (slot == 31) open(player, Page.SPIKES);
            else if (slot == 33) open(player, Page.ADVICE);
            else if (slot == 45) open(player, Page.OVERVIEW);
        } else if (slot == 49 && holder.page == Page.WORLDS) {
            open(player, Page.WORLDS);
        }
    }

    private void startSpark(Player player) {
        boolean started = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spark profiler start --timeout 300");
        if (started) {
            plugin.message(player, "&aSpark profiler started for 10 minutes. Check the server console for its report URL.");
        } else {
            plugin.message(player, "&cSpark is not available on this server.");
        }
    }

    private static String heapColor(double percent) {
        return percent >= 85.0 ? "&c" : percent >= 70.0 ? "&e" : "&a";
    }

    private static String mb(double bytes) {
        return NUMBER.format(bytes / 1024.0 / 1024.0);
    }

    private static String time(long millis) {
        if (millis <= 0L) return "not available";
        return java.time.LocalTime.ofInstant(java.time.Instant.ofEpochMilli(millis),
                java.time.ZoneId.systemDefault()).withNano(0).toString();
    }
}
