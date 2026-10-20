package cn.nankexi;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

/** Small world-scoped command surface; never grants global Essentials or operator permissions. */
final class CreativeTools implements Listener {
    private final MiaoServer plugin;private final MiaoInterface ui;
    CreativeTools(MiaoServer p,MiaoInterface ui){plugin=p;this.ui=ui;Bukkit.getPluginManager().registerEvents(this,p);}
    static boolean building(World w){return w!=null&&Set.of("flat","architect").contains(w.getName());}
    private void say(Player p,String s){p.sendMessage("§6南可熙提醒您：§f"+s);}
    boolean command(Player p,String[] args){
        if(!ui.authenticated(p)){say(p,"请先登录。");return true;}
        if(!building(p.getWorld())){say(p,"建筑便利功能仅在云峦筑境和创造超平坦使用。");return true;}
        String action=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
        switch(action){
            case "tp"->{
                if(args.length!=2){say(p,"/tp 玩家名：只传送自己到当前建筑世界的玩家。");break;}
                Player target=Bukkit.getPlayerExact(args[1]);
                if(target==null||!ui.authenticated(target)||!p.getWorld().equals(target.getWorld())){say(p,"目标必须是当前建筑世界内已登录的在线玩家。");break;}
                if(!p.teleport(target.getLocation()))say(p,"传送未成功。");
            }
            case "mode","gamemode"->{
                if(args.length!=2){say(p,"/gamemode creative 或 /gamemode survival，只切换自己。");break;}
                GameMode mode=switch(args[1].toLowerCase(Locale.ROOT)){case "0","s","survival"->GameMode.SURVIVAL;case "1","c","creative"->GameMode.CREATIVE;default->null;};
                if(mode==null){say(p,"建筑区只开放创造和生存两种模式。");break;}
                p.setGameMode(mode);say(p,"已切换为"+(mode==GameMode.CREATIVE?"创造":"生存")+"模式，仍使用建筑世界的独立背包。");
            }
            case "fly"->{if(args.length!=1){say(p,"/fly 只切换自己的建筑飞行。");break;}boolean enabled=!p.getAllowFlight();p.setFlying(false);p.setAllowFlight(enabled);say(p,enabled?"建筑飞行已开启，双击跳跃飞行。":"建筑飞行已关闭。");}
            default->say(p,"建筑便利：/tp 玩家名（同世界）、/gamemode creative|survival、/fly；离开建筑区后失效。");
        }return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void intercept(PlayerCommandPreprocessEvent e){
        Player p=e.getPlayer();if(p.isOp()||!building(p.getWorld())||!ui.authenticated(p))return;
        String[] parts=e.getMessage().substring(1).trim().split("\\s+");String root=parts[0].toLowerCase(Locale.ROOT);root=root.substring(root.lastIndexOf(':')+1);
        if(!Set.of("tp","teleport","gamemode","gm","fly").contains(root))return;
        e.setCancelled(true);parts[0]=switch(root){case "tp","teleport"->"tp";case "gm","gamemode"->"mode";default->root;};command(p,parts);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void changed(PlayerChangedWorldEvent e){
        Player p=e.getPlayer();if(building(p.getWorld())){if(ui.authenticated(p))say(p,"建筑区为和平难度。便利命令：/tp 玩家名、/gamemode creative|survival、/fly。");return;}
        if(building(e.getFrom()))Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline()&&!building(p.getWorld())&&!p.isOp()&&!p.hasPermission("essentials.fly")&&p.getGameMode()==GameMode.SURVIVAL){p.setFlying(false);p.setAllowFlight(false);}});
    }
}
