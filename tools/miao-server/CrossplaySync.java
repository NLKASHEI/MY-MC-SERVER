package cn.nankexi;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.configuration.file.YamlConfiguration;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import fr.xephi.authme.api.v3.AuthMeApi;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.link.PlayerLink;
import org.geysermc.cumulus.form.*;

/** Select a shared existing UUID; never copy or merge player/plugin databases. */
final class CrossplaySync implements Listener {
    record Account(UUID id,String name,boolean pe,String ip,long first) {}
    record Binding(String javaName,UUID javaId,UUID peId,UUID shared,String source,String state) {}
    private record Input(Account peer,boolean own,long until) {}
    private final MiaoServer plugin;private final MiaoInterface ui;
    private final File file;private final YamlConfiguration data=new YamlConfiguration();
    private final Map<UUID,Account> accounts=new HashMap<>();
    private volatile Map<String,Binding> bindings=Map.of();
    private final Map<UUID,Input> inputs=new ConcurrentHashMap<>();
    private final Map<String,Long> attempts=new HashMap<>();
    private final Set<UUID> working=new HashSet<>();
    private final String salt;
    private static final class Holder implements InventoryHolder {
        final UUID owner;Inventory inventory;final Map<Integer,Runnable> actions=new HashMap<>();
        Holder(Player p){owner=p.getUniqueId();}public Inventory getInventory(){return inventory;}
    }
    CrossplaySync(MiaoServer p,MiaoInterface ui) {
        plugin=p;this.ui=ui;file=new File(p.getDataFolder(),"crossplay-sync.yml");
        try{if(file.exists())data.load(file);}catch(Exception e){throw new IllegalStateException("Refusing to replace unreadable sync identities",e);}
        salt=data.getString("ip-salt",UUID.randomUUID().toString());data.set("ip-salt",salt);
        var section=data.getConfigurationSection("accounts");if(section!=null)for(String key:section.getKeys(false)) {
            String root="accounts."+key;UUID id=UUID.fromString(key);accounts.put(id,new Account(id,data.getString(root+".name"),data.getBoolean(root+".pe"),data.getString(root+".ip",""),data.getLong(root+".first")));
        }
        refresh();Map<String,Binding> checking=new HashMap<>();bindings.forEach((key,b)->checking.put(key,new Binding(b.javaName(),b.javaId(),b.peId(),b.shared(),b.source(),b.state().equals("ACTIVE")?"CHECKING":b.state())));bindings=Map.copyOf(checking);
        Bukkit.getPluginManager().registerEvents(this,p);
        Bukkit.getScheduler().runTask(p,()->{recover();seed();});
        Bukkit.getScheduler().runTaskTimer(p,this::recover,1200L,1200L);
    }
    private PlayerLink links(){return FloodgateApi.getInstance().getPlayerLink();}
    private void say(Player p,String s){p.sendMessage("§6南可熙提醒您： §f"+s);}
    private void main(Runnable r){if(plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,r);}
    private void playerMain(Player p,Runnable r){main(()->{if(p.isOnline()&&ui.authenticated(p))r.run();});}
    private void save() {
        try {File tmp=new File(file.getParentFile(),file.getName()+".tmp");data.save(tmp);Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        catch(IOException e){throw new IllegalStateException("Cannot persist sync identity journal",e);}
    }
    private void refresh() {
        Map<String,Binding> snapshot=new HashMap<>();var section=data.getConfigurationSection("bindings");
        if(section!=null)for(String key:section.getKeys(false)) {
            String root="bindings."+key;snapshot.put(key,new Binding(data.getString(root+".java-name"),UUID.fromString(data.getString(root+".java-id")),UUID.fromString(data.getString(root+".pe-id")),UUID.fromString(data.getString(root+".shared")),data.getString(root+".source"),data.getString(root+".state")));
        }bindings=Map.copyOf(snapshot);
    }
    private void write(Binding b) {
        String root="bindings."+b.javaName().toLowerCase(Locale.ROOT);data.set(root+".java-name",b.javaName());data.set(root+".java-id",b.javaId().toString());data.set(root+".pe-id",b.peId().toString());data.set(root+".shared",b.shared().toString());data.set(root+".source",b.source());data.set(root+".state",b.state());save();refresh();
    }
    private Binding binding(UUID id){return bindings.values().stream().filter(b->id.equals(b.javaId())||id.equals(b.peId())||id.equals(b.shared())).findFirst().orElse(null);}
    private String hashIp(String address) {
        if(address==null||address.isBlank())return "";
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((salt+"|"+address).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
    }
    private Account current(Player p) {
        var addr=p.getAddress();String ip=addr==null||addr.getAddress()==null?"":hashIp(addr.getAddress().getHostAddress());
        return new Account(p.getUniqueId(),p.getName(),ui.bedrock(p),ip,p.getFirstPlayed()>0?p.getFirstPlayed():System.currentTimeMillis());
    }
    private void record(Account a) {
        Account before=accounts.get(a.id());if(before!=null)a=new Account(a.id(),a.name(),a.pe(),a.ip(),Math.min(before.first(),a.first()));accounts.put(a.id(),a);
        String root="accounts."+a.id();data.set(root+".name",a.name());data.set(root+".pe",a.pe());data.set(root+".ip",a.ip());data.set(root+".first",a.first());
    }
    // Include authenticated historical accounts so the old device need not be online.
    private void seed() {
        List<Account> known=new ArrayList<>();for(OfflinePlayer p:Bukkit.getOfflinePlayers())if(p.getName()!=null)known.add(new Account(p.getUniqueId(),p.getName(),FloodgateApi.getInstance().isFloodgateId(p.getUniqueId()),"",p.getFirstPlayed()));
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            List<Account> found=new ArrayList<>();var auth=AuthMeApi.getInstance();
            for(Account a:known)if(auth.isRegistered(a.name())&&auth.getLastLoginTime(a.name())!=null&&auth.getLastLoginTime(a.name()).isAfter(java.time.Instant.EPOCH))found.add(new Account(a.id(),a.name(),a.pe(),hashIp(auth.getLastIp(a.name())),a.first()>0?a.first():System.currentTimeMillis()));
            main(()->{for(Account a:found)if(!accounts.containsKey(a.id()))record(a);save();});
        });
    }
    static boolean candidate(Account own,Account peer,boolean sameIp) {
        return !own.id().equals(peer.id())&&own.pe()!=peer.pe()&&(!sameIp||(!own.ip().isEmpty()&&own.ip().equals(peer.ip())));
    }
    static UUID source(Account own,Account peer,boolean keepOwn){return keepOwn?own.id():peer.id();}
    void login(Player p) {
        Account own=current(p);record(own);save();Binding b=binding(own.id());
        if(b!=null){say(p,"双端进度来源：「"+b.source()+"」。当前使用同一份数据，另一端登录也会继承。");return;}
        if(!p.hasPermission("floodgate.command.linkaccount"))return;
        List<Account> peers=accounts.values().stream().filter(q->candidate(own,q,true)&&binding(q.id())==null).sorted(Comparator.comparingLong(Account::first)).limit(4).toList();
        if(peers.isEmpty())return;
        say(p,"检测到同 IP 的另一个平台账号。你可以验证其服内密码并继承它的进度，也可以忽略，稍后在账号菜单手动绑定。");
        if(ui.bedrock(p))picker(p,peers,0,"发现可同步的进度");
        else for(Account q:peers)p.sendMessage(Component.text("[同步 "+label(q)+" 的进度]  点击选择，不会直接绑定").clickEvent(ClickEvent.runCommand("miao sync select "+q.id())));
    }
    private String label(Account a){return (a.pe()?"PE · ":"PC · ")+a.name();}
    void command(Player p,String[] a) {
        if(!ui.authenticated(p)){say(p,"请先完成自己的登录。");return;}
        if(!p.hasPermission("floodgate.command.linkaccount")){say(p,"你没有账号绑定权限。");return;}
        if(a.length>0&&a[0].equals("unlink")){if(a.length==2&&a[1].equals("confirm"))unlink(p);else say(p,"解除后两端恢复各自原来的身份。确认：/miao sync unlink confirm");return;}
        if(binding(p.getUniqueId())!=null){say(p,"已绑定，进度来源："+binding(p.getUniqueId()).source()+"。解除：/miao sync unlink");return;}
        Account own=current(p);record(own);save();
        if(a.length==2&&a[0].equals("select")) {
            Account peer=null;try{peer=accounts.get(UUID.fromString(a[1]));}catch(IllegalArgumentException ignored){}
            if(peer==null)peer=accounts.values().stream().filter(q->q.name().equalsIgnoreCase(a[1])).findFirst().orElse(null);
            if(peer==null||!candidate(own,peer,false)){say(p,"找不到另一个平台的已登录账号。");return;}choose(p,peer);return;
        }
        List<Account> peers=accounts.values().stream().filter(q->candidate(own,q,false)&&binding(q.id())==null).sorted(Comparator.comparing((Account q)->!q.ip().equals(own.ip())).thenComparingLong(Account::first)).toList();
        picker(p,peers,0,"选择要绑定的账号");
    }
    private Holder panel(Player p,int size,String title){Holder h=new Holder(p);h.inventory=Bukkit.createInventory(h,size,title);for(int i=0;i<size;i++)put(h,i,Material.GRAY_STAINED_GLASS_PANE," ",()->{});return h;}
    private void put(Holder h,int slot,Material mat,String title,Runnable action,String... lore){ItemStack item=new ItemStack(mat);var meta=item.getItemMeta();meta.setDisplayName(title);meta.setLore(Arrays.asList(lore));item.setItemMeta(meta);h.inventory.setItem(slot,item);h.actions.put(slot,action);}
    private void picker(Player p,List<Account> peers,int requested,String title) {
        int page=Math.max(0,Math.min(requested,Math.max(0,(peers.size()-1)/28))),start=page*28,end=Math.min(start+28,peers.size());
        if(ui.bedrock(p)) {
            var b=SimpleForm.builder().title(title).content("请选择你自己拥有的另一个平台账号。\n验证对方的服内密码后才会绑定；下一步选择进度来源。");List<Runnable> actions=new ArrayList<>();
            for(Account q:peers.subList(start,end)){b.button(label(q));actions.add(()->choose(p,q));}if(page>0){b.button("上一页");actions.add(()->picker(p,peers,page-1,title));}if(end<peers.size()){b.button("下一页");actions.add(()->picker(p,peers,page+1,title));}b.button("暂不同步 / 返回指南针");actions.add(()->ui.command(p,new String[]{"menu"}));b.validResultHandler(r->playerMain(p,actions.get(r.clickedButtonId())));FloodgateApi.getInstance().sendForm(p.getUniqueId(),b.build());return;
        }
        Holder h=panel(p,54,"§8双端绑定 · 选择账号 · "+(page+1));for(int i=start;i<end;i++){Account q=peers.get(i);int offset=i-start,slot=10+offset/7*9+offset%7;put(h,slot,Material.PLAYER_HEAD,"§b"+label(q),()->choose(p,q),"§7必须验证这个账号的服内密码","§7点击后选择保留 PC 或 PE 的进度");ItemStack item=h.inventory.getItem(slot);var meta=(org.bukkit.inventory.meta.SkullMeta)item.getItemMeta();meta.setOwningPlayer(Bukkit.getOfflinePlayer(q.id()));item.setItemMeta(meta);}
        if(page>0)put(h,45,Material.ARROW,"§f上一页",()->picker(p,peers,page-1,title));if(end<peers.size())put(h,53,Material.ARROW,"§f下一页",()->picker(p,peers,page+1,title));put(h,49,Material.COMPASS,"§f暂不同步 / 返回指南针",()->ui.command(p,new String[]{"menu"}));p.openInventory(h.inventory);
        if(peers.isEmpty())say(p,"另一个平台需要至少完成一次注册登录；不必同时在线。也可使用原 Floodgate 验证码绑定。");
    }
    private void choose(Player p,Account peer) {
        Account own=current(p);if(!candidate(own,peer,false)||binding(own.id())!=null||binding(peer.id())!=null){say(p,"账号绑定状态已变化，请重新打开菜单。");return;}
        String description="绑定 "+label(own)+" ↔ "+label(peer)+"\n\n选择保留哪一份进度。另一份旧数据保留，物品和金币不叠加。\n验证账号："+peer.name()+"\n完成后两端重连，均使用 Java 账号的服内密码。";
        boolean ownFirst=own.first()<peer.first();
        if(ui.bedrock(p)){var form=SimpleForm.builder().title("选择进度来源").content(description).button("保留 "+label(peer)+(ownFirst?"":"（先玩的账号，推荐）")).button("保留 "+label(own)+(ownFirst?"（先玩的账号，推荐）":"")).button("取消").validResultHandler(r->playerMain(p,()->{if(r.clickedButtonId()<2)password(p,peer,r.clickedButtonId()==1);})).build();FloodgateApi.getInstance().sendForm(p.getUniqueId(),form);return;}
        Holder h=panel(p,27,"§8双端绑定 · 选择进度来源");put(h,11,Material.CHEST,"§a保留 "+label(peer),()->password(p,peer,false),ownFirst?"§7对方账号的进度":"§e先玩的账号，推荐","§7另一份旧数据保留；不叠加物品金币");put(h,15,Material.ENDER_CHEST,"§b保留 "+label(own),()->password(p,peer,true),ownFirst?"§e先玩的账号，推荐":"§7当前账号的进度","§7另一份旧数据保留；不叠加物品金币");put(h,22,Material.ARROW,"§f取消",()->command(p,new String[0]));p.openInventory(h.inventory);
    }
    void cancelInput(Player p){inputs.remove(p.getUniqueId());}
    private void password(Player p,Account peer,boolean own) {
        ui.names.cancelInput(p);inputs.remove(p.getUniqueId());Account from=own?current(p):peer;
        say(p,"将同步「"+label(from)+"」的进度。现在验证「"+peer.name()+"」的服内密码，完成前不会切换数据。");
        if(ui.bedrock(p)){var form=CustomForm.builder().title("验证绑定账号").label("进度来源："+label(from)+"\n请输入 "+peer.name()+" 的服内密码（不是 Xbox 密码）。不保存密码，取消或留空不会绑定。输入框会显示输入内容。")
            .input("对方账号的服内密码","请输入服内密码").validResultHandler(r->{String secret=r.asInput(1);playerMain(p,()->verify(p,peer,own,secret));}).build();FloodgateApi.getInstance().sendForm(p.getUniqueId(),form);return;}
        p.closeInventory();inputs.put(p.getUniqueId(),new Input(peer,own,System.currentTimeMillis()+120000));say(p,"下一条聊天只用于密码验证，不会广播。输入「取消」退出；两分钟内有效。不要把密码写成命令。");
    }
    private void verify(Player p,Account peer,boolean own,String secret) {
        if(secret.isEmpty()||secret.equals("取消")){say(p,"已取消绑定。");return;}
        Account caller=current(p);String throttle=caller.id()+":"+peer.id();long now=System.currentTimeMillis();
        if(attempts.getOrDefault(throttle,0L)>now||attempts.getOrDefault(peer.id().toString(),0L)>now){say(p,"请稍后再试，每次验证至少间隔十秒。");return;}
        attempts.put(throttle,now+10000);attempts.put(peer.id().toString(),now+10000);
        if(binding(caller.id())!=null||binding(peer.id())!=null||working.contains(caller.id())||working.contains(peer.id())){say(p,"账号已绑定或正在处理，请勿重复提交。");return;}
        working.add(caller.id());working.add(peer.id());
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            boolean valid=false,alreadyLinked=false;try{alreadyLinked=links().isLinkedPlayer(caller.id()).get(15,TimeUnit.SECONDS)||links().isLinkedPlayer(peer.id()).get(15,TimeUnit.SECONDS);if(!alreadyLinked)valid=AuthMeApi.getInstance().checkPassword(peer.name(),secret);}catch(Exception ignored){}
            boolean passed=valid,occupied=alreadyLinked;playerMain(p,()->{working.remove(caller.id());working.remove(peer.id());if(occupied){say(p,"其中一个账号已通过 Floodgate 绑定，请先解除原绑定；这次没有更改进度。");return;}if(!passed){say(p,"账号密码不正确或验证暂不可用，此次没有绑定或更改进度。");return;}bind(p,caller,peer,own);});
            main(()->{if(!p.isOnline()||!ui.authenticated(p)){working.remove(caller.id());working.remove(peer.id());}});
        });
    }
    private void bind(Player p,Account own,Account peer,boolean keepOwn) {
        if(!candidate(own,peer,false)||binding(own.id())!=null||binding(peer.id())!=null||working.contains(own.id())||working.contains(peer.id())){say(p,"账号状态已变化，此次未绑定。");return;}
        Account java=own.pe()?peer:own,pe=own.pe()?own:peer;Account from=keepOwn?own:peer;
        if(!java.name().matches("[A-Za-z0-9_]{1,16}")){say(p,"Java 账号格式不支持绑定。");return;}
        Binding b=new Binding(java.name(),java.id(),pe.id(),source(own,peer,keepOwn),label(from),"PREPARED");
        try{if(file.exists())Files.copy(file.toPath(),new File(file.getParentFile(),"crossplay-sync.before-"+System.currentTimeMillis()+".yml").toPath());write(b);}catch(IOException e){say(p,"绑定备份失败，请联系管理员；身份没有改变。");return;}
        working.add(own.id());working.add(peer.id());
        kick(b,"双端绑定正在完成。进度来源："+b.source()+"。请稍候重连，两端使用 Java 账号 "+b.javaName()+" 的服内密码。");
        Bukkit.getScheduler().runTaskLater(plugin,()->Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            try {
                PlayerLink api=links();if(!api.isEnabledAndAllowed())throw new IllegalStateException("Local linking disabled");
                if(api.isLinkedPlayer(b.javaId()).get(15,TimeUnit.SECONDS)||api.isLinkedPlayer(b.peId()).get(15,TimeUnit.SECONDS))throw new IllegalStateException("Account already linked");
                api.linkPlayer(b.peId(),b.shared(),b.javaName()).get(20,TimeUnit.SECONDS);
                var result=api.getLinkedPlayer(b.peId()).get(15,TimeUnit.SECONDS);
                if(result==null||!b.shared().equals(result.getJavaUniqueId())||!b.javaName().equals(result.getJavaUsername()))throw new IllegalStateException("Link verification failed");
                main(()->{write(new Binding(b.javaName(),b.javaId(),b.peId(),b.shared(),b.source(),"ACTIVE"));working.remove(own.id());working.remove(peer.id());plugin.getLogger().info("双端绑定完成："+b.javaName()+"，进度来源："+b.source()+"；旧数据未复制或删除。");});
            }catch(Exception e){main(()->{working.remove(own.id());working.remove(peer.id());plugin.getLogger().warning("双端绑定未完成，已阻止账号进服，正在核对绑定日志："+e.getClass().getSimpleName());recover();});}
        }),40L);
    }
    private void kick(Binding b,String message){for(Player p:Bukkit.getOnlinePlayers())if(p.getUniqueId().equals(b.javaId())||p.getUniqueId().equals(b.peId())||p.getUniqueId().equals(b.shared()))p.kick(Component.text("南可熙提醒您："+message));}
    private void recover() {
        for(Binding b:bindings.values())if(!b.state().equals("ACTIVE")&&!working.contains(b.javaId())&&!working.contains(b.peId()))Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            try {
                var linked=links().getLinkedPlayer(b.peId()).get(15,TimeUnit.SECONDS);
                boolean matching=linked!=null&&linked.getJavaUniqueId().equals(b.shared())&&linked.getJavaUsername().equals(b.javaName());
                main(()->{
                    Binding latest=bindings.get(b.javaName().toLowerCase(Locale.ROOT));if(!b.equals(latest))return;
                    if(matching&&!b.state().equals("UNLINKING")){write(new Binding(b.javaName(),b.javaId(),b.peId(),b.shared(),b.source(),"ACTIVE"));}
                    else if(linked==null){data.set("bindings."+b.javaName().toLowerCase(Locale.ROOT),null);save();refresh();}
                    else {write(new Binding(b.javaName(),b.javaId(),b.peId(),b.shared(),b.source(),"REVIEW"));plugin.getLogger().severe("双端绑定日志与 Floodgate 不一致，请核对："+b.javaName());}
                });
            }catch(Exception e){plugin.getLogger().warning("暂时无法核对双端绑定，保留身份日志，稍后重试。");}
        });
    }
    private void unlink(Player p) {
        Binding b=binding(p.getUniqueId());if(b==null){p.performCommand("floodgate:unlinkaccount");return;}if(!b.state().equals("ACTIVE")){say(p,"绑定正在核对，请联系管理员。");return;}
        write(new Binding(b.javaName(),b.javaId(),b.peId(),b.shared(),b.source(),"UNLINKING"));kick(b,"正在解除绑定；两端重连后恢复各自原有进度。");
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{try{links().unlinkPlayer(b.peId()).get(20,TimeUnit.SECONDS);}catch(Exception e){plugin.getLogger().warning("解除绑定需要核对，请勿删除身份日志。");}main(this::recover);});
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void profile(AsyncPlayerPreLoginEvent e) {
        Binding b=bindings.get(e.getName().toLowerCase(Locale.ROOT));if(b==null)b=bindings.values().stream().filter(q->q.peId().equals(e.getUniqueId())).findFirst().orElse(null);if(b==null)return;
        if(!b.state().equals("ACTIVE")){e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,"南可熙提醒您：绑定正在完成或核对，请稍后重连。");return;}
        if(!e.getName().equalsIgnoreCase(b.javaName())){e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,"南可熙提醒您：Floodgate 身份尚未刷新，请稍后重连以同步正确进度。");return;}
        if(e.getName().equalsIgnoreCase(b.javaName())){var profile=e.getPlayerProfile().clone();profile.setId(b.shared());profile.setName(b.javaName());e.setPlayerProfile(profile);}
    }
    @EventHandler(priority=EventPriority.LOWEST) public void input(AsyncChatEvent e) {
        Input request=inputs.remove(e.getPlayer().getUniqueId());if(request==null)return;e.setCancelled(true);
        String secret=PlainTextComponentSerializer.plainText().serialize(e.message());playerMain(e.getPlayer(),()->{if(request.until()<System.currentTimeMillis()){say(e.getPlayer(),"验证输入已超时，这条消息没有发送，请重新打开绑定菜单。");return;}verify(e.getPlayer(),request.peer(),request.own(),secret);});
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void nativeUnlink(PlayerCommandPreprocessEvent e) {
        String command=e.getMessage().substring(1).split(" ",2)[0].toLowerCase(Locale.ROOT);if(command.contains(":"))command=command.substring(command.indexOf(':')+1);
        if((command.equals("unlinkaccount")||command.equals("linkaccount"))&&binding(e.getPlayer().getUniqueId())!=null){e.setCancelled(true);if(command.equals("unlinkaccount"))command(e.getPlayer(),new String[]{"unlink"});else say(e.getPlayer(),"已建立或正在处理绑定，请通过账号菜单管理，避免进度来源被改变。");}
    }
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getInventory().getHolder() instanceof Holder h))return;e.setCancelled(true);if(e.getWhoClicked() instanceof Player p&&h.owner.equals(p.getUniqueId())&&e.getRawSlot()>=0&&e.getRawSlot()<h.inventory.getSize()){Runnable r=h.actions.get(e.getRawSlot());if(r!=null)playerMain(p,r);}}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getInventory().getHolder() instanceof Holder)e.setCancelled(true);}
    @EventHandler public void quit(PlayerQuitEvent e){inputs.remove(e.getPlayer().getUniqueId());}
}
