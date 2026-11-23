package de.leon.customguitest.css;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public final class CssGuiEngine {
    private final JavaPlugin plugin;
    private final Path menusDirectory;

    public CssGuiEngine(JavaPlugin plugin) {
        this.plugin = plugin;
        this.menusDirectory = plugin.getDataFolder().toPath().resolve("menus");
    }

    public void initialize() {
        try {
            Files.createDirectories(menusDirectory);
            Path example = menusDirectory.resolve("main.mcss");
            if (!Files.exists(example)) {
                try (var in = plugin.getResource("menus/main.mcss")) {
                    if (in != null) Files.copy(in, example);
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not prepare CSS menu directory: " + exception.getMessage());
        }
    }

    public Set<String> menuNames() {
        try (var files = Files.list(menusDirectory)) {
            Set<String> names = new TreeSet<>();
            files.filter(p -> p.getFileName().toString().endsWith(".mcss"))
                    .forEach(p -> names.add(p.getFileName().toString().replaceFirst("\\.mcss$", "")));
            return names;
        } catch (IOException exception) { return Set.of(); }
    }

    public void open(Player player, String name) throws IOException {
        if (!name.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid menu name.");
        Path file = menusDirectory.resolve(name + ".mcss").normalize();
        if (!file.startsWith(menusDirectory) || !Files.isRegularFile(file))
            throw new NoSuchFileException("Menu not found: " + name);

        CssStyleSheet sheet = CssStyleSheet.parse(Files.readString(file, StandardCharsets.UTF_8));
        Map<String, String> screen = sheet.stylesFor("screen");
        int rows = parseInt(screen.getOrDefault("rows", "6"), 6);
        rows = Math.max(1, Math.min(6, rows));
        String title = unquote(screen.getOrDefault("title", name));
        CssGuiHolder holder = new CssGuiHolder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, Component.text(title));
        holder.setInventory(inventory);

        Material background = material(screen.getOrDefault("background", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE);
        ItemStack filler = namedItem(background, " ");
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);

        for (CssStyleSheet.Rule rule : sheet.rules()) {
            String selector = rule.selector().trim();
            if (!selector.startsWith(".") && !selector.startsWith("#")) continue;
            Map<String, String> style = rule.declarations();
            if (!style.containsKey("slot")) continue;
            int slot = parseInt(style.get("slot"), -1);
            if (slot < 0 || slot >= inventory.getSize()) continue;
            Material type = material(style.getOrDefault("material", "PAPER"), Material.PAPER);
            String text = unquote(style.getOrDefault("text", selector.substring(1)));
            ItemStack item = namedItem(type, text);
            ItemMeta meta = item.getItemMeta();
            String color = style.get("color");
            if (color != null) meta.displayName(Component.text(text, parseColor(color, TextColor.color(255,255,255))));
            String lore = style.get("lore");
            if (lore != null) meta.lore(Arrays.stream(unquote(lore).split("\\|")).map(line -> Component.text(line)).toList());
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            holder.setAction(slot, unquote(style.getOrDefault("action", "")));
        }
        player.openInventory(inventory);
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    private Material material(String value, Material fallback) {
        Material parsed = Material.matchMaterial(unquote(value).toUpperCase(Locale.ROOT));
        return parsed == null ? fallback : parsed;
    }

    private TextColor parseColor(String value, TextColor fallback) {
        try { return TextColor.fromHexString(value.trim()); }
        catch (Exception ignored) { return fallback; }
    }

    private int parseInt(String value, int fallback) {
        try { return Integer.parseInt(unquote(value)); }
        catch (Exception ignored) { return fallback; }
    }

    private String unquote(String value) {
        String v = value.trim();
        if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))))
            return v.substring(1, v.length() - 1);
        return v;
    }
}
