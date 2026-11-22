package de.leon.customguitest.art;

import de.leon.customguitest.CustomGuiTestPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class MapArtworkRegistry {
    private final CustomGuiTestPlugin plugin;
    private final Map<MapArtwork, MapView> views = new EnumMap<>(MapArtwork.class);

    public MapArtworkRegistry(CustomGuiTestPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        World world = Bukkit.getWorlds().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No loaded world is available for creating map artwork."));
        for (MapArtwork artwork : MapArtwork.values()) {
            String path = "maps." + artwork.name().toLowerCase();
            int savedId = plugin.getConfig().getInt(path, -1);
            MapView view = savedId >= 0 ? Bukkit.getMap(savedId) : null;
            if (view == null) {
                view = Bukkit.createMap(world);
                plugin.getConfig().set(path, view.getId());
            }
            view.setWorld(world);
            view.setLocked(true);
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(false);
            for (MapRenderer renderer : new ArrayList<>(view.getRenderers())) {
                view.removeRenderer(renderer);
            }
            view.addRenderer(new ArtworkMapRenderer(artwork));
            views.put(artwork, view);
        }
        plugin.saveConfig();
        plugin.getLogger().info("Loaded " + views.size() + " custom map textures for GUI items.");
    }

    public ItemStack item(MapArtwork artwork, String name, int hexColor, String... loreLines) {
        MapView view = views.get(artwork);
        if (view == null) {
            throw new IllegalStateException("Map artwork has not been initialized: " + artwork);
        }
        ItemStack stack = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) stack.getItemMeta();
        meta.setMapView(view);
        meta.displayName(Component.text(name, TextColor.color(hexColor))
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = java.util.Arrays.stream(loreLines)
                .map(line -> (Component) Component.text(line, TextColor.color(0xB7C8DF))
                        .decoration(TextDecoration.ITALIC, false))
                .toList();
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack tile(MapArtwork artwork) {
        MapView view = views.get(artwork);
        if (view == null) {
            throw new IllegalStateException("Map artwork has not been initialized: " + artwork);
        }
        ItemStack stack = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) stack.getItemMeta();
        meta.setMapView(view);
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }
}
