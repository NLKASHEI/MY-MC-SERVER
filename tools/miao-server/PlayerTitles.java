package cn.nankexi;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.format.TextColor;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.milkbowl.vault.economy.Economy;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.cumulus.form.*;

/** UUID-owned cosmetics; historical backfill is idempotent and never grants gameplay powers. */
final class PlayerTitles implements Listener {
    record Title(String id,Component display,String legacy,String plain,String kind,String metric,long target,double price,Material icon,String description,Map<String,Long> requirements){}
    record View(String legacy,String plain,boolean chat,boolean tab,boolean sidebar){}
    private record Draft(String id,String name,int palette){}
    private final Map<UUID,Long> customInputs=new ConcurrentHashMap<>();
    private final Map<UUID,Draft> drafts=new HashMap<>();
    private static final String[] PALETTE_NAMES={"星河紫青","落日金红","薄荷青绿","月光粉蓝"};
    private static final int[][] PALETTES={{0xA78BFA,0x22D3EE},{0xFDE68A,0xFB7185},{0xBBF7D0,0x14B8A6},{0xF9A8D4,0x93C5FD}};
    private double customPrice;
    private static final Map<String,Statistic> RAW=Map.ofEntries(Map.entry("kills",Statistic.MOB_KILLS),Map.entry("fish",Statistic.FISH_CAUGHT),Map.entry("walk_cm",Statistic.WALK_ONE_CM),Map.entry("sprint_cm",Statistic.SPRINT_ONE_CM),Map.entry("swim_cm",Statistic.SWIM_ONE_CM),Map.entry("elytra_cm",Statistic.AVIATE_ONE_CM),Map.entry("boat_cm",Statistic.BOAT_ONE_CM),Map.entry("horse_cm",Statistic.HORSE_ONE_CM),Map.entry("rail_cm",Statistic.MINECART_ONE_CM),Map.entry("trades",Statistic.TRADED_WITH_VILLAGER),Map.entry("bred",Statistic.ANIMALS_BRED),Map.entry("enchanted",Statistic.ITEM_ENCHANTED),Map.entry("raids",Statistic.RAID_WIN),Map.entry("sleep",Statistic.SLEEP_IN_BED));
    private static final Map<String,org.bukkit.entity.EntityType> BOSSES=Map.of("dragon",org.bukkit.entity.EntityType.ENDER_DRAGON,"wither",org.bukkit.entity.EntityType.WITHER,"elder",org.bukkit.entity.EntityType.ELDER_GUARDIAN);
    private static final Set<String> METRICS;
    static{Set<String> all=new HashSet<>(RAW.keySet());all.addAll(BOSSES.keySet());all.addAll(Set.of("joined","seconds","mined","placed","advancements","biomes","balance","team_size","harvest"));METRICS=Set.copyOf(all);}
    private final MiaoServer plugin;private final MiaoInterface ui;private final File file;
    private final YamlConfiguration data=new YamlConfiguration();
    private final LinkedHashMap<String,Title> catalog=new LinkedHashMap<>();
    private final Map<UUID,View> views=new ConcurrentHashMap<>();
    private final Map<UUID,Map<String,Long>> snapshots=new ConcurrentHashMap<>();
    private final Map<UUID,Integer> counts=new ConcurrentHashMap<>();
    private final Map<UUID,Integer> totals=new ConcurrentHashMap<>();
    private boolean dirty;
    private static final class Holder implements InventoryHolder {
        final UUID owner;final Map<Integer,Runnable> actions=new HashMap<>();Inventory inventory;
        Holder(Player p){owner=p.getUniqueId();}public Inventory getInventory(){return inventory;}
    }
    PlayerTitles(MiaoServer plugin,MiaoInterface ui){
        this.plugin=plugin;this.ui=ui;file=new File(plugin.getDataFolder(),"titles-data.yml");
        try{if(file.exists())data.load(file);}catch(Exception e){throw new IllegalStateException("Refusing to overwrite unreadable title ownership",e);}
        loadCatalog();var players=data.getConfigurationSection("players");if(players!=null)for(String key:players.getKeys(false))refresh(UUID.fromString(key));
        Bukkit.getPluginManager().registerEvents(this,plugin);
        if(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))new TitlePlaceholders(plugin,this).register();
        Bukkit.getScheduler().runTaskTimer(plugin,()->{for(Player p:Bukkit.getOnlinePlayers())if(ui.authenticated(p))check(p,true);flush();},600L,600L);
    }
    private void loadCatalog(){
        var source=YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"titles.yml"));var section=source.getConfigurationSection("titles");
        customPrice=source.getDouble("custom-price",100000);if(!Double.isFinite(customPrice)||customPrice<=0)throw new IllegalStateException("Invalid custom title price");
        if(source.getInt("schema")!=1||section==null)throw new IllegalStateException("Invalid title catalog");
        for(String id:section.getKeys(false)){
            String root="titles."+id,kind=source.getString(root+".kind","milestone"),metric=source.getString(root+".metric","joined");long target=source.getLong(root+".target",1);double price=source.getDouble(root+".price",0);
            if(!id.matches("[a-z0-9_]{1,40}")||!Set.of("milestone","shop","admin").contains(kind)||!METRICS.contains(metric)||target<1||!Double.isFinite(price)||price<0||(kind.equals("shop")&&price<=0))throw new IllegalStateException("Invalid title definition: "+id);
            Component display=MiniMessage.miniMessage().deserialize(source.getString(root+".display",id));String plain=PlainTextComponentSerializer.plainText().serialize(display);
            if(plain.codePointCount(0,plain.length())>24||plain.contains("\n"))throw new IllegalStateException("Title too long: "+id);
            Material icon=Material.matchMaterial(source.getString(root+".icon","NAME_TAG"));if(icon==null||!icon.isItem())throw new IllegalStateException("Invalid title icon: "+id);
            Map<String,Long> requirements=new LinkedHashMap<>();var conditions=source.getConfigurationSection(root+".requirements");
            if(conditions!=null)for(String key:conditions.getKeys(false)){long value=conditions.getLong(key);if(!METRICS.contains(key)||value<1)throw new IllegalStateException("Invalid title requirement: "+id+" / "+key);requirements.put(key,value);}
            if(requirements.isEmpty())requirements.put(metric,target);
            catalog.put(id,new Title(id,display,LegacyComponentSerializer.legacySection().serialize(display),plain,kind,metric,target,price,icon,source.getString(root+".description","纯外观称号，无属性加成"),Map.copyOf(requirements)));
        }
    }
    private String root(UUID id){return "players."+id;}
    static boolean validCustom(String name){int size=name.codePointCount(0,name.length());return size>=1&&size<=10&&name.equals(name.strip())&&name.codePoints().allMatch(c->Character.isLetterOrDigit(c)||c=='_'||c=='-'||c=='·'||c==' ');}
    private Title custom(String id,String name,int palette){
        if(!validCustom(name)||palette<0||palette>=PALETTES.length)throw new IllegalArgumentException("Invalid custom title");
        int[] points=("【"+name+"】").codePoints().toArray();int a=PALETTES[palette][0],b=PALETTES[palette][1];Component display=Component.empty();
        for(int i=0;i<points.length;i++){double t=i/(double)Math.max(1,points.length-1);int red=(int)(((a>>16)&255)*(1-t)+((b>>16)&255)*t),green=(int)(((a>>8)&255)*(1-t)+((b>>8)&255)*t),blue=(int)((a&255)*(1-t)+(b&255)*t);display=display.append(Component.text(new String(Character.toChars(points[i])),TextColor.color(red,green,blue)).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));}
        return new Title(id,display,LegacyComponentSerializer.legacySection().serialize(display),"【"+name+"】","custom","joined",1,customPrice,Material.NAME_TAG,"个人自定义称号 · "+PALETTE_NAMES[palette],Map.of("joined",1L));
    }
    private Title title(UUID owner,String id){Title standard=catalog.get(id);if(standard!=null)return standard;String key=root(owner)+".custom."+id;if(data.contains(key+".name"))return custom(id,data.getString(key+".name"),data.getInt(key+".palette"));Draft draft=drafts.get(owner);return draft!=null&&draft.id().equals(id)?custom(draft.id(),draft.name(),draft.palette()):null;}
    private int total(UUID owner){var custom=data.getConfigurationSection(root(owner)+".custom");return catalog.size()+(custom==null?0:custom.getKeys(false).size());}
    private void say(Player p,String text){p.sendMessage("§6南可熙提醒您：§f"+text);}
    private void later(Player p,Runnable action){Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline()&&ui.authenticated(p))action.run();});}
    private void save(){
        try{File tmp=new File(file.getParentFile(),file.getName()+".tmp");data.save(tmp);Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);dirty=false;}
        catch(IOException e){throw new IllegalStateException("Cannot persist title ownership",e);}
    }
    void flush(){if(dirty)save();}
    private boolean owned(UUID id,String title){return data.getBoolean(root(id)+".owned."+title);}
    private void refresh(UUID id){
        String base=root(id);Title selected=title(id,data.getString(base+".equipped",""));totals.put(id,total(id));
        var ownership=data.getConfigurationSection(base+".owned");counts.put(id,ownership==null?0:(int)ownership.getKeys(false).stream().filter(key->owned(id,key)&&title(id,key)!=null).count());Map<String,Long> current=new HashMap<>();for(String metric:METRICS)current.put(metric,data.getLong(base+".progress."+metric));snapshots.put(id,Map.copyOf(current));
        if(selected==null||!owned(id,selected.id())){views.remove(id);return;}
        views.put(id,new View(selected.legacy(),selected.plain(),data.getBoolean(base+".show.chat",true),data.getBoolean(base+".show.tab",true),data.getBoolean(base+".show.sidebar",true)));
    }
    String prefix(UUID id,String location){View v=views.get(id);return v!=null&&switch(location){case "chat"->v.chat();case "tab"->v.tab();case "sidebar"->v.sidebar();default->false;}?v.legacy()+"§r ":"";}
    String plain(UUID id){View v=views.get(id);return v==null?"未佩戴":v.plain();}
    String placeholder(UUID id,String key){
        if(key.equals("total"))return Integer.toString(id==null?catalog.size():totals.getOrDefault(id,catalog.size()));if(id==null)return "";
        if(key.equals("owned"))return Integer.toString(counts.getOrDefault(id,0));if(key.equals("plain"))return plain(id);
        if(key.equals("title")){View view=views.get(id);return view==null?"":view.legacy();}
        if(key.startsWith("metric_"))return Long.toString(snapshots.getOrDefault(id,Map.of()).getOrDefault(key.substring(7),0L));
        return null;
    }
    private long nonnegative(int value){return Math.max(0L,value);}
    private long completed(Player p){long count=0;var it=Bukkit.advancementIterator();while(it.hasNext()){var a=it.next();if(a.getKey().getNamespace().equals("minecraft")&&!a.getKey().getKey().startsWith("recipes/")&&p.getAdvancementProgress(a).isDone())count++;}return count;}
    private void track(UUID id,String metric,long value){String key=root(id)+".progress."+metric;if(value>data.getLong(key)){data.set(key,value);dirty=true;}}
    private Map<String,Long> raw(Player p){Map<String,Long> values=new HashMap<>();RAW.forEach((key,stat)->values.put(key,nonnegative(p.getStatistic(stat))));BOSSES.forEach((key,type)->values.put(key,nonnegative(p.getStatistic(Statistic.KILL_ENTITY,type))));return values;}
    private void sample(Player p,boolean credit){
        String base=root(p.getUniqueId());boolean historical=!data.getBoolean(base+".historical-seeded");
        for(var entry:raw(p).entrySet()){
            String metric=entry.getKey(),snapshot=base+".raw-high-water."+metric;long value=entry.getValue(),before=data.getLong(snapshot),progress=data.getLong(base+".progress."+metric);
            if(historical)track(p.getUniqueId(),metric,value);else if(credit&&value>before)track(p.getUniqueId(),metric,progress+value-before);
            if(value>before){data.set(snapshot,value);dirty=true;}
        }
        if(historical){data.set(base+".historical-seeded",true);dirty=true;}
    }
    List<String> check(Player p,boolean announce){
        if(!ui.authenticated(p))return List.of();UUID id=p.getUniqueId();sample(p,eligible(p));
        track(id,"joined",1);track(id,"seconds",ui.titleMetric(p,"seconds"));track(id,"mined",ui.titleMetric(p,"mined"));track(id,"placed",ui.titleMetric(p,"placed"));
        track(id,"advancements",completed(p));var biome=Bukkit.getAdvancement(NamespacedKey.minecraft("adventure/adventuring_time"));if(biome!=null)track(id,"biomes",p.getAdvancementProgress(biome).getAwardedCriteria().size());
        Economy eco=economy();if(eco!=null){double balance=eco.getBalance(p);if(Double.isFinite(balance)&&balance>=0)track(id,"balance",(long)balance);}
        String team=plugin.team(id);if(team!=null)track(id,"team_size",plugin.members(team).size());track(id,"harvest",ui.titleMetric(p,"harvest"));List<String> unlocked=new ArrayList<>();
        for(Title t:catalog.values())if(t.kind().equals("milestone")&&!owned(id,t.id())&&t.requirements().entrySet().stream().allMatch(requirement->data.getLong(root(id)+".progress."+requirement.getKey())>=requirement.getValue())){
            data.set(root(id)+".owned."+t.id(),true);data.set(root(id)+".earned."+t.id(),System.currentTimeMillis());dirty=true;unlocked.add(t.id());
        }
        if(!unlocked.isEmpty()){
            // Publish ownership before notifying; a failed write never announces a lost unlock.
            save();if(announce){String preview=unlocked.stream().limit(4).map(key->catalog.get(key).legacy()).reduce((a,b)->a+"§r、"+b).orElse("");say(p,"已解锁/补发 "+unlocked.size()+" 个称号："+preview+"§r"+(unlocked.size()>4?"等。":"。")+"指南针 → 称号殿堂选择佩戴。");}
        }
        refresh(id);return List.copyOf(unlocked);
    }
    void login(Player p){check(p,true);if(data.contains(root(p.getUniqueId())+".purchase"))say(p,"有一笔称号购买待管理员核对；为避免重复扣款，已暂停新的称号购买。进度和已有称号不受影响。");}
    private boolean eligible(Player p){return ui.authenticated(p)&&p.getGameMode()==GameMode.SURVIVAL&&!Set.of("flat","architect","lobby").contains(p.getWorld().getName());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void mode(PlayerGameModeChangeEvent e){if(ui.authenticated(e.getPlayer()))sample(e.getPlayer(),eligible(e.getPlayer()));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){if(ui.authenticated(e.getPlayer())&&e.getTo()!=null&&!e.getFrom().getWorld().equals(e.getTo().getWorld()))sample(e.getPlayer(),eligible(e.getPlayer()));}
    @EventHandler public void world(PlayerChangedWorldEvent e){if(ui.authenticated(e.getPlayer()))sample(e.getPlayer(),false);}
    @EventHandler public void quit(PlayerQuitEvent e){customInputs.remove(e.getPlayer().getUniqueId());drafts.remove(e.getPlayer().getUniqueId());if(ui.authenticated(e.getPlayer()))sample(e.getPlayer(),eligible(e.getPlayer()));flush();}
    private void equip(Player p,String id){
        if(!ui.authenticated(p))return;if(!id.isEmpty()&&(title(p.getUniqueId(),id)==null||!owned(p.getUniqueId(),id))){say(p,"尚未拥有这个称号。");return;}
        String key=root(p.getUniqueId())+".equipped",before=data.getString(key);data.set(key,id.isEmpty()?null:id);
        try{save();}catch(RuntimeException e){data.set(key,before);throw e;}refresh(p.getUniqueId());ui.decorate(p);say(p,id.isEmpty()?"已摘下称号，已解锁记录保留。":"已佩戴 "+title(p.getUniqueId(),id).legacy()+"§r；可以分别设置显示位置。");
    }
    private void visibility(Player p,String location){
        if(!Set.of("chat","tab","sidebar").contains(location))return;
        String key=root(p.getUniqueId())+".show."+location;boolean before=data.getBoolean(key,true);data.set(key,!before);
        try{save();}catch(RuntimeException e){data.set(key,before);throw e;}refresh(p.getUniqueId());ui.decorate(p);say(p,"已"+(before?"隐藏":"显示")+switch(location){case "chat"->"聊天";case "tab"->"TAB 列表";default->"侧边栏";}+"称号。");
    }
    private Economy economy(){var registration=Bukkit.getServicesManager().getRegistration(Economy.class);return registration==null?null:registration.getProvider();}
    boolean purchase(Player p,String id){
        if(!ui.authenticated(p))return false;Title t=title(p.getUniqueId(),id);if(t==null||!Set.of("shop","custom").contains(t.kind())){say(p,"该称号不出售。");return false;}
        UUID uuid=p.getUniqueId();String base=root(uuid);if(owned(uuid,id)){say(p,"已拥有，不会重复扣款；点击即可佩戴。");return false;}
        if(CreativeTools.building(p.getWorld())||p.getGameMode()==GameMode.CREATIVE||p.getGameMode()==GameMode.SPECTATOR){say(p,"请回生存或主城，以非创造模式购买称号。");return false;}
        if(data.contains(base+".purchase")){say(p,"有待核对的购买记录，请联系管理员，暂不重复扣款。");return false;}
        Economy eco=economy();if(eco==null||!eco.has(p,t.price())){say(p,"金币不足或经济系统暂不可用；需要 "+money(t.price())+" 金币。");return false;}
        data.set(base+".purchase.title",id);data.set(base+".purchase.price",t.price());data.set(base+".purchase.at",System.currentTimeMillis());save();
        if(t.kind().equals("custom")){Draft draft=drafts.get(uuid);if(draft==null||!draft.id().equals(id)){data.set(base+".purchase",null);save();say(p,"自定义预览已过期，请重新编辑。");return false;}data.set(base+".purchase.custom-name",draft.name());data.set(base+".purchase.custom-palette",draft.palette());save();}
        // Vault has no transaction ID. On ambiguous failure retain the intent; never guess and charge twice.
        net.milkbowl.vault.economy.EconomyResponse result;
        try{result=eco.withdrawPlayer(p,t.price());}catch(Exception e){say(p,"购买结果暂不确定，已保留核对记录，请联系管理员；不会自动再次扣款。");plugin.getLogger().warning("Title purchase needs review: "+uuid+" / "+id);return false;}
        if(result==null){say(p,"经济系统未返回明确结果，已保留核对记录，暂不重复扣款。");return false;}
        if(!result.transactionSuccess()){data.set(base+".purchase",null);save();say(p,"扣款未成功，称号没有购买。");return false;}
        if(t.kind().equals("custom")){Draft draft=drafts.get(uuid);data.set(base+".custom."+id+".name",draft.name());data.set(base+".custom."+id+".palette",draft.palette());}
        data.set(base+".owned."+id,true);data.set(base+".earned."+id,System.currentTimeMillis());Object intent=data.getConfigurationSection(base+".purchase").getValues(false);data.set(base+".purchase",null);
        try{save();}catch(RuntimeException e){data.set(base+".owned."+id,null);data.set(base+".earned."+id,null);if(t.kind().equals("custom"))data.set(base+".custom."+id,null);data.createSection(base+".purchase",(Map<?,?>)intent);say(p,"金币已扣，称号记录待管理员核对；不会再次扣款。");plugin.getLogger().log(java.util.logging.Level.SEVERE,"Paid title purchase could not be saved: "+uuid,e);return false;}
        if(t.kind().equals("custom"))drafts.remove(uuid);refresh(uuid);say(p,"花费 "+money(t.price())+" 金币解锁 "+t.legacy()+"§r。点击已拥有的称号即可佩戴。");return true;
    }
    private String money(double amount){return String.format(Locale.ROOT,"%,.0f",amount);}
    private String metricName(String metric){return switch(metric){case "joined"->"首次登录";case "seconds"->"在线时长";case "mined"->"生存挖掘";case "placed"->"生存放置";case "kills"->"击败生物";case "fish"->"钓鱼收获";case "advancements"->"原版进度";case "biomes"->"探索群系";case "balance"->"最高余额";case "team_size"->"伙伴人数";case "harvest"->"生存农作物挖掘";case "trades"->"村民交易";case "bred"->"动物繁殖";case "enchanted"->"附魔物品";case "raids"->"袭击胜利";case "sleep"->"入睡次数";case "dragon"->"末影龙击杀";case "wither"->"凋灵击杀";case "elder"->"远古守卫者击杀";case "walk_cm"->"步行";case "sprint_cm"->"疾跑";case "swim_cm"->"游泳";case "elytra_cm"->"鞘翅飞行";case "boat_cm"->"乘船";case "horse_cm"->"骑马";case "rail_cm"->"矿车旅行";default->metric;};}
    private String progress(UUID id,String metric,long target){long current=data.getLong(root(id)+".progress."+metric);return metric.equals("seconds")?String.format(Locale.ROOT,"%.1f / %.0f 小时",current/3600.0,target/3600.0):metric.endsWith("_cm")?String.format(Locale.ROOT,"%.1f / %.1f km",current/100000.0,target/100000.0):Math.min(current,target)+" / "+target;}
    private List<String> requirements(UUID id,Title title){return title.requirements().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->metricName(e.getKey())+"："+progress(id,e.getKey(),e.getValue())).toList();}
    private String status(Player p,Title t){return owned(p.getUniqueId(),t.id())?(t.id().equals(data.getString(root(p.getUniqueId())+".equipped"))?"佩戴中":"已拥有 · 点击佩戴"):t.kind().equals("shop")?"购买："+money(t.price())+" 金币":t.kind().equals("admin")?"活动授予":String.join(" / ",requirements(p.getUniqueId(),t));}
    private String expand(Player p,Title title,String text){String expanded=text.replace("{player}",ui.display(p)).replace("{owned}",Integer.toString(counts.getOrDefault(p.getUniqueId(),0))).replace("{total}",Integer.toString(total(p.getUniqueId()))).replace("{equipped}",plain(p.getUniqueId())).replace("{title}",title.plain()).replace("{progress}",String.join("；",requirements(p.getUniqueId(),title)));for(String metric:METRICS)expanded=expanded.replace("{metric:"+metric+"}",Long.toString(data.getLong(root(p.getUniqueId())+".progress."+metric)));return expanded;}
    private List<Title> list(Player p,String filter){List<Title> all=new ArrayList<>(catalog.values());var custom=data.getConfigurationSection(root(p.getUniqueId())+".custom");if(custom!=null)for(String key:custom.getKeys(false))all.add(title(p.getUniqueId(),key));return all.stream().filter(t->switch(filter){case "owned"->owned(p.getUniqueId(),t.id());case "milestone"->t.kind().equals("milestone");case "shop"->t.kind().equals("shop");default->true;}).toList();}
    private String filterName(String filter){return switch(filter){case "owned"->"我的称号";case "milestone"->"里程碑";case "shop"->"称号商店";default->"全部称号";};}
    private String nextFilter(String filter){return switch(filter){case "all"->"owned";case "owned"->"milestone";case "milestone"->"shop";default->"all";};}
    void command(Player p,String[] args){
        customInputs.remove(p.getUniqueId());
        if(!ui.authenticated(p)){say(p,"请先登录。");return;}String action=args.length==0?"all":args[0].toLowerCase(Locale.ROOT);
        switch(action){
            case "equip"->{if(args.length==2)equip(p,args[1]);else say(p,"/titles equip 称号ID");}
            case "off"->equip(p,"");
            case "show"->{if(args.length==2)visibility(p,args[1]);else visibilityMenu(p);}
            case "check"->{check(p,true);say(p,"已核对历史统计，达成的里程碑会自动补发；未记录的历史不会估算。");}
            case "custom"->customInput(p);
            case "all","owned","milestone","shop"->{check(p,true);menu(p,action,0);}
            default->say(p,"/titles：称号殿堂；/titles owned；/titles shop；/titles custom；/titles off；/titles check；/titles show。");
        }
    }
    private Holder panel(Player p,int size,String title){Holder h=new Holder(p);h.inventory=Bukkit.createInventory(h,size,title);for(int i=0;i<size;i++)put(h,i,Material.GRAY_STAINED_GLASS_PANE,Component.text(" "),()->{});return h;}
    private void put(Holder h,int slot,Material icon,Component name,Runnable action,String... lore){ItemStack item=new ItemStack(icon);var meta=item.getItemMeta();meta.displayName(name);meta.setLore(Arrays.asList(lore));item.setItemMeta(meta);h.inventory.setItem(slot,item);h.actions.put(slot,action);}
    private void put(Holder h,int slot,Material icon,String name,Runnable action,String... lore){put(h,slot,icon,Component.text(name),action,lore);}
    private void menu(Player p,String filter,int requested){
        List<Title> list=list(p,filter);int size=ui.bedrock(p)?8:28,page=Math.max(0,Math.min(requested,Math.max(0,(list.size()-1)/size))),start=page*size,end=Math.min(start+size,list.size());
        if(ui.bedrock(p)){
            var form=SimpleForm.builder().title("称号殿堂 · "+filterName(filter)).content("当前佩戴："+plain(p.getUniqueId())+"\n拥有 "+counts.getOrDefault(p.getUniqueId(),0)+" / "+total(p.getUniqueId())+" 个 · 金币："+(economy()==null?"暂不可用":money(economy().getBalance(p)))+"\n纯外观，无属性加成。里程碑自动补发；购买需再次确认。\n第 "+(page+1)+" 页 / "+Math.max(1,(list.size()+size-1)/size)+" 页");List<Runnable> actions=new ArrayList<>();
            for(Title t:list.subList(start,end)){form.button(t.plain()+"\n"+status(p,t));actions.add(()->select(p,t,filter,page));}
            if(page>0){form.button("上一页");actions.add(()->menu(p,filter,page-1));}if(end<list.size()){form.button("下一页");actions.add(()->menu(p,filter,page+1));}
            form.button("自定义称号 · "+money(customPrice)+" 金币");actions.add(()->customInput(p));form.button("切换分类 → "+filterName(nextFilter(filter)));actions.add(()->menu(p,nextFilter(filter),0));form.button("显示位置设置");actions.add(()->visibilityMenu(p));form.button("摘下称号");actions.add(()->{equip(p,"");menu(p,filter,page);});form.button("返回指南针");actions.add(()->ui.command(p,new String[]{"menu"}));
            form.validResultHandler(r->later(p,actions.get(r.clickedButtonId())));FloodgateApi.getInstance().sendForm(p.getUniqueId(),form.build());return;
        }
        Holder h=panel(p,54,"§8称号殿堂 · "+filterName(filter)+" · "+(page+1));
        put(h,4,Material.NETHER_STAR,"§6当前："+plain(p.getUniqueId()),()->visibilityMenu(p),"§7拥有：§e"+counts.getOrDefault(p.getUniqueId(),0)+" §7/ "+total(p.getUniqueId()),"§7金币：§6"+(economy()==null?"暂不可用":money(economy().getBalance(p))),"§7点击设置聊天 / TAB / 侧边栏显示","§7达成后自动解锁，老玩家上线核对补发");
        for(int i=start;i<end;i++){Title t=list.get(i);int n=i-start,slot=10+(n/7)*9+n%7;List<String> lore=new ArrayList<>();lore.add("§7"+expand(p,t,t.description()));if(t.kind().equals("milestone"))for(String line:requirements(p.getUniqueId(),t))lore.add("§7"+line);lore.add("§e"+(owned(p.getUniqueId(),t.id())?status(p,t):t.kind().equals("shop")?"价格："+money(t.price())+" 金币":t.kind().equals("admin")?"活动授予":"达成全部条件自动解锁"));lore.add("§8ID："+t.id());put(h,slot,owned(p.getUniqueId(),t.id())?t.icon():Material.GRAY_DYE,t.display(),()->select(p,t,filter,page),lore.toArray(String[]::new));}
        if(page>0)put(h,45,Material.ARROW,"§f上一页",()->menu(p,filter,page-1));
        put(h,46,Material.BOOK,"§b分类 → "+filterName(nextFilter(filter)),()->menu(p,nextFilter(filter),0));
        put(h,47,Material.ANVIL,"§6自定义称号 · "+money(customPrice)+" 金币",()->customInput(p),"§7输入文字、选择渐变配色，再预览确认","§7每个独立称号永久解锁，可收藏多个","§7取消不扣款；修改文字需另购一个");
        put(h,48,Material.CLOCK,"§a刷新与补发",()->{check(p,true);menu(p,filter,page);});put(h,49,Material.COMPASS,"§f返回指南针",()->ui.command(p,new String[]{"menu"}));put(h,50,Material.COMPARATOR,"§d显示位置",()->visibilityMenu(p));put(h,51,Material.MILK_BUCKET,"§7摘下称号",()->{equip(p,"");menu(p,filter,page);});
        if(end<list.size())put(h,53,Material.ARROW,"§f下一页",()->menu(p,filter,page+1));p.openInventory(h.inventory);
    }
    private void select(Player p,Title t,String filter,int page){
        if(owned(p.getUniqueId(),t.id())){equip(p,t.id());menu(p,filter,page);return;}
        if(Set.of("shop","custom").contains(t.kind())){confirm(p,t,filter,page);return;}say(p,t.plain()+"："+status(p,t)+"。"+expand(p,t,t.description()));
    }
    private void customInput(Player p){
        ui.community.cancelInput(p);
        ui.names.cancelInput(p);ui.sync.cancelInput(p);customInputs.remove(p.getUniqueId());drafts.remove(p.getUniqueId());
        if(ui.bedrock(p)){
            var form=CustomForm.builder().title("自定义称号 · "+money(customPrice)+" 金币").input("称号文字（1–10 字，不含颜色代码）","例如：南风执剑人").dropdown("渐变配色",Arrays.asList(PALETTE_NAMES),0)
                .validResultHandler(r->{String name=r.asInput(0);int palette=r.asDropdown(1);later(p,()->draft(p,name,palette));}).build();FloodgateApi.getInstance().sendForm(p.getUniqueId(),form);return;
        }
        p.closeInventory();customInputs.put(p.getUniqueId(),System.currentTimeMillis()+120000);say(p,"请输入自定义称号（1–10 字），支持中文、字母、数字、空格、_、-、·。下一条聊天仅用于编辑，不广播。输入「取消」退出；预览确认后才扣 "+money(customPrice)+" 金币。");
    }
    void cancelInput(Player p){customInputs.remove(p.getUniqueId());drafts.remove(p.getUniqueId());}
    private void draft(Player p,String name,int palette){
        if(!validCustom(name)){say(p,"称号请用 1–10 字，不支持颜色代码、控制符或首尾空格；当前未扣款。");return;}
        if(palette<0||palette>=PALETTES.length){say(p,"配色无效，未扣款。");return;}
        Draft draft=new Draft("custom_"+UUID.randomUUID().toString().replace("-",""),name,palette);drafts.put(p.getUniqueId(),draft);confirm(p,custom(draft.id(),draft.name(),draft.palette()),"owned",0);
    }
    @EventHandler(priority=EventPriority.LOWEST) public void customText(AsyncChatEvent e){
        Long until=customInputs.remove(e.getPlayer().getUniqueId());if(until==null)return;e.setCancelled(true);String name=PlainTextComponentSerializer.plainText().serialize(e.message());
        later(e.getPlayer(),()->{
            if(until<System.currentTimeMillis()||name.equals("取消")){say(e.getPlayer(),"已取消或输入超时，未扣款。");return;}
            if(!validCustom(name)){say(e.getPlayer(),"文字无效，未扣款。请重新打开自定义称号。");return;}
            Holder h=panel(e.getPlayer(),27,"§8选择渐变配色 · 未扣款");for(int i=0;i<PALETTES.length;i++){int palette=i;put(h,10+i*2,Material.NAME_TAG,custom("preview",name,i).display(),()->draft(e.getPlayer(),name,palette),"§7"+PALETTE_NAMES[i],"§7下一步预览确认；价格 "+money(customPrice)+" 金币");}put(h,22,Material.BARRIER,"§7取消 · 不扣款",()->menu(e.getPlayer(),"owned",0));e.getPlayer().openInventory(h.inventory);
        });
    }
    private void confirm(Player p,Title t,String filter,int page){
        if(ui.bedrock(p)){
            var form=ModalForm.builder().title("确认购买称号").content(t.plain()+"\n价格："+money(t.price())+" 金币\n"+t.description()+"\n永久解锁，仅外观。取消不扣款；购买后自由佩戴。").button1("确认购买").button2("取消")
                .validResultHandler(r->later(p,()->{if(r.clickedButtonId()==0)purchase(p,t.id());menu(p,filter,page);})).build();FloodgateApi.getInstance().sendForm(p.getUniqueId(),form);return;
        }
        Holder h=panel(p,27,"§8确认购买 · "+t.plain());put(h,4,t.icon(),t.display(),()->{},"§7"+t.description(),"§e价格："+money(t.price())+" 金币");put(h,11,Material.EMERALD,"§a确认购买",()->{purchase(p,t.id());menu(p,filter,page);});put(h,15,Material.BARRIER,"§7取消 · 不扣款",()->menu(p,filter,page));p.openInventory(h.inventory);
    }
    private void visibilityMenu(Player p){
        if(ui.bedrock(p)){
            String base=root(p.getUniqueId())+".show.";var form=CustomForm.builder().title("称号显示设置").toggle("聊天称号",data.getBoolean(base+"chat",true)).toggle("TAB 列表称号",data.getBoolean(base+"tab",true)).toggle("侧边栏称号行",data.getBoolean(base+"sidebar",true));
            form.validResultHandler(r->{boolean chat=r.asToggle(0),tab=r.asToggle(1),sidebar=r.asToggle(2);later(p,()->{data.set(base+"chat",chat);data.set(base+"tab",tab);data.set(base+"sidebar",sidebar);save();refresh(p.getUniqueId());ui.decorate(p);menu(p,"owned",0);});});FloodgateApi.getInstance().sendForm(p.getUniqueId(),form.build());return;
        }
        Holder h=panel(p,27,"§8称号 · 显示位置");String[] locations={"chat","tab","sidebar"},names={"聊天","TAB 列表","侧边栏称号行"};
        for(int i=0;i<3;i++){String location=locations[i];boolean shown=data.getBoolean(root(p.getUniqueId())+".show."+location,true);put(h,11+i*2,shown?Material.LIME_DYE:Material.GRAY_DYE,(shown?"§a显示：":"§7隐藏：")+names[i],()->{visibility(p,location);visibilityMenu(p);},"§7点击切换；重登后保留");}put(h,22,Material.ARROW,"§f返回我的称号",()->menu(p,"owned",0));p.openInventory(h.inventory);
    }
    boolean admin(org.bukkit.command.CommandSender sender,String[] args){
        if(sender instanceof Player player&&!ui.authenticated(player)){sender.sendMessage("请先完成登录。");return true;}
        if(!sender.hasPermission("miao.titles.admin")){sender.sendMessage("没有称号管理权限。");return true;}
        if(args.length<2){sender.sendMessage("/titleadmin grant|revoke|review|resolve 玩家名或UUID [称号ID 或 grant|cancel]；resolve 只处理扣款待核对记录，不再次扣费或退款。");return true;}
        UUID id=null;try{id=UUID.fromString(args[1]);}catch(IllegalArgumentException ignored){}
        Player online=Bukkit.getPlayerExact(args[1]);if(online!=null)id=online.getUniqueId();
        if(id==null){sender.sendMessage("离线玩家请填 UUID；在线玩家可填实际名字。");return true;}String base=root(id);
        if(args[0].equals("review")){sender.sendMessage("待核对购买："+data.getString(base+".purchase.title","无")+"，金额："+data.getDouble(base+".purchase.price"));return true;}
        if(args[0].equals("resolve")&&args.length==3&&Set.of("grant","cancel").contains(args[2])){
            String title=data.getString(base+".purchase.title");if(title==null){sender.sendMessage("没有待核对购买。");return true;}
            if(args[2].equals("grant")){
                String name=data.getString(base+".purchase.custom-name");int palette=data.getInt(base+".purchase.custom-palette",-1);
                if(name!=null){if(!validCustom(name)||palette<0||palette>=PALETTES.length){sender.sendMessage("自定义称号核对记录无效，未更改。");return true;}data.set(base+".custom."+title+".name",name);data.set(base+".custom."+title+".palette",palette);}
                data.set(base+".owned."+title,true);
            }data.set(base+".purchase",null);save();refresh(id);sender.sendMessage("核对已记录；本操作不扣费、不退款。");return true;
        }
        if(args.length!=3||title(id,args[2])==null||!Set.of("grant","revoke").contains(args[0])){sender.sendMessage("请使用有效称号 ID 和 grant/revoke 操作。");return true;}
        boolean grant=args[0].equals("grant");data.set(base+".owned."+args[2],grant?true:null);if(!grant&&args[2].equals(data.getString(base+".equipped")))data.set(base+".equipped",null);save();refresh(id);
        Player target=Bukkit.getPlayer(id);if(target!=null)ui.decorate(target);sender.sendMessage("称号记录已更新。里程碑达到条件时会自动再次解锁；撤销主要用于活动/商店称号。");return true;
    }
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Holder h))return;e.setCancelled(true);if(e.getWhoClicked() instanceof Player p&&h.owner.equals(p.getUniqueId())&&e.getRawSlot()>=0&&e.getRawSlot()<h.inventory.getSize()){Runnable action=h.actions.get(e.getRawSlot());if(action!=null)later(p,action);}}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Holder)e.setCancelled(true);}
}
