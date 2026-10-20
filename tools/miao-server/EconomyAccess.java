package cn.nankexi;

import java.util.*;
import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

final class EconomyAccess implements Listener {
    private static final Set<String> COMMANDS=Set.of("shop","eshop","sellgui","sellall","sellallitem","sellallhand","pay","sell");
    EconomyAccess(MiaoServer plugin){Bukkit.getPluginManager().registerEvents(this,plugin);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void command(PlayerCommandPreprocessEvent event) {
        var player=event.getPlayer();
        if(!Set.of("flat","architect").contains(player.getWorld().getName())&&player.getGameMode()!=GameMode.CREATIVE&&player.getGameMode()!=GameMode.SPECTATOR)return;
        String command=event.getMessage().substring(1).split("\\s+",2)[0].toLowerCase(Locale.ROOT);
        command=command.substring(command.lastIndexOf(':')+1);
        if(COMMANDS.contains(command)) {
            event.setCancelled(true);
            player.sendMessage("§6南可熙提醒您： §f请回到生存世界并使用生存模式交易。");
        }
    }
}
