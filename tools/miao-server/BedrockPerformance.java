package cn.nankexi;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

/** Limit transmitted terrain for phones, without changing simulation or Java view distances. */
final class BedrockPerformance implements Listener {
    private final MiaoServer plugin;private final java.util.function.Predicate<Player> bedrock;
    BedrockPerformance(MiaoServer plugin,MiaoInterface ui){this(plugin,ui::bedrock);}
    BedrockPerformance(MiaoServer plugin,java.util.function.Predicate<Player> bedrock){this.plugin=plugin;this.bedrock=bedrock;Bukkit.getPluginManager().registerEvents(this,plugin);}
    void apply(Player player){if(!bedrock.test(player))return;int distance=plugin.getConfig().getInt("bedrock.send-view-distance",8);if(distance!=-1&&(distance<2||distance>32))throw new IllegalArgumentException("Invalid Bedrock send distance");player.setSendViewDistance(distance);}
    private void queue(Player player){Bukkit.getScheduler().runTaskLater(plugin,()->{if(player.isOnline())apply(player);},2);}
    @EventHandler public void join(PlayerJoinEvent e){queue(e.getPlayer());}
    @EventHandler public void world(PlayerChangedWorldEvent e){queue(e.getPlayer());}
}
