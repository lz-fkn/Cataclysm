package lz.mc.cataclysm;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.GameMode;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class GameManager {
    private final Cataclysm plugin;
    private final Scoreboard scoreboard;
    private final List<String> teamNames = Arrays.asList("Red","Green","Blue","Yellow");
    // Use a map to store assignments: TeamName -> List<Player UUID>
    private final Map<String, List<UUID>> assignments = new LinkedHashMap<>();
    private boolean running = false;
    private UUID host = null;
    private int countdownTask = -1;

    public GameManager(Cataclysm plugin){
        this.plugin = plugin;
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        this.scoreboard = mgr.getMainScoreboard();
        
        // Apply colors to scoreboard teams AND initialize the assignments map
        ChatColor[] colors = {ChatColor.RED, ChatColor.GREEN, ChatColor.BLUE, ChatColor.YELLOW};
        
        for(int i = 0; i < teamNames.size(); i++){
            String t = teamNames.get(i);
            Team team = scoreboard.getTeam(t);
            if(team == null) team = scoreboard.registerNewTeam(t);
            team.setAllowFriendlyFire(false);
            team.setDisplayName(t);
            // CRITICAL FIX FOR TEAM COLORS
            if (i < colors.length) {
                 team.setColor(colors[i]); 
            }
            // CRITICAL FIX FOR ASSIGNMENT FAILURE: Initialize the internal assignment map with keys/empty lists
            assignments.put(t, new ArrayList<>()); 
        }
    }

    public boolean isRunning(){ return running; }
    public void setHost(UUID host){ this.host = host; }
    public UUID getHost(){ return host; }

    /** Clears all internal player assignments and scoreboard entries. */
    public void clearAssignments(){
        assignments.values().forEach(List::clear);
        // Ensure scoreboard teams are also cleared of players
        for(Team t: scoreboard.getTeams()){
            t.getEntries().forEach(t::removeEntry);
        }
    }
    
    public List<String> getTeamNames() { return teamNames; }

    /**
     * Assigns all players who are in SURVIVAL or ADVENTURE mode and are not already assigned.
     * This logic respects previous manual assignments and ensures balanced teams.
     */
    private void autoAssignRemainingPlayers(){
        // 1. Get all UUIDs currently assigned (manually or previously)
        Set<UUID> assignedPlayers = assignments.values().stream().flatMap(List::stream).collect(Collectors.toSet());
        
        // 2. Find unassigned players in a playable mode
        List<Player> unassigned = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !assignedPlayers.contains(p.getUniqueId()))
                .filter(p-> p.getGameMode()==GameMode.SURVIVAL || p.getGameMode()==GameMode.ADVENTURE)
                .collect(Collectors.toList());
        
        Collections.shuffle(unassigned);
        
        // 3. Assign unassigned players, balancing based on current team size
        for(Player p: unassigned){
            // Find the team with the smallest current size (now works because map keys are initialized)
            String team = assignments.entrySet().stream()
                            .min(Comparator.comparingInt(e -> e.getValue().size()))
                            .map(Map.Entry::getKey)
                            .orElse(teamNames.get(new Random().nextInt(teamNames.size()))); 
                            
            assignments.get(team).add(p.getUniqueId()); // Use get() not computeIfAbsent since it's initialized
        }
    }

    public void applyScoreboardTeams(){
        // clear existing team members on scoreboard
        for(String t: teamNames){
            Team team = scoreboard.getTeam(t);
            if(team==null) continue;
            for(String e: team.getEntries().toArray(new String[0])) team.removeEntry(e);
        }
        // Add current assignments to scoreboard
        for(Map.Entry<String,List<UUID>> e: assignments.entrySet()){
            Team team = scoreboard.getTeam(e.getKey());
            if (team == null) continue;
            for(UUID u: e.getValue()){
                Player p = Bukkit.getPlayer(u);
                if(p!=null) team.addEntry(p.getName());
            }
        }
    }

    /**
     * METHOD REQUIRED BY /cataclysm team <team> [players...]
     * Assigns multiple players to a team. Removes them from their previous team.
     */
    public void assignPlayersToTeam(String team, List<Player> players){
        if (!teamNames.contains(team)) return;
        
        for(Player p : players) {
            assignments.values().forEach(list -> list.remove(p.getUniqueId()));
            assignments.get(team).add(p.getUniqueId()); // Use get() not computeIfAbsent
        }
        applyScoreboardTeams();
        broadcastAssignments();
    }
    
    /**
     * METHOD REQUIRED BY /cataclysm team <team>
     */
    public List<UUID> getTeamMembers(String teamName) {
        return assignments.getOrDefault(teamName, Collections.emptyList());
    }

    /**
     * METHOD REQUIRED BY /cataclysm trigger start
     */
    public void start(Player hostPlayer) { 
        if(running || countdownTask != -1) return;
        this.host = hostPlayer.getUniqueId();
        
        // 1. Assign all players who haven't been manually assigned.
        autoAssignRemainingPlayers(); 
        
        // 1b. CRITICAL FIX: Apply assignments to scoreboard and announce to all players
        applyScoreboardTeams();
        broadcastAssignments(); 
        
        // 2. Check if any player was assigned at all
        boolean anyAssigned = assignments.values().stream().anyMatch(list -> !list.isEmpty());
        if(!anyAssigned){
             sendMessage(hostPlayer, Texts.getInstance().get("game.start.no_players", "&cCannot start game: no players in SURVIVAL/ADVENTURE mode were available for assignment."));
             return;
        }

        // 3. Start countdown
        startCountdown(10); 
    }

    private void startCountdown(int seconds){
        if(running) return;
        
        Location lobby = SpawnPointStorage.getInstance().getSpawn("lobby");
        if(lobby==null){
            broadcast(Texts.getInstance().get("game.countdown.no_lobby", "&cCannot start game: lobby spawn point is not set."));
            return;
        }
        
        final int[] sec = {seconds};
        
        String initialChat = Texts.getInstance().get("game.countdown.initial.chat", "&eCataclysm: Starting in %s seconds...").formatted(seconds);
        broadcast(initialChat);
        
        if(countdownTask!=-1) Bukkit.getScheduler().cancelTask(countdownTask);
        countdownTask = new BukkitRunnable(){
            @Override public void run(){
                if(sec[0] <= 0){
                    plugin.getLogger().info("Countdown finished, starting game now.");
                    this.cancel();
                    countdownTask = -1;
                    startGameNow(); // Start game after countdown
                    return;
                }
                
                String title = Texts.getInstance().get("game.countdown.tick.title", "").formatted(sec[0]);
                String subtitle = Texts.getInstance().get("game.countdown.tick.subtitle", "&eStarting in %s s").formatted(sec[0]);
                String chat = Texts.getInstance().get("game.countdown.tick.chat", "&eStarting in %s s").formatted(sec[0]);
                
                if(sec[0] <=5 || sec[0] % 5 == 0){
                    Bukkit.getOnlinePlayers().forEach(p->{
                        String translatedTitle = ChatColor.translateAlternateColorCodes('&', title);
                        String translatedSubtitle = ChatColor.translateAlternateColorCodes('&', subtitle);
                        if (!title.isEmpty()) {
                            p.sendTitle(translatedTitle, translatedSubtitle, 5, 20, 5);
                        } else if (!subtitle.isEmpty()) { 
                            p.sendTitle("", translatedSubtitle, 5, 20, 5);
                        }
                        if(chat != null && !chat.isEmpty()) p.sendMessage(ChatColor.translateAlternateColorCodes('&', chat));
                    });
                }
                sec[0]--;
            }
        }.runTaskTimer(plugin, 0L, 20L).getTaskId();
    }

    private void startGameNow(){
        running = true;
        broadcast(Texts.getInstance().get("game.start.message", "&cCataclysm has begun!"));
        
        // Activate Event Monitoring (This starts the random checking for events)
        plugin.getBloodMoonEvent().activateMonitoring();
        plugin.getSolarEvent().activateMonitoring();
        
        // Teleport players to spawn
        assignments.forEach((team, list)->{
            Location loc = SpawnPointStorage.getInstance().getSpawn(team);
            if(loc==null) {
                plugin.getLogger().warning("Spawn point for team " + team + " is not set! Skipping teleport for team members.");
                return;
            }
            
            for(UUID u: list){
                Player p = Bukkit.getPlayer(u);
                if(p==null) continue;
                
                p.teleport(loc); 
                
                p.setGameMode(GameMode.SURVIVAL);
                p.setHealth(p.getMaxHealth());
                p.setFoodLevel(20);
                p.getInventory().clear();
            }
        });
    }

    /**
     * METHOD REQUIRED BY /cataclysm trigger stop
     */
    public void stop(){
        endGame(null); 
    }

    public void endGame(String winnerTeam){
        if(!running && countdownTask == -1) return;
        
        if(countdownTask!=-1) Bukkit.getScheduler().cancelTask(countdownTask);
        countdownTask = -1;
        
        // Stop all events and monitoring
        plugin.getBloodMoonEvent().stop();
        plugin.getAcidRainEvent().stop();
        plugin.getSolarEvent().stop();
        plugin.getBloodMoonEvent().deactivateMonitoring();
        plugin.getSolarEvent().deactivateMonitoring();

        running = false;
        
        String endMsg = (winnerTeam != null) 
                             ? Texts.getInstance().get("game.end.winner", "&cGame Over! Team %s won the Cataclysm!").formatted(winnerTeam)
                             : Texts.getInstance().get("game.end.stop", "&cGame Over! The Cataclysm has been stopped.");
        broadcast(endMsg);

        Bukkit.getScheduler().runTaskLater(plugin, ()->{
            Location lobby = SpawnPointStorage.getInstance().getSpawn("lobby");
            for(Player p: Bukkit.getOnlinePlayers()){
                if(lobby!=null) p.teleport(lobby);
                p.setGameMode(GameMode.ADVENTURE);
            }
            clearAssignments(); // Clears internal assignments AND scoreboard entries
        }, 100L);
    }

    public Optional<String> playerTeam(Player p){
        for(Map.Entry<String,List<UUID>> e: assignments.entrySet()){
            if(e.getValue().contains(p.getUniqueId())) return Optional.of(e.getKey());
        }
        return Optional.empty();
    }
    
    // NEW HELPER: Get team color for broadcast
    private ChatColor getTeamColor(String teamName){
        Team team = scoreboard.getTeam(teamName);
        // Ensure we handle cases where the team or its color is null
        return (team != null && team.getColor() != null) ? team.getColor() : ChatColor.WHITE;
    }

    private void broadcastAssignments(){
        broadcast(Texts.getInstance().get("game.assignments.header", "&6--- Team Assignments ---"));
        
        // Use a simpler format string that only takes the team name and the list of players.
        // The color will be prepended manually in the code for robustness.
        // It is recommended to set the 'game.assignments.line' in your Texts YAML to: "Team %s: %s"
        String baseFormat = Texts.getInstance().get("game.assignments.line", "Team %s: %s"); 

        for(Map.Entry<String,List<UUID>> e: assignments.entrySet()){
            String teamName = e.getKey();
            ChatColor color = getTeamColor(teamName); 
            
            String names = e.getValue().stream().map(uuid->{
                Player p = Bukkit.getPlayer(uuid);
                // Return player name colored with their team color, or "(offline)"
                return p!=null? color + p.getName() : color + "(offline)";
            }).collect(Collectors.joining("&f, ")); // &f resets color for the separator
            
            // FIX #1: If the team is empty, provide a message, colored in a neutral grey/white.
            String finalNames = names.isEmpty() ? "&7(None Assigned)" : names;

            // FIX #2: Explicitly prepend the team color.
            // This ensures the entire line is colored by the team's color, overriding any accidental color in the Texts file.
            String line = color + baseFormat.formatted(teamName, finalNames);
            
            broadcast(line);
        }
        broadcast("&6------------------------");
    }
    
    private void broadcast(String msg){
        String formattedMsg = ChatColor.translateAlternateColorCodes('&', msg);
        Bukkit.getOnlinePlayers().forEach(p->p.sendMessage(formattedMsg));
        plugin.getLogger().info(ChatColor.stripColor(formattedMsg));
    }
    
    private void sendMessage(Player p, String msg) {
        p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }
}