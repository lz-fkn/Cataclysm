package lz.mc.cataclysm.commands;

import lz.mc.cataclysm.Cataclysm;
import lz.mc.cataclysm.Texts;
import lz.mc.cataclysm.SpawnPointStorage;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class CataclysmCommand implements CommandExecutor, TabCompleter {
    private final Cataclysm plugin;

    public CataclysmCommand(Cataclysm plugin){ this.plugin = plugin; }
    
    private void sendMessage(CommandSender sender, String path, String def, Object... formatArgs) {
        String msg = Texts.getInstance().get(path, def).formatted(formatArgs);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!sender.isOp()){
            sendMessage(sender, "command.no_permission", "&cYou don't have permission to use this command.");
            return true;
        }
        
        if(args.length==0){
            sendMessage(sender, "command.usage", "&6Usage: /cataclysm <trigger|team|setspawn|reload>");
            return true;
        }
        String sub = args[0].toLowerCase();
        switch(sub){
            case "trigger":
                if(args.length<2){ sendMessage(sender, "command.trigger.usage", "&6Usage: /cataclysm trigger <start|stop|event>"); return true; }
                String triggerSub = args[1].toLowerCase();
                
                if(triggerSub.equalsIgnoreCase("start")){
                    if(!(sender instanceof Player)){ sendMessage(sender, "command.trigger.start.player_only", "&cOnly players can start the game."); return true; }
                    plugin.getGameManager().start( (Player)sender );
                } else if(triggerSub.equalsIgnoreCase("stop")){
                    plugin.getGameManager().stop();
                } else if(triggerSub.equalsIgnoreCase("event")){
                    if(args.length < 3) {
                        sendMessage(sender, "command.trigger.event.usage", "&6Usage: /cataclysm trigger event <blood_moon|acid_rain|solar_activity|stop>");
                        return true;
                    }
                    String eventName = args[2].toLowerCase();
                    boolean started = false;
                    
                    if (eventName.equals("stop")) {
                        // Stop all events
                        plugin.getBloodMoonEvent().stop();
                        plugin.getAcidRainEvent().stop();
                        plugin.getSolarEvent().stop();
                        sendMessage(sender, "command.trigger.event.stopped_all", "&aAll active events have been stopped.");
                        return true;
                    }

                    if (plugin.isEventActive()) {
                        sendMessage(sender, "command.trigger.event.already_running", "&cAnother event is already running.");
                        return true;
                    }

                    switch (eventName) {
                        case "blood_moon":
                            // manualStart returns false if not night (as requested by user)
                            started = plugin.getBloodMoonEvent().manualStart(); 
                            break;
                        case "acid_rain":
                            started = plugin.getAcidRainEvent().manualStart();
                            break;
                        case "solar_activity":
                            started = plugin.getSolarEvent().manualStart();
                            break;
                        default:
                            sendMessage(sender, "command.trigger.event.unknown", "&cUnknown event: %s", eventName);
                            return true;
                    }
                    
                    if (started) {
                        sendMessage(sender, "command.trigger.event.success", "&aEvent %s has been manually started.", eventName);
                    } else if(eventName.equals("blood_moon")) {
                         // Specific message if Blood Moon failed because it's day time
                        sendMessage(sender, "command.trigger.event.blood_moon.day_fail", "&cBlood Moon can only be manually triggered at night (13000-23000 ticks).");
                    } else {
                        sendMessage(sender, "command.trigger.event.already_running", "&cAnother event is already running or failed to start.");
                    }
                } else {
                     sendMessage(sender, "command.trigger.usage", "&6Usage: /cataclysm trigger <start|stop|event>");
                }
                return true;
            case "team":
                if(args.length<2){ sendMessage(sender, "command.team.usage", "&6Usage: /cataclysm team <team> [players...]"); return true; }
                String team = args[1];
                if(!plugin.getGameManager().getTeamNames().contains(team)){
                    sendMessage(sender, "command.team.not_found", "&cTeam %s not found. Available: %s", team, String.join(", ", plugin.getGameManager().getTeamNames()));
                    return true;
                }
                if(args.length==2){
                    // Show current team members
                    List<UUID> members = plugin.getGameManager().getTeamMembers(team);
                    String names = members.stream().map(uuid -> {
                        Player p = Bukkit.getPlayer(uuid);
                        return p!=null?p.getName():"(offline)";
                    }).collect(Collectors.joining(", "));
                    sendMessage(sender, "command.team.members", "&6Team %s members: %s", team, names.isEmpty() ? "(none)" : names);
                    return true;
                }
                
                List<Player> players = new ArrayList<>();
                for(int i=2; i<args.length; i++){
                    Player p = Bukkit.getPlayer(args[i]);
                    if(p==null){
                        sendMessage(sender, "command.player.not_found", "&cPlayer %s not found or offline.", args[i]);
                        return true;
                    }
                    players.add(p);
                }
                
                plugin.getGameManager().assignPlayersToTeam(team, players);
                String playerNames = players.stream().map(Player::getName).collect(Collectors.joining(", "));
                sendMessage(sender, "command.team.assigned", "&aPlayers %s assigned to team %s", playerNames, team);
                return true;
            case "setspawn":
                if(!(sender instanceof Player)){ sendMessage(sender, "command.setspawn.player_only", "&cOnly players can set spawn points."); return true; }
                if(args.length<2){ sendMessage(sender, "command.setspawn.usage", "&6Usage: /cataclysm setspawn <lobby|team>"); return true; }
                Player p = (Player) sender;
                String spawnKey = args[1]; // Team names are case sensitive in the list, but we allow mixed case input
                
                if(plugin.getGameManager().getTeamNames().contains(spawnKey) || spawnKey.equalsIgnoreCase("lobby")){
                    SpawnPointStorage.getInstance().setSpawn(spawnKey, p.getLocation());
                    sendMessage(sender, "command.setspawn.success", "&aSpawn point for '%s' set to your current location.", spawnKey);
                } else {
                    sendMessage(sender, "command.setspawn.invalid", "&cInvalid spawn point key. Use 'lobby' or one of the team names: %s", String.join(", ", plugin.getGameManager().getTeamNames()));
                }
                return true;
            case "reload":
                plugin.reloadConfig();
                plugin.loadYamls(); 
                sendMessage(sender, "command.reload.success", "&aCataclysm configuration and texts reloaded.");
                return true;
            default:
                sendMessage(sender, "command.usage", "&6Usage: /cataclysm <trigger|team|setspawn|reload>");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if(!sender.isOp()) return Collections.emptyList();
        
        if(args.length==1){
            return Arrays.asList("trigger", "team", "setspawn", "reload").stream().filter(s->s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        } else if(args.length==2){
            if(args[0].equalsIgnoreCase("trigger")){
                return Arrays.asList("start", "stop", "event").stream().filter(s->s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            } else if(args[0].equalsIgnoreCase("team")){
                return plugin.getGameManager().getTeamNames().stream().filter(s->s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            } else if(args[0].equalsIgnoreCase("setspawn")){
                List<String> options = new ArrayList<>(plugin.getGameManager().getTeamNames());
                options.add("lobby");
                return options.stream().filter(s->s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
        } else if(args.length==3 && args[0].equalsIgnoreCase("trigger") && args[1].equalsIgnoreCase("event")){
            return Arrays.asList("blood_moon", "acid_rain", "solar_activity", "stop").stream().filter(s->s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        } else if(args.length > 2 && args[0].equalsIgnoreCase("team")){
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[args.length-1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}