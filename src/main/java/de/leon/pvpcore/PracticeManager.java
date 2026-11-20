package de.leon.pvpcore;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

public final class PracticeManager {
    public enum Mode { STUN_SLAM, SWORD }
    private final PvPCore plugin;
    private World world;
    public PracticeManager(PvPCore plugin) { this.plugin = plugin; }

    public Mode mode(Player p) {
        try { return Mode.valueOf(plugin.getConfig().getString("practice.players." + p.getUniqueId() + ".mode", "STUN_SLAM")); }
        catch (IllegalArgumentException ex) { return Mode.STUN_SLAM; }
    }
    public void setMode(Player p, Mode mode) {
        plugin.getConfig().set("practice.players." + p.getUniqueId() + ".mode", mode.name());
        plugin.saveConfig();
    }
    public void enter(Player p) {
        if (world == null || !world.isLoaded()) {
            world = Bukkit.getWorld("prac_rooms");
            if (world == null) world = new WorldCreator("prac_rooms").type(WorldType.FLAT).createWorld();
        }
        if (world == null) { plugin.message(p, "&cPractice world could not be loaded."); return; }
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime(1000L);
        String base = "practice.players." + p.getUniqueId();
        int index;
        if (plugin.getConfig().contains(base + ".room")) index = plugin.getConfig().getInt(base + ".room");
        else {
            index = plugin.getConfig().getInt("practice.next-room", 0);
            plugin.getConfig().set("practice.next-room", index + 1);
            plugin.getConfig().set(base + ".room", index);
            plugin.saveConfig();
        }
        int cx = 128 + index * 96, cz = 128;
        if (!plugin.getConfig().getBoolean(base + ".built", false)) {
            buildRoom(cx, cz);
            plugin.getConfig().set(base + ".built", true);
            plugin.saveConfig();
        }
        plugin.bots().removeOwned(p);
        p.teleport(new Location(world, cx + 0.5, 81, cz + 0.5));
        applyKit(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && p.getWorld() == world) plugin.bots().spawn(p);
        }, 2L);
        plugin.message(p, "&aEntered your private " + (mode(p) == Mode.SWORD ? "&bSword" : "&6Stun Slam") + " room.");
    }
    private void buildRoom(int cx, int cz) {
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) {
            world.getBlockAt(cx+x, 80, cz+z).setType(Material.SMOOTH_STONE, false);
            if (Math.abs(x) == 24 || Math.abs(z) == 24)
                for (int y=81; y<=86; y++) world.getBlockAt(cx+x,y,cz+z).setType(Material.GLASS,false);
        }
    }
    public void applyKit(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[]{gear(Material.DIAMOND_BOOTS),gear(Material.DIAMOND_LEGGINGS),gear(Material.DIAMOND_CHESTPLATE),gear(Material.DIAMOND_HELMET)});
        if (mode(p) == Mode.SWORD) p.getInventory().setItem(0, gear(Material.DIAMOND_SWORD));
        else {
            p.getInventory().setItem(0, enchanted(Material.NETHERITE_AXE, "sharpness", 5));
            ItemStack mace = enchanted(Material.MACE, "density", 5); enchant(mace, "wind_burst", 3);
            p.getInventory().setItem(1, mace);
            p.getInventory().setItem(2, enchanted(Material.SPEAR, "lunge", 3));
        }
        loadSavedKit(p, p.getInventory());
        p.getInventory().setHeldItemSlot(0);
        p.setHealth(20); p.setFoodLevel(20); p.setSaturation(20); p.setFireTicks(0);
    }
    public void applyBotKit(Player bot, Player owner) {
        bot.getInventory().clear();
        bot.getInventory().setArmorContents(new ItemStack[]{gear(Material.DIAMOND_BOOTS),gear(Material.DIAMOND_LEGGINGS),gear(Material.DIAMOND_CHESTPLATE),gear(Material.DIAMOND_HELMET)});
        if (mode(owner) == Mode.SWORD) bot.getInventory().setItem(0, gear(Material.DIAMOND_SWORD));
        else {
            bot.getInventory().setItem(0, enchanted(Material.NETHERITE_AXE, "sharpness", 5));
            ItemStack mace = enchanted(Material.MACE, "density", 5); enchant(mace, "wind_burst", 3);
            bot.getInventory().setItem(1, mace);
            bot.getInventory().setItem(2, enchanted(Material.SPEAR, "lunge", 3));
        }
        loadSavedKit(owner, bot.getInventory());
    }
    public void applyKitToInventory(Player player, Inventory editor) {
        editor.clear();
        if (mode(player) == Mode.SWORD) editor.setItem(0, gear(Material.DIAMOND_SWORD));
        else {
            editor.setItem(0, enchanted(Material.NETHERITE_AXE, "sharpness", 5));
            ItemStack mace = enchanted(Material.MACE, "density", 5); enchant(mace, "wind_burst", 3);
            editor.setItem(1, mace);
            editor.setItem(2, enchanted(Material.SPEAR, "lunge", 3));
        }
        editor.setItem(45, gear(Material.DIAMOND_HELMET));
        editor.setItem(46, gear(Material.DIAMOND_CHESTPLATE));
        editor.setItem(47, gear(Material.DIAMOND_LEGGINGS));
        editor.setItem(48, gear(Material.DIAMOND_BOOTS));
        String path = "practice.players." + player.getUniqueId() + ".kits." + mode(player).name();
        if (!plugin.getConfig().contains(path + ".contents")) return;
        java.util.List<?> contents = plugin.getConfig().getList(path + ".contents");
        if (contents != null) for (int slot=0; slot<Math.min(36, contents.size()); slot++) {
            Object value = contents.get(slot);
            if (value instanceof ItemStack item) editor.setItem(slot, item.clone());
        }
        editor.setItem(45, plugin.getConfig().getItemStack(path + ".helmet"));
        editor.setItem(46, plugin.getConfig().getItemStack(path + ".chestplate"));
        editor.setItem(47, plugin.getConfig().getItemStack(path + ".leggings"));
        editor.setItem(48, plugin.getConfig().getItemStack(path + ".boots"));
    }
    public void saveKit(Player player, Inventory editor) {
        String path = "practice.players." + player.getUniqueId() + ".kits." + mode(player).name();
        java.util.List<ItemStack> contents = new java.util.ArrayList<>();
        for (int slot=0; slot<36; slot++) contents.add(editor.getItem(slot));
        plugin.getConfig().set(path + ".contents", contents);
        plugin.getConfig().set(path + ".helmet", editor.getItem(45));
        plugin.getConfig().set(path + ".chestplate", editor.getItem(46));
        plugin.getConfig().set(path + ".leggings", editor.getItem(47));
        plugin.getConfig().set(path + ".boots", editor.getItem(48));
        plugin.saveConfig();
    }
    private void loadSavedKit(Player player, PlayerInventory inventory) {
        String path = "practice.players." + player.getUniqueId() + ".kits." + mode(player).name();
        if (!plugin.getConfig().contains(path + ".contents")) return;
        java.util.List<?> contents = plugin.getConfig().getList(path + ".contents");
        if (contents != null) for (int slot=0; slot<Math.min(36, contents.size()); slot++) {
            Object value = contents.get(slot);
            if (value instanceof ItemStack item) inventory.setItem(slot, item.clone());
        }
        inventory.setHelmet(plugin.getConfig().getItemStack(path + ".helmet"));
        inventory.setChestplate(plugin.getConfig().getItemStack(path + ".chestplate"));
        inventory.setLeggings(plugin.getConfig().getItemStack(path + ".leggings"));
        inventory.setBoots(plugin.getConfig().getItemStack(path + ".boots"));
    }
    private ItemStack gear(Material type) {
        ItemStack item = new ItemStack(type); ItemMeta meta = item.getItemMeta(); meta.setUnbreakable(true); item.setItemMeta(meta); return item;
    }
    private ItemStack enchanted(Material type, String key, int level) {
        ItemStack item = gear(type); enchant(item,key,level); return item;
    }
    private void enchant(ItemStack item, String key, int level) {
        Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        if (e == null) { plugin.getLogger().warning("Missing enchantment: " + key); return; }
        ItemMeta meta = item.getItemMeta(); meta.addEnchant(e,level,true); item.setItemMeta(meta);
    }
}