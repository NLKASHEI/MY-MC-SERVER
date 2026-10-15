package cn.nankexi;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import fr.xephi.authme.events.LoginEvent;
import fr.xephi.authme.events.LogoutEvent;

/** UUID allow-list for ViaVersion only. A listed account must still authenticate. */
public final class PacketExemptions implements Listener {
    private final MiaoServer plugin;
    private final File file;
    private final Function<UUID,UserConnection> connections;
    private final YamlConfiguration data=new YamlConfiguration();
    private final Map<UUID,Applied> applied=new HashMap<>();
    private static final class Applied {
        final UserConnection connection;
        boolean changed; // Accessed only on this connection's Netty event loop.
        Applied(UserConnection connection){this.connection=connection;}
    }
    PacketExemptions(MiaoServer plugin) {
        this(plugin,new File(plugin.getDataFolder(),"packet-exemptions.yml"),id->Via.getManager().getConnectionManager().getClientConnection(id),true);
    }
    PacketExemptions(MiaoServer plugin,File file,Function<UUID,UserConnection> connections,boolean schedule) {
        this.plugin=plugin;this.file=file;this.connections=connections;
        try {
            if(file.exists())data.load(file);
            for(String key:keys())if(!UUID.fromString(key).toString().equals(key))throw new IOException("Invalid packet allow-list UUID");
        }catch(Exception e){throw new IllegalStateException("Refusing unreadable packet exemption data",e);}
        if(schedule) {
            Bukkit.getPluginManager().registerEvents(this,plugin);
            Bukkit.getScheduler().runTaskTimer(plugin,()->Bukkit.getOnlinePlayers().forEach(this::refresh),1L,20L);
        }
    }
    private Set<String> keys(){var section=data.getConfigurationSection("players");return section==null?Set.of():section.getKeys(false);}
    boolean listed(UUID id){return data.contains("players."+id);}
    private void say(CommandSender sender,String text){sender.sendMessage("§6南可熙提醒您： §f"+text);}
    private void onConnection(Applied entry,Runnable action) {
        var channel=entry.connection.getChannel();if(channel==null||!channel.isOpen())return;
        try {if(channel.eventLoop().inEventLoop())action.run();else channel.eventLoop().execute(action);}
        catch(java.util.concurrent.RejectedExecutionException ignored){/* Connection already closed. */}
    }
    private void restore(UUID id) {
        Applied prior=applied.remove(id);if(prior==null)return;
        onConnection(prior,()->{if(prior.changed){prior.connection.getPacketTracker().reset();prior.connection.getPacketTracker().setPacketLimiterEnabled(true);prior.changed=false;}});
    }
    void refresh(Player player) {
        UUID id=player.getUniqueId();
        if(!data.getBoolean("bootstrap-nlkashei-complete",false)&&player.getName().equalsIgnoreCase("NLKASHEI")&&plugin.authenticated(player)) {
            String path="players."+id;Object before=data.get(path);
            data.set(path,player.getName());data.set("bootstrap-nlkashei-complete",true);
            try {save();plugin.getLogger().info("NLKASHEI 已按登录后的实际 UUID 加入发包豁免名单。");}
            catch(IOException e){data.set(path,before);data.set("bootstrap-nlkashei-complete",false);plugin.getLogger().log(java.util.logging.Level.SEVERE,"预置发包豁免保存失败",e);return;}
        }
        if(!listed(id)||!plugin.authenticated(player)){restore(id);return;}
        UserConnection connection=connections.apply(id);
        Applied prior=applied.get(id);
        if(prior!=null&&prior.connection==connection){disable(prior);return;}
        restore(id);if(connection==null)return;
        Applied entry=new Applied(connection);applied.put(id,entry);
        disable(entry);
    }
    private void disable(Applied entry){onConnection(entry,()->{var tracker=entry.connection.getPacketTracker();if(tracker.isPacketLimiterEnabled()){entry.changed=true;tracker.setPacketLimiterEnabled(false);}});}
    @EventHandler(priority=EventPriority.MONITOR)
    public void login(LoginEvent event){if(Bukkit.isPrimaryThread())refresh(event.getPlayer());Bukkit.getScheduler().runTask(plugin,()->refresh(event.getPlayer()));}
    @EventHandler(priority=EventPriority.MONITOR)
    public void logout(LogoutEvent event){
        if(Bukkit.isPrimaryThread())restore(event.getPlayer().getUniqueId());
        else Bukkit.getScheduler().runTask(plugin,()->restore(event.getPlayer().getUniqueId()));
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event){restore(event.getPlayer().getUniqueId());}
    void stop(){for(UUID id:new ArrayList<>(applied.keySet()))restore(id);}
    private void save()throws IOException {
        file.getAbsoluteFile().getParentFile().mkdirs();
        File temp=new File(file.getParentFile(),file.getName()+".tmp");data.save(temp);
        try{Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
    }
    boolean command(CommandSender sender,String[] args) {
        if(!sender.hasPermission("miao.packetallow.admin")){say(sender,"只有 OP 或获授权的管理员可以管理发包豁免。");return true;}
        if(sender instanceof Player player&&!plugin.authenticated(player)){say(sender,"请先完成账号登录。");return true;}
        String action=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
        if(action.equals("list")&&args.length==1) {
            say(sender,keys().isEmpty()?"发包豁免名单为空。":"发包豁免："+String.join("、",keys().stream().map(id->data.getString("players."+id)+" ["+id+"]").toList()));return true;
        }
        if(!Set.of("add","remove","status").contains(action)||args.length>2) {
            say(sender,"/packetallow add|remove|status [在线登录名或 UUID]；不写目标时操作自己。/packetallow list 查看名单。");return true;
        }
        Player target=null;UUID id=null;String name=null;
        if(args.length==1&&sender instanceof Player player){target=player;id=player.getUniqueId();name=player.getName();}
        else if(args.length==2) {
            target=Bukkit.getPlayerExact(args[1]);
            if(target!=null){id=target.getUniqueId();name=target.getName();}
            else {
                try{UUID parsed=UUID.fromString(args[1]);if(!parsed.toString().equalsIgnoreCase(args[1]))throw new IllegalArgumentException();id=parsed;}
                catch(IllegalArgumentException ignored){for(String key:keys())if(args[1].equalsIgnoreCase(data.getString("players."+key))){id=UUID.fromString(key);break;}}
                if(id!=null){target=Bukkit.getPlayer(id);name=target==null?data.getString("players."+id,id.toString()):target.getName();}
            }
        }
        if(id==null){say(sender,"请填写在线玩家的实际登录名或完整 UUID；控制台必须写目标。不会按陌生名字创建账号。");return true;}
        if(action.equals("status")){say(sender,name+"："+(listed(id)?"已在豁免名单":"未在豁免名单")+"；"+(target!=null&&plugin.authenticated(target)&&applied.containsKey(id)?"当前连接已提交豁免。":"当前连接未豁免（未登录、离线或无需 ViaVersion 转换）。"));return true;}
        String path="players."+id;Object before=data.get(path);
        data.set(path,action.equals("add")?name:null);
        try{save();}catch(IOException e){data.set(path,before);plugin.getLogger().log(java.util.logging.Level.SEVERE,"发包豁免名单保存失败",e);say(sender,"保存失败，未更改名单，请检查服务器磁盘。");return true;}
        if(target!=null)refresh(target);else restore(id);
        say(sender,(action.equals("add")?"已加入发包豁免：":"已移除发包豁免：")+name+"。按 UUID 保存，重启保留；只有登录成功后生效。");
        return true;
    }
    List<String> complete(CommandSender sender,String[] args) {
        if(!sender.hasPermission("miao.packetallow.admin")||(sender instanceof Player p&&!plugin.authenticated(p)))return List.of();
        if(args.length==1)return List.of("add","remove","list","status").stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if(args.length==2&&Set.of("add","remove","status").contains(args[0].toLowerCase(Locale.ROOT))) {
            Set<String> names=new TreeSet<>();Bukkit.getOnlinePlayers().forEach(p->names.add(p.getName()));keys().forEach(id->names.add(data.getString("players."+id)));
            return names.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
