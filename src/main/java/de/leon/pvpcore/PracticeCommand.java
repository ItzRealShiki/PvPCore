package de.leon.pvpcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.List;

public final class PracticeCommand implements CommandExecutor, Listener {
    private static final class Holder implements InventoryHolder {
        Inventory inv; public Inventory getInventory(){ return inv; }
    }
    private final PvPCore plugin;
    public PracticeCommand(PvPCore plugin){this.plugin=plugin;}
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player p)) return plugin.playerOnly(sender);
        if(!plugin.hasCore(p,"Member.Core")){plugin.message(p,"&cYou do not have permission.");return true;}
        if(args.length==0){main(p);return true;}
        if(args[0].equalsIgnoreCase("kit")){kit(p);return true;}
        if(args[0].equalsIgnoreCase("join")){plugin.practice().enter(p);return true;}
        plugin.message(p,"&eUsage: /prac [kit|join]");return true;
    }
    private ItemStack item(Material type,String name,String... lore){
        ItemStack i=new ItemStack(type);ItemMeta m=i.getItemMeta();m.setDisplayName(plugin.color(name));m.setLore(java.util.Arrays.stream(lore).map(plugin::color).toList());i.setItemMeta(m);return i;
    }
    private Inventory create(String title){
        Holder h=new Holder();h.inv=Bukkit.createInventory(h,27,plugin.color(title));return h.inv;
    }
    private void main(Player p){
        Inventory i=create("&8✦ ᴘʀᴀᴄᴛɪᴄᴇ");
        i.setItem(11,item(Material.MACE,"&6Stun Slam","&7Axe, mace and spear practice.","&eClick to enter"));
        i.setItem(15,item(Material.DIAMOND_SWORD,"&bSword","&7Diamond sword vs diamond sword.","&eClick to enter"));
        i.setItem(22,item(Material.ANVIL,"&aKit Editor","&7Select and customize your kit."));
        p.openInventory(i);
    }
    private void kit(Player p){
        Inventory i=create("&8✦ ᴋɪᴛ ᴇᴅɪᴛᴏʀ");
        i.setItem(10,item(Material.NETHERITE_AXE,"&6Stun Slam Kit","&7Sharpness V Axe","&7Density V + Wind Burst III Mace","&7Lunge III Spear","&eClick to select"));
        i.setItem(13,item(Material.DIAMOND_SWORD,"&bSword Kit","&7Full Diamond Armor","&7Diamond Sword","&eClick to select"));
        i.setItem(16,item(Material.BOOK,"&fSelected: "+plugin.practice().mode(p).name(),"&7Kits are saved per player."));
        i.setItem(22,item(Material.ARROW,"&cBack"));
        p.openInventory(i);
    }
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p) || !(e.getView().getTopInventory().getHolder() instanceof Holder))return;
        e.setCancelled(true);if(e.getClickedInventory()!=e.getView().getTopInventory())return;
        String title=e.getView().getTitle();int s=e.getSlot();
        if(title.equals(plugin.color("&8✦ ᴘʀᴀᴄᴛɪᴄᴇ"))){
            if(s==11){plugin.practice().setMode(p,PracticeManager.Mode.STUN_SLAM);p.closeInventory();plugin.practice().enter(p);}
            else if(s==15){plugin.practice().setMode(p,PracticeManager.Mode.SWORD);p.closeInventory();plugin.practice().enter(p);}
            else if(s==22)kit(p);
        }else if(title.equals(plugin.color("&8✦ ᴋɪᴛ ᴇᴅɪᴛᴏʀ"))){
            if(s==10){plugin.practice().setMode(p,PracticeManager.Mode.STUN_SLAM);kit(p);}
            else if(s==13){plugin.practice().setMode(p,PracticeManager.Mode.SWORD);kit(p);}
            else if(s==22)main(p);
        }
    }
}