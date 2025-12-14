package lz.mc.cataclysm.events;

import lz.mc.cataclysm.Cataclysm;
import lz.mc.cataclysm.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class AcidRainEvent extends Event implements Listener {
    private int damagePlayerTask = -1;
    private int damageArmorTask = -1;
    private final Random rnd = new Random();
    private boolean enabled = false; // Internal flag for the event effect tasks
    private boolean manuallyControlled = false;

    // Armor damage constants (in ticks)
    private static final int DAMAGE_PLAYER_INTERVAL = 40; // 2 seconds
    private static final int DAMAGE_LEATHER_INTERVAL = 40; // 2 seconds (Fitting armor rate)
    private static final int DAMAGE_OTHER_INTERVAL = 5; // 0.25 seconds (Unfitting armor rate, also the task frequency)

    public AcidRainEvent(Cataclysm plugin){ super(plugin); }

    @Override
    public void activateMonitoring() {
        // nothing to poll - we rely on WeatherChangeEvent
    }
    
    @Override
    public void deactivateMonitoring() {
        // Event is stopped globally in onDisable, no task monitoring to stop here
    }
    
    @EventHandler
    public void onWeatherChange(WeatherChangeEvent e){
        if(!plugin.getGameManager().isRunning()) return;

        World w = e.getWorld();
        boolean isNowStormy = e.toWeatherState();
        boolean wasStormy = w.hasStorm();

        if (w != plugin.getMainWorld()) return; // Only process weather in the main world

        if (isNowStormy && !wasStormy) {
            // Rain/Storm is STARTING
            if (!this.active && !plugin.isEventActive()) {
                // 1/4 chance to trigger Acid Rain
                if (rnd.nextInt(4) == 0) {
                    start();
                }
            }
        } else if (!isNowStormy && wasStormy) {
            // Rain/Storm is ENDING
            // If Acid Rain is active and not manually controlled, it should stop with the rain.
            if (this.active && !manuallyControlled) {
                stop();
            }
        }
    }

    private void startTasks(){
        // Task 1: Damage unprotected players (Runs every 40 ticks / 2 seconds)
        damagePlayerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if(!enabled) {
                    cancel();
                    return;
                }
                
                World mainWorld = plugin.getMainWorld();
                // Check if it's still raining in the main world
                if(!mainWorld.hasStorm() && !manuallyControlled){
                    stop();
                    return;
                }

                for(Player p : Bukkit.getOnlinePlayers()){
                    // Damage only players under the sky during rain
                    if(!p.getWorld().hasStorm() || !isUnderSky(p)) continue;
                    
                    // Check for leather armor or if player has a helmet
                    if(!isWearingFullArmor(p, Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS)){
                        // Damage player if not wearing full leather armor
                        p.damage(1.0); // 1.0 damage is half a heart
                    }
                }
            }
        }.runTaskTimer(plugin, DAMAGE_PLAYER_INTERVAL, DAMAGE_PLAYER_INTERVAL).getTaskId();

        // Task 2: Damage armor (Runs every 5 ticks / 0.25 seconds)
        damageArmorTask = new BukkitRunnable() {
            private int leatherDamageCounter = 0;
            
            @Override
            public void run() {
                if(!enabled) {
                    cancel();
                    return;
                }
                
                World mainWorld = plugin.getMainWorld();
                // Check if it's still raining in the main world
                if(!mainWorld.hasStorm() && !manuallyControlled){
                    stop();
                    return;
                }
                
                leatherDamageCounter++;

                for(Player p : Bukkit.getOnlinePlayers()){
                    if(!p.getWorld().hasStorm() || !isUnderSky(p)) continue;
                    
                    // Damage non-leather armor every 5 ticks (interval of 5)
                    damageNonLeatherArmor(p, 1);
                    
                    // Damage leather armor every 40 ticks (interval of 8, since this runs every 5 ticks)
                    if(leatherDamageCounter * 5 >= DAMAGE_LEATHER_INTERVAL){
                        damageLeatherArmor(p, 1);
                    }
                }
                
                if(leatherDamageCounter * 5 >= DAMAGE_LEATHER_INTERVAL) leatherDamageCounter = 0;
            }
        }.runTaskTimer(plugin, 5L, 5L).getTaskId();
    }
    
    private void damageNonLeatherArmor(Player p, int amount) {
        // Helper to damage all armor *except* leather
        if(p.getInventory().getHelmet() != null && p.getInventory().getHelmet().getType() != Material.LEATHER_HELMET) damageArmor(p, p.getInventory().getHelmet(), amount);
        if(p.getInventory().getChestplate() != null && p.getInventory().getChestplate().getType() != Material.LEATHER_CHESTPLATE) damageArmor(p, p.getInventory().getChestplate(), amount);
        if(p.getInventory().getLeggings() != null && p.getInventory().getLeggings().getType() != Material.LEATHER_LEGGINGS) damageArmor(p, p.getInventory().getLeggings(), amount);
        if(p.getInventory().getBoots() != null && p.getInventory().getBoots().getType() != Material.LEATHER_BOOTS) damageArmor(p, p.getInventory().getBoots(), amount);
    }
    
    private void damageLeatherArmor(Player p, int amount) {
        // Helper to damage *only* leather armor
        if(p.getInventory().getHelmet() != null && p.getInventory().getHelmet().getType() == Material.LEATHER_HELMET) damageArmor(p, p.getInventory().getHelmet(), amount);
        if(p.getInventory().getChestplate() != null && p.getInventory().getChestplate().getType() == Material.LEATHER_CHESTPLATE) damageArmor(p, p.getInventory().getChestplate(), amount);
        if(p.getInventory().getLeggings() != null && p.getInventory().getLeggings().getType() == Material.LEATHER_LEGGINGS) damageArmor(p, p.getInventory().getLeggings(), amount);
        if(p.getInventory().getBoots() != null && p.getInventory().getBoots().getType() == Material.LEATHER_BOOTS) damageArmor(p, p.getInventory().getBoots(), amount);
    }

    // --- Public methods for manual control ---

    /**
     * Attempts to manually start the Acid Rain event. Forces rain.
     * @return true if the event started, false if another event is running.
     */
    public boolean manualStart() {
        if(plugin.isEventActive()) return false;
        
        this.manuallyControlled = true;
        
        // Force rain/storm
        plugin.setWorldRain(true);
        
        start();
        return true;
    }

    @Override
    public void start() {
        // Global check for auto-trigger is in onWeatherChange(), but manualStart() calls start() directly.
        if(enabled) return;
        this.active = true;
        this.enabled = true;

        // If not manually controlled, the auto-trigger relies on the WeatherChangeEvent to set the rain.
        if (!manuallyControlled) {
             // Ensure the main world is stormy, in case the onWeatherChange was for another world
             if (!plugin.getMainWorld().hasStorm()) {
                 // If the main world isn't stormy, force it to ensure the event works.
                 plugin.setWorldRain(true);
             }
        }

        startTasks();
        sendMessages(true);
    }

    @Override
    public void stop() {
        if(!enabled) return;
        this.active = false;
        this.enabled = false;
        
        if(damagePlayerTask!=-1) Bukkit.getScheduler().cancelTask(damagePlayerTask);
        if(damageArmorTask!=-1) Bukkit.getScheduler().cancelTask(damageArmorTask);
        damagePlayerTask = damageArmorTask = -1;
        
        // If the event was manually controlled, clear the weather on stop
        if (manuallyControlled) {
            plugin.setWorldRain(false);
            manuallyControlled = false;
        }

        sendMessages(false);
    }
    
    private void sendMessages(boolean isStart) {
        // ⭐ FINAL, CLEAN IMPLEMENTATION: SINGLE CALL TO CENTRALIZED MESSAGE SENDER
        Texts.sendEventMessages("acidrain", isStart);
    }
}