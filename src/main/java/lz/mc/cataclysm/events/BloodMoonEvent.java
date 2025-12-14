package lz.mc.cataclysm.events;

import lz.mc.cataclysm.Cataclysm;
import lz.mc.cataclysm.Texts;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import io.papermc.paper.world.MoonPhase; 

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class BloodMoonEvent extends Event {

    private int taskId = -1;
    private final Map<String, Integer> stopMonitorTasks = new HashMap<>(); 
    private final Set<String> activeWorlds = new HashSet<>(); 
    private final Random rnd = new Random();

    public BloodMoonEvent(Cataclysm plugin){ super(plugin); }

    @Override
    public void activateMonitoring() {
        if(taskId!=-1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::checkNight, 0L, 200L).getTaskId();
    }
    
    @Override
    public void deactivateMonitoring() {
        if(taskId!=-1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
        new HashSet<>(stopMonitorTasks.values()).forEach(Bukkit.getScheduler()::cancelTask);
        stopMonitorTasks.clear();
        activeWorlds.clear();
        this.active = false;
    }

    private void checkNight(){
        if(!plugin.getGameManager().isRunning()) return;

        for(World world : Bukkit.getWorlds()){
            if(activeWorlds.contains(world.getName())) continue;
            
            long day = world.getFullTime() / 24000L;
            MoonPhase currentPhase = MoonPhase.getPhase(day);
            
            if(currentPhase == MoonPhase.FULL_MOON){
                if(world.getTime() > 13000 && world.getTime() < 23000){ 
                    startEventInWorld(world);
                }
            }
        }
    }
    
    private boolean startEventInWorld(World world){
        if (activeWorlds.contains(world.getName())) return false;
        
        if (activeWorlds.isEmpty()) {
            this.active = true;
            sendMessages(true);
        }

        activeWorlds.add(world.getName());
        startStopMonitor(world);

        world.setAmbientSpawnLimit(10); 
        world.getEntitiesByClasses(Monster.class).forEach(e -> {
            if(e instanceof Creeper){
                try{ ((Creeper)e).setPowered(true); }catch(Throwable ignored){}
            } else {
                if (e instanceof LivingEntity) {
                    LivingEntity le = (LivingEntity) e;
                    le.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 60 * 60, 2, false, true));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 60 * 60, 2, false, true));
                }
            }
        });
        
        return true;
    }

    private void startStopMonitor(World world) {
         if (stopMonitorTasks.containsKey(world.getName())) {
            Bukkit.getScheduler().cancelTask(stopMonitorTasks.get(world.getName()));
        }
        
        int monitorId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeWorlds.contains(world.getName())) {
                    cancel();
                    return;
                }
                
                if (world.getTime() < 13000 || world.getTime() > 23000) {
                    stopEventInWorld(world);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 200L).getTaskId();
        
        stopMonitorTasks.put(world.getName(), monitorId);
    }


    private void stopEventInWorld(World world){
        if (!activeWorlds.remove(world.getName())) return;
        
        if (stopMonitorTasks.containsKey(world.getName())) {
            Bukkit.getScheduler().cancelTask(stopMonitorTasks.remove(world.getName()));
        }
        
        world.setAmbientSpawnLimit(1); 
        
        if (activeWorlds.isEmpty()) {
            this.active = false;
            sendMessages(false);
        }
    }
    
    public boolean manualStart() {
        if (this.active) return false;

        World world = plugin.getMainWorld();
        if (world == null) return false;
        
        if (!(world.getTime() > 13000 && world.getTime() < 23000)) {
            return false;
        }

        return startEventInWorld(world);
    }


    @Override
    public void start() {
        if(this.active) return;
        
        World world = plugin.getMainWorld();
        if (world != null) {
            startEventInWorld(world);
        }
    }

    @Override
    public void stop() {
        if(!this.active) return;
        
        new HashSet<>(activeWorlds).forEach(worldName -> {
             World w = Bukkit.getWorld(worldName);
             if (w != null) stopEventInWorld(w);
        });
    }

    private void sendMessages(boolean isStart) {
        // ⭐ FINAL, CLEAN IMPLEMENTATION: SINGLE CALL TO CENTRALIZED MESSAGE SENDER
        Texts.sendEventMessages("bloodmoon", isStart);
    }
}