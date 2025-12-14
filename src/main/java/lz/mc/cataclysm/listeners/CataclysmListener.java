package lz.mc.cataclysm.listeners;

import lz.mc.cataclysm.Cataclysm;
import lz.mc.cataclysm.GameManager;
import lz.mc.cataclysm.events.BloodMoonEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class CataclysmListener implements Listener {
    private final Cataclysm plugin;
    public CataclysmListener(Cataclysm plugin){ this.plugin = plugin; }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent e){
        if(!plugin.getGameManager().isRunning()) return;
        BloodMoonEvent bm = plugin.getBloodMoonEvent();
        if(bm.isActive()){
            Entity ent = e.getEntity();
            if(ent instanceof Monster){
                if(ent instanceof Creeper){
                    try{ ((Creeper)ent).setPowered(true); }catch(Throwable ignored){}
                } else {
                    Monster m = (Monster) ent;
                    m.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.RESISTANCE, 20 * 60 * 60, 0, false, true));
                    m.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.STRENGTH, 20 * 60 * 60, 0, false, true));
                }
            }
        }
    }

    @EventHandler
    public void onRegen(org.bukkit.event.entity.EntityRegainHealthEvent e){
        if(e.getEntity() instanceof org.bukkit.entity.Player){
            org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason r = e.getRegainReason();
            if(r == org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.SATIATED || r == org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.REGEN){
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e){
        org.bukkit.entity.Player p = e.getEntity();
        // switch to spectator shortly after death so respawn flow completes
        Bukkit.getScheduler().runTaskLater(plugin, ()->p.setGameMode(GameMode.SPECTATOR), 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e){
        // nothing special now, could teleport to team spawn if spectating is disabled
    }
}