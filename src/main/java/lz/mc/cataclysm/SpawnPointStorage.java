package lz.mc.cataclysm;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class SpawnPointStorage {
    private static SpawnPointStorage instance;
    private File file;
    private FileConfiguration cfg;
    private JavaPlugin plugin;

    private SpawnPointStorage(){}

    public static SpawnPointStorage getInstance(){
        if(instance==null) instance=new SpawnPointStorage();
        return instance;
    }

    public void init(JavaPlugin plugin){
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(),"spawnpoints.yml");
        if(!file.exists()){
            plugin.saveResource("spawnpoints.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void save(){
        try {
            cfg.save(file);
        } catch (IOException e){
            plugin.getLogger().log(Level.SEVERE,"Failed to save spawnpoints.yml",e);
        }
    }

    public void setSpawn(String key, Location loc){
        String base = key + ".location.";
        cfg.set(base + "world", loc.getWorld().getName());
        cfg.set(base + "x", loc.getX());
        cfg.set(base + "y", loc.getY());
        cfg.set(base + "z", loc.getZ());
        cfg.set(base + "yaw", loc.getYaw());
        cfg.set(base + "pitch", loc.getPitch());
        save();
    }

    public Location getSpawn(String key){
        if(!cfg.contains(key + ".location.world")) return null;
        String world = cfg.getString(key + ".location.world");
        double x = cfg.getDouble(key + ".location.x");
        double y = cfg.getDouble(key + ".location.y");
        double z = cfg.getDouble(key + ".location.z");
        float yaw = (float)cfg.getDouble(key + ".location.yaw");
        float pitch = (float)cfg.getDouble(key + ".location.pitch");
        return new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
    }
}
