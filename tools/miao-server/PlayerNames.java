package cn.nankexi;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.SkullMeta;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.cumulus.form.*;

/** Display aliases never participate in account lookup or permission decisions. */
final class PlayerNames implements Listener {
    private final MiaoServer plugin;
    private final MiaoInterface ui;
    private final File file;
    private final YamlConfiguration data=new YamlConfiguration();
    private final Map<UUID,String> nicknames=new ConcurrentHashMap<>();
    private final Map<UUID,Map<UUID,String>> notes=new ConcurrentHashMap<>();
    private final Map<UUID,Input> inputs=new ConcurrentHashMap<>();
    private record Input(UUID target,long expires) {}
    private static final class Holder implements InventoryHolder {
        final UUID owner; final Map<Integer,Runnable> actions=new HashMap<>(); Inventory inventory;
        Holder(Player p){owner=p.getUniqueId();} public Inventory getInventory(){return inventory;}
    }
    PlayerNames(MiaoServer p,MiaoInterface ui) {
        plugin=p;this.ui=ui;file=new File(p.getDataFolder(),"player-names.yml");
        try {if(file.exists())data.load(file);}catch(Exception e){throw new IllegalStateException("Refusing to replace unreadable player names",e);}
        var players=data.getConfigurationSection("players");
        if(players!=null)for(String key:players.getKeys(false)) {
            UUID id=UUID.fromString(key);String nick=data.getString("players."+key+".nickname","");if(valid(nick))nicknames.put(id,nick);
            var section=data.getConfigurationSection("players."+key+".notes");
            if(section!=null)for(String target:section.getKeys(false)){String note=section.getString(target,"");if(valid(note))notes.computeIfAbsent(id,k->new ConcurrentHashMap<>()).put(UUID.fromString(target),note);}
        }
        Bukkit.getPluginManager().registerEvents(this,p);
        // EssentialsChat installs its renderer at HIGHEST; wrap it after all plugins enable.
        Bukkit.getScheduler().runTask(p,()->Bukkit.getPluginManager().registerEvent(AsyncChatEvent.class,new Listener(){},EventPriority.HIGHEST,(listener,event)->chat((AsyncChatEvent)event),p,true));
    }
    static boolean valid(String s) {
        int length=s.codePointCount(0,s.length());
        return length>=1&&length<=16&&s.equals(s.strip())&&s.codePoints().allMatch(c->Character.isLetterOrDigit(c)||c=='_'||c=='-'||c==' ');
    }
    String nickname(UUID id,String fallback){return nicknames.getOrDefault(id,fallback);}
    String label(UUID viewer,UUID target,String fallback){return notes.getOrDefault(viewer,Map.of()).getOrDefault(target,nickname(target,fallback));}
    String label(Player viewer,Player target){return notes.getOrDefault(viewer.getUniqueId(),Map.of()).getOrDefault(target.getUniqueId(),ui.display(target));}
    private void say(Player p,String text){p.sendMessage("§6南可熙提醒您： §f"+text);}
    private void main(Player p,Runnable action){Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline()&&ui.authenticated(p))action.run();});}
    private void save() {
        try {File temp=new File(file.getParentFile(),file.getName()+".tmp");data.save(temp);Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        catch(IOException e){throw new IllegalStateException("Cannot persist player names",e);}
    }
    boolean set(Player p,UUID target,String value) {
        if(!ui.authenticated(p))return false;
        if(!value.isEmpty()&&!valid(value)){say(p,"请使用 1–16 个中文、字母、数字、空格、下划线或短横线，不支持颜色代码。");return false;}
        String key="players."+p.getUniqueId();
        if(target==null) {
            data.set(key+".nickname",value.isEmpty()?null:value);
            if(value.isEmpty())nicknames.remove(p.getUniqueId());else nicknames.put(p.getUniqueId(),value);
            ui.decorate(p);say(p,value.isEmpty()?"已恢复原来的显示名字。":"公开昵称已设为「"+value+"」，实际账号不变。");
        }else {
            data.set(key+".notes."+target,value.isEmpty()?null:value);
            var own=notes.computeIfAbsent(p.getUniqueId(),k->new ConcurrentHashMap<>());if(value.isEmpty())own.remove(target);else own.put(target,value);
            say(p,value.isEmpty()?"已删除这位玩家的私人备注。":"私人备注已保存，只有你能看到。");
        }
        save();return true;
    }
    void command(Player p,String[] args) {
        if(!ui.authenticated(p)){say(p,"请先完成登录。");return;}
        if(args.length==0){menu(p);return;}
        if(args[0].equals("nick")){set(p,null,String.join(" ",Arrays.copyOfRange(args,1,args.length)));return;}
        if(args[0].equals("note")&&args.length>=2) {
            OfflinePlayer target=Arrays.stream(Bukkit.getOfflinePlayers()).filter(q->q.getName()!=null&&q.getName().equalsIgnoreCase(args[1])).findFirst().orElse(null);
            Player online=Bukkit.getPlayerExact(args[1]);if(online!=null&&ui.authenticated(online))target=online;
            if(target==null){say(p,"找不到这个实际账号，请从头像列表选择，或填写曾进服玩家的真实账号名。");return;}
            set(p,target.getUniqueId(),String.join(" ",Arrays.copyOfRange(args,2,args.length)));return;
        }
        say(p,"请从菜单操作，或 /miao names nick 昵称；/miao names note 实际账号 备注。省略文字可恢复或删除。");
    }
    private void edit(Player p,UUID target,String account) {
        ui.sync.cancelInput(p);
        String current=target==null?nicknames.getOrDefault(p.getUniqueId(),""):notes.getOrDefault(p.getUniqueId(),Map.of()).getOrDefault(target,"");
        if(ui.bedrock(p)) {
            var form=CustomForm.builder().title(target==null?"我的公开昵称":"私人备注 · "+account)
                .label(target==null?"只改显示名，账号和存档保持不变。空白可恢复原名。":"备注仅自己可见。空白可删除，实际账号："+account)
                .input("显示文字（1–16 字）","中文或字母数字",current)
                .validResultHandler(r->{String value=r.asInput(1);main(p,()->set(p,target,value));}).build();
            FloodgateApi.getInstance().sendForm(p.getUniqueId(),form);
        }else {
            p.closeInventory();inputs.put(p.getUniqueId(),new Input(target,System.currentTimeMillis()+120000));
            say(p,"请在聊天中输入"+(target==null?"公开昵称":"「"+account+"」的私人备注")+"，这条文字不会发送给别人。输入「取消」退出，「清除」删除；两分钟内有效。");
        }
    }
    private void put(Holder h,int slot,Material material,String title,Runnable action,String... lore) {
        ItemStack item=new ItemStack(material);var meta=item.getItemMeta();meta.setDisplayName(title);meta.setLore(Arrays.asList(lore));item.setItemMeta(meta);h.inventory.setItem(slot,item);h.actions.put(slot,action);
    }
    private Holder panel(Player p,int size,String title) {
        Holder h=new Holder(p);h.inventory=Bukkit.createInventory(h,size,title);
        for(int i=0;i<size;i++)put(h,i,Material.GRAY_STAINED_GLASS_PANE," ",()->{});return h;
    }
    void menu(Player p) {
        ui.sync.cancelInput(p);
        inputs.remove(p.getUniqueId());
        if(ui.bedrock(p)) {
            FloodgateApi.getInstance().sendForm(p.getUniqueId(),SimpleForm.builder().title("昵称与私人备注").content("公开昵称："+ui.display(p)+"\n实际账号："+p.getName()+"\n私人备注只影响你的聊天与玩家选择列表。")
                .button("修改我的公开昵称").button("给玩家备注 / 管理备注").button("恢复我的原名").button("返回指南针")
                .validResultHandler(r->main(p,()->{switch(r.clickedButtonId()){case 0->edit(p,null,p.getName());case 1->targets(p,0);case 2->set(p,null,"");case 3->ui.command(p,new String[]{"menu"});}})).build());return;
        }
        Holder h=panel(p,27,"§8昵称与备注 · 我的玩家中心");
        put(h,10,Material.NAME_TAG,"§b修改我的公开昵称",()->edit(p,null,p.getName()),"§7当前："+ui.display(p),"§7账号、存档和权限保持原样");
        put(h,13,Material.PLAYER_HEAD,"§a给玩家备注",()->targets(p,0),"§7选择头像，新建或修改私人备注","§7聊天与选择列表仅对你显示");
        put(h,16,Material.MILK_BUCKET,"§e恢复我的原名",()->set(p,null,""));
        put(h,22,Material.ARROW,"§f返回指南针",()->ui.command(p,new String[]{"menu"}));p.openInventory(h.inventory);
    }
    void cancelInput(Player p){inputs.remove(p.getUniqueId());}
    private void targets(Player p,int requested) {
        Map<UUID,OfflinePlayer> known=new HashMap<>();
        for(Player q:Bukkit.getOnlinePlayers())if(ui.authenticated(q)&&!q.getUniqueId().equals(p.getUniqueId()))known.put(q.getUniqueId(),q);
        for(UUID id:notes.getOrDefault(p.getUniqueId(),Map.of()).keySet())known.putIfAbsent(id,Bukkit.getOfflinePlayer(id));
        List<OfflinePlayer> list=known.values().stream().sorted(Comparator.comparing(q->Objects.toString(q.getName(),q.getUniqueId().toString()),String.CASE_INSENSITIVE_ORDER)).toList();
        int page=Math.max(0,Math.min(requested,Math.max(0,(list.size()-1)/28))),start=page*28,end=Math.min(start+28,list.size());
        if(ui.bedrock(p)) {
            SimpleForm.Builder b=SimpleForm.builder().title("私人备注 · "+(page+1)).content(list.isEmpty()?"暂无在线玩家或已有备注。可用 /miao names note 实际账号 备注，为离线好友备注。":"选择玩家，空白备注即可删除。");
            List<Runnable> actions=new ArrayList<>();for(int i=start;i<end;i++){OfflinePlayer q=list.get(i);b.button(label(p.getUniqueId(),q.getUniqueId(),Objects.toString(q.getName(),"未知账号"))+" · "+Objects.toString(q.getName(),"离线"));actions.add(()->edit(p,q.getUniqueId(),Objects.toString(q.getName(),q.getUniqueId().toString())));}
            if(page>0){b.button("上一页");actions.add(()->targets(p,page-1));}if(end<list.size()){b.button("下一页");actions.add(()->targets(p,page+1));}b.button("返回昵称中心");actions.add(()->menu(p));
            b.validResultHandler(r->main(p,actions.get(r.clickedButtonId())));FloodgateApi.getInstance().sendForm(p.getUniqueId(),b.build());return;
        }
        Holder h=panel(p,54,"§8私人备注 · 玩家头像 · "+(page+1));
        for(int i=start;i<end;i++) {
            OfflinePlayer q=list.get(i);int offset=i-start,slot=10+offset/7*9+offset%7;String account=Objects.toString(q.getName(),q.getUniqueId().toString());
            put(h,slot,Material.PLAYER_HEAD,"§a"+label(p.getUniqueId(),q.getUniqueId(),account),()->edit(p,q.getUniqueId(),account),"§7实际账号："+account,"§7点击编辑，仅你可见；输入清除可删除");
            ItemStack item=h.inventory.getItem(slot);SkullMeta meta=(SkullMeta)item.getItemMeta();meta.setOwningPlayer(q);item.setItemMeta(meta);
        }
        if(page>0)put(h,45,Material.ARROW,"§f上一页",()->targets(p,page-1));if(end<list.size())put(h,53,Material.ARROW,"§f下一页",()->targets(p,page+1));
        put(h,49,Material.COMPASS,"§f返回昵称中心",()->menu(p));p.openInventory(h.inventory);
    }
    @EventHandler(priority=EventPriority.LOWEST) public void input(AsyncChatEvent e) {
        Input input=inputs.remove(e.getPlayer().getUniqueId());if(input==null)return;
        e.setCancelled(true);String value=PlainTextComponentSerializer.plainText().serialize(e.message());
        main(e.getPlayer(),()->{if(System.currentTimeMillis()>input.expires()){say(e.getPlayer(),"输入已超时，这条消息没有发送，请重新打开昵称菜单。");return;}if(value.equals("取消")){say(e.getPlayer(),"已取消。");return;}set(e.getPlayer(),input.target(),value.equals("清除")?"":value);});
    }
    public void chat(AsyncChatEvent e) {
        var renderer=e.renderer();UUID target=e.getPlayer().getUniqueId();String account=e.getPlayer().getName();String publicName=PlainTextComponentSerializer.plainText().serialize(e.getPlayer().displayName());
        // Viewer-unaware renderers cache their first result. Cache a neutral token, never a private note.
        String token="NLK_NAME_"+UUID.randomUUID();Component placeholder=Component.text(token);
        e.renderer((source,display,message,viewer)->{
            String name=viewer instanceof Player p?notes.getOrDefault(p.getUniqueId(),Map.of()).getOrDefault(target,publicName):publicName;
            Component alias=Component.text(name).hoverEvent(HoverEvent.showText(Component.text("实际账号："+account+"\n公开昵称："+publicName)));
            return renderer.render(source,placeholder,message,viewer).replaceText(builder->builder.matchLiteral(token).replacement(alias));
        });
    }
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getInventory().getHolder() instanceof Holder h))return;e.setCancelled(true);if(e.getWhoClicked() instanceof Player p&&h.owner.equals(p.getUniqueId())&&e.getRawSlot()>=0&&e.getRawSlot()<h.inventory.getSize()){Runnable action=h.actions.get(e.getRawSlot());if(action!=null)main(p,action);}}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getInventory().getHolder() instanceof Holder)e.setCancelled(true);}
    @EventHandler public void quit(PlayerQuitEvent e){inputs.remove(e.getPlayer().getUniqueId());}
}
