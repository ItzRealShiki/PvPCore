package de.leon.pvpcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Arrays;

public final class PracticeCommand implements CommandExecutor, Listener {
    private static final class Holder implements InventoryHolder {
        final String kind;
        Inventory inv;
        Holder(String kind){this.kind=kind;}
        public Inventory getInventory(){return inv;}
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
        ItemStack i=new ItemStack(type);ItemMeta m=i.getItemMeta();m.setDisplayName(plugin.color(name));m.setLore(Arrays.stream(lore).map(plugin::color).toList());i.setItemMeta(m);return i;
    }
    private Inventory create(String title,String kind,int size){
        Holder h=new Holder(kind);h.inv=Bukkit.createInventory(h,size,plugin.color(title));return h.inv;
    }
    private void main(Player p){
        Inventory i=create("&8✦ ᴘʀᴀᴄᴛɪᴄᴇ","main",27);
        i.setItem(11,item(Material.MACE,"&6Stun Slam","&7Axe, mace and spear practice.","&eClick to enter"));
        i.setItem(15,item(Material.DIAMOND_SWORD,"&bSword","&7Diamond sword vs diamond sword.","&eClick to enter"));
        i.setItem(22,item(Material.ANVIL,"&aKit Editor","&7Swap items and edit your loadout."));
        p.openInventory(i);
    }
    private void kit(Player p){
        Inventory i=create("&8✦ ᴋɪᴛ ᴇᴅɪᴛᴏʀ","selector",27);
        i.setItem(10,item(Material.NETHERITE_AXE,"&6Stun Slam Kit","&7Netherite Axe · Sharpness V","&7Mace · Density V · Wind Burst III","&7Spear · Lunge III","&eClick to edit"));
        i.setItem(13,item(Material.DIAMOND_SWORD,"&bSword Kit","&7Full Diamond Armor","&7Diamond Sword","&eClick to edit"));
        i.setItem(16,item(Material.BOOK,"&fSelected: "+plugin.practice().mode(p).name(),"&7Each kit is saved separately."));
        i.setItem(22,item(Material.ARROW,"&cBack"));
        p.openInventory(i);
    }
    private void edit(Player p){
        Inventory inv=create("&8✦ ᴋɪᴛ ᴇᴅɪᴛᴏʀ · "+plugin.practice().mode(p).name(),"editor",54);
        plugin.practice().applyKitToInventory(p, inv);
        for(int s=36;s<=44;s++) inv.setItem(s,item(Material.GRAY_STAINED_GLASS_PANE,"&8 "));
        inv.setItem(49,item(Material.BOOK,"&eKit Editor","&7Move items within this menu.","&7Armor slots: 45–48.","&7Your changes save when you close."));
        inv.setItem(53,item(Material.EMERALD,"&aSave & Close","&7Save your kit and return."));
        p.openInventory(inv);
    }
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p) || !(e.getView().getTopInventory().getHolder() instanceof Holder h))return;
        if(h.kind.equals("editor")){
            if(e.getClickedInventory()==e.getView().getTopInventory()){
                int s=e.getSlot();
                if(s==53){e.setCancelled(true);plugin.practice().saveKit(p,e.getView().getTopInventory());p.closeInventory();plugin.message(p,"&aKit saved.");return;}
                if(s>=36 && s<=44 || s==49){e.setCancelled(true);return;}
                e.setCancelled(false);
            } else e.setCancelled(true);
            return;
        }
        e.setCancelled(true);if(e.getClickedInventory()!=e.getView().getTopInventory())return;
        int s=e.getSlot();
        if(h.kind.equals("main")){
            if(s==11){plugin.practice().setMode(p,PracticeManager.Mode.STUN_SLAM);p.closeInventory();plugin.practice().enter(p);}
            else if(s==15){plugin.practice().setMode(p,PracticeManager.Mode.SWORD);p.closeInventory();plugin.practice().enter(p);}
            else if(s==22)kit(p);
        }else if(h.kind.equals("selector")){
            if(s==10){plugin.practice().setMode(p,PracticeManager.Mode.STUN_SLAM);edit(p);}
            else if(s==13){plugin.practice().setMode(p,PracticeManager.Mode.SWORD);edit(p);}
            else if(s==22)main(p);
        }
    }
    @EventHandler public void close(InventoryCloseEvent e){
        if(!(e.getPlayer() instanceof Player p) || !(e.getInventory().getHolder() instanceof Holder h) || !h.kind.equals("editor"))return;
        plugin.practice().saveKit(p,e.getInventory());
    }
}