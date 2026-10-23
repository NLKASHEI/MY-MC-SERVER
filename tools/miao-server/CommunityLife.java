package cn.nankexi;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.floodgate.api.FloodgateApi;

/** Shared storage and private input for community features. Player state never lives in templates. */
final class CommunityLife implements Listener {
    static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    final MiaoServer plugin;final MiaoInterface ui;final YamlConfiguration data=new YamlConfiguration(),config;
    final CommunityMenu menus;final CommunityLandmarks landmarks;final CommunityJobs jobs;final CommunityReport reports;
    final RelicShop relics;
    private final File file;
    private record Input(long expires,Consumer<String> accept){}
    private final Map<UUID,Input> inputs=new ConcurrentHashMap<>();
    private final Map<UUID,Long> actionAt=new HashMap<>();
    CommunityLife(MiaoServer plugin,MiaoInterface ui){
        this.plugin=plugin;this.ui=ui;file=new File(plugin.getDataFolder(),"community-data.yml");
        try{if(file.exists())data.load(file);}catch(Exception e){throw new IllegalStateException("Refusing to overwrite unreadable community data",e);}
        try{config=new YamlConfiguration();config.load(new File(plugin.getDataFolder(),"community.yml"));}catch(Exception e){throw new IllegalStateException("Cannot read community configuration",e);}
        if(config.getInt("schema")!=1)throw new IllegalStateException("Invalid community schema");
        menus=new CommunityMenu(this);landmarks=new CommunityLandmarks(this);reports=new CommunityReport(this);jobs=new CommunityJobs(this);
        relics=new RelicShop(this);relics.validate();
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Bukkit.getScheduler().runTaskTimer(plugin,()->{for(Player p:Bukkit.getOnlinePlayers())if(ui.authenticated(p))reports.sample(p);},100,100);
        Bukkit.getScheduler().runTaskTimer(plugin,this::save,1200,1200);
    }
    LocalDate today(){return LocalDate.now(ZONE);}
    String day(){return today().toString();}
    String player(UUID id){return "players."+id;}
    void say(Player p,String text){p.sendMessage("§6南可熙提醒您：§f"+text);}
    void later(Player p,Runnable action){Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline()&&ui.authenticated(p))action.run();});}
    boolean survival(Player p){return ui.authenticated(p)&&p.getGameMode()==GameMode.SURVIVAL&&(SurvivalRules.member(p.getWorld())||MiningWorld.mine(p.getWorld()));}
    boolean trading(Player p){return ui.authenticated(p)&&!CreativeTools.building(p.getWorld())&&p.getGameMode()!=GameMode.CREATIVE&&p.getGameMode()!=GameMode.SPECTATOR;}
    boolean cooldown(Player p){long now=System.currentTimeMillis(),last=actionAt.getOrDefault(p.getUniqueId(),0L);if(now-last<500)return false;actionAt.put(p.getUniqueId(),now);return true;}
    void save(){
        try{File tmp=new File(file.getParentFile(),file.getName()+".tmp");data.save(tmp);Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        catch(IOException e){throw new IllegalStateException("Cannot persist community data",e);}
    }
    void change(Runnable action){
        String before=data.saveToString();
        try{action.run();save();}catch(RuntimeException e){try{data.loadFromString(before);}catch(Exception restore){e.addSuppressed(restore);}throw e;}
    }
    static boolean text(String s,int max,boolean empty){return s!=null&&(empty||!s.isBlank())&&s.equals(s.strip())&&s.codePointCount(0,s.length())<=max&&s.codePoints().noneMatch(c->Character.isISOControl(c)||c=='§'||c=='&'||c=='<'||c=='>');}
    void cancelInput(Player p){inputs.remove(p.getUniqueId());}
    void input(Player p,String title,String hint,String initial,Consumer<String> accept){
        ui.names.cancelInput(p);ui.sync.cancelInput(p);ui.titles.cancelInput(p);cancelInput(p);
        if(ui.bedrock(p)){
            FloodgateApi.getInstance().sendForm(p.getUniqueId(),CustomForm.builder().title(title).input(hint,"",initial).validResultHandler(r->{String value=r.asInput(0).strip();later(p,()->accept.accept(value));}).build());
        }else{p.closeInventory();inputs.put(p.getUniqueId(),new Input(System.currentTimeMillis()+120000,accept));say(p,title+"："+hint+"；输入「取消」退出。");}
    }
    @EventHandler(priority=EventPriority.LOWEST) public void inputChat(AsyncChatEvent e){
        Input request=inputs.get(e.getPlayer().getUniqueId());if(request==null)return;e.setCancelled(true);
        if(!inputs.remove(e.getPlayer().getUniqueId(),request))return;
        String value=PlainTextComponentSerializer.plainText().serialize(e.message()).strip();
        later(e.getPlayer(),()->{if(request.expires()<System.currentTimeMillis()||value.equals("取消")){say(e.getPlayer(),"已取消。");return;}request.accept().accept(value);});
    }
    void command(Player p,String[] args){
        if(!ui.authenticated(p))return;cancelInput(p);String action=args.length==0?"home":args[0];
        switch(action){case "marks"->landmarks.list(p,0);case "orders"->jobs.orders(p,0,false);case "quests"->jobs.quests(p);case "report"->reports.menu(p,7);case "relics"->relics.menu(p);default->home(p);}
    }
    void home(Player p){menus.show(p,"社区生活","一起逛逛，顺手赚点金币。",List.of(
        new CommunityMenu.Entry(Material.LODESTONE,"§b地标导航",List.of("§7登记建筑 · 自由参观"),()->landmarks.list(p,0)),
        new CommunityMenu.Entry(Material.WRITABLE_BOOK,"§6悬赏板",List.of("§7玩家订单 · 系统收购"),()->jobs.orders(p,0,false)),
        new CommunityMenu.Entry(Material.TARGET,"§a趣味委托",List.of("§7每日目标 · 金币奖励"),()->jobs.quests(p))),0,()->ui.command(p,new String[]{"menu"}),List.of());}
    void login(Player p){reports.sample(p);jobs.login(p);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void mode(PlayerGameModeChangeEvent e){if(ui.authenticated(e.getPlayer()))reports.sample(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){if(ui.authenticated(e.getPlayer()))reports.sample(e.getPlayer());}
    @EventHandler public void world(PlayerChangedWorldEvent e){reports.baseline(e.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent e){cancelInput(e.getPlayer());if(ui.authenticated(e.getPlayer()))reports.sample(e.getPlayer());reports.forget(e.getPlayer());actionAt.remove(e.getPlayer().getUniqueId());save();}
    void stop(){for(Player p:Bukkit.getOnlinePlayers())if(ui.authenticated(p))reports.sample(p);save();}
}
