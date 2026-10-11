package cn.nankexi;

import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.world.WorldLoadEvent;

/** Persistent vanilla sleep rule; spectator/AFK exemptions stay with the existing plugins. */
final class MajoritySleep implements Listener {
    private final MiaoServer plugin;
    MajoritySleep(MiaoServer plugin) {
        this.plugin=plugin;
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Bukkit.getWorlds().forEach(this::apply);
    }
    private void apply(World world) {
        if(world.getEnvironment()==World.Environment.NORMAL&&plugin.getConfig().getStringList("sleep.worlds").contains(world.getName()))
            world.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE,51);
    }
    @EventHandler public void loaded(WorldLoadEvent event){apply(event.getWorld());}
}
