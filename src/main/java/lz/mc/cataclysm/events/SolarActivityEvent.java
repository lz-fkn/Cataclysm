package lz.mc.cataclysm.events;

import lz.mc.cataclysm.Cataclysm;
import lz.mc.cataclysm.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class SolarActivityEvent extends Event {
    private int checkTask = -1;
    private int damageTask = -1;
    private boolean activeFlag = false; // Used to control daemon tasks
    private boolean manuallyControlled = false;
    private final Random rnd = new Random();

    // Armor damage constants (in ticks)
    private static final int DAMAGE_IRON_INTERVAL = 40; // 2 seconds (Fitting armor rate)
    private static final int DAMAGE_OTHER_INTERVAL = 5; // 0.25 seconds (Unfitting armor rate, also the task frequency)
    private static final int PLAYER_IGNITE_INTERVAL = 10; // 0.5 seconds

    public SolarActivityEvent(Cataclysm plugin){ super(plugin); }

    @Override
    public void activateMonitoring() {
        if(checkTask!=-1) Bukkit.getScheduler().cancelTask(checkTask);
        // check every 60 seconds (1200 ticks) for random trigger and stop condition
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 1200L).getTaskId();
    }
    
    @Override
    public void deactivateMonitoring() {
        if(checkTask!=-1) Bukkit.getScheduler().cancelTask(checkTask);
        checkTask = -1;
        this.active = false;
        this.activeFlag = false;
        this.manuallyControlled = false;
    }
    
    private void tick() {
        if(!plugin.getGameManager().isRunning()) return;

        World mainWorld = plugin.getMainWorld();
        if(mainWorld == null) return;
        
        long time = mainWorld.getTime();
        boolean isDaytime = time >= 0 && time < 12500;

        if (this.active) {
            // Check for natural stop: evening/nightfall
            if (!isDaytime) {
                stop();
                return;
            }
        } else {
            // Check for natural start: daytime, 1/100 chance, and no other event is running
            if (isDaytime && !plugin.isEventActive()) {
                if (rnd.nextInt(100) == 0) { // 1/100 chance
                    start();
                }
            }
        }
    }

    private void startDaemon(){
        if(damageTask!=-1) Bukkit.getScheduler().cancelTask(damageTask);
        
        // Damage task (runs every 5 ticks / 0.25s)
        damageTask = new BukkitRunnable() {
            private int otherDamageCounter = 0;
            private int ironDamageCounter = 0;
            private int igniteCounter = 0;
            
            @Override
            public void run() {
                if(!activeFlag || !plugin.getGameManager().isRunning()){
                    cancel();
                    return;
                }
                
                World mainWorld = plugin.getMainWorld();
                if(mainWorld == null) return;

                otherDamageCounter += 5;
                ironDamageCounter += 5;
                igniteCounter += 5;

                // Check for full iron armor status outside the loop to avoid re-calculating per player
                Material h = Material.IRON_HELMET;
                Material c = Material.IRON_CHESTPLATE;
                Material l = Material.IRON_LEGGINGS;
                Material b = Material.IRON_BOOTS;

                for(Player p : mainWorld.getPlayers()){
                    if(!isUnderSky(p)) continue;
                    
                    boolean isWearingFullIron = isWearingFullArmor(p, h, c, l, b);

                    // Damage leather/gold/chain/diamond/netherite/no-armor 
                    // BUT only if NOT wearing full iron armor.
                    if(!isWearingFullIron && otherDamageCounter >= DAMAGE_OTHER_INTERVAL){
                        // Simplified damage logic: check each piece individually
                        damageArmor(p, p.getInventory().getHelmet(), 1);
                        damageArmor(p, p.getInventory().getChestplate(), 1);
                        damageArmor(p, p.getInventory().getLeggings(), 1);
                        damageArmor(p, p.getInventory().getBoots(), 1);
                    }
                    
                    // Damage iron armor (ONLY if wearing full iron)
                    if(isWearingFullIron){
                        if(ironDamageCounter >= DAMAGE_IRON_INTERVAL){
                            damageArmor(p, p.getInventory().getHelmet(), 1);
                            damageArmor(p, p.getInventory().getChestplate(), 1);
                            damageArmor(p, p.getInventory().getLeggings(), 1);
                            damageArmor(p, p.getInventory().getBoots(), 1);
                        }
                    }

                    // Ignite players
                    if(igniteCounter >= PLAYER_IGNITE_INTERVAL && !isWearingFullIron){
                        p.setFireTicks(20); 
                    }
                }
                
                if(otherDamageCounter >= DAMAGE_OTHER_INTERVAL) otherDamageCounter = 0;
                if(ironDamageCounter >= DAMAGE_IRON_INTERVAL) ironDamageCounter = 0;
                if(igniteCounter >= PLAYER_IGNITE_INTERVAL) igniteCounter = 0;
            }
        }.runTaskTimer(plugin, 5L, 5L).getTaskId();
    }

    private void sendMessages(boolean isStart) {
        // ⭐ FINAL, CLEAN IMPLEMENTATION: SINGLE CALL TO CENTRALIZED MESSAGE SENDER
        Texts.sendEventMessages("solar", isStart);
    }
    // --- Public methods for manual control ---

    /**
     * Attempts to manually start the Solar Activity event. Sets time to day.
     * @return true if the event started, false if another event is running.
     */
    public boolean manualStart() {
        if(plugin.isEventActive()) return false;
        
        this.manuallyControlled = true;
        
        // Set time to day (1000 ticks)
        plugin.setWorldTime(1000); 
        
        start();
        return true;
    }
    
    @Override
    public void start() {
        if(activeFlag) return;
        
        this.active = true;
        this.activeFlag = true;
        startDaemon();
        sendMessages(true);
    }

    @Override
    public void stop() {
        if(!activeFlag) return;
        this.active = false;
        this.activeFlag = false;
        this.manuallyControlled = false;
        
        if(damageTask!=-1) Bukkit.getScheduler().cancelTask(damageTask);
        damageTask = -1;
        
        sendMessages(false);
    }
}