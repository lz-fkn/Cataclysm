package lz.mc.cataclysm;

import lz.mc.cataclysm.commands.CataclysmCommand;
import lz.mc.cataclysm.events.*;
import lz.mc.cataclysm.listeners.CataclysmListener;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class Cataclysm extends JavaPlugin {

    private static Cataclysm instance;
    private GameManager gameManager;
    private BloodMoonEvent bloodMoonEvent;
    private AcidRainEvent acidRainEvent;
    private SolarActivityEvent solarEvent;
    
    public static String PLUGIN_VERSION;

    public static Cataclysm get() { return instance; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadYamls();
        this.gameManager = new GameManager(this);
        this.bloodMoonEvent = new BloodMoonEvent(this);
        this.acidRainEvent = new AcidRainEvent(this);
        this.solarEvent = new SolarActivityEvent(this);
        
        PLUGIN_VERSION = this.getPluginMeta().getVersion();

        // commands & listeners
        this.getCommand("cataclysm").setExecutor(new CataclysmCommand(this));
        // register listeners: general and event-specific
        Bukkit.getPluginManager().registerEvents(new CataclysmListener(this), this);
        Bukkit.getPluginManager().registerEvents(this.acidRainEvent, this); // acid rain uses WeatherChangeEvent
        
        // Start monitoring for events
        this.bloodMoonEvent.activateMonitoring();
        this.solarEvent.activateMonitoring();

        getLogger().info("\n\u001B[31m_________         __                .__                        \n"
        		+ "\\_   ___ \\_____ _/  |______    ____ |  | ___.__. ______ _____  \n"
        		+ "/    \\  \\/\\__  \\\\   __\\__  \\ _/ ___\\|  |<   |  |/  ___//     \\ \n"
        		+ "\\     \\____/ __ \\|  |  / __ \\\\  \\___|  |_\\___  |\\___ \\|  Y Y  \\\n"
        		+ " \\______  (____  /__| (____  /\\___  >____/ ____/____  >__|_|  /\n"
        		+ "        \\/     \\/          \\/     \\/     \\/         \\/      \\/ \u001B[0m\n\n"
        		+ "Cataclysm v" + PLUGIN_VERSION + ", by Elzzie!");
    }

    public void loadYamls(){
        SpawnPointStorage.getInstance().init(this);
        Texts.getInstance().init(this);
    }

    @Override
    public void onDisable() {
        // Deactivate all event monitoring and stop active events
        this.bloodMoonEvent.deactivateMonitoring();
        this.solarEvent.deactivateMonitoring();
        this.acidRainEvent.stop(); // Stop immediately on disable
        
        getLogger().info("Cataclysm disabled");
    }

    public GameManager getGameManager() { return gameManager; }
    public BloodMoonEvent getBloodMoonEvent() { return bloodMoonEvent; }
    public AcidRainEvent getAcidRainEvent() { return acidRainEvent; }
    public SolarActivityEvent getSolarEvent() { return solarEvent; }
    
    // --- Event Management Helpers ---
    
    /**
     * Checks if any major catastrophic event is currently running.
     * @return true if an event is active, false otherwise.
     */
    public boolean isEventActive() {
        return bloodMoonEvent.isActive() || acidRainEvent.isActive() || solarEvent.isActive();
    }
    
    /**
     * Gets the primary world. Assumes the first loaded world is the main game world.
     * @return The main World object.
     */
    public World getMainWorld() {
        List<World> worlds = Bukkit.getWorlds();
        if(worlds.isEmpty()) return null;
        return worlds.get(0);
    }
    
    /**
     * Sets the time of day in the main world.
     * @param time The time in ticks (0-24000).
     */
    public void setWorldTime(long time) {
        World w = getMainWorld();
        if(w != null) {
            w.setTime(time);
        }
    }
    
    /**
     * Sets or clears the rain/storm weather in the main world.
     * @param rain true to set storm, false to clear.
     */
    public void setWorldRain(boolean rain) {
        World w = getMainWorld();
        if(w != null) {
            if(rain) {
                w.setStorm(true);
                w.setThundering(false); 
                // Set duration long enough so it won't clear immediately. 1 hour = 72000 ticks
                w.setWeatherDuration(20 * 60 * 60); 
                w.setThunderDuration(0);
            } else {
                w.setStorm(false);
                w.setThundering(false);
                w.setWeatherDuration(0);
                w.setThunderDuration(0);
            }
        }
    }
}