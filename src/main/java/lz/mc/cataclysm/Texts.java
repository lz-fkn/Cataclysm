package lz.mc.cataclysm;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
// Removed: import org.bukkit.Title; // This import caused the compilation error

import java.io.File;

public class Texts {
    private static Texts instance;
    private File file;
    private FileConfiguration cfg;
    private JavaPlugin plugin;

    // --- Public Message Execution Method ---
    
    /**
     * Sends all messages (Title, Subtitle, Chat) to all online players for a given event state.
     * This is the only method event classes should call for output.
     * @param eventName The key name of the event (e.g., "bloodmoon", "solar").
     * @param isStart True for "start" messages, false for "stop" messages.
     */
    public static void sendEventMessages(String eventName, boolean isStart) {
        Texts textsInstance = getInstance(); // Get instance once

        String suffix = isStart ? "start" : "stop";
        // Solar activity has a hard-coded chat fallback; other events may need one too.
        String chatFallback = eventName.equals("solar") && isStart ? "Solar activity is high!" : "";

        // 1. Retrieve all message parts (already color-translated by get() into '§' format)
        String titleString = textsInstance.get(eventName + "." + suffix + ".title", "");
        String subtitleString = textsInstance.get(eventName + "." + suffix + ".subtitle", "");
        String chat = textsInstance.get(eventName + "." + suffix + ".chat", chatFallback);

        // Title display timing constants (in ticks, 20 ticks = 1 second)
        final int FADE_IN = 10;
        final int STAY = 70;
        final int FADE_OUT = 10;
        
        // 2. Send messages to all players using the modern Bukkit API
        Bukkit.getOnlinePlayers().forEach(p -> {
            
            // Send Title and Subtitle using the preferred non-deprecated Player#sendTitle method
            if (isStart) {
                // Only send a title if there is content for it
                if (!titleString.isEmpty() || !subtitleString.isEmpty()) {
                    // This method handles the title, subtitle, and timing in a non-deprecated way
                    p.sendTitle(titleString, subtitleString, FADE_IN, STAY, FADE_OUT); 
                }
            } else {
                // If it's a 'stop' event, clear the title first to be safe, then optionally show a stop title
                p.sendTitle("", "", 0, 0, 0); // Clear any existing title (zero duration)
                if (!titleString.isEmpty() || !subtitleString.isEmpty()) {
                    // Show stop title
                    p.sendTitle(titleString, subtitleString, FADE_IN, STAY, FADE_OUT); 
                }
            }
            
            // Send Chat (The String already contains Minecraft color codes '§' from get())
            if (!chat.isEmpty()) {
                p.sendMessage(chat); 
            }
        });
    }

    // --- Core Configuration Methods ---

    private Texts(){}

    public static Texts getInstance(){ 
        if(instance==null) instance=new Texts(); 
        return instance; 
    }

    public void init(JavaPlugin plugin){
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(),"texts.yml");
        if(!file.exists()) plugin.saveResource("texts.yml", false);
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    public String get(String path, String def){
        if(!cfg.contains(path)) return def;
        // Automatically translate '&' color codes to the Minecraft '§' symbol
        // This is necessary because we are sending plain Strings to player.sendTitle/sendMessage
        return cfg.getString(path).replace('&', '§'); 
    }
}