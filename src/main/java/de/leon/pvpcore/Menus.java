package de.leon.pvpcore;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public final class Menus implements Listener {
    private final PvPCore plugin;

    private static final class MenuHolder implements InventoryHolder {
        private final String key;
        private Inventory inventory;
        private final Map<Integer, java.util.UUID> targets = new HashMap<>();
        private MenuHolder(String key) { this.key = key; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public Menus(PvPCore plugin) { this.plugin = plugin; }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(plugin.color(name));
        if (lore.length > 0) meta.setLore(Arrays.stream(lore).map(plugin::color).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private Inventory create(String key, String title, int size) {
        MenuHolder holder = new MenuHolder(key);
        Inventory inv = Bukkit.createInventory(holder, size, plugin.color(title));
        holder.inventory = inv;
        return inv;
    }

    public void openDuels(Player p) {
        Inventory inv = create("duels", "&8ᴅᴜᴇʟ · ɪɴᴠɪᴛᴀᴛɪᴏɴꜱ", 27);
        Player challenger = plugin.fights().incomingFrom(p);
        if (challenger == null || !challenger.isOnline()) {
            inv.setItem(11, item(Material.GRAY_DYE, "&7No incoming invitation", "&7You do not have a pending invitation."));
        } else {
            inv.setItem(11, item(Material.PAPER, "&ePending invitation", "&7From: &f" + challenger.getName(), "&7Use the existing /fight accept or /fight deny commands to respond."));
        }
        int outgoing = plugin.fights().outgoingInviteCount(p);
        inv.setItem(15, item(Material.BARRIER, "&cCancel outgoing invitations", "&7Pending: &f" + outgoing, "&eClick to cancel all outgoing invitations"));
        inv.setItem(22, item(Material.BARRIER, "&7Close"));
        p.openInventory(inv);
    }

    public void openPvp(Player p) {
        Inventory inv = create("pvp", plugin.getConfig().getString("menus.title-pvp", "&8ᴛʀᴀɪɴɪɴɢ ʙᴏᴛ"), 54);
        boolean totem = botOption(p, "infinite-totem", true);
        boolean knockback = botOption(p, "knockback", true);
        boolean shield = botOption(p, "shield", false);
        inv.setItem(10, item(Material.IRON_CHESTPLATE, "&aSurvival Player", "&7Real server-side player entity.", "&7Vanilla gravity, collisions and movement."));
        inv.setItem(13, item(totem ? Material.TOTEM_OF_UNDYING : Material.GRAY_DYE, (totem ? "&a" : "&c") + "Infinite Totem", "&7Uses vanilla totem resurrection.", "&7Automatically replaces a used totem.", "&7Status: " + (totem ? "&aEnabled" : "&cDisabled")));
        inv.setItem(16, item(knockback ? Material.IRON_CHESTPLATE : Material.GRAY_DYE, (knockback ? "&a" : "&c") + "Knockback", "&7Allows normal player knockback.", "&7Status: " + (knockback ? "&aEnabled" : "&cDisabled")));
        inv.setItem(22, item(Material.SHIELD, (shield ? "&a" : "&c") + "Shield", "&7Makes the bot hold its shield up.", "&7Status: " + (shield ? "&aEnabled" : "&cDisabled"), "&eClick to toggle"));
        inv.setItem(28, blastItem(p, "helmet", Material.NETHERITE_HELMET, "Helmet"));
        inv.setItem(30, blastItem(p, "chestplate", Material.NETHERITE_CHESTPLATE, "Chestplate"));
        inv.setItem(32, blastItem(p, "leggings", Material.NETHERITE_LEGGINGS, "Leggings"));
        inv.setItem(34, blastItem(p, "boots", Material.NETHERITE_BOOTS, "Boots"));
        inv.setItem(37, item(botOption(p, "armor-unbreakable", true) ? Material.ANVIL : Material.DAMAGED_ANVIL,
                "&eUnbreakable Armor", "&7Status: " + (botOption(p, "armor-unbreakable", true) ? "&aEnabled" : "&cDisabled"), "&eClick to toggle"));
        inv.setItem(39, item(Material.ENCHANTED_BOOK, "&dProtection Level: " + plugin.getConfig().getInt("bots.options." + p.getUniqueId() + ".protection-level", 4),
                "&7Click to cycle Protection 0–IV."));
        inv.setItem(45, item(Material.BARRIER, "&cRemove Bot", "&7Remove your active bot."));
        inv.setItem(49, item(Material.PLAYER_HEAD, "&aCreate Bot", "&7Creates your survival player bot with", "&7unbreakable netherite armor.", "&7Your previous bot is replaced automatically."));
        p.openInventory(inv);
    }

    private ItemStack blastItem(Player p, String piece, Material material, String label) {
        boolean enabled = botOption(p, "blast-" + piece, false);
        return item(material, (enabled ? "&a" : "&c") + label + " - Blast Protection", "&7Unbreakable netherite armor.", "&7Blast Protection IV: " + (enabled ? "&aEnabled" : "&cDisabled"), "&eClick to toggle");
    }

    private boolean botOption(Player p, String option, boolean defaultValue) {
        return plugin.getConfig().getBoolean("bots.options." + p.getUniqueId() + "." + option, defaultValue);
    }

    private void toggleBotOption(Player p, String option, boolean defaultValue) {
        String path = "bots.options." + p.getUniqueId() + "." + option;
        plugin.getConfig().set(path, !plugin.getConfig().getBoolean(path, defaultValue));
        plugin.saveConfig();
        plugin.bots().refreshOwned(p);
        openPvp(p);
    }

    public void openRtp(Player p) {
        Inventory inv = create("rtp", plugin.getConfig().getString("menus.title-rtp", "&8ʀᴀɴᴅᴏᴍ ᴛᴇʟᴇᴘᴏʀᴛ"), 27);
        inv.setItem(10, item(Material.GRASS_BLOCK, "&aPlains", "&7Natural Minecraft terrain", "&eClick to teleport"));
        inv.setItem(12, item(Material.SAND, "&6Desert", "&7Natural desert terrain", "&eClick to teleport"));
        inv.setItem(14, item(Material.SNOW_BLOCK, "&bSnow", "&7Natural snowy terrain", "&eClick to teleport"));
        inv.setItem(16, item(Material.TERRACOTTA, "&cBadlands", "&7Natural badlands terrain", "&eClick to teleport"));
        inv.setItem(22, item(Material.COMPASS, "&dRandom", "&7Choose a random arena world", "&eClick to teleport"));
        inv.setItem(26, item(Material.BARRIER, "&cClose", "&7Close this menu."));
        p.openInventory(inv);
    }

    public void openBoxThemes(Player p) {
        Inventory inv = create("box-themes", "&8ᴘʀɪᴠᴀᴛᴇ ʙᴏx ᴅᴇꜱɪɢɴ", 54);
        String[] themes = {"PLAINS", "FLOWER_PLAINS", "CHERRY_BLOSSOM", "DESERT", "BADLANDS",
                "PALE_GARDEN", "END", "NETHER", "WARPED", "CRIMSON"};
        Material[] icons = {Material.GRASS_BLOCK, Material.POPPY, Material.CHERRY_SAPLING, Material.SAND,
                Material.RED_SAND, Material.PALE_MOSS_BLOCK, Material.END_STONE, Material.NETHERRACK,
                Material.WARPED_NYLIUM, Material.CRIMSON_NYLIUM};
        int[] slots = {10, 12, 14, 16, 19, 21, 23, 25, 30, 32};
        String selected = plugin.practice().arenaTheme(p);
        for (int i = 0; i < themes.length; i++) {
            String name = themes[i].replace('_', ' ');
            inv.setItem(slots[i], item(icons[i], (themes[i].equals(selected) ? "&a✓ " : "&e") + name,
                    "&7Personal box theme.", "&eClick to select"));
        }
        inv.setItem(49, item(Material.ARROW, "&cBack", "&7Return to practice menu."));
        p.openInventory(inv);
    }

    public void openAdminBoxes(Player p) {
        Inventory inv = create("admin-boxes", "&8ᴀᴅᴍɪɴ · ᴘʟᴀʏᴇʀ ʙᴏxᴇꜱ", 54);
        MenuHolder holder = (MenuHolder) inv.getHolder();
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            inv.setItem(slot, item(Material.PLAYER_HEAD, "&e" + target.getName(),
                    "&7Teleport to this player's private box.", "&eClick to teleport"));
            holder.targets.put(slot, target.getUniqueId());
            slot++;
        }
        if (slot == 0) inv.setItem(22, item(Material.BARRIER, "&cNo players online"));
        inv.setItem(49, item(Material.ARROW, "&cBack"));
        p.openInventory(inv);
    }

    public void openAdminKits(Player p) {
        Inventory inv = create("admin-kits", "&8ᴀᴅᴍɪɴ · ᴋɪᴛꜱ", 27);
        inv.setItem(10, item(Material.DIAMOND_SWORD, "&bSword Kit", "&7Edit the global 1v1 Sword kit."));
        inv.setItem(16, item(Material.MACE, "&6Spear Mace Kit", "&7Edit the global 1v1 Spear Mace kit."));
        inv.setItem(22, item(Material.ARROW, "&cBack"));
        p.openInventory(inv);
    }

    private void openAdminKitEditor(Player p, PracticeManager.Mode mode) {
        String key = "admin-kit-editor-" + mode.name();
        Inventory inv = create(key, "&8ᴀᴅᴍɪɴ · " + mode.name() + " ᴋɪᴛ", 54);
        plugin.practice().applyAdminKitToInventory(mode, inv);
        for (int slot = 36; slot <= 44; slot++) inv.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, "&8 "));
        inv.setItem(49, item(Material.BOOK, "&eKit Editor", "&7Changes apply to future 1v1 fights.", "&7Armor slots: 45–48.", "&7Close to save."));
        inv.setItem(53, item(Material.EMERALD, "&aSave & Close"));
        p.openInventory(inv);
    }

    public void openAdmin(Player p) {
        Inventory inv = create("admin", plugin.getConfig().getString("menus.title-admin", "&8ᴘᴠᴘᴄᴏʀᴇ ᴀᴅᴍɪɴ"), 27);
        inv.setItem(10, item(Material.PLAYER_HEAD, "&aPlayer Boxes", "&7Teleport to an online player's box."));
        inv.setItem(12, item(Material.ZOMBIE_HEAD, "&eBot Management", "&7Remove all training NPCs."));
        inv.setItem(14, item(Material.COMMAND_BLOCK, "&6Gamemode Management", "&7Open gamemode selector."));
        inv.setItem(16, item(Material.ANVIL, "&bKit Management", "&7Edit Sword and Spear Mace 1v1 kits."));
        boolean duelBreaking = plugin.getConfig().getBoolean("duels.block-breaking.enabled", false);
        inv.setItem(18, item(duelBreaking ? Material.IRON_PICKAXE : Material.WOODEN_PICKAXE,
                (duelBreaking ? "&a" : "&c") + "Duel Block Breaking: " + (duelBreaking ? "ON" : "OFF"),
                "&7Allow duel players to break arena blocks.", "&7Default: OFF", "&eClick to toggle"));
        inv.setItem(20, item(Material.COMPARATOR, "&dPlugin Settings", "&7Reload PvPCore configuration."));
        inv.setItem(22, item(Material.BARRIER, "&cClose", "&7Close this menu."));
        p.openInventory(inv);
    }

    public void openGamemode(Player p) {
        Inventory inv = create("gm", "&8ɢᴀᴍᴇᴍᴏᴅᴇ", 27);
        inv.setItem(10, item(Material.GRASS_BLOCK, "&aSurvival", "&7Set your game mode."));
        inv.setItem(12, item(Material.DIAMOND_BLOCK, "&bCreative", "&7Set your game mode."));
        inv.setItem(14, item(Material.IRON_SWORD, "&6Adventure", "&7Set your game mode."));
        inv.setItem(16, item(Material.ENDER_EYE, "&dSpectator", "&7Set your game mode."));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) return;
        if (holder.key.startsWith("admin-kit-editor-")) {
            if (e.isShiftClick()) { e.setCancelled(true); return; }
            if (e.getClickedInventory() != top) { e.setCancelled(true); return; }
            int editorSlot = e.getSlot();
            e.setCancelled(editorSlot >= 36 && editorSlot <= 44 || editorSlot >= 49);
            if (editorSlot == 53) {
                String modeName = holder.key.substring("admin-kit-editor-".length());
                plugin.practice().saveAdminKit(PracticeManager.Mode.valueOf(modeName), top);
                p.closeInventory();
                plugin.message(p, "&aGlobal 1v1 kit saved.");
            }
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;

        int slot = e.getSlot();
        switch (holder.key) {
            case "duels" -> {\n                if (slot == 15) {\n                    int cancelled = plugin.fights().cancelOutgoingInvites(p);\n                    plugin.message(p, cancelled == 0 ? "&7No outgoing invitations to cancel." : "&aCancelled &f" + cancelled + " &apending invitation(s).");\n                    openDuels(p);\n                } else if (slot == 22) p.closeInventory();\n            }\n            case "pvp" -> {
                switch (slot) {
                    case 13 -> toggleBotOption(p, "infinite-totem", true);
                    case 16 -> toggleBotOption(p, "knockback", true);
                    case 22 -> toggleBotOption(p, "shield", false);
                    case 37 -> toggleBotOption(p, "armor-unbreakable", true);
                    case 39 -> {
                        String path = "bots.options." + p.getUniqueId() + ".protection-level";
                        int level = plugin.getConfig().getInt(path, 4);
                        plugin.getConfig().set(path, (level + 1) % 5);
                        plugin.saveConfig();
                        plugin.bots().refreshOwned(p);
                        openPvp(p);
                    }
                    case 28 -> toggleBotOption(p, "blast-helmet", false);
                    case 30 -> toggleBotOption(p, "blast-chestplate", false);
                    case 32 -> toggleBotOption(p, "blast-leggings", false);
                    case 34 -> toggleBotOption(p, "blast-boots", false);
                    case 45 -> { plugin.bots().removeOwned(p); plugin.message(p, "&aYour bot was removed."); p.closeInventory(); }
                    case 49 -> { plugin.bots().spawn(p); p.closeInventory(); }
                    default -> { }
                }
            }
            case "rtp" -> {
                switch (slot) {
                    case 10 -> { p.closeInventory(); plugin.worlds().teleport(p, "plains"); }
                    case 12 -> { p.closeInventory(); plugin.worlds().teleport(p, "desert"); }
                    case 14 -> { p.closeInventory(); plugin.worlds().teleport(p, "snow"); }
                    case 16 -> { p.closeInventory(); plugin.worlds().teleport(p, "badlands"); }
                    case 22 -> { p.closeInventory(); plugin.worlds().randomTeleport(p); }
                    case 26 -> p.closeInventory();
                    default -> { }
                }
            }
            case "admin" -> {
                if (!plugin.hasCore(p, "Admin.Core")) return;
                switch (slot) {
                    case 10 -> openAdminBoxes(p);
                    case 12 -> { plugin.bots().removeAll(); plugin.message(p, "&aAll training NPCs removed."); }
                    case 14 -> openGamemode(p);
                    case 16 -> openAdminKits(p);
                    case 18 -> {
                        boolean enabled = plugin.getConfig().getBoolean("duels.block-breaking.enabled", false);
                        plugin.getConfig().set("duels.block-breaking.enabled", !enabled);
                        plugin.saveConfig();
                        openAdmin(p);
                    }
                    case 20 -> { plugin.reloadConfig(); plugin.message(p, "&aConfiguration reloaded."); p.closeInventory(); }
                    case 22 -> p.closeInventory();
                    default -> { }
                }
            }
            case "box-themes" -> {
                String[] themes = {"PLAINS", "FLOWER_PLAINS", "CHERRY_BLOSSOM", "DESERT", "BADLANDS",
                        "PALE_GARDEN", "END", "NETHER", "WARPED", "CRIMSON"};
                int[] slots = {10, 12, 14, 16, 19, 21, 23, 25, 30, 32};
                for (int i = 0; i < slots.length; i++) {
                    if (slot == slots[i]) {
                        plugin.practice().setArenaTheme(p, themes[i]);
                        openBoxThemes(p);
                        return;
                    }
                }
                if (slot == 49) p.closeInventory();
            }
            case "admin-boxes" -> {
                if (!plugin.hasCore(p, "Admin.Core")) return;
                java.util.UUID targetId = holder.targets.get(slot);
                if (targetId != null) {
                    Player target = Bukkit.getPlayer(targetId);
                    if (target == null) plugin.message(p, "&cThat player is no longer online.");
                    else { plugin.practice().teleportToBox(p, target); p.closeInventory(); }
                } else if (slot == 49) openAdmin(p);
            }
            case "admin-kits" -> {
                if (!plugin.hasCore(p, "Admin.Core")) return;
                if (slot == 10) openAdminKitEditor(p, PracticeManager.Mode.SWORD);
                else if (slot == 16) openAdminKitEditor(p, PracticeManager.Mode.STUN_SLAM);
                else if (slot == 22) openAdmin(p);
            }
            case "gm" -> {
                if (!plugin.hasCore(p, "Admin.Core")) return;
                switch (slot) {
                    case 10 -> p.setGameMode(GameMode.SURVIVAL);
                    case 12 -> p.setGameMode(GameMode.CREATIVE);
                    case 14 -> p.setGameMode(GameMode.ADVENTURE);
                    case 16 -> p.setGameMode(GameMode.SPECTATOR);
                    default -> { return; }
                }
                plugin.message(p, "&aGamemode changed to &f" + p.getGameMode().name() + "&a.");
                p.closeInventory();
            }
        }
    }

    @EventHandler
    public void onAdminKitClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)
                || !holder.key.startsWith("admin-kit-editor-")) return;
        try {
            PracticeManager.Mode mode = PracticeManager.Mode.valueOf(
                    holder.key.substring("admin-kit-editor-".length()));
            plugin.practice().saveAdminKit(mode, event.getInventory());
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Could not save an admin kit with an invalid mode name.");
        }
    }
}
