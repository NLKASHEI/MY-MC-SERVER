package cn.nankexi;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.world.WorldLoadEvent;
import io.papermc.paper.event.world.WorldDifficultyChangeEvent;
import io.papermc.paper.event.world.WorldGameRuleChangeEvent;

/** Only the three survival dimensions follow the main overworld; independent worlds stay independent. */
final class SurvivalRules implements Listener {
    private final MiaoServer plugin;private boolean queued,syncing;
    SurvivalRules(MiaoServer plugin){this.plugin=plugin;Bukkit.getPluginManager().registerEvents(this,plugin);initialize();sync();}
    private void initialize(){
        File marker=new File(plugin.getDataFolder(),"world-policy-v0920.done");if(marker.exists())return;
        try{
            File config=new File(plugin.getDataFolder().getParentFile(),"Multiverse-Core/worlds.yml");Path backup=config.toPath().resolveSibling("worlds.yml.before-world-policy-v0920");if(config.exists()&&!Files.exists(backup))Files.copy(config.toPath(),backup);
            var manager=org.mvplugins.multiverse.core.MultiverseCoreApi.get().getWorldManager();
            for(World world:Bukkit.getWorlds()){
                if(member(world)||MiningWorld.mine(world)){
                    Difficulty difficulty=member(world)?Difficulty.HARD:Difficulty.PEACEFUL;
                    world.setDifficulty(difficulty);world.setGameRule(GameRule.KEEP_INVENTORY,true);
                    var managed=manager.getWorld(world).getOrNull();if(managed!=null){managed.setDifficulty(difficulty).get();if(!world.getName().equals("world"))managed.setRespawnWorld("world").get();}
                }else if(LobbyGuard.lobby(world)){
                    var managed=manager.getWorld(world).getOrNull();if(managed!=null)managed.setGameMode(GameMode.ADVENTURE).get();
                    for(var player:world.getPlayers())player.setGameMode(GameMode.ADVENTURE);
                }
            }
            manager.saveWorldsConfig();Files.writeString(marker.toPath(),"Survival HARD/keep inventory; mining PEACEFUL/keep inventory; lobby ADVENTURE\n");
        }catch(Exception e){throw new IllegalStateException("Cannot apply requested world rules",e);}
    }
    static boolean member(World world){return world!=null&&Set.of("world","world_nether","world_the_end").contains(world.getName());}
    void sync(){
        World main=Bukkit.getWorld("world");if(main==null||syncing)return;syncing=true;
        try{for(String name:List.of("world_nether","world_the_end")){
            World target=Bukkit.getWorld(name);if(target==null)continue;
            if(target.getDifficulty()!=main.getDifficulty())target.setDifficulty(main.getDifficulty());
            for(GameRule<?> rule:Registry.GAME_RULE)if(main.getFeatureFlags().containsAll(rule.requiredFeatures())&&target.getFeatureFlags().containsAll(rule.requiredFeatures()))copy(main,target,rule);
        }}finally{syncing=false;}
    }
    private <T> void copy(World main,World target,GameRule<T> rule){T value=main.getGameRuleValue(rule);if(value!=null&&!Objects.equals(value,target.getGameRuleValue(rule)))target.setGameRule(rule,value);}
    private void queue(World world){if(!member(world)||syncing||queued)return;queued=true;Bukkit.getScheduler().runTask(plugin,()->{queued=false;sync();});}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void rule(WorldGameRuleChangeEvent e){queue(e.getWorld());}
    @EventHandler(priority=EventPriority.MONITOR) public void difficulty(WorldDifficultyChangeEvent e){queue(e.getWorld());}
    @EventHandler public void load(WorldLoadEvent e){queue(e.getWorld());}
}
