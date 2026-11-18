package de.leon.pvpcore;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public final class ItemEditCommand implements TabExecutor, Listener {
    private final PvPCore plugin;
    private final NamespacedKey rightCommand, leftCommand, rightEffect, leftEffect;
    public ItemEditCommand(PvPCore plugin) {
        this.plugin=plugin;
        rightCommand=new NamespacedKey(plugin,"it_right_command");
        leftCommand=new NamespacedKey(plugin,"it_left_command");
        rightEffect=new NamespacedKey(plugin,"it_right_effect");
        leftEffect=new NamespacedKey(plugin,"it_left_effect");
    }
    private boolean allowed(Player p){return plugin.hasCore(p,"Admin.Core");}
    private void msg(Player p,String s){plugin.message(p,s);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player p)){plugin.playerOnly(sender);return true;}
        if(!allowed(p)){msg(p,"&cYou do not have permission.");return true;}
        ItemStack item=p.getInventory().getItemInMainHand();
        if(args.length==0){open(p);return true;}
        if(item.getType().isAir()){msg(p,"&cHold an item in your main hand.");return true;}
        ItemMeta meta=item.getItemMeta(); if(meta==null)return true;
        switch(args[0].toLowerCase(Locale.ROOT)){
            case "enchant" -> {
                if(args.length<3){msg(p,"&eUsage: /it enchant <enchantment> <level>");return true;}
                Enchantment enchant=Registry.ENCHANTMENT.get(NamespacedKey.minecraft(args[1].toLowerCase(Locale.ROOT)));
                int level; try{level=Integer.parseInt(args[2]);}catch(NumberFormatException ex){msg(p,"&cInvalid level.");return true;}
                if(enchant==null||level<1||level>255){msg(p,"&cInvalid enchantment or level (1-255).");return true;}
                meta.addEnchant(enchant,level,true); item.setItemMeta(meta); msg(p,"&aEnchantment added.");
            }
            case "name" -> {
                if(args.length<2){msg(p,"&eUsage: /it name <name>");return true;}
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',String.join(" ",Arrays.copyOfRange(args,1,args.length))));
                item.setItemMeta(meta);msg(p,"&aItem name updated.");
            }
            case "lore" -> {
                if(args.length<3){msg(p,"&eUsage: /it lore <add|set> <text>");return true;}
                List<String> lore=meta.hasLore()?new ArrayList<>(meta.getLore()):new ArrayList<>();
                String text=ChatColor.translateAlternateColorCodes('&',String.join(" ",Arrays.copyOfRange(args,2,args.length)));
                if(args[1].equalsIgnoreCase("add"))lore.add(text);
                else if(args[1].equalsIgnoreCase("set")){if(lore.isEmpty())lore.add(text);else lore.set(lore.size()-1,text);}
                else {msg(p,"&cUse add or set.");return true;}
                meta.setLore(lore);item.setItemMeta(meta);msg(p,"&aItem lore updated.");
            }
            case "hide" -> {
                if(args.length<2){msg(p,"&eUsage: /it hide <enchants|attributes|all>");return true;}
                switch(args[1].toLowerCase(Locale.ROOT)){
                    case "enchants" -> meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    case "attributes" -> meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                    case "all" -> meta.addItemFlags(ItemFlag.values());
                    default -> {msg(p,"&cUnknown hide option.");return true;}
                }
                item.setItemMeta(meta);msg(p,"&aItem flags updated.");
            }
            case "action" -> {
                if(args.length<4){msg(p,"&eUsage: /it action <right|left> <command|effect> <value>");return true;}
                String side=args[1].toLowerCase(Locale.ROOT),type=args[2].toLowerCase(Locale.ROOT);
                if(!side.equals("right")&&!side.equals("left")||!type.equals("command")&&!type.equals("effect")){msg(p,"&cInvalid action type.");return true;}
                NamespacedKey key=side.equals("right")?(type.equals("command")?rightCommand:rightEffect):(type.equals("command")?leftCommand:leftEffect);
                meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,String.join(" ",Arrays.copyOfRange(args,3,args.length)));
                item.setItemMeta(meta);msg(p,"&aItem action saved.");
            }
            default -> {msg(p,"&eUse /it to open the editor or /it <enchant|name|lore|hide|action>.");}
        }
        return true;
    }
    private void open(Player p){
        Inventory inv=Bukkit.createInventory(null,27,plugin.color("&8ɪᴛᴇᴍ ᴇᴅɪᴛᴏʀ"));
        inv.setItem(10,button(Material.ENCHANTED_BOOK,"&bEnchantments","/it enchant <enchantment> <level>"));
        inv.setItem(12,button(Material.NAME_TAG,"&eItem Name","/it name <name>"));
        inv.setItem(14,button(Material.WRITABLE_BOOK,"&aItem Lore","/it lore <add|set> <text>"));
        inv.setItem(16,button(Material.BARRIER,"&cHide Flags","/it hide <enchants|attributes|all>"));
        inv.setItem(22,button(Material.COMPASS,"&dItem Actions","/it action <right|left> <command|effect> <value>"));
        p.openInventory(inv);
    }
    private ItemStack button(Material m,String name,String lore){
        ItemStack it=new ItemStack(m);ItemMeta meta=it.getItemMeta();meta.setDisplayName(plugin.color(name));meta.setLore(List.of(plugin.color("&7"+lore)));it.setItemMeta(meta);return it;
    }
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p)||!e.getView().getTitle().equals(plugin.color("&8ɪᴛᴇᴍ ᴇᴅɪᴛᴏʀ")))return;
        e.setCancelled(true);if(e.getRawSlot()<0||e.getRawSlot()>=e.getView().getTopInventory().getSize())return;
        String help=switch(e.getRawSlot()){
            case 10->"/it enchant <enchantment> <level>";
            case 12->"/it name <name>";
            case 14->"/it lore <add|set> <text>";
            case 16->"/it hide <enchants|attributes|all>";
            case 22->"/it action <right|left> <command|effect> <value>";
            default->null;
        };
        if(help!=null){p.closeInventory();msg(p,"&e"+help);}
    }
    @EventHandler public void interact(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND)return;
        ItemStack item=e.getItem();if(item==null||!item.hasItemMeta())return;
        Action a=e.getAction();boolean right=a==Action.RIGHT_CLICK_AIR||a==Action.RIGHT_CLICK_BLOCK;
        boolean left=a==Action.LEFT_CLICK_AIR||a==Action.LEFT_CLICK_BLOCK;if(!right&&!left)return;
        String command=item.getItemMeta().getPersistentDataContainer().get(right?rightCommand:leftCommand,PersistentDataType.STRING);
        if(command!=null)e.getPlayer().performCommand(command.startsWith("/")?command.substring(1):command);
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(!sender.hasPermission("Admin.Core"))return List.of();
        if(args.length==1)return List.of("enchant","name","lore","hide","action").stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if(args.length==2)return switch(args[0].toLowerCase(Locale.ROOT)){case "hide"->List.of("enchants","attributes","all");case "lore"->List.of("add","set");case "action"->List.of("right","left");default->List.of();};
        if(args.length==3&&args[0].equalsIgnoreCase("action"))return List.of("command","effect");
        return List.of();
    }
}
