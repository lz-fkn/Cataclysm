package lz.mc.cataclysm.events;

import lz.mc.cataclysm.Cataclysm;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public abstract class Event {
    protected final Cataclysm plugin;
    protected boolean active = false;

    public Event(Cataclysm plugin){ this.plugin = plugin; }

    public abstract void activateMonitoring();
    // Added a default empty implementation for optional monitoring tasks
    public void deactivateMonitoring(){} 
    public abstract void start();
    public abstract void stop();
    public boolean isActive(){ return active; }

    protected void damageArmor(Player p, ItemStack it, int amount){
        if(it==null) return;
        ItemMeta meta = it.getItemMeta();
        if(meta instanceof Damageable){
            Damageable d = (Damageable) meta;
            d.setDamage(d.getDamage()+amount);
            // Must use setItemMeta to apply changes from the Damageable object
            it.setItemMeta(meta); 
            // if broken
            if(d.getDamage() >= it.getType().getMaxDurability()){
                // This removes the item from the specific equipment slot it occupies
                // as 'it' is a reference to the item in the slot (e.g., p.getInventory().getHelmet())
                it.setAmount(0); // Sets the amount to 0, effectively removing the item stack.
            }
        }
    }

    protected boolean isWearingFullArmor(Player p, Material h, Material c, Material l, Material b){
        ItemStack ih = p.getInventory().getHelmet();
        ItemStack ic = p.getInventory().getChestplate();
        ItemStack il = p.getInventory().getLeggings();
        ItemStack ib = p.getInventory().getBoots();
        if(ih==null||ic==null||il==null||ib==null) return false;
        return ih.getType()==h && ic.getType()==c && il.getType()==l && ib.getType()==b;
    }

    protected boolean isUnderSky(Player p){
        // Checks if player's current y is at or below the highest block in that x,z column (i.e., not under a roof)
        Location loc = p.getLocation();
        return p.getWorld().getHighestBlockAt(loc).getY() <= loc.getY();
    }
}