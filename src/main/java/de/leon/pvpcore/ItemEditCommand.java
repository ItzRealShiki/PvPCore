package de.leon.pvpcore;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
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
        if(args.length==0){msg(p,"&eUsage: /it <enchant|name|lore|hide|action>. Use tab completion for options.");return true;}
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
    @EventHandler public void interact(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND)return;
        ItemStack item=e.getItem();if(item==null||!item.hasItemMeta())return;
        Action a=e.getAction();boolean right=a==Action.RIGHT_CLICK_AIR||a==Action.RIGHT_CLICK_BLOCK;
        boolean left=a==Action.LEFT_CLICK_AIR||a==Action.LEFT_CLICK_BLOCK;if(!right&&!left)return;
        String command=item.getItemMeta().getPersistentDataContainer().get(right?rightCommand:leftCommand,PersistentDataType.STRING);
        if(command!=null)e.getPlayer().performCommand(command.startsWith("/")?command.substring(1):command);
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(!plugin.hasCore(sender,"Admin.Core"))return List.of();
        if(args.length==1)return complete(List.of("enchant","name","lore","hide","action"),args[0]);
        if(args.length==2)return switch(args[0].toLowerCase(Locale.ROOT)){
            case "enchant" -> {
                List<String> names=new ArrayList<>();
                for(Enchantment enchantment:Registry.ENCHANTMENT)names.add(enchantment.getKey().getKey());
                yield complete(names,args[1]);
            }
            case "lore" -> complete(List.of("add","set"),args[1]);
            case "hide" -> complete(List.of("enchants","attributes","all"),args[1]);
            case "action" -> complete(List.of("right","left"),args[1]);
            default -> List.of();
        };
        if(args.length==3&&args[0].equalsIgnoreCase("enchant"))return complete(List.of("1","2","3","4","5","10","255"),args[2]);
        if(args.length==3&&args[0].equalsIgnoreCase("action"))return complete(List.of("command","effect"),args[2]);
        return List.of();
    }
    private List<String> complete(List<String> choices,String input){
        String prefix=input.toLowerCase(Locale.ROOT);
        return choices.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
