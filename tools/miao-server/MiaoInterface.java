package cn.nankexi;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.events.LoginEvent;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.cumulus.form.*;

/** Mobile native forms, private preferences, onboarding and progress. */
final class MiaoInterface implements Listener {
    private final MiaoServer plugin;
    final PlayerNames names;
    final CrossplaySync sync;
    private final YamlConfiguration prefs = new YamlConfiguration();
    private final File file;
    private final Map<UUID, Long> started = new HashMap<>(), checkpoint = new HashMap<>();
    private final Map<String,String> aliases = new HashMap<>();
    private final Location initialLobbySpawn;
    private boolean dirty;
    private final Map<UUID,Integer> noTeleportUntil = new HashMap<>();
    static final LinkedHashMap<String,String> FIELDS = new LinkedHashMap<>();
    static final List<String> DEFAULT = List.of("player","online","ping","total","session","tps","world","team","balance","placed","mined","kills","fishing");
    static final Material[] FAVORITES = {Material.STONE,Material.COBBLESTONE,Material.OAK_LOG,Material.OAK_PLANKS,Material.DIRT,Material.IRON_ORE,Material.DIAMOND_ORE,Material.DEEPSLATE_DIAMOND_ORE,Material.REDSTONE_ORE,Material.WHEAT};
    static {
        FIELDS.put("player","玩家名称"); FIELDS.put("online","在线人数"); FIELDS.put("ping","网络延迟");
        FIELDS.put("total","总游玩时长"); FIELDS.put("session","本次游玩时长"); FIELDS.put("tps","服务器状态");
        FIELDS.put("world","当前世界"); FIELDS.put("team","伙伴队伍"); FIELDS.put("balance","金币余额");
        FIELDS.put("placed","生存放置总数"); FIELDS.put("mined","生存挖掘总数"); FIELDS.put("block","关注方块：放置/挖掘");
        FIELDS.put("kills","击败生物"); FIELDS.put("fishing","钓鱼收获"); FIELDS.put("deaths","死亡次数"); FIELDS.put("distance","步行里程");
    }
    MiaoInterface(MiaoServer p) {
        plugin=p; file=new File(p.getDataFolder(),"preferences.yml");
        World lobby=Bukkit.getWorld("lobby");initialLobbySpawn=lobby==null?null:lobby.getSpawnLocation().clone();
        try { if (file.exists()) prefs.load(file); } catch(Exception e) { throw new IllegalStateException("Cannot read sidebar preferences",e); }
        sync=new CrossplaySync(p,this);
        names=new PlayerNames(p,this);
        new BedrockCommandNames(p,this);
        scanMenus(); Bukkit.getPluginManager().registerEvents(this,p);
        Bukkit.getScheduler().runTaskTimer(p,()->{ for(Player player:Bukkit.getOnlinePlayers()) { flushTime(player); if(authenticated(player)) decorate(player); } save(); },1200,1200);
    }
    boolean authenticated(Player p) { return !p.hasMetadata("fakeplayer:spawned_at") && AuthMeApi.getInstance().isAuthenticated(p); }
    boolean bedrock(Player p) { return FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId()); }
    String display(Player p) {
        if (!bedrock(p)) return names.nickname(p.getUniqueId(),p.getName());
        var f=FloodgateApi.getInstance().getPlayer(p.getUniqueId());
        return "[PE]"+names.nickname(p.getUniqueId(),f!=null && !f.isLinked()?f.getUsername():p.getName());
    }
    void decorate(Player p) { p.setDisplayName("§b"+display(p)+"§r"); p.setPlayerListName("§b"+display(p)+"§r"); }
    private String path(Player p) { return "players."+p.getUniqueId(); }
    private void say(Player p,String s) { p.sendMessage("§6南可熙提醒您： §f"+s); }
    private void main(Player p,Runnable r) { Bukkit.getScheduler().runTask(plugin,()->{ if(p.isOnline()&&authenticated(p)) r.run(); }); }
    private void save() {
        if(!dirty) return;
        try { File t=new File(file.getParentFile(),"preferences.yml.tmp"); prefs.save(t); Files.move(t.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING); dirty=false; }
        catch(IOException e) { throw new IllegalStateException("Cannot persist preferences",e); }
    }
    private void flushTime(Player p) {
        Long last=checkpoint.get(p.getUniqueId()); if(last==null) return;
        long now=System.nanoTime(), seconds=(now-last)/1_000_000_000L;
        if(seconds>0) { prefs.set(path(p)+".total-seconds",prefs.getLong(path(p)+".total-seconds")+seconds); checkpoint.put(p.getUniqueId(),last+seconds*1_000_000_000L);dirty=true; }
    }
    private static String duration(long sec) { return sec/3600+"小时"+sec/60%60+"分"; }
    boolean enabled(Player p) { return plugin.getConfig().getBoolean("sidebar.enabled",true)&&prefs.getBoolean(path(p)+".sidebar.enabled",true); }
    List<String> selected(Player p) {
        if(prefs.contains(path(p)+".sidebar.fields"))return new ArrayList<>(prefs.getStringList(path(p)+".sidebar.fields"));
        List<String> initial=plugin.getConfig().getStringList("sidebar.default-fields");return new ArrayList<>(initial.isEmpty()?DEFAULT:initial);
    }
    private Material favorite(Player p) { Material m=Material.matchMaterial(prefs.getString(path(p)+".sidebar.block","OAK_LOG"));return m!=null&&m.isBlock()?m:Material.OAK_LOG; }
    static String blockName(Material m) { return switch(m) { case STONE->"石头";case COBBLESTONE->"圆石";case OAK_LOG->"橡木原木";case OAK_PLANKS->"橡木木板";case DIRT->"泥土";case IRON_ORE->"铁矿石";case DIAMOND_ORE->"钻石矿石";case DEEPSLATE_DIAMOND_ORE->"深层钻石矿";case REDSTONE_ORE->"红石矿石";case WHEAT->"小麦";default->m.name();}; }
    List<String> lines(Player p,int online,String team) {
        List<String> result=new ArrayList<>();
        long now=System.nanoTime(), pending=(now-checkpoint.getOrDefault(p.getUniqueId(),now))/1_000_000_000L;
        long total=prefs.getLong(path(p)+".total-seconds",Integer.toUnsignedLong(p.getStatistic(Statistic.PLAY_ONE_MINUTE))/20)+pending;
        long session=(now-started.getOrDefault(p.getUniqueId(),now))/1_000_000_000L;
        double tps=Math.min(20,Bukkit.getTPS()[0]);
        String world=switch(p.getWorld().getName()){case "flat"->"创造超平坦";case "lobby"->"温馨主城";case "world_nether"->"下界";case "world_the_end"->"末地";default->"自由生存";};
        Set<String> chosen=new LinkedHashSet<>(selected(p));
        List<String> order=List.of("player","world","team","total","session","balance","placed","mined","block","kills","fishing","deaths","distance","online","ping","tps");
        String lastGroup=""; int shown=0;
        for(String key:order) {
            if(!chosen.contains(key)||shown>=13)continue;
            String group=List.of("online","ping","tps").contains(key)?"§8── §b服务器 §8──":"§8── §6我的成长 §8──";
            if(!group.equals(lastGroup)){result.add(group);lastGroup=group;}
            String s=switch(key) {
                case "player"->"§f玩家 §a"+display(p);case "online"->"§f在线 §b"+online+" §7/ "+Bukkit.getMaxPlayers();case "ping"->"§f延迟 §e"+p.getPing()+" ms";
                case "total"->"§f总游玩 §d"+duration(total);case "session"->"§f本次 §d"+duration(session);
                case "tps"->"§f状态 "+(tps>=18?"§a流畅":"§e繁忙")+" §7TPS §f"+String.format(Locale.ROOT,"%.1f",tps);
                case "world"->"§f世界 §a"+world;case "team"->"§f队伍 §b"+team;
                case "balance"->{ var eco=Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);yield "§f金币 §6"+(eco==null?"--":String.format(Locale.ROOT,"%.1f",eco.getProvider().getBalance(p))); }
                case "placed"->"§f放置 §a"+prefs.getLong(path(p)+".progress.placed");case "mined"->"§f挖掘 §a"+prefs.getLong(path(p)+".progress.mined");
                case "block"->{Material m=favorite(p);yield "§f"+blockName(m)+" §a"+prefs.getLong(path(p)+".blocks."+m+".placed")+" §7/ §b"+prefs.getLong(path(p)+".blocks."+m+".mined");}
                case "kills"->"§f击败生物 §c"+p.getStatistic(Statistic.MOB_KILLS);case "fishing"->"§f钓鱼收获 §b"+p.getStatistic(Statistic.FISH_CAUGHT);
                case "deaths"->"§f死亡 §7"+p.getStatistic(Statistic.DEATHS);case "distance"->"§f步行 §e"+String.format(Locale.ROOT,"%.2f",p.getStatistic(Statistic.WALK_ONE_CM)/100000.0)+" km";
                default->null;
            };if(s!=null){result.add(s.replace("§f","§7").replaceFirst(" ","  §8› §r"));shown++;}
        }
        return result;
    }
    boolean sidebar(Player p,String[] a) {
        if(!authenticated(p)) {say(p,"请先完成登录。");return true;}
        String sub=a.length==0?"toggle":a[0].toLowerCase(Locale.ROOT);
        switch(sub) {
            case "settings"->{Bukkit.getScheduler().runTaskLater(plugin,()->{if(p.isOnline()&&authenticated(p)){if(bedrock(p))settingsForm(p);else settingsInventory(p);}},2L);return true;}
            case "on","off","toggle"->{boolean v=sub.equals("on")||(!sub.equals("off")&&!enabled(p));prefs.set(path(p)+".sidebar.enabled",v);dirty=true;if(!v)plugin.detach(p);say(p,v?"侧边栏已打开。":"侧边栏已关闭，重启和重登仍保留设置。");}
            case "field"->{if(a.length!=2||!FIELDS.containsKey(a[1])){say(p,"选择数据：/sidebar settings");return true;}toggleField(p,a[1]);}
            case "preset"->{if(a.length!=2)return true;List<String> fields=a[1].equals("progress")?List.of("player","total","session","balance","placed","mined","block","kills","fishing","deaths","distance","team"):List.of("player","online","ping","total","session","tps","world","team","balance");prefs.set(path(p)+".sidebar.fields",fields);dirty=true;say(p,"已应用显示方案。");}
            case "block"->{if(a.length!=2)return true;Material m=Material.matchMaterial(a[1]);if(m==null||!m.isBlock()){say(p,"请输入有效方块，如 /sidebar block OAK_LOG。");return true;}prefs.set(path(p)+".sidebar.block",m.name());List<String> fields=selected(p);if(!fields.contains("block")&&fields.size()<13)fields.add("block");prefs.set(path(p)+".sidebar.fields",fields);dirty=true;say(p,"关注方块："+blockName(m));}
            default->say(p,"/sidebar on | off | settings；可选择状态、成长数据和关注方块。");
        }save();return true;
    }
    private void toggleField(Player p,String key) {
        List<String> f=selected(p);if(!f.remove(key)){if(f.size()>=13){say(p,"最多显示十三项，请先关掉一项。");return;}f.add(key);}
        prefs.set(path(p)+".sidebar.fields",f);dirty=true;say(p,FIELDS.get(key)+(f.contains(key)?"：显示":"：隐藏"));save();
    }
    private void settingsForm(Player p) {
        List<String> keys=new ArrayList<>(FIELDS.keySet());List<String> chosen=selected(p);
        CustomForm.Builder b=CustomForm.builder().title("侧边栏 · 我的成长").toggle("显示侧边栏",enabled(p));
        for(String key:keys)b.toggle(FIELDS.get(key),chosen.contains(key));
        b.dropdown("关注方块（数字为放置 / 挖掘）",Arrays.stream(FAVORITES).map(MiaoInterface::blockName).toList(),Math.max(0,Arrays.asList(FAVORITES).indexOf(favorite(p))));
        b.validResultHandler(r->{boolean show=r.asToggle(0);List<String> fields=new ArrayList<>();for(int i=0;i<keys.size();i++)if(r.asToggle(i+1))fields.add(keys.get(i));int block=r.asDropdown(keys.size()+1);
            main(p,()->{if(fields.size()>13){say(p,"最多选择十三项，此次设置未保存。");settingsForm(p);return;}prefs.set(path(p)+".sidebar.enabled",show);prefs.set(path(p)+".sidebar.fields",fields);prefs.set(path(p)+".sidebar.block",FAVORITES[block].name());dirty=true;save();if(!show)plugin.detach(p);say(p,"显示设置已保存。");});});
        FloodgateApi.getInstance().sendForm(p.getUniqueId(),b.build());
    }
    private static final class Holder implements InventoryHolder {
        final UUID owner;final String type;final Map<Integer,String> actions=new HashMap<>();Inventory inventory;
        Holder(Player p,String t){owner=p.getUniqueId();type=t;}public Inventory getInventory(){return inventory;}
    }
    private void put(Holder h,int slot,Material mat,String name,String action,String... lore) {
        ItemStack item=new ItemStack(mat);ItemMeta meta=item.getItemMeta();meta.setDisplayName(name);meta.setLore(Arrays.asList(lore));item.setItemMeta(meta);h.inventory.setItem(slot,item);h.actions.put(slot,action);
    }
    private void settingsInventory(Player p) {
        Holder h=new Holder(p,"sidebar");h.inventory=Bukkit.createInventory(h,45,"§8我的侧边栏 · 成长记录");
        put(h,4,enabled(p)?Material.LIME_DYE:Material.GRAY_DYE,enabled(p)?"§a侧边栏：已开启":"§7侧边栏：已关闭","toggle","§7点击切换，设置随身份保存");
        int slot=10;for(var entry:FIELDS.entrySet()){if(slot%9==8)slot+=2;boolean on=selected(p).contains(entry.getKey());put(h,slot++,on?Material.LIME_STAINED_GLASS_PANE:Material.GRAY_STAINED_GLASS_PANE,(on?"§a✔ ":"§7○ ")+entry.getValue(),"field "+entry.getKey(),"§7点击显示/隐藏；最多十三项","§7放置和挖掘从本版本开始累计");}
        put(h,36,Material.CLOCK,"§b状态方案","preset status");put(h,38,Material.DIAMOND_PICKAXE,"§a成长方案","preset progress");
        put(h,40,favorite(p).isItem()?favorite(p):Material.OAK_LOG,"§e关注方块："+blockName(favorite(p)),"blocks","§7点击选择要统计的方块");put(h,44,Material.COMPASS,"§6返回指南针菜单","menu");p.openInventory(h.inventory);
    }
    private void blockInventory(Player p) {
        Holder h=new Holder(p,"sidebar");h.inventory=Bukkit.createInventory(h,27,"§8关注方块 · 放置与挖掘");
        for(int i=0;i<FAVORITES.length;i++)put(h,i,FAVORITES[i].isItem()?FAVORITES[i]:Material.WHEAT,blockName(FAVORITES[i]),"block "+FAVORITES[i].name());
        put(h,22,Material.ARROW,"§a返回显示设置","settings");p.openInventory(h.inventory);
    }
    boolean command(Player p,String[] a) {
        if(!authenticated(p)){say(p,"请先完成登录。");return true;}
        if(a.length==0){openServiceMenu(p,"survival");return true;}
        if(a[0].equals("link")||a[0].equals("sync")){sync.command(p,Arrays.copyOfRange(a,1,a.length));return true;}
        if(a[0].equals("sidebar"))return sidebar(p,Arrays.copyOfRange(a,1,a.length));
        if(a[0].equals("menu")){openServiceMenu(p,a.length>1?a[1]:"survival");return true;}
        if(a[0].equals("shop")){openShop(p,a.length>1?a[1]:null);return true;}
        if(a[0].equals("deathback")){deathBack(p);return true;}
        if(a[0].equals("land"))return plugin.land(p,Arrays.copyOfRange(a,1,a.length));
        if(a[0].equals("teaminvite")){inviteMenu(p,0);return true;}
        if(a[0].equals("map")){mapLink(p);return true;}
        if(a[0].equals("names")){names.command(p,Arrays.copyOfRange(a,1,a.length));return true;}
        return true;
    }
    private void mapLink(Player p) {
        String url=plugin.getConfig().getString("online-map.url","");
        try {var uri=java.net.URI.create(url);if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException();}
        catch(Exception e){say(p,"在线地图地址尚未配置，请联系管理员。");return;}
        p.sendMessage(net.kyori.adventure.text.Component.text("南可熙提醒您：点击查看在线地图 → "+url,net.kyori.adventure.text.format.NamedTextColor.AQUA).clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url)));
        if(bedrock(p))FloodgateApi.getInstance().sendForm(p.getUniqueId(),SimpleForm.builder().title("南可熙 · 在线地图").content("浏览器可查看三维地图和建筑。\n\n"+url+"\n\n若手机无法点击聊天链接，请复制地址到浏览器。地图更新有渲染延迟。").button("返回指南针").validResultHandler(r->main(p,()->openServiceMenu(p,"survival"))).build());
    }
    private void link(Player p,String[] args) {
        sync.command(p,args);
    }
    private void scanMenus() {
        File dir=new File(plugin.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus");File[] files=dir.listFiles((d,n)->n.endsWith(".yml"));if(files==null)return;
        for(File f:files){YamlConfiguration y=YamlConfiguration.loadConfiguration(f);for(String cmd:y.getStringList("open_command"))aliases.put(cmd.toLowerCase(Locale.ROOT),f.getName().replace(".yml",""));}
    }
    private void runActions(Player p,List<String> actions) {
        for(String action:actions) {
            if(action.equals("[close]"))continue;
            if(action.startsWith("[openguimenu] ")){openMenu(p,action.substring(14).trim());continue;}
            if(action.startsWith("[message] ")){p.sendMessage(ChatColor.translateAlternateColorCodes('&',action.substring(10)));continue;}
            if(action.startsWith("[player] ")) {
                String cmd=action.substring(9).trim();String root=cmd.split(" ",2)[0].toLowerCase(Locale.ROOT);
                if(root.equals("accountlink")){link(p,new String[0]);continue;}
                if(root.equals("team")){plugin.onCommand(p,plugin.getCommand("mteam"),"mteam",cmd.contains(" ")?cmd.substring(cmd.indexOf(' ')+1).split(" "):new String[0]);continue;}
                if(root.equals("sidebar")){sidebar(p,cmd.contains(" ")?cmd.substring(cmd.indexOf(' ')+1).split(" "):new String[0]);continue;}
                if(aliases.containsKey(root)){openMenu(p,aliases.get(root));continue;}
                p.performCommand(cmd.replace("%player_name%",p.getName()).replace("%player%",p.getName()));
            }
        }
    }
    private void openServiceMenu(Player p,String name) {
        main(p,()->{
            if(bedrock(p)){openMenu(p,name);return;}
            try {
                var menus=Bukkit.getPluginManager().getPlugin("DeluxeMenus");
                Class<?> api=Class.forName("com.extendedclip.deluxemenus.menu.Menu",true,menus.getClass().getClassLoader());
                Optional<?> menu=(Optional<?>)api.getMethod("getMenuByName",String.class).invoke(null,name);
                if(menu.isEmpty()){say(p,"菜单未加载，请联系管理员。");return;}
                api.getMethod("openMenu",Player.class).invoke(menu.get(),p);
            }catch(Exception e){plugin.getLogger().log(java.util.logging.Level.SEVERE,"打开服务中心菜单失败",e);say(p,"菜单打开失败，请联系管理员查看日志。");}
        });
    }
    private void openShop(Player p,String section) {
        if(p.getWorld().getName().equals("flat")||p.getGameMode()==GameMode.CREATIVE||p.getGameMode()==GameMode.SPECTATOR){say(p,"请回到生存世界并使用生存模式交易。");return;}
        if(section==null){openServiceMenu(p,"systemshop");return;}
        if(!Set.of("supplies","nlk_resources","nlk_farming","nlk_drops","nlk_technical").contains(section)||!p.hasPermission("economyshopgui.shop."+section)){say(p,"暂时无法打开该分类。");return;}
        main(p,()->{
            try {
                var shop=Bukkit.getPluginManager().getPlugin("EconomyShopGUI");
                if(shop==null||!shop.isEnabled())throw new IllegalStateException("EconomyShopGUI not enabled");
                Object category=shop.getClass().getMethod("getSection",String.class).invoke(shop,section);
                if(category==null)throw new IllegalStateException("Missing shop section: "+section);
                Class<?> api=Class.forName("me.gypopo.economyshopgui.objects.shops.ShopSection",true,shop.getClass().getClassLoader());
                // The final argument disables BACK on both Java inventories and Bedrock forms.
                api.getMethod("openShopSection",Player.class,int.class,boolean.class).invoke(category,p,1,false);
            }catch(Exception e){plugin.getLogger().log(java.util.logging.Level.SEVERE,"打开系统商店失败",e);say(p,"商店暂时无法打开，请联系管理员查看日志。");}
        });
    }
    private void deathBack(Player p) {
        if(!p.hasPermission("essentials.back")||!p.hasPermission("essentials.back.ondeath")){say(p,"暂时无法返回死亡点。");return;}
        Location death=p.getLastDeathLocation();
        if(death==null||death.getWorld()==null){say(p,"还没有可返回的死亡记录。");return;}
        main(p,()->{
            try {
                var essentials=Bukkit.getPluginManager().getPlugin("Essentials");
                Object user=essentials.getClass().getMethod("getUser",Player.class).invoke(essentials,p);
                user.getClass().getMethod("setLastLocation",Location.class).invoke(user,death);
                p.performCommand("essentials:back");
            }catch(Exception e){plugin.getLogger().log(java.util.logging.Level.SEVERE,"返回死亡点失败",e);say(p,"返回失败，请联系管理员查看日志。");}
        });
    }
    private void openMenu(Player p,String name) {
        if(!name.matches("[a-z_]+"))return;
        File f=new File(plugin.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus/"+name+".yml");if(!f.isFile()){say(p,"菜单不存在，请联系管理员。");return;}
        YamlConfiguration y=YamlConfiguration.loadConfiguration(f);ConfigurationSection section=y.getConfigurationSection("items");if(section==null)return;
        List<ConfigurationSection> buttons=section.getKeys(false).stream().map(section::getConfigurationSection).filter(Objects::nonNull)
            .filter(i->!i.getStringList("left_click_commands").isEmpty()).sorted(Comparator.comparingInt(i->i.getInt("slot",0))).toList();
        SimpleForm.Builder b=SimpleForm.builder().title(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',y.getString("menu_title","NLK 服务中心"))))
            .content("§6温馨家园 · 自由生存\n§7点击功能进入，关闭窗口可返回游戏。");
        for(var i:buttons){String label=ChatColor.translateAlternateColorCodes('&',i.getString("display_name","功能"));List<String> lore=i.getStringList("lore");
            String detail=lore.isEmpty()?"":ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',lore.get(0)));b.button(label+(detail.isBlank()?"":"\n§7"+detail));}
        b.validResultHandler(r->{int id=r.clickedButtonId();if(id>=0&&id<buttons.size())main(p,()->runActions(p,buttons.get(id).getStringList("left_click_commands")));});
        if(!FloodgateApi.getInstance().sendForm(p.getUniqueId(),b.build()))say(p,"表单发送失败，请重新打开菜单。");
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void menuCommand(PlayerCommandPreprocessEvent e) {
        Player p=e.getPlayer();if(!authenticated(p))return;
        String cmd=e.getMessage().substring(1).toLowerCase(Locale.ROOT);String root=cmd.split(" ",2)[0];
        String simple=root.contains(":")?root.substring(root.indexOf(':')+1):root;
        if(plugin.inCuboid(p)&&Set.of("trust","untrust","trustlist","abandonclaim","containertrust","accesstrust","permissiontrust").contains(simple)) {
            e.setCancelled(true);
            String original=e.getMessage().substring(1);String[] args=original.contains(" ")?original.substring(original.indexOf(' ')+1).trim().split("\\s+"):new String[0];
            if(Set.of("containertrust","accesstrust","permissiontrust").contains(simple)){say(p,"立体领地使用好友建造信任；精细访问命令仅适用于旧领地。");return;}
            String action=simple.equals("trustlist")?"info":simple.equals("abandonclaim")?"delete":simple;
            String[] forwarded=new String[args.length+1];forwarded[0]=action;System.arraycopy(args,0,forwarded,1,args.length);plugin.land(p,forwarded);return;
        }
        if(root.equals("team")){e.setCancelled(true);String original=e.getMessage().substring(1);main(p,()->plugin.onCommand(p,plugin.getCommand("mteam"),"mteam",original.contains(" ")?original.substring(original.indexOf(' ')+1).split(" "):new String[0]));return;}
        if(root.equals("back")||root.equals("essentials:back")){e.setCancelled(true);deathBack(p);return;}
        if(root.equals("shop")){e.setCancelled(true);openShop(p,cmd.contains(" ")?cmd.substring(cmd.indexOf(' ')+1):null);return;}
        if(!bedrock(p))return;
        if(!aliases.containsKey(root))return;e.setCancelled(true);openMenu(p,aliases.get(root));
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e) {
        if(!(e.getView().getTopInventory().getHolder() instanceof Holder h))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)||!h.owner.equals(p.getUniqueId())||!authenticated(p))return;
        String action=h.actions.get(e.getRawSlot());if(action==null)return;
        main(p,()->clicked(p,h,action));
    }
    private void clicked(Player p,Holder h,String action) {
        if(h.type.equals("team-invite")) {
            if(action.equals("back")){p.closeInventory();openServiceMenu(p,"teams");return;}
            if(action.startsWith("page ")){inviteMenu(p,Integer.parseInt(action.substring(5)));return;}
            p.closeInventory();inviteTarget(p,UUID.fromString(action));return;
        }
        if(h.type.equals("link")) {
            Player target=Bukkit.getPlayer(UUID.fromString(action));p.closeInventory();
            if(target==null||!bedrock(target)||!authenticated(target)){say(p,"该手机账号已离线，请重新选择。");return;}
            String gamertag=FloodgateApi.getInstance().getPlayer(target.getUniqueId()).getUsername();
            if(!gamertag.matches("[A-Za-z0-9 _-]{1,32}")){say(p,"Xbox 名含特殊字符，请按聊天说明手动发起绑定。");return;}
            p.performCommand("linkaccount \""+gamertag+"\"");return;
        }
        if(action.equals("blocks")){blockInventory(p);return;}
        if(action.equals("menu")){p.closeInventory();openServiceMenu(p,"survival");return;}
        sidebar(p,action.split(" "));if(!action.equals("settings"))settingsInventory(p);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e) { if(e.getView().getTopInventory().getHolder() instanceof Holder)e.setCancelled(true); }
    private void inviteTarget(Player p,UUID id) {
        Player target=Bukkit.getPlayer(id);
        if(target==null||!plugin.canInvite(p)||!plugin.inviteCandidate(p,target)){say(p,"队伍权限或玩家状态已变化，请重新选择。");return;}
        plugin.onCommand(p,plugin.getCommand("mteam"),"mteam",new String[]{"invite",target.getName()});
    }
    private void inviteMenu(Player p,int requestedPage) {
        if(!plugin.canInvite(p)){say(p,"请先加入队伍，并确认队伍未满；队员也能邀请伙伴。");return;}
        List<? extends Player> players=Bukkit.getOnlinePlayers().stream().filter(q->plugin.inviteCandidate(p,q)).sorted(Comparator.comparing(Player::getName,String.CASE_INSENSITIVE_ORDER)).toList();
        int page=Math.max(0,Math.min(requestedPage,Math.max(0,(players.size()-1)/28))),start=page*28,end=Math.min(start+28,players.size());
        if(bedrock(p)) {
            List<? extends Player> shown=players.subList(start,end);var form=SimpleForm.builder().title("邀请伙伴 · "+(page+1)+"页").content(players.isEmpty()?"暂时没有可邀请的在线玩家。":"点击玩家发送邀请，对方接受后才会加入。");
            for(Player target:shown)form.button("§e"+names.label(p,target)+"\n§7账号："+target.getName());
            if(page>0)form.button("上一页");if(end<players.size())form.button("下一页");form.button("返回伙伴小队");
            form.validResultHandler(result->main(p,()->{int choice=result.clickedButtonId();if(choice<shown.size()){inviteTarget(p,shown.get(choice).getUniqueId());return;}int cursor=shown.size();if(page>0&&choice==cursor++){inviteMenu(p,page-1);return;}if(end<players.size()&&choice==cursor++){inviteMenu(p,page+1);return;}openServiceMenu(p,"teams");}));
            FloodgateApi.getInstance().sendForm(p.getUniqueId(),form.build());return;
        }
        Holder h=new Holder(p,"team-invite");h.inventory=Bukkit.createInventory(h,54,"§8邀请在线伙伴 · "+(page+1)+"页");
        for(int slot=0;slot<54;slot++){ItemStack frame=new ItemStack(Material.GRAY_STAINED_GLASS_PANE);var meta=frame.getItemMeta();meta.setDisplayName(" ");frame.setItemMeta(meta);h.inventory.setItem(slot,frame);}
        for(int index=start;index<end;index++) {
            Player target=players.get(index);int offset=index-start,slot=10+(offset/7)*9+offset%7;
            put(h,slot,Material.PLAYER_HEAD,"§e"+names.label(p,target),target.getUniqueId().toString(),"§7账号："+target.getName(),"§7对方接受后加入，五分钟有效");
            ItemStack head=h.inventory.getItem(slot);var meta=(org.bukkit.inventory.meta.SkullMeta)head.getItemMeta();meta.setOwningPlayer(target);head.setItemMeta(meta);
        }
        put(h,4,Material.BOOK,"§6一起安家 · 选择伙伴","back",players.isEmpty()?"§7暂时没有可邀请玩家":"§7已过滤自己、假人、未登录及已组队玩家");
        if(page>0)put(h,45,Material.ARROW,"§f上一页","page "+(page-1));if(end<players.size())put(h,53,Material.ARROW,"§f下一页","page "+(page+1));
        put(h,49,Material.COMPASS,"§f返回伙伴小队","back");p.openInventory(h.inventory);
    }
    private boolean guide(ItemStack item) {
        return item!=null&&item.getType()==Material.COMPASS&&item.hasItemMeta()
            &&"生存指南针".equals(ChatColor.stripColor(item.getItemMeta().getDisplayName()));
    }
    @EventHandler(priority=EventPriority.LOWEST) public void compass(PlayerInteractEvent e) {
        if(!guide(e.getItem())||e.getHand()!=EquipmentSlot.HAND)return;
        e.setCancelled(true);e.setUseInteractedBlock(Event.Result.DENY);e.setUseItemInHand(Event.Result.DENY);
        Player p=e.getPlayer();if(!authenticated(p))return;
        // DROP_ITEM can synthesize LEFT_CLICK_AIR. Only the actual swing event teleports.
        if(e.getAction()==Action.RIGHT_CLICK_AIR||e.getAction()==Action.RIGHT_CLICK_BLOCK){
            noTeleportUntil.put(p.getUniqueId(),Bukkit.getCurrentTick()+2);
            openServiceMenu(p,"survival");
        }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void dropCompass(PlayerDropItemEvent e) {
        noTeleportUntil.put(e.getPlayer().getUniqueId(),Bukkit.getCurrentTick()+2);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void swingCompass(PlayerArmSwingEvent e) {
        Player p=e.getPlayer();
        if(e.getHand()!=EquipmentSlot.HAND||!guide(p.getInventory().getItemInMainHand())||!p.hasPermission("miao.compass.teleport"))return;
        main(p,()->{
            if(Bukkit.getCurrentTick()<=noTeleportUntil.getOrDefault(p.getUniqueId(),-1)||!guide(p.getInventory().getItemInMainHand()))return;
            noTeleportUntil.put(p.getUniqueId(),Bukkit.getCurrentTick()+2);
            teleportCompass(p);
        });
    }
    private void teleportCompass(Player p) {
        Block b=p.getTargetBlockExact(100);if(b==null){say(p,"一百格内没有目标方块。");return;}
        for(int y=b.getY()+1;y<=Math.min(b.getY()+12,b.getWorld().getMaxHeight()-2);y++) {
            if(b.getWorld().getBlockAt(b.getX(),y,b.getZ()).isPassable()&&b.getWorld().getBlockAt(b.getX(),y+1,b.getZ()).isPassable()) {
                Location dest=new Location(b.getWorld(),b.getX()+.5,y,b.getZ()+.5,p.getLocation().getYaw(),p.getLocation().getPitch());
                if(b.getWorld().getWorldBorder().isInside(dest)){p.teleport(dest);return;}
            }
        }say(p,"目标上方没有安全空间。");
    }
    @EventHandler public void login(LoginEvent e) {
        Player p=e.getPlayer();Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(!p.isOnline()||!authenticated(p))return;
            long now=System.nanoTime();started.putIfAbsent(p.getUniqueId(),now);checkpoint.putIfAbsent(p.getUniqueId(),now);
            if(!prefs.contains(path(p)+".total-seconds")){prefs.set(path(p)+".total-seconds",Integer.toUnsignedLong(p.getStatistic(Statistic.PLAY_ONE_MINUTE))/20);dirty=true;}
            if(!prefs.getBoolean(path(p)+".arrived-v080")) {
                World lobby=Bukkit.getWorld("lobby");if(lobby!=null){p.teleport(lobby.getSpawnLocation());p.setGameMode(GameMode.ADVENTURE);prefs.set(path(p)+".arrived-v080",true);dirty=true;}
            }
            decorate(p);
            boolean compass=false;for(ItemStack i:p.getInventory().getContents())if(i!=null&&i.getType()==Material.COMPASS&&i.hasItemMeta()&&i.getItemMeta().getDisplayName().equals("§a生存指南针"))compass=true;
            if(!compass){ItemStack i=new ItemStack(Material.COMPASS);ItemMeta meta=i.getItemMeta();meta.setDisplayName("§a生存指南针");meta.setLore(List.of("§7右键打开服务中心","§7管理员左键传送，普通玩家左键无操作"));i.setItemMeta(meta);p.getInventory().addItem(i);}
            say(p,"欢迎来到 §6南可熙的神奇妙妙服§f！右键指南针选择世界。");
            String version=plugin.getDescription().getVersion();
            if(!version.equals(prefs.getString(path(p)+".update-notice-version"))) {
                say(p,"服务器已更新到 §a"+version+"§f：新增单人发包豁免，管理员可用 /packetallow 管理；按 UUID 保存，登录成功后生效。");
                prefs.set(path(p)+".update-notice-version",version);dirty=true;
            }
            sync.login(p);
            save();
        },20);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void initial(io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent e) {
        if(initialLobbySpawn!=null&&e.isNewPlayer())e.setSpawnLocation(initialLobbySpawn.clone());
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void world(PlayerChangedWorldEvent e) { if(LobbyGuard.lobby(e.getPlayer().getWorld()))e.getPlayer().setGameMode(GameMode.ADVENTURE); }
    @EventHandler public void quit(PlayerQuitEvent e) {Player p=e.getPlayer();flushTime(p);started.remove(p.getUniqueId());checkpoint.remove(p.getUniqueId());noTeleportUntil.remove(p.getUniqueId());save();}
    private void progress(Player p,Material material,String kind) {
        if(!authenticated(p)||p.getGameMode()!=GameMode.SURVIVAL||java.util.Set.of("flat","lobby").contains(p.getWorld().getName()))return;
        for(String key:List.of(path(p)+".progress."+kind,path(p)+".blocks."+material+"."+kind))prefs.set(key,prefs.getLong(key)+1);dirty=true;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(BlockPlaceEvent e) {progress(e.getPlayer(),e.getBlock().getType(),"placed");}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void mine(BlockBreakEvent e) {progress(e.getPlayer(),e.getBlock().getType(),"mined");}
    void stop() { for(Player p:Bukkit.getOnlinePlayers())flushTime(p);save(); }
}
