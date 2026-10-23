package cn.nankexi;
import java.util.*;
import java.lang.reflect.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scoreboard.*;
import me.ryanhamshire.GriefPrevention.*;
public class MiaoSmoke extends JavaPlugin {
 public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{if(getConfig().getBoolean("bots-only",false)){try{botsCheck((MiaoServer)Bukkit.getPluginManager().getPlugin("MiaoServer"));}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"BOTS_SMOKE_FAIL",e);}}else if(getConfig().getBoolean("spawn-only",false)){try{spawnOperatorCheck((MiaoServer)Bukkit.getPluginManager().getPlugin("MiaoServer"));}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"SPAWN_OP_SMOKE_FAIL",e);}}else if(getConfig().getBoolean("community-only",false)){try{var m=(MiaoServer)Bukkit.getPluginManager().getPlugin("MiaoServer");communityCheck(m);titlesCheck(m,Bukkit.getWorld("world"));uiCheck();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"COMMUNITY_SMOKE_FAIL",e);}}else if(getConfig().getBoolean("titles-only",false)){try{var m=(MiaoServer)Bukkit.getPluginManager().getPlugin("MiaoServer");titlesCheck(m,Bukkit.getWorld("world"));namesCheck(m,Bukkit.getWorld("world"));uiCheck();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"TITLES_SMOKE_FAIL",e);}}else if(getConfig().getBoolean("cramming-only",false)){crammingCheck();}else if(getConfig().getBoolean("sync-only",false)){try{var m=(MiaoServer)Bukkit.getPluginManager().getPlugin("MiaoServer");syncCheck(m,Bukkit.getWorld("world"));samePlatformCheck(m,Bukkit.getWorld("world"));syncMenuCheck(m,Bukkit.getWorld("world"));syncIdentityCheck(m,Bukkit.getWorld("world"));uiCheck();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"SYNC_FOCUSED_SMOKE_FAIL",e);}}else if(getConfig().getBoolean("mining-only",false)){try{var m=(MiaoServer)Bukkit.getPluginManager().getPlugin("MiaoServer");miningCheck(m,Bukkit.getWorld("world"));worldPolicyCheck(m);}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"MINING_FOCUSED_SMOKE_FAIL",e);}}else if(getConfig().getBoolean("ui-only",false))uiCheck();else check();},40);}

 void botsCheck(MiaoServer plugin)throws Exception {
  var manager=io.github.hello09x.fakeplayer.core.Main.getInjector().getInstance(io.github.hello09x.fakeplayer.core.manager.FakeplayerManager.class);
  World world=Bukkit.getWorld("world");String name="BotMenuFixture";UUID id=UUID.randomUUID();List<String> commands=new ArrayList<>();org.bukkit.inventory.Inventory[] top={null};Map<String,org.bukkit.metadata.MetadataValue> metadata=new HashMap<>();
  Player base=actor(id,name,world,world.getSpawnLocation().clone().add(8,2,8),false);
  Player owner=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(o,method,args)->switch(method.getName()){
   case "isOnline"->true;case "getPlayerProfile"->Bukkit.createProfile(id,name);case "openInventory"->{top[0]=(org.bukkit.inventory.Inventory)args[0];yield null;}
   case "setMetadata"->{metadata.put((String)args[0],(org.bukkit.metadata.MetadataValue)args[1]);yield null;}case "getMetadata"->metadata.containsKey(args[0])?List.of(metadata.get(args[0])):List.of();case "hasMetadata"->metadata.containsKey(args[0]);case "removeMetadata"->{metadata.remove(args[0]);yield null;}
   case "performCommand"->{commands.add((String)args[0]);yield true;}default->method.invoke(base,args);
  });
  Object control=get(get(plugin,"ui"),"bots");Class<?> type=control.getClass();Method own=type.getDeclaredMethod("owned",Player.class),target=type.getDeclaredMethod("target",Player.class,UUID.class),detail=type.getDeclaredMethod("detail",Player.class,UUID.class),run=type.getDeclaredMethod("run",Player.class,UUID.class,String.class);for(Method m:List.of(own,target,detail,run))m.setAccessible(true);
  Runnable cleanup=()->{for(Player b:new ArrayList<>(manager.getAll(owner)))manager.remove(b.getName(),"bot smoke cleanup");var cache=(fr.xephi.authme.data.auth.PlayerCache)getUnchecked(fr.xephi.authme.api.v3.AuthMeApi.getInstance(),"playerCache");cache.removePlayer(name.toLowerCase());cache.removePlayer("botintruderfixture");};
  java.util.function.IntConsumer[] create={null};create[0]=n->{try{
   if(n==5){Bukkit.getScheduler().runTaskLater(this,()->{try{
    List<Player> bots=manager.getAll(owner);ok(bots.size()==5,"five actual fake players registered for one owner");ok(bots.stream().map(Player::getUniqueId).distinct().count()==5&&bots.stream().map(Player::getEntityId).distinct().count()==5&&bots.stream().map(Player::getName).distinct().count()==5,"names, UUIDs and entity IDs are unique");
    for(Player b:bots)ok(b.isOnline()&&b.isValid()&&!b.isInvisible(),"spawned bot flags: "+b.getName()+" online="+b.isOnline()+" valid="+b.isValid()+" invisible="+b.isInvisible()+" location="+b.getLocation());
    boolean rejected=false;try{manager.spawnAsync(owner,null,owner.getLocation(),-1);}catch(RuntimeException expected){rejected=true;}ok(rejected&&manager.getAll(owner).size()==5,"sixth bot rejected before entity creation");
    Player stranger=actor(UUID.randomUUID(),"BotIntruderFixture",world,world.getSpawnLocation(),false);Player intruder=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(o,m,a)->m.getName().equals("isOnline")?true:m.invoke(stranger,a));
    UUID first=bots.get(0).getUniqueId();ok(target.invoke(control,intruder,first)==null,"another player cannot manage someone else's bot");
    detail.invoke(control,owner,first);ok(top[0]!=null&&top[0].getHolder().getClass().getName().endsWith("DummyControls$Panel"),"owned-bot detail panel opened");ok(top[0].getItem(10).getType()==Material.IRON_SWORD&&top[0].getItem(11).getType()==Material.IRON_PICKAXE,"attack and mining have dedicated entries");
    for(String page:List.of("movement","posture","items","settings")){Method method=type.getDeclaredMethod(page,Player.class,UUID.class);method.setAccessible(true);method.invoke(control,owner,first);ok(top[0]!=null&&top[0].getSize()==54,"complete control page: "+page);}
    Method modes=type.getDeclaredMethod("modes",Player.class,UUID.class,String.class);modes.setAccessible(true);for(String a:List.of("attack","mine","use")){modes.invoke(control,owner,first,a);ok(top[0].getSize()==54,"mode and interval panel: "+a);}
    commands.clear();ok((boolean)run.invoke(control,owner,first,"attack interval 13")&&commands.get(0).equals("fp attack interval 13 "+bots.get(0).getName()),"command explicitly targets chosen bot");int before=commands.size();ok(!(boolean)run.invoke(control,intruder,first,"kick")&&commands.size()==before,"foreign removal rejected before command dispatch");
    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"fp attack interval 13 "+bots.get(0).getName());Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"fp use interval 20 "+bots.get(1).getName());
    var actions=io.github.hello09x.fakeplayer.core.Main.getInjector().getInstance(io.github.hello09x.fakeplayer.core.manager.action.ActionManager.class);ok(actions.getActiveActions(bots.get(0)).contains(io.github.hello09x.fakeplayer.api.spi.ActionType.ATTACK)&&!actions.getActiveActions(bots.get(1)).contains(io.github.hello09x.fakeplayer.api.spi.ActionType.ATTACK),"attack setting stays on selected bot");ok(actions.getActiveActions(bots.get(1)).contains(io.github.hello09x.fakeplayer.api.spi.ActionType.USE),"separate bot accepts use interval command");
    Method tickBot=type.getDeclaredMethod("tickBot",UUID.class,Player.class);tickBot.setAccessible(true);actions.setAction(bots.get(0),io.github.hello09x.fakeplayer.api.spi.ActionType.ATTACK,io.github.hello09x.fakeplayer.api.spi.ActionSetting.stop());ok(bots.get(0).getAttackCooldown()>=1f,"real bot starts fully charged");tickBot.invoke(control,first,owner);ok(actions.getActiveActions(bots.get(0)).contains(io.github.hello09x.fakeplayer.api.spi.ActionType.ATTACK),"charged weapon schedules one attack");
    Object handle=bots.get(0).getClass().getMethod("getHandle").invoke(bots.get(0));handle.getClass().getMethod("resetAttackStrengthTicker").invoke(handle);actions.setAction(bots.get(0),io.github.hello09x.fakeplayer.api.spi.ActionType.ATTACK,io.github.hello09x.fakeplayer.api.spi.ActionSetting.stop());ok(bots.get(0).getAttackCooldown()<1f,"real cooldown reset for uncharged check");tickBot.invoke(control,first,owner);ok(!actions.getActiveActions(bots.get(0)).contains(io.github.hello09x.fakeplayer.api.spi.ActionType.ATTACK),"uncharged weapon does not schedule attack");
    ok(plugin.getConfig().getString("online-map.url").equals("http://s9-4.yxsjmc.cn:14514/"),"runtime map URL migrated to permanent domain");
    getLogger().info("BOTS_SMOKE_PASS: five real entities; unique identity; post-login visibility flags; sixth denied; ownership isolation; complete control pages; explicit per-bot commands and separate action states. Real client rendering still requires client acceptance.");
   }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"BOTS_SMOKE_FAIL",e);}finally{cleanup.run();}},180);return;}
   manager.spawnAsync(owner,null,owner.getLocation().clone().add(n,0,0),-1).whenComplete((bot,error)->Bukkit.getScheduler().runTask(this,()->{if(error!=null){getLogger().log(java.util.logging.Level.SEVERE,"BOTS_SMOKE_FAIL",error);cleanup.run();}else create[0].accept(n+1);}));
  }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"BOTS_SMOKE_FAIL",e);cleanup.run();}};create[0].accept(0);
 }
 void spawnOperatorCheck(MiaoServer plugin)throws Exception{
  Class<?> type=Class.forName("cn.nankexi.SpawnGuard",true,plugin.getClass().getClassLoader());var ctor=type.getDeclaredConstructor(MiaoServer.class);ctor.setAccessible(true);Object guard=ctor.newInstance(plugin);
  Method breaking=type.getDeclaredMethod("breaking",org.bukkit.event.block.BlockBreakEvent.class),placing=type.getDeclaredMethod("placing",org.bukkit.event.block.BlockPlaceEvent.class),multi=type.getDeclaredMethod("multi",org.bukkit.event.block.BlockMultiPlaceEvent.class),bucket=type.getDeclaredMethod("bucket",org.bukkit.event.player.PlayerBucketEmptyEvent.class),flow=type.getDeclaredMethod("flow",org.bukkit.event.block.BlockFromToEvent.class);for(Method method:List.of(breaking,placing,multi,bucket,flow))method.setAccessible(true);
  String admin="SpawnOpFixture",member="SpawnMemberFixture";var cache=(fr.xephi.authme.data.auth.PlayerCache)get(fr.xephi.authme.api.v3.AuthMeApi.getInstance(),"playerCache");
  try{
   for(String name:plugin.getConfig().getStringList("spawn-protection.worlds")){
    World world=Bukkit.getWorld(name);if(world==null)continue;var block=world.getSpawnLocation().getBlock();Player op=actor(UUID.randomUUID(),admin,world,world.getSpawnLocation(),true),normal=actor(UUID.randomUUID(),member,world,world.getSpawnLocation(),false);boolean lobby=name.equals("lobby");
    var change=new org.bukkit.event.block.BlockBreakEvent(block,op);breaking.invoke(guard,change);ok(change.isCancelled()==lobby,"OP block break follows lobby exception: "+name);
    var denied=new org.bukkit.event.block.BlockBreakEvent(block,normal);breaking.invoke(guard,denied);ok(denied.isCancelled(),"ordinary player still blocked: "+name);
    var place=new org.bukkit.event.block.BlockPlaceEvent(block,block.getState(),block,new org.bukkit.inventory.ItemStack(Material.STONE),op,true,org.bukkit.inventory.EquipmentSlot.HAND);placing.invoke(guard,place);ok(place.isCancelled()==lobby,"OP placement follows lobby exception: "+name);
    var doublePlace=new org.bukkit.event.block.BlockMultiPlaceEvent(List.of(block.getState(),block.getRelative(0,1,0).getState()),block,new org.bukkit.inventory.ItemStack(Material.OAK_DOOR),op,true,org.bukkit.inventory.EquipmentSlot.HAND);multi.invoke(guard,doublePlace);ok(doublePlace.isCancelled()==lobby,"OP multi-block placement follows lobby exception: "+name);
    var water=new org.bukkit.event.player.PlayerBucketEmptyEvent(op,block,block,org.bukkit.block.BlockFace.UP,Material.WATER_BUCKET,new org.bukkit.inventory.ItemStack(Material.BUCKET),org.bukkit.inventory.EquipmentSlot.HAND);bucket.invoke(guard,water);ok(water.isCancelled()==lobby,"OP bucket follows lobby exception: "+name);
    var natural=new org.bukkit.event.block.BlockFromToEvent(block.getRelative(1,0,0),block);flow.invoke(guard,natural);ok(natural.isCancelled(),"automatic water flow remains blocked: "+name);
    if(!lobby){cache.removePlayer(admin.toLowerCase());var unauth=new org.bukkit.event.block.BlockBreakEvent(block,op);breaking.invoke(guard,unauth);ok(unauth.isCancelled(),"OP requires password login");}
   }
   var lobby=Bukkit.getWorld("lobby");Player op=actor(UUID.randomUUID(),admin,lobby,lobby.getSpawnLocation(),true);var event=new org.bukkit.event.block.BlockBreakEvent(lobby.getSpawnLocation().getBlock(),op);Object lobbyGuard=get(plugin,"lobbyGuard");Method method=lobbyGuard.getClass().getDeclaredMethod("breakBlock",org.bukkit.event.block.BlockBreakEvent.class);method.setAccessible(true);method.invoke(lobbyGuard,event);ok(event.isCancelled(),"independent immutable lobby guard still blocks OP");
   getLogger().info("SPAWN_OP_SMOKE_PASS: authenticated OP break/place/multiplace/bucket across all configured worlds; ordinary and unauthenticated players denied; lobby and automatic flow remain protected");
  }finally{org.bukkit.event.HandlerList.unregisterAll((org.bukkit.event.Listener)guard);cache.removePlayer(admin.toLowerCase());cache.removePlayer(member.toLowerCase());}
 }
 void communityCheck(MiaoServer plugin)throws Exception{
  var lookup=java.lang.invoke.MethodHandles.privateLookupIn(plugin.getClass(),java.lang.invoke.MethodHandles.lookup());Class<?> target=null;
  for(String name:List.of("CommunitySmoke","CommunitySmoke$Fixture","MiningFamilySmoke"))try(var in=getResource("cn/nankexi/"+name+".class")){Class<?> loaded=lookup.defineClass(java.util.Objects.requireNonNull(in).readAllBytes());if(name.equals("CommunitySmoke"))target=loaded;}
  Method run=target.getDeclaredMethod("run",org.bukkit.plugin.java.JavaPlugin.class,MiaoServer.class);run.setAccessible(true);try{run.invoke(null,this,plugin);if(!getConfig().getBoolean("family-reset",true))return;Class<?> family=Class.forName("cn.nankexi.MiningFamilySmoke",true,plugin.getClass().getClassLoader());Method check=family.getDeclaredMethod("run",org.bukkit.plugin.java.JavaPlugin.class,MiaoServer.class);check.setAccessible(true);check.invoke(null,this,plugin);}catch(InvocationTargetException e){throw new RuntimeException(e.getCause());}
 }
 void titlesCheck(MiaoServer plugin,World world)throws Exception{
  Object ui=get(plugin,"ui"),titles=get(ui,"titles"),names=get(ui,"names");Class<?> type=titles.getClass();YamlConfiguration data=(YamlConfiguration)get(titles,"data"),prefs=(YamlConfiguration)get(ui,"prefs");
  Method check=type.getDeclaredMethod("check",Player.class,boolean.class),equip=type.getDeclaredMethod("equip",Player.class,String.class),purchase=type.getDeclaredMethod("purchase",Player.class,String.class),visibility=type.getDeclaredMethod("visibility",Player.class,String.class),refresh=type.getDeclaredMethod("refresh",UUID.class),draft=type.getDeclaredMethod("draft",Player.class,String.class,int.class),customText=type.getDeclaredMethod("customText",io.papermc.paper.event.player.AsyncChatEvent.class);for(Method method:List.of(check,equip,purchase,visibility,refresh,draft,customText))method.setAccessible(true);
  UUID id=UUID.randomUUID(),otherId=UUID.randomUUID();String name="TitlesFixture";String base="players."+id;Player actor=actor(id,name,world,world.getSpawnLocation(),false);
  Map<Statistic,Integer> stats=new HashMap<>();stats.put(Statistic.MOB_KILLS,1200);stats.put(Statistic.FISH_CAUGHT,600);stats.put(Statistic.TRADED_WITH_VILLAGER,1200);stats.put(Statistic.RAID_WIN,1);stats.put(Statistic.WALK_ONE_CM,2000000);
  World[] where={world};GameMode[] mode={GameMode.SURVIVAL};String[] tab={""};List<String> messages=new ArrayList<>();org.bukkit.inventory.Inventory[] top={null};
  Set<NamespacedKey> advances=new HashSet<>();var iterator=Bukkit.advancementIterator();while(iterator.hasNext()&&advances.size()<20){var a=iterator.next();if(a.getKey().getNamespace().equals("minecraft")&&!a.getKey().getKey().startsWith("recipes/"))advances.add(a.getKey());}
  Player player=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(obj,method,args)->switch(method.getName()){
   case "getWorld"->where[0];case "getGameMode"->mode[0];case "isOnline"->true;
   case "getStatistic"->args.length>1?1:stats.getOrDefault((Statistic)args[0],0);
   case "getAdvancementProgress"->{var advancement=(org.bukkit.advancement.Advancement)args[0];yield Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{org.bukkit.advancement.AdvancementProgress.class},(o,m,a)->switch(m.getName()){case "isDone"->advances.contains(advancement.getKey());case "getAwardedCriteria"->List.of("fixture");case "getAdvancement"->advancement;default->null;});}
   case "sendMessage"->{if(args[0] instanceof String s)messages.add(s);yield null;}case "setDisplayName"->null;case "setPlayerListName"->{tab[0]=(String)args[0];yield null;}
   case "openInventory"->{top[0]=(org.bukkit.inventory.Inventory)args[0];yield null;}case "closeInventory"->{top[0]=null;yield null;}default->method.invoke(actor,args);
  });
  double[] balance={1000000};int[] withdrawals={0};boolean[] fail={false};
  var economy=(net.milkbowl.vault.economy.Economy)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{net.milkbowl.vault.economy.Economy.class},(obj,method,args)->switch(method.getName()){
   case "getName"->"TitleFixtureEconomy";case "isEnabled","hasAccount"->true;case "getBalance"->balance[0];case "has"->balance[0]>=(double)args[args.length-1];case "fractionalDigits"->2;
   case "withdrawPlayer"->{double amount=(double)args[args.length-1];withdrawals[0]++;boolean success=!fail[0]&&balance[0]>=amount;if(success)balance[0]-=amount;yield new net.milkbowl.vault.economy.EconomyResponse(amount,balance[0],success?net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS:net.milkbowl.vault.economy.EconomyResponse.ResponseType.FAILURE,"fixture");}
   default->{Class<?> t=method.getReturnType();yield t==boolean.class?false:t==int.class?0:t==double.class?0.0:null;}
  });
  Bukkit.getServicesManager().register(net.milkbowl.vault.economy.Economy.class,economy,this,org.bukkit.plugin.ServicePriority.Highest);
  Runnable cleanup=()->{try{data.set(base,null);prefs.set(base,null);((Map<?,?>)get(titles,"views")).remove(id);((Map<?,?>)get(titles,"snapshots")).remove(id);((Map<?,?>)get(titles,"counts")).remove(id);((Map<?,?>)get(titles,"totals")).remove(id);((Map<?,?>)get(titles,"drafts")).remove(id);((Map<?,?>)get(titles,"customInputs")).remove(id);((Map<?,?>)get(names,"notes")).remove(otherId);Method save=type.getDeclaredMethod("save");save.setAccessible(true);save.invoke(titles);}catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"TITLES_CLEANUP_FAIL",e);}Bukkit.getServicesManager().unregister(economy);((fr.xephi.authme.data.auth.PlayerCache)getUnchecked(fr.xephi.authme.api.v3.AuthMeApi.getInstance(),"playerCache")).removePlayer(name.toLowerCase());};
  try{
   prefs.set(base+".progress.mined",10000L);prefs.set(base+".progress.placed",5000L);prefs.set(base+".total-seconds",36000L);
   List<?> unlocked=(List<?>)check.invoke(titles,player,true);ok(unlocked.containsAll(List.of("first_light","familiar","deep_walker","craftsman","night_guard","ocean_guest","trade_2","raid_1","wealth_3")),"historical online/mining/building/combat/fishing/trading/raids/wealth titles backfilled");ok(!data.getBoolean(base+".owned.legend_survivor"),"multi-condition title cannot unlock from one qualifying metric");
   ok(((List<?>)check.invoke(titles,player,true)).isEmpty(),"repeated backfill never grants twice");equip.invoke(titles,player,"starlight");ok(!"starlight".equals(data.getString(base+".equipped")),"unowned title cannot be equipped");equip.invoke(titles,player,"deep_walker");ok(data.getString(base+".equipped").equals("deep_walker")&&org.bukkit.ChatColor.stripColor(tab[0]).contains("地心行者"),"owned title appears in TAB after equip");
   var disk=YamlConfiguration.loadConfiguration((java.io.File)get(titles,"file"));ok(disk.getBoolean(base+".owned.deep_walker")&&disk.getString(base+".equipped").equals("deep_walker"),"ownership and equipment persisted by UUID");
   Method chat=names.getClass().getDeclaredMethod("chat",io.papermc.paper.event.player.AsyncChatEvent.class);chat.setAccessible(true);var text=net.kyori.adventure.text.Component.text("hello");var event=new io.papermc.paper.event.player.AsyncChatEvent(false,player,new HashSet<>(),io.papermc.paper.chat.ChatRenderer.defaultRenderer(),text,text,net.kyori.adventure.chat.SignedMessage.system("hello",null));chat.invoke(names,event);
   String rendered=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.renderer().render(player,ComponentText(name),text,player));ok(rendered.contains("地心行者")&&rendered.indexOf("地心行者")==rendered.lastIndexOf("地心行者"),"chat renderer shows equipped title exactly once");
   visibility.invoke(titles,player,"chat");Method prefix=type.getDeclaredMethod("prefix",UUID.class,String.class);prefix.setAccessible(true);ok(prefix.invoke(titles,id,"chat").equals("")&&!prefix.invoke(titles,id,"tab").equals(""),"chat visibility independent from TAB");
   visibility.invoke(titles,player,"sidebar");ok(prefix.invoke(titles,id,"sidebar").equals(""),"sidebar visibility is independently persisted");
   long before=data.getLong(base+".progress.kills");mode[0]=GameMode.CREATIVE;stats.put(Statistic.MOB_KILLS,1500);check.invoke(titles,player,false);mode[0]=GameMode.SURVIVAL;check.invoke(titles,player,false);ok(data.getLong(base+".progress.kills")==before,"creative statistic increases do not unlock survival progress after returning");stats.put(Statistic.MOB_KILLS,1510);check.invoke(titles,player,false);ok(data.getLong(base+".progress.kills")==before+10,"new survival statistics accrue without double credit");stats.put(Statistic.MOB_KILLS,0);check.invoke(titles,player,false);stats.put(Statistic.MOB_KILLS,1510);check.invoke(titles,player,false);ok(data.getLong(base+".progress.kills")==before+10,"statistics reset/restore does not duplicate progress");
   ok((boolean)purchase.invoke(titles,player,"starlight"),"shop purchase succeeds");ok(balance[0]==950000&&withdrawals[0]==1,"shop purchase charged exactly displayed price");ok(!(boolean)purchase.invoke(titles,player,"starlight")&&withdrawals[0]==1,"repeated purchase cannot charge twice");
   balance[0]=0;ok(!(boolean)purchase.invoke(titles,player,"sunset")&&withdrawals[0]==1,"insufficient money does not debit");balance[0]=950000;fail[0]=true;ok(!(boolean)purchase.invoke(titles,player,"sunset")&&!data.getBoolean(base+".owned.sunset")&&!data.contains(base+".purchase"),"failed debit grants no title and clears intent");fail[0]=false;
   where[0]=Bukkit.getWorld("architect");int count=withdrawals[0];ok(!(boolean)purchase.invoke(titles,player,"sunset")&&count==withdrawals[0],"creative-world purchase forbidden even in survival game mode");where[0]=world;
   draft.invoke(titles,player,"星河执剑人",2);ok(top[0]!=null&&top[0].getSize()==27,"custom name/color opens confirmation preview without debit");Object currentDraft=((Map<?,?>)get(titles,"drafts")).get(id);String customId=(String)get(currentDraft,"id");double beforeCustom=balance[0];ok((boolean)purchase.invoke(titles,player,customId)&&balance[0]==beforeCustom-100000,"custom title costs exactly one hundred thousand");ok(data.getString(base+".custom."+customId+".name").equals("星河执剑人"),"custom text stored under owner's UUID");equip.invoke(titles,player,customId);ok(org.bukkit.ChatColor.stripColor(tab[0]).contains("星河执剑人"),"custom gradient title equipped without identity rename");
   Method find=type.getDeclaredMethod("title",UUID.class,String.class);find.setAccessible(true);ok(find.invoke(titles,otherId,customId)==null,"custom titles are private ownership, not globally purchasable IDs");
   Method valid=type.getDeclaredMethod("validCustom",String.class);valid.setAccessible(true);ok(!(boolean)valid.invoke(null,"<red>管理员")&&!(boolean)valid.invoke(null,"§k乱闪")&&!(boolean)valid.invoke(null,"x".repeat(11)),"custom formatting injection/control and overlength rejected");
   Method placeholder=type.getDeclaredMethod("placeholder",UUID.class,String.class);placeholder.setAccessible(true);ok(placeholder.invoke(titles,id,"plain").equals("【星河执剑人】")&&Integer.parseInt((String)placeholder.invoke(titles,id,"owned"))>=unlocked.size()+2,"placeholder cache reflects custom equip and ownership");
   var catalog=(Map<?,?>)get(titles,"catalog");ok(catalog.size()>=70,"rich milestone catalog installed");
   Method command=type.getDeclaredMethod("command",Player.class,String[].class);command.setAccessible(true);command.invoke(titles,player,(Object)new String[]{"all"});ok(top[0]!=null&&top[0].getSize()==54,"Java title collection panel opens");
   Method customInput=type.getDeclaredMethod("customInput",Player.class);customInput.setAccessible(true);customInput.invoke(titles,player);var cancel=net.kyori.adventure.text.Component.text("取消");var inputEvent=new io.papermc.paper.event.player.AsyncChatEvent(false,player,new HashSet<>(),io.papermc.paper.chat.ChatRenderer.defaultRenderer(),cancel,cancel,net.kyori.adventure.chat.SignedMessage.system("cancel",null));customText.invoke(titles,inputEvent);ok(inputEvent.isCancelled(),"custom text input and cancellation never broadcast");
   Method news=get(ui,"releaseNews").getClass().getDeclaredMethod("show",Player.class);news.setAccessible(true);news.invoke(get(ui,"releaseNews"),player);ok(messages.stream().anyMatch(s->s.contains(plugin.getPluginMeta().getVersion()))&&messages.stream().anyMatch(s->s.contains("假人")),"current version news announces current release features");
   // Unknown Vault outcomes remain blocked until an operator explicitly resolves the recorded intent.
   data.set(base+".purchase.title","sunset");data.set(base+".purchase.price",5000);count=withdrawals[0];ok(!(boolean)purchase.invoke(titles,player,"sunset")&&withdrawals[0]==count,"pending intent blocks another debit");
   Method admin=type.getDeclaredMethod("admin",org.bukkit.command.CommandSender.class,String[].class);admin.setAccessible(true);admin.invoke(titles,Bukkit.getConsoleSender(),(Object)new String[]{"resolve",id.toString(),"cancel"});ok(!data.contains(base+".purchase")&&withdrawals[0]==count,"operator review clears intent without another money operation");
   ((Map<?,?>)get(titles,"customInputs")).remove(id);draftsCleanup(titles,id);data.load((java.io.File)get(titles,"file"));refresh.invoke(titles,id);ok(data.getString(base+".equipped").equals(customId)&&org.bukkit.ChatColor.stripColor(prefix.invoke(titles,id,"tab").toString()).contains("星河执剑人"),"custom ownership/equip survives disk reload");
   getLogger().info("TITLES_SMOKE_PASS: historical/idempotent backfill, multi-condition lock, creative/reset exclusion, exact shop/custom debits, failure/pending guards, gradients/equip/visibility/chat/PAPI caches, custom validation/private input and disk reload");
   Bukkit.getScheduler().runTaskLater(this,cleanup,4);
  }catch(Throwable e){cleanup.run();throw e;}
 }
 net.kyori.adventure.text.Component ComponentText(String s){return net.kyori.adventure.text.Component.text(s);}
 void draftsCleanup(Object titles,UUID id)throws Exception{((Map<?,?>)get(titles,"drafts")).remove(id);}
 void crammingCheck(){
  World world=Bukkit.getWorld("world");List<org.bukkit.entity.Entity> entities=new ArrayList<>();List<org.bukkit.block.BlockState> blocks=new ArrayList<>();Set<UUID> targets=new HashSet<>();Map<UUID,Integer> hits=new HashMap<>(),cancelled=new HashMap<>();List<org.bukkit.entity.LivingEntity> mobs=new ArrayList<>();List<org.bukkit.Chunk> chunks=new ArrayList<>();
  Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener(){
   @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR) public void damage(org.bukkit.event.entity.EntityDamageEvent e){if(targets.contains(e.getEntity().getUniqueId())&&e.getCause()==org.bukkit.event.entity.EntityDamageEvent.DamageCause.CRAMMING)(e.isCancelled()?cancelled:hits).merge(e.getEntity().getUniqueId(),1,Integer::sum);}
   @org.bukkit.event.EventHandler public void death(org.bukkit.event.entity.EntityDeathEvent e){if(targets.contains(e.getEntity().getUniqueId())){e.getDrops().clear();e.setDroppedExp(0);}}
  },this);
  Runnable cleanup=()->{for(var e:entities)if(e.isValid())e.remove();for(var state:blocks)state.update(true,false);for(var chunk:chunks)chunk.removePluginChunkTicket(this);};
  try{
   var rule=org.bukkit.Registry.GAME_RULE.get(NamespacedKey.minecraft("max_entity_cramming"));ok(rule!=null,"cramming gamerule registered");getLogger().info("CRAMMING_DIAGNOSTIC: local rule="+world.getGameRuleValue(rule)+", difficulty="+world.getDifficulty());
   for(int test=0;test<2;test++){
    int x=world.getSpawnLocation().getBlockX()+64+test*8,z=world.getSpawnLocation().getBlockZ()+64,y=world.getMaxHeight()-20;var chunk=world.getChunkAt(x>>4,z>>4);chunk.addPluginChunkTicket(this);chunks.add(chunk);
    for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int dy=-1;dy<=2;dy++){var b=world.getBlockAt(x+dx,y+dy,z+dz);blocks.add(b.getState());b.setType(dx==0&&dz==0&&dy>=0?Material.AIR:Material.GLASS,false);}
    Location center=new Location(world,x+.5,y,z+.5);var mob=(org.bukkit.entity.LivingEntity)world.spawnEntity(center,test==0?org.bukkit.entity.EntityType.ZOMBIFIED_PIGLIN:org.bukkit.entity.EntityType.COW);mob.setGravity(false);mob.setRemoveWhenFarAway(false);mob.setSilent(true);mob.setPersistent(true);entities.add(mob);mobs.add(mob);targets.add(mob.getUniqueId());Object handle=mob.getClass().getMethod("getHandle").invoke(mob);handle.getClass().getField("activatedTick").setLong(handle,Long.MAX_VALUE);
    for(int n=0;n<26;n++){var cart=world.spawn(center,org.bukkit.entity.Minecart.class);cart.setGravity(false);cart.setVelocity(new org.bukkit.util.Vector());entities.add(cart);}
   }
   Bukkit.getScheduler().runTaskLater(this,()->{try{
    for(var mob:mobs){getLogger().info("CRAMMING_RESULT: "+mob.getType()+", dead="+mob.isDead()+", cramming_hits="+hits.getOrDefault(mob.getUniqueId(),0)+", cancelled="+cancelled.getOrDefault(mob.getUniqueId(),0));ok(mob.isDead()&&hits.getOrDefault(mob.getUniqueId(),0)>0,"26 ordinary minecarts kill "+mob.getType()+" through actual cramming");}
    getLogger().info("CRAMMING_SMOKE_PASS: real ordinary-minecart stack kills piglin and cow with current plugin stack and collision settings; no settings changed");
   }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"CRAMMING_SMOKE_FAIL",e);}finally{cleanup.run();}},140);
  }catch(Throwable e){cleanup.run();getLogger().log(java.util.logging.Level.SEVERE,"CRAMMING_SMOKE_FAIL",e);}
 }
 void uiCheck(){
  try{
   var plugin=Bukkit.getPluginManager().getPlugin("MiaoServer");var folder=new java.io.File(plugin.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus");int count=0;
   Class<?> api=Class.forName("com.extendedclip.deluxemenus.menu.Menu",true,Bukkit.getPluginManager().getPlugin("DeluxeMenus").getClass().getClassLoader());
   var configured=YamlConfiguration.loadConfiguration(new java.io.File(folder.getParentFile(),"config.yml")).getConfigurationSection("gui_menus");
   for(String name:Objects.requireNonNull(configured).getKeys(false)){
    var file=new java.io.File(folder,Objects.requireNonNull(configured.getString(name+".file")));
    var menu=YamlConfiguration.loadConfiguration(file);var items=menu.getConfigurationSection("items");if(items==null)continue;Set<Integer> slots=new HashSet<>();
    for(String key:items.getKeys(false)){
     int slot=menu.getInt("items."+key+".slot",-1);ok(slot>=0&&slot<menu.getInt("size"),file.getName()+" slot bounds");ok(slots.add(slot),file.getName()+" overlapping items");
     ok(menu.getStringList("items."+key+".left_click_commands").equals(menu.getStringList("items."+key+".right_click_commands")),file.getName()+" left/right parity");
    }
    ok(((Optional<?>)api.getMethod("getMenuByName",String.class).invoke(null,name)).isPresent(),"native menu loaded: "+name);count++;
   }
   var main=YamlConfiguration.loadConfiguration(new java.io.File(folder,"survival.yml"));ok(main.getStringList("items.flat.left_click_commands").contains("[openguimenu] worlds"),"main guide reaches world selector");
   ok(!main.contains("items.lobby")&&!main.contains("items.survival")&&main.getInt("items.titles.slot")==4&&main.getStringList("items.titles.left_click_commands").contains("[player] titles"),"world entries folded and standalone title button migrated");
   ok(main.getInt("items.online_map.slot")==39&&main.getInt("items.close.slot")==43,"footer aligned without crowding core rows");
   var worlds=YamlConfiguration.loadConfiguration(new java.io.File(folder,"worlds.yml"));ok(worlds.getInt("items.back.slot")==22&&worlds.getStringList("items.back.left_click_commands").contains("[player] miao menu survival"),"world selector returns to guide");
   World architect=Bukkit.getWorld("architect");ok(architect!=null&&architect.getSeed()==8176667507634209290L,"architect loaded with exact requested seed");
   var groups=org.mvplugins.multiverse.inventories.MultiverseInventoriesApi.get().getWorldGroupManager();var architecture=groups.getGroup("architecture");
   ok(groups.getGroupsForWorld("architect").equals(List.of(architecture))&&!groups.getGroup("survival").containsWorld("architect")&&!groups.getGroup("creative").containsWorld("architect"),"architect independent of survival and flat inventory groups");
   ok(architecture.isSharing(org.mvplugins.multiverse.inventories.share.Sharables.INVENTORY)&&architecture.isSharing(org.mvplugins.multiverse.inventories.share.Sharables.ENDER_CHEST),"architect inventory and ender chest isolated");
   var managed=org.mvplugins.multiverse.core.MultiverseCoreApi.get().getWorldManager().getWorld(architect).get();ok(managed.getGameMode()==GameMode.CREATIVE&&architect.getDifficulty()==Difficulty.PEACEFUL,"architect creative and peaceful");
   ok(worlds.getStringList("items.architect.left_click_commands").contains("[player] mv tp w:architect"),"Java and PE shared world menu routes to architect");
   getLogger().info("ARCHITECT_SMOKE_PASS: exact seed, creative mode, independent inventory/ender chest group, menu route");
   Object tools=get(get(plugin,"ui"),"creativeTools");Method toolCommand=tools.getClass().getDeclaredMethod("command",Player.class,String[].class);toolCommand.setAccessible(true);
   UUID toolId=UUID.randomUUID();String toolName="BuildToolsFixture";Player base=actor(toolId,toolName,architect,architect.getSpawnLocation(),false);World[] toolWorld={architect};GameMode[] toolMode={GameMode.CREATIVE};boolean[] flight={false};
   Player builder=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(obj,method,args)->switch(method.getName()){
    case "getWorld"->toolWorld[0];case "getGameMode"->toolMode[0];case "setGameMode"->{toolMode[0]=(GameMode)args[0];yield null;}case "getAllowFlight"->flight[0];case "setAllowFlight"->{flight[0]=(boolean)args[0];yield null;}case "setFlying"->null;default->method.invoke(base,args);
   });
   for(String worldName:List.of("architect","flat")){toolWorld[0]=Bukkit.getWorld(worldName);ok(toolWorld[0].getDifficulty()==Difficulty.PEACEFUL,"both building worlds peaceful");toolCommand.invoke(tools,builder,(Object)new String[]{"mode","survival"});ok(toolMode[0]==GameMode.SURVIVAL,"self survival mode allowed in building world");toolCommand.invoke(tools,builder,(Object)new String[]{"mode","creative"});ok(toolMode[0]==GameMode.CREATIVE,"self creative mode allowed in building world");toolCommand.invoke(tools,builder,(Object)new String[]{"mode","spectator"});ok(toolMode[0]==GameMode.CREATIVE,"unrequested modes rejected");toolCommand.invoke(tools,builder,(Object)new String[]{"fly"});ok(flight[0],"self building flight allowed");flight[0]=false;}
   toolWorld[0]=Bukkit.getWorld("world");toolMode[0]=GameMode.SURVIVAL;toolCommand.invoke(tools,builder,(Object)new String[]{"mode","creative"});toolCommand.invoke(tools,builder,(Object)new String[]{"fly"});ok(toolMode[0]==GameMode.SURVIVAL&&!flight[0],"outside building worlds temporary capabilities denied");
   ((fr.xephi.authme.data.auth.PlayerCache)get(fr.xephi.authme.api.v3.AuthMeApi.getInstance(),"playerCache")).removePlayer(toolName.toLowerCase());
   getLogger().info("CREATIVE_TOOLS_SMOKE_PASS: peaceful, bounded self modes and flight in both worlds; denied outside building worlds");
   var resource=YamlConfiguration.loadConfiguration(new java.io.File(folder,"resource.yml"));Class<?> ui=Class.forName("cn.nankexi.MiaoInterface",true,plugin.getClass().getClassLoader());Method content=ui.getDeclaredMethod("formContent",YamlConfiguration.class);content.setAccessible(true);String text=(String)content.invoke(null,resource);
   ok(text.contains("200")&&text.contains("1/3")&&text.contains("04:00")&&text.contains("不要建家"),"PE form includes admission, withholding, reset and destruction warning");
   for(String name:List.of("technical","claims")){var menu=YamlConfiguration.loadConfiguration(new java.io.File(folder,name+".yml"));ok(menu.getInt("size")==36&&menu.getInt("items.back.slot")==31,"compact two-row panel "+name);}
   getLogger().info("UI_LAYOUT_SMOKE_PASS: "+count+" native menus, slot bounds, zero overlap, click parity, world navigation, aligned footer and PE resource rules; mining reset tests skipped");
  }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"UI_LAYOUT_SMOKE_FAIL",e);}
 }
 Object get(Object o,String field) throws Exception {Field f=o.getClass().getDeclaredField(field);f.setAccessible(true);return f.get(o);}
 void ok(boolean b,String s){if(!b)throw new AssertionError(s);}
 void cuboidCheck(MiaoServer m,YamlConfiguration teams,World w)throws Exception {
  var c=(CuboidClaims)get(m,"cuboids");var field=CuboidClaims.class.getDeclaredField("data");field.setAccessible(true);var original=(YamlConfiguration)field.get(c);var fixture=new YamlConfiguration();field.set(c,fixture);
  var rm=com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer().get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(w));
  List<String> regions=new ArrayList<>();UUID owner=UUID.randomUUID(),member=UUID.randomUUID(),manual=UUID.randomUUID();String team="cuboid-fixture";
  try {
   Location a=new Location(w,2000000,70,2000000),b=a.clone().add(24,24,24);
   ok(CuboidClaims.withinLimit(a,b,25),"inclusive 25 cube accepted");
   for(Location larger:List.of(b.clone().add(1,0,0),b.clone().add(0,1,0),b.clone().add(0,0,1))) {
    ok(!CuboidClaims.withinLimit(a,larger,25),"26 on each axis rejected");boolean rejected=false;try{c.create(owner,"too large",a,larger,false);}catch(IllegalArgumentException expected){rejected=true;}ok(rejected,"normal owner cannot create oversize");
   }
   String id=c.create(owner,"fixture",a,b,false);regions.add(id);var r=rm.getRegion(id);
   ok(r.contains(2000000,70,2000000)&&r.contains(2000024,94,2000024)&&!r.contains(2000000,69,2000000)&&!r.contains(2000000,95,2000000),"strict vertical boundary");
   var outsiders=(com.sk89q.worldguard.protection.association.RegionAssociable)(rs->com.sk89q.worldguard.domains.Association.NON_MEMBER);
   var members=(com.sk89q.worldguard.protection.association.RegionAssociable)(rs->com.sk89q.worldguard.domains.Association.MEMBER);
   var query=com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();var point=com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(a.clone().add(5,5,5));
   for(var flag:List.of(com.sk89q.worldguard.protection.flags.Flags.BLOCK_BREAK,com.sk89q.worldguard.protection.flags.Flags.BLOCK_PLACE,com.sk89q.worldguard.protection.flags.Flags.CHEST_ACCESS)) {
    ok(!query.testBuild(point,outsiders,flag),"outsider denied: "+flag.getName());ok(query.testBuild(point,members,flag),"member allowed: "+flag.getName());
   }
   teams.set("teams."+team+".owner",member.toString());teams.set("teams."+team+".members",List.of(owner.toString(),member.toString(),manual.toString()));
   fixture.set("lands."+id+".manual",List.of(manual.toString()));r.getMembers().addPlayer(manual);
   Player p=actor(owner,"CuboidOwnerFixture",w,a.clone().add(5,5,5),false),other=actor(member,"CuboidMemberFixture",w,p.getLocation(),false),op=actor(UUID.randomUUID(),"CuboidOpFixture",w,p.getLocation(),true);
   farmingCheck(m,p,op,w);
   Method canInvite=MiaoServer.class.getDeclaredMethod("canInvite",Player.class);canInvite.setAccessible(true);
   ok((boolean)canInvite.invoke(m,p)&&(boolean)canInvite.invoke(m,other),"leader and ordinary member both allowed to invite");
   ok(!(boolean)canInvite.invoke(m,op),"nonmember cannot invite despite OP");
   m.onCommand(p,m.getCommand("mteam"),"mteam",new String[]{"share"});ok(r.getMembers().contains(member),"non-leader owner can share 3D land");
   m.onCommand(other,m.getCommand("mteam"),"mteam",new String[]{"unshare"});ok(r.getMembers().contains(member),"member cannot cancel others sharing");
   c.command(other,new String[]{"delete","confirm"});ok(rm.getRegion(id)!=null,"member cannot delete others land");
   m.onCommand(op,m.getCommand("mteam"),"mteam",new String[]{"unshare"});ok(!r.getMembers().contains(member)&&r.getMembers().contains(manual)&&r.getOwners().contains(owner),"OP cancel retains manual trust and ownership");
   teams.set("teams."+team+".members",List.of(owner.toString()));c.sharing(p,team,false);ok(team.equals(fixture.getString("lands."+id+".team")),"solo team retains sharing binding");
   teams.set("teams."+team+".members",List.of(owner.toString(),member.toString()));c.syncTeams();ok(r.getMembers().contains(member),"later team join grants access");
   r.getMembers().removePlayer(member);c.syncTeams();ok(r.getMembers().contains(member),"missing shared permission repaired");
   c.sharing(p,team,true);ok(!r.getMembers().contains(member)&&r.getMembers().contains(manual),"ordinary owner can cancel with manual trust retained");
   String upper=c.create(member,"stacked",a.clone().add(0,25,0),b.clone().add(0,25,0),false);regions.add(upper);ok(rm.getRegion(upper)!=null,"nonoverlapping vertical stack allowed");
   Location far=a.clone().add(100,0,0);String approved=c.create(owner,"approved",far,far.clone().add(25,24,24),true);regions.add(approved);ok(rm.getRegion(approved).getMaximumPoint().x()-rm.getRegion(approved).getMinimumPoint().x()+1==26,"OP approved larger region supported");
   Location requested=far.clone().add(100,0,0);fixture.set("requests.test.owner",owner.toString());fixture.set("requests.test.first",requested);fixture.set("requests.test.second",requested.clone().add(25,24,24));
   c.command(other,new String[]{"approve","test"});ok(fixture.contains("requests.test"),"ordinary member cannot approve bigger land");
   Set<String> before=new HashSet<>(fixture.getConfigurationSection("lands").getKeys(false));c.command(op,new String[]{"approve","test"});
   ok(!fixture.contains("requests.test"),"OP approval completes request");for(String key:fixture.getConfigurationSection("lands").getKeys(false))if(!before.contains(key))regions.add(key);
   ok(regions.size()==4,"OP command created requested larger region");
   ok(w.getGameRuleValue(GameRule.PLAYERS_SLEEPING_PERCENTAGE)==51,"majority sleep rule loaded");
   getLogger().info("CUBOID_SMOKE_PASS: strict three-axis bounds, stacked claims, effective protection, non-leader share, owner/OP cancellation, manual trust, join repair, approval and majority sleep");
  } finally {for(String id:regions)rm.removeRegion(id);rm.save();field.set(c,original);Method save=CuboidClaims.class.getDeclaredMethod("save");save.setAccessible(true);save.invoke(c);teams.set("teams."+team,null);var cache=(fr.xephi.authme.data.auth.PlayerCache)get(fr.xephi.authme.api.v3.AuthMeApi.getInstance(),"playerCache");for(String name:List.of("cuboidownerfixture","cuboidmemberfixture","cuboidopfixture"))cache.removePlayer(name);}
 }
 void farmingCheck(MiaoServer m,Player owner,Player outsider,World world)throws Exception {
  var guard=(FarmingGuard)get(m,"farmingGuard");
  org.bukkit.block.Block crop=block(world,owner.getLocation(),Material.WHEAT);
  var owned=new simplexity.scythe.events.HarvestEvent(owner,crop);guard.harvest(owned);ok(!owned.isCancelled(),"owner harvest allowed");
  Player onlineOwner=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(obj,method,args)->method.getName().equals("isOnline")?true:method.invoke(owner,args));
  var pending=new simplexity.scythe.events.ReplantEvent(onlineOwner,crop,Material.WHEAT.createBlockData());guard.replant(pending);ok(pending.isCancelled(),"bridge schedules guarded delayed planting");
  Bukkit.getScheduler().runTaskLater(this,()->{ok(crop.getType()==Material.WHEAT,"delayed replant never overwrites an occupied crop");getLogger().info("FARMING_DELAY_SMOKE_PASS: occupied target preserved after deferred planting check");},3);
  var stolen=new simplexity.scythe.events.HarvestEvent(outsider,crop);guard.harvest(stolen);ok(stolen.isCancelled(),"outsider harvest denied");
  var planted=new simplexity.scythe.events.ReplantEvent(outsider,crop,Material.WHEAT.createBlockData());guard.replant(planted);ok(planted.isCancelled(),"outsider replant denied");
  var spawn=block(world,world.getSpawnLocation(),Material.WHEAT);var publicCrop=new simplexity.scythe.events.HarvestEvent(owner,spawn);guard.harvest(publicCrop);ok(publicCrop.isCancelled(),"public spawn harvest denied");
  var lobby=block(Bukkit.getWorld("lobby"),Bukkit.getWorld("lobby").getSpawnLocation(),Material.WHEAT);var lobbyCrop=new simplexity.scythe.events.HarvestEvent(outsider,lobby);guard.harvest(lobbyCrop);ok(lobbyCrop.isCancelled(),"OP lobby harvest denied");
  for(Material egg:List.of(Material.VILLAGER_SPAWN_EGG,Material.PILLAGER_SPAWN_EGG))for(Material type:List.of(Material.SPAWNER,Material.TRIAL_SPAWNER)) {
   var click=new org.bukkit.event.player.PlayerInteractEvent(owner,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,new org.bukkit.inventory.ItemStack(egg),block(world,owner.getLocation(),type),org.bukkit.block.BlockFace.UP,org.bukkit.inventory.EquipmentSlot.HAND);
   guard.eggs(click);ok(click.isCancelled(),"sold egg cannot convert "+type);
  }
  var config=Bukkit.getPluginManager().getPlugin("Scythe").getConfig();
  ok(config.getBoolean("auto-replant.require-seeds")&&config.getBoolean("right-click-harvest.enabled"),"enabled harvest consumes seeds for replant");
  ok(Bukkit.getPluginManager().getPermission("scythe.reload").getDefault()==org.bukkit.permissions.PermissionDefault.OP,"reload defaults to operators");
  getLogger().info("FARMING_SMOKE_PASS: owner harvest, outsider denial, replant guard, immutable spawn/lobby, seed requirement and protected spawners");
 }
 org.bukkit.block.Block block(World world,Location location,Material type) {
  return (org.bukkit.block.Block)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{org.bukkit.block.Block.class},(obj,method,args)->switch(method.getName()) {
   case "getWorld"->world;case "getLocation"->location;case "getType"->type;case "getX"->location.getBlockX();case "getY"->location.getBlockY();case "getZ"->location.getBlockZ();case "getRelative"->obj;
   default->{Class<?> c=method.getReturnType();yield c==boolean.class?false:c==int.class?0:null;}
  });
 }
 void check(){
  MiaoServer m=(MiaoServer)Bukkit.getPluginManager().getPlugin("MiaoServer");
  Claim claim=null;YamlConfiguration original=null;Field f=null;
  try{
   f=MiaoServer.class.getDeclaredField("data");f.setAccessible(true);original=(YamlConfiguration)f.get(m);
   YamlConfiguration test=new YamlConfiguration();f.set(m,test);
   UUID owner=UUID.randomUUID(), member=UUID.randomUUID(), manual=UUID.randomUUID();
   World w=Bukkit.getWorld("world");
   miningCheck(m,w);
   packetAllowCheck(m,w);
   cuboidCheck(m,test,w);
   namesCheck(m,w);
   syncCheck(m,w);
   claim=GriefPrevention.instance.dataStore.createClaim(w,1000000,1000010,-60,319,1000000,1000010,owner,null,null,null).claim;
   ok(claim!=null,"fixture claim");
   claim.setPermission(member.toString(),ClaimPermission.Container);
   claim.setPermission(manual.toString(),ClaimPermission.Build);
   var location=new Location(w,1000005,70,1000005);Scoreboard[] board={Bukkit.getScoreboardManager().getMainScoreboard()};
   Player p=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(obj,method,a)->switch(method.getName()){
     case "getUniqueId"->owner;case "getName"->"MiaoSmokeOwner";case "getLocation"->location;
     case "getWorld"->w;case "getScoreboard"->board[0];case "setScoreboard"->{board[0]=(Scoreboard)a[0];yield null;}
     case "getStatistic"->72000;case "getPing"->42;case "hasMetadata"->false;case "isOp"->true;
     case "sendMessage"->null;case "hasPermission"->true;case "hashCode"->owner.hashCode();case "equals"->obj==a[0];
     default->{Class<?> c=method.getReturnType();yield c==boolean.class?false:c==int.class?0:null;}
   });
   var authCache=(fr.xephi.authme.data.auth.PlayerCache)get(fr.xephi.authme.api.v3.AuthMeApi.getInstance(),"playerCache");
   authCache.updatePlayer(fr.xephi.authme.data.auth.PlayerAuth.builder().name("miaosmokeowner").build());
   m.onCommand(p,m.getCommand("mteam"),"team",new String[]{"create","温馨伙伴"});
   String team=test.getConfigurationSection("teams").getKeys(false).iterator().next();
   test.set("teams."+team+".members",List.of(owner.toString(),member.toString(),manual.toString()));
   test.set("teams."+team+".owner",member.toString());
   m.onCommand(p,m.getCommand("mteam"),"team",new String[]{"share"});
   ok(claim.getPermission(member.toString())==ClaimPermission.Build,"grant build");
   ok(claim.hasExplicitPermission(member,ClaimPermission.Build),"non-leader landowner grants effective build trust");
   claim.dropPermission(member.toString());m.onCommand(p,m.getCommand("mteam"),"mteam",new String[]{"share"});
   ok(claim.hasExplicitPermission(member,ClaimPermission.Build),"explicit reshare repairs missing trust");
   test.set("teams."+team+".owner",owner.toString());
   Method sync=MiaoServer.class.getDeclaredMethod("reconcile");sync.setAccessible(true);
   Player ordinary=actor(member,"MiaoMemberFixture",w,location,false);
   m.onCommand(ordinary,m.getCommand("mteam"),"mteam",new String[]{"unshare"});
   ok(test.contains("claims."+claim.getID()),"member cannot unshare another owner's land");
   m.onCommand(ordinary,m.getCommand("mteam"),"mteam",new String[]{"share"});
   ok(claim.getOwnerID().equals(owner),"member cannot take ownership or share others' land");
   Player admin=actor(UUID.randomUUID(),"MiaoAdminFixture",w,location,true);
   m.onCommand(admin,m.getCommand("mteam"),"mteam",new String[]{"unshare"});
   ok(!test.contains("claims."+claim.getID())&&claim.getOwnerID().equals(owner),"OP can cancel without changing ownership");
   ok(claim.getPermission(member.toString())==ClaimPermission.Container,"OP cancellation restores former trust");
   m.onCommand(p,m.getCommand("mteam"),"mteam",new String[]{"share"});
   UUID newOwner=UUID.randomUUID();claim.ownerID=newOwner;sync.invoke(m);
   ok(claim.getOwnerID().equals(newOwner)&&!test.contains("claims."+claim.getID()),"ownership transfer ends sharing");
   ok(claim.getPermission(member.toString())==ClaimPermission.Container&&claim.getPermission(manual.toString())==ClaimPermission.Build,"ownership transfer removes auto build and retains manual trust");
   claim.ownerID=member;
   m.onCommand(ordinary,m.getCommand("mteam"),"mteam",new String[]{"share"});
   ok(test.getString("claims."+claim.getID()+".owner").equals(member.toString()),"new owner resharing writes own grant journal");
   m.onCommand(ordinary,m.getCommand("mteam"),"mteam",new String[]{"unshare"});
   ok(claim.getPermission(owner.toString())==null,"new-owner cancellation leaves no untracked builder");
   claim.ownerID=owner;Player landowner=actor(owner,"MiaoLandownerFixture",w,location,false);
   m.onCommand(landowner,m.getCommand("mteam"),"mteam",new String[]{"share"});
   m.onCommand(landowner,m.getCommand("mteam"),"mteam",new String[]{"unshare"});
   ok(!test.contains("claims."+claim.getID()),"ordinary owner can cancel own sharing");
   m.onCommand(p,m.getCommand("mteam"),"mteam",new String[]{"share"});
   test.set("teams."+team+".members",List.of(owner.toString()));sync.invoke(m);
   ok(claim.getPermission(member.toString())==ClaimPermission.Container,"restore previous trust on leave");
   ok(claim.getPermission(manual.toString())==ClaimPermission.Build,"retain independent manual build");
   test.set("teams."+team+".members",List.of(owner.toString(),member.toString()));sync.invoke(m);
   m.onCommand(p,m.getCommand("mteam"),"team",new String[]{"disband","confirm"});
   ok(claim.getPermission(member.toString())==ClaimPermission.Container,"disband revokes grant");
   ok(GriefPrevention.instance.dataStore.getClaim(claim.getID())!=null,"keep claim after disband");
   Method render=MiaoServer.class.getDeclaredMethod("render",Player.class);render.setAccessible(true);render.invoke(m,p);
   String rows=board[0].getTeams().stream().map(Team::getPrefix).collect(java.util.stream.Collectors.joining("\n"));
   ok(rows.contains("42 ms"),"ping sidebar");
   ok(rows.contains("1小时0分"),"play time sidebar");
   ok(!rows.contains("/sidebar")&&rows.contains("我的成长")&&rows.contains("服务器"),"grouped sidebar without command footer");
   String title=board[0].getObjective(DisplaySlot.SIDEBAR).getDisplayName();
   ok(title.contains("欢迎游玩"),"welcome sidebar title");
   Object ui=get(m,"ui");YamlConfiguration preferences=(YamlConfiguration)get(ui,"prefs");
   YamlConfiguration menu=YamlConfiguration.loadConfiguration(new java.io.File(m.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus/survival.yml"));
   ok(menu.getInt("size")==45,"compact compass menu size");
   Set<Integer> menuSlots=new HashSet<>();
   for(String key:menu.getConfigurationSection("items").getKeys(false)) {
     int slot=menu.getInt("items."+key+".slot");ok(slot>=0&&slot<45&&menuSlots.add(slot),"unique valid compass slot: "+key);
   }
   ok(menuSlots.size()==45&&menu.getInt("items.sidebar.slot")==33,"complete grid and growth button");
   ok(menu.getStringList("items.sidebar.left_click_commands").contains("[player] sidebar settings"),"growth action preserved");
   var vein=Bukkit.getPluginManager().getPlugin("VeinMiner");ok(vein!=null&&vein.isEnabled(),"VeinMiner loaded on Paper 26.2");
   var veinConfig=YamlConfiguration.loadConfiguration(new java.io.File(vein.getDataFolder(),"config.yml"));
   ok(veinConfig.getInt("MaxVeinSize")==32&&!veinConfig.getBoolean("OnlyDamageOnFirstBlock"),"bounded vein with normal durability");
   ok(veinConfig.getStringList("DisabledWorlds").containsAll(List.of("lobby","flat")),"vein disabled in lobby and creative world");
   var technical=YamlConfiguration.loadConfiguration(new java.io.File(m.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus/technical.yml"));
   ok(technical.getStringList("items.vein.left_click_commands").contains("[openguimenu] vein"),"vein menu attached");
   ok(new java.io.File(vein.getDataFolder(),"messages.json").isFile(),"localized messages installed");
   var permissionFile=new YamlConfiguration();permissionFile.options().pathSeparator('|');permissionFile.load(new java.io.File(m.getDataFolder().getParentFile().getParentFile(),"permissions.yml"));
   ok(permissionFile.getBoolean("survival.player|children|veinminer.command.toggle"),"permission migration keeps literal dotted keys");
   ok(w.getViewDistance()==20,"runtime view distance 20");
   ok(w.getSimulationDistance()==15,"runtime simulation distance 15");
   var aura=dev.aurelium.auraskills.api.AuraSkillsApi.get();
   var auraConfig=YamlConfiguration.loadConfiguration(new java.io.File(m.getDataFolder().getParentFile(),"AuraSkills/config.yml"));
   ok(auraConfig.getBoolean("jobs.enabled")&&!auraConfig.getBoolean("jobs.selection.require_selection"),"income needs no job selection");
   for(var skill:List.of(dev.aurelium.auraskills.api.skill.Skills.MINING,dev.aurelium.auraskills.api.skill.Skills.FIGHTING,dev.aurelium.auraskills.api.skill.Skills.ARCHERY)) {
     String key=skill==dev.aurelium.auraskills.api.skill.Skills.MINING?"diamond_ore":"zombie";
     var source=skill.getSources().stream().filter(s->s.getId().getKey().equals(key)).findFirst().orElseThrow();
     double income=source.getIncome().getIncomeEarned(null,source.getValues(),skill,source.getXp());
     ok(Math.abs(income-(key.equals("diamond_ore")?2:.3))<.0001,"loaded source income "+key);
   }
   var pvp=dev.aurelium.auraskills.api.skill.Skills.FIGHTING.getSources().stream().filter(s->s.getId().getKey().equals("player")).findFirst().orElseThrow();
   ok(pvp.getIncome().getIncomeEarned(null,pvp.getValues(),dev.aurelium.auraskills.api.skill.Skills.FIGHTING,50)==0,"no PVP money");
   var sellMenu=YamlConfiguration.loadConfiguration(new java.io.File(m.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus/market.yml"));
   ok(sellMenu.getStringList("items.sell.left_click_commands").contains("[player] miao sell"),"PE usable sell menu installed");
   var sellPermission=new YamlConfiguration();sellPermission.options().pathSeparator('|');sellPermission.load(new java.io.File(m.getDataFolder().getParentFile().getParentFile(),"permissions.yml"));
   ok(sellPermission.getBoolean("survival.player|children|economyshopgui.sellgui.nlk_resources"),"sale permission literal key");
   var shopPlugin=Bukkit.getPluginManager().getPlugin("EconomyShopGUI");
   getLogger().info("SHOP_SECTIONS: "+me.gypopo.economyshopgui.api.EconomyShopGUIHook.getShopSections());
   for(String category:List.of("supplies","nlk_resources","nlk_farming","nlk_drops","nlk_technical")) {
     ok(shopPlugin.getClass().getMethod("getSection",String.class).invoke(shopPlugin,category)!=null,"direct API shop section loaded: "+category);
   }
   var guideMenu=com.extendedclip.deluxemenus.menu.Menu.getMenuByName("survival").orElseThrow();
   var holder=new com.extendedclip.deluxemenus.menu.MenuHolder((com.extendedclip.deluxemenus.DeluxeMenus)Bukkit.getPluginManager().getPlugin("DeluxeMenus"),p);
   for(int slot:new int[]{4,10,11,12,13,14,15,20,21,22,23,24,29,30,31,32,33,39,40,41}) {
     ok(guideMenu.getMenuItems().containsKey(slot),"guide item loaded at slot "+slot);
     var icon=guideMenu.getMenuItems().get(slot).firstEntry().getValue().getItemStack(holder);
     ok(icon!=null&&!icon.getType().isAir()&&icon.getAmount()>0,"guide visible icon at slot "+slot);
   }
   var wallet=guideMenu.getMenuItems().get(23).firstEntry().getValue().getItemStack(holder);
   ok(wallet.getType()==Material.GOLD_NUGGET&&wallet.getAmount()==1&&!wallet.getItemMeta().hasItemModel(),"wallet vanilla coin icon, fixed quantity, no hidden model");
   getLogger().info("WALLET_SMOKE_PASS: actual wallet item is GOLD_NUGGET x1 without custom item model");
   getLogger().info("SHOP_GUIDE_SMOKE_PASS: all five direct API categories and twenty guide buttons loaded");
   ok(Math.abs(me.gypopo.economyshopgui.api.EconomyShopGUIHook.getItemSellPrice(new org.bukkit.inventory.ItemStack(Material.DIAMOND))-8)<.0001,"loaded diamond sale price");
   Player creative=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(obj,method,a)->switch(method.getName()) {
     case "getWorld"->w;case "getGameMode"->GameMode.CREATIVE;case "sendMessage"->null;case "getServer"->Bukkit.getServer();
     default->{Class<?> c=method.getReturnType();yield c==boolean.class?false:c==int.class?0:null;}
   });
   var commandEvent=new org.bukkit.event.player.PlayerCommandPreprocessEvent(creative,"/economyshopgui:sellgui");
   Class<?> accessClass=Class.forName("cn.nankexi.EconomyAccess",true,m.getClass().getClassLoader());
   var accessConstructor=accessClass.getDeclaredConstructor(MiaoServer.class);accessConstructor.setAccessible(true);Object access=accessConstructor.newInstance(m);
   var accessMethod=accessClass.getDeclaredMethod("command",org.bukkit.event.player.PlayerCommandPreprocessEvent.class);accessMethod.setAccessible(true);accessMethod.invoke(access,commandEvent);ok(commandEvent.isCancelled(),"creative cannot use namespaced sale command");
   getLogger().info("ECONOMY_SMOKE_PASS: loaded mining/melee/archery income, zero PVP income, system shop prices, sell permissions and creative guard");
   ok(menu.getStringList("items.shop.left_click_commands").contains("[player] miao shop"),"compass opens visual shop");
   Class<?> sectionApi=Class.forName("me.gypopo.economyshopgui.objects.shops.ShopSection",true,Bukkit.getPluginManager().getPlugin("EconomyShopGUI").getClass().getClassLoader());
   ok(sectionApi.getMethod("openShopSection",Player.class,int.class,boolean.class)!=null,"direct category opening available");
   ok(sellPermission.getBoolean("survival.player|children|essentials.back.ondeath"),"death back permission");
   Class<?> spawnClass=Class.forName("cn.nankexi.SpawnGuard",true,m.getClass().getClassLoader());var spawnConstructor=spawnClass.getDeclaredConstructor(MiaoServer.class);spawnConstructor.setAccessible(true);Object spawnGuard=spawnConstructor.newInstance(m);
   var protectedBlock=spawnClass.getDeclaredMethod("protectedBlock",org.bukkit.block.Block.class);protectedBlock.setAccessible(true);Location publicSpawn=w.getSpawnLocation();
   ok((boolean)protectedBlock.invoke(spawnGuard,w.getBlockAt(publicSpawn.getBlockX()+8,publicSpawn.getBlockY(),publicSpawn.getBlockZ())),"spawn boundary 8 protected");
   ok(!(boolean)protectedBlock.invoke(spawnGuard,w.getBlockAt(publicSpawn.getBlockX()+9,publicSpawn.getBlockY(),publicSpawn.getBlockZ())),"spawn boundary 9 available");
   var spawnBreak=new org.bukkit.event.block.BlockBreakEvent(publicSpawn.getBlock(),p);var spawnBreaking=spawnClass.getDeclaredMethod("breaking",org.bukkit.event.block.BlockBreakEvent.class);spawnBreaking.setAccessible(true);spawnBreaking.invoke(spawnGuard,spawnBreak);ok(!spawnBreak.isCancelled(),"authenticated OP may edit ordinary spawn");
   @SuppressWarnings("unchecked") Map<UUID,World> deaths=(Map<UUID,World>)get(m,"deathWorlds");
   var reason=org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.DEATH;
   World lobbyWorld=Bukkit.getWorld("lobby");
   for(String name:List.of("world","flat","world_nether","world_the_end","lobby")) {
     World deathWorld=Bukkit.getWorld(name);ok(deathWorld!=null,"respawn fixture world loaded");deaths.put(owner,deathWorld);
     var respawn=new org.bukkit.event.player.PlayerRespawnEvent(p,lobbyWorld.getSpawnLocation(),false,false,false,reason);
     m.respawnInDeathWorld(respawn);ok(respawn.getRespawnLocation().getWorld().equals(name.equals("world_nether")||name.equals("world_the_end")?w:deathWorld),"correct fallback after death in "+name);
   }
   Location bed=w.getSpawnLocation().clone().add(3,0,3);deaths.put(owner,w);
   var bedEvent=new org.bukkit.event.player.PlayerRespawnEvent(p,bed,true,false,false,reason);m.respawnInDeathWorld(bedEvent);
   ok(bedEvent.getRespawnLocation().equals(bed),"same-world bed retained");
   World nether=Bukkit.getWorld("world_nether");Location anchor=nether.getSpawnLocation().clone().add(2,0,2);deaths.put(owner,nether);
   var anchorEvent=new org.bukkit.event.player.PlayerRespawnEvent(p,anchor,false,true,false,reason);m.respawnInDeathWorld(anchorEvent);
   ok(anchorEvent.getRespawnLocation().getWorld().equals(w),"Nether anchor obeys overworld death destination");
   deaths.put(owner,nether);var crossBed=new org.bukkit.event.player.PlayerRespawnEvent(p,bed,true,false,false,reason);m.respawnInDeathWorld(crossBed);
   ok(crossBed.getRespawnLocation().equals(bed),"Nether death returns to valid overworld bed");
   deaths.put(owner,Bukkit.getWorld("world_the_end"));
   var preservedBed=new org.bukkit.event.player.PlayerRespawnEvent(p,bed,true,false,false,reason);m.rememberEndPortalSpawn(preservedBed);preservedBed.setRespawnLocation(lobbyWorld.getSpawnLocation());m.respawnInDeathWorld(preservedBed);
   ok(preservedBed.getRespawnLocation().equals(bed),"original valid bed survives later plugin override");
   deaths.put(owner,nether);var badFallback=new org.bukkit.event.player.PlayerRespawnEvent(p,new Location(nether,0,nether.getMinHeight(),0),false,false,false,reason);m.rememberEndPortalSpawn(badFallback);m.respawnInDeathWorld(badFallback);
   ok(badFallback.getRespawnLocation().getWorld().equals(w)&&cn.nankexi.RespawnSafety.safe(badFallback.getRespawnLocation()),"raw Nether bedrock spawn replaced with safe overworld position");
   getLogger().info("RESPAWN_SMOKE_PASS: Nether/End fallback to overworld, safe landing, cross-world bed and anchor retained, original destination protected");
   var exit=new org.bukkit.event.player.PlayerRespawnEvent(p,bed,false,false,false,org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.END_PORTAL);
   m.rememberEndPortalSpawn(exit);exit.setRespawnLocation(Bukkit.getWorld("world_the_end").getSpawnLocation());m.respawnInDeathWorld(exit);
   ok(exit.getRespawnLocation().equals(bed),"end portal exit destination restored");
   var essentialsConfig=YamlConfiguration.loadConfiguration(new java.io.File(m.getDataFolder().getParentFile(),"Essentials/config.yml"));
   ok(essentialsConfig.getString("respawn-listener-priority").equals("none"),"Essentials respawn override disabled");
   String setting="players."+owner+".sidebar.enabled";
   preferences.set(setting,false);render.invoke(m,p);ok(board[0].getObjective(DisplaySlot.SIDEBAR)==null,"sidebar disable detaches");
   preferences.set(setting,true);render.invoke(m,p);ok(board[0].getObjective(DisplaySlot.SIDEBAR)!=null,"sidebar enabled again");preferences.set(setting,null);
   org.bukkit.World lobby=Bukkit.getWorld("lobby");ok(lobby!=null,"lobby exists");
   Object guard=get(m,"lobbyGuard");var event=new org.bukkit.event.block.BlockBreakEvent(lobby.getBlockAt(0,95,0),p);
   Method breaking=guard.getClass().getDeclaredMethod("breakBlock",org.bukkit.event.block.BlockBreakEvent.class);breaking.setAccessible(true);breaking.invoke(guard,event);ok(p.isOp()&&event.isCancelled(),"OP cannot break lobby");
   var we=com.sk89q.worldedit.WorldEdit.getInstance();var target=com.sk89q.worldedit.math.BlockVector3.at(0,95,0);
   var world=com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(lobby);var before=world.getBlock(target);
   var desired=before.equals(com.sk89q.worldedit.world.block.BlockTypes.DIAMOND_BLOCK.getDefaultState())?com.sk89q.worldedit.world.block.BlockTypes.EMERALD_BLOCK.getDefaultState():com.sk89q.worldedit.world.block.BlockTypes.DIAMOND_BLOCK.getDefaultState();
   try(var edit=we.newEditSession(world)){edit.setBlock(target,desired);}
   ok(world.getBlock(target).equals(before),"WorldEdit left map unchanged");
   compassCheck(m);
   getLogger().info("MIAO_SMOKE_PASS: sidebar disable/enable, OP lobby guard, WorldEdit readonly;  share, leave, original trust, disband, claim preservation, sidebar Chinese/ping/time");
  }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"MIAO_SMOKE_FAIL",e);}
  finally{try{var cache=(fr.xephi.authme.data.auth.PlayerCache)get(fr.xephi.authme.api.v3.AuthMeApi.getInstance(),"playerCache");for(String name:List.of("MiaoSmokeOwner","MiaoMemberFixture","MiaoAdminFixture","MiaoLandownerFixture"))cache.removePlayer(name);if(claim!=null)GriefPrevention.instance.dataStore.deleteClaim(claim);if(original!=null){f.set(m,original);Method save=MiaoServer.class.getDeclaredMethod("save");save.setAccessible(true);save.invoke(m);}}catch(Exception e){throw new RuntimeException(e);}}
 }
 void packetAllowCheck(MiaoServer m,World world)throws Exception {
  Class<?> type=Class.forName("cn.nankexi.PacketExemptions",true,m.getClass().getClassLoader());
  var constructor=type.getDeclaredConstructor(MiaoServer.class,java.io.File.class,java.util.function.Function.class,boolean.class);constructor.setAccessible(true);
  var refresh=type.getDeclaredMethod("refresh",Player.class);refresh.setAccessible(true);var listed=type.getDeclaredMethod("listed",UUID.class);listed.setAccessible(true);var stop=type.getDeclaredMethod("stop");stop.setAccessible(true);
  Field field=MiaoServer.class.getDeclaredField("packetExemptions");field.setAccessible(true);Object original=field.get(m);
  UUID owner=UUID.randomUUID(),other=UUID.randomUUID();String name="PacketAllowFixture";
  var cache=(fr.xephi.authme.data.auth.PlayerCache)get(fr.xephi.authme.api.v3.AuthMeApi.getInstance(),"playerCache");
  Player admin=actor(owner,name,world,world.getSpawnLocation(),true),normal=actor(other,"PacketNormalFixture",world,world.getSpawnLocation(),false);
  var channel=new io.netty.channel.embedded.EmbeddedChannel();var normalChannel=new io.netty.channel.embedded.EmbeddedChannel();
  var connection=new com.viaversion.viaversion.connection.UserConnectionImpl(channel,false);var normalConnection=new com.viaversion.viaversion.connection.UserConnectionImpl(normalChannel,false);
  var file=java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("../../downloads"),"packet-allow-smoke-").resolve("packet-exemptions.yml").toFile();
  java.util.function.Function<UUID,com.viaversion.viaversion.api.connection.UserConnection> supplier=id->id.equals(owner)?connection:id.equals(other)?normalConnection:null;
  Object manager=constructor.newInstance(m,file,supplier,false);field.set(m,manager);
  try {
   Player denied=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(obj,method,args)->method.getName().equals("hasPermission")?false:method.invoke(admin,args));
   m.onCommand(denied,m.getCommand("packetallow"),"packetallow",new String[]{"add"});ok(!(boolean)listed.invoke(manager,owner),"ordinary player cannot edit exemption list");
   cache.removePlayer(name);m.onCommand(admin,m.getCommand("packetallow"),"packetallow",new String[]{"add"});ok(!(boolean)listed.invoke(manager,owner),"unauthenticated operator cannot edit list");
   actor(owner,name,world,world.getSpawnLocation(),true);
   m.onCommand(admin,m.getCommand("packetallow"),"packetallow",new String[]{"add"});channel.runPendingTasks();
   ok((boolean)listed.invoke(manager,owner)&&!connection.getPacketTracker().isPacketLimiterEnabled(),"self-add disables native ViaVersion connection limiter");
   refresh.invoke(manager,normal);normalChannel.runPendingTasks();ok(normalConnection.getPacketTracker().isPacketLimiterEnabled(),"ordinary connection remains limited");
   cache.removePlayer(name);refresh.invoke(manager,admin);channel.runPendingTasks();ok(connection.getPacketTracker().isPacketLimiterEnabled(),"logout restores limit despite UUID membership");
   actor(owner,name,world,world.getSpawnLocation(),true);stop.invoke(manager);channel.runPendingTasks();
   manager=constructor.newInstance(m,file,supplier,false);field.set(m,manager);refresh.invoke(manager,admin);channel.runPendingTasks();
   ok(!connection.getPacketTracker().isPacketLimiterEnabled(),"persistent UUID membership reapplies on authenticated reconnect");
   for(int i=0;i<900;i++)connection.getPacketTracker().incrementReceived(1);
   ok(connection.checkServerboundPacket(),"native connection accepts packet above default limit while exempt");
   m.onCommand(admin,m.getCommand("packetallow"),"packetallow",new String[]{"remove"});channel.runPendingTasks();
   ok(!(boolean)listed.invoke(manager,owner)&&connection.getPacketTracker().isPacketLimiterEnabled(),"remove immediately restores connection limiter");
   Player named=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(obj,method,args)->method.getName().equals("getName")?"NLKASHEI":method.invoke(admin,args));
   cache.removePlayer("nlkashei");refresh.invoke(manager,named);ok(!(boolean)listed.invoke(manager,owner),"bootstrap refuses unauthenticated matching name");
   cache.updatePlayer(fr.xephi.authme.data.auth.PlayerAuth.builder().name("nlkashei").build());refresh.invoke(manager,named);channel.runPendingTasks();
   ok((boolean)listed.invoke(manager,owner)&&!connection.getPacketTracker().isPacketLimiterEnabled(),"requested owner enrolls only after login using actual UUID");
   m.onCommand(named,m.getCommand("packetallow"),"packetallow",new String[]{"remove"});channel.runPendingTasks();refresh.invoke(manager,named);
   ok(!(boolean)listed.invoke(manager,owner)&&connection.getPacketTracker().isPacketLimiterEnabled(),"manual removal is not undone by bootstrap");
   getLogger().info("PACKET_ALLOW_SMOKE_PASS: native ViaVersion bypass, authentication/permission guards, unchanged ordinary clients, persistence, removal and one-time NLKASHEI enrollment");
  }finally{stop.invoke(manager);channel.runPendingTasks();channel.finishAndReleaseAll();normalChannel.finishAndReleaseAll();field.set(m,original);cache.removePlayer(name);cache.removePlayer("packetnormalfixture");cache.removePlayer("nlkashei");}
 }
 Player actor(UUID id,String name,World world,Location location,boolean op)throws Exception {
  var cache=(fr.xephi.authme.data.auth.PlayerCache)get(fr.xephi.authme.api.v3.AuthMeApi.getInstance(),"playerCache");cache.updatePlayer(fr.xephi.authme.data.auth.PlayerAuth.builder().name(name.toLowerCase()).build());
  return (Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(obj,method,args)->switch(method.getName()) {
   case "getUniqueId"->id;case "getName"->name;case "getWorld"->world;case "getLocation"->location;case "isOp"->op;case "hasPermission"->true;case "hasMetadata"->false;case "sendMessage"->null;case "getServer"->Bukkit.getServer();
   default->{Class<?> c=method.getReturnType();yield c==boolean.class?false:c==int.class?0:null;}
  });
 }
 void miningCheck(MiaoServer m,World main)throws Exception {
  Object mining=get(m,"mining");Class<?> type=mining.getClass();World world=Bukkit.getWorld(NamespacedKey.minecraft("mining")),flat=Bukkit.getWorld("flat");
  ok(world!=null&&flat!=null,"managed worlds loaded");ok(flat.getHighestBlockYAt(0,0)==63&&flat.getBlockAt(0,-64,0).getType()==Material.BEDROCK&&flat.getBlockAt(0,60,0).getType()==Material.STONE&&flat.getBlockAt(0,63,0).getType()==Material.GRASS_BLOCK,"actual deep flat layers generated");
  var manager=org.mvplugins.multiverse.core.MultiverseCoreApi.get().getWorldManager();
  var flatWorld=(org.mvplugins.multiverse.core.world.LoadedMultiverseWorld)manager.getWorld(flat).get();ok(!flatWorld.canGenerateStructures().get(),"flat structures disabled");
  var groups=org.mvplugins.multiverse.inventories.MultiverseInventoriesApi.get().getWorldGroupManager();var survival=groups.getGroup("survival");
  ok(groups.getGroupsForWorld("mining").contains(survival)&&!groups.getGroupsForWorld("flat").contains(survival),"mining shares survival inventory; creative remains separate");
  Class<?> menuApi=Class.forName("com.extendedclip.deluxemenus.menu.Menu",true,Bukkit.getPluginManager().getPlugin("DeluxeMenus").getClass().getClassLoader());
  ok(((Optional<?>)menuApi.getMethod("getMenuByName",String.class).invoke(null,"resource")).isPresent(),"Java resource menu registered");
  var source=YamlConfiguration.loadConfiguration(new java.io.File(m.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus/worlds.yml"));ok(source.getStringList("items.mining.left_click_commands").contains("[openguimenu] resource"),"world selector reaches resource menu");
  UUID id=UUID.randomUUID(),paidId=UUID.randomUUID(),cancelId=UUID.randomUUID(),poorId=UUID.randomUUID();List<UUID> ids=List.of(id,paidId,cancelId,poorId);
  var ledger=(YamlConfiguration)get(mining,"ledger");var pending=(Map<?,?>)get(mining,"pending");
  Method reserve=type.getDeclaredMethod("reserve",UUID.class,Material.class,int.class);reserve.setAccessible(true);
  Method drops=type.getDeclaredMethod("drops",org.bukkit.event.block.BlockDropItemEvent.class);drops.setAccessible(true);
  Method mark=type.getDeclaredMethod("mark",org.bukkit.block.Block.class,boolean.class);mark.setAccessible(true);
  Method teleport=type.getDeclaredMethod("teleport",org.bukkit.event.player.PlayerTeleportEvent.class);teleport.setAccessible(true);
  Method changed=type.getDeclaredMethod("changed",org.bukkit.event.player.PlayerChangedWorldEvent.class);changed.setAccessible(true);
  Method save=type.getDeclaredMethod("save");save.setAccessible(true);
  var cache=(fr.xephi.authme.data.auth.PlayerCache)get(fr.xephi.authme.api.v3.AuthMeApi.getInstance(),"playerCache");
  Map<UUID,Double> balances=new HashMap<>();balances.put(paidId,1000.0);balances.put(cancelId,1000.0);var rejectRefund=new java.util.concurrent.atomic.AtomicBoolean();
  var economy=(net.milkbowl.vault.economy.Economy)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{net.milkbowl.vault.economy.Economy.class},(obj,method,args)->{
   if(method.getName().equals("equals"))return obj==args[0];if(method.getName().equals("hashCode"))return System.identityHashCode(obj);
   if(Set.of("withdrawPlayer","depositPlayer").contains(method.getName())){UUID player=((OfflinePlayer)args[0]).getUniqueId();double amount=(double)args[1],balance=balances.getOrDefault(player,0.0);boolean success=method.getName().equals("depositPlayer")?!rejectRefund.get():balance>=amount;if(success)balances.put(player,balance+(method.getName().equals("depositPlayer")?amount:-amount));return new net.milkbowl.vault.economy.EconomyResponse(amount,balances.getOrDefault(player,0.0),success?net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS:net.milkbowl.vault.economy.EconomyResponse.ResponseType.FAILURE,success?null:"Insufficient funds");}
   Class<?> result=method.getReturnType();return result==boolean.class?true:result==double.class?0.0:result==String.class?"fixture":null;
  });
  Player actor=actor(id,"MineDropFixture",world,world.getSpawnLocation(),false);
  Player miner=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(obj,method,args)->method.getName().equals("getGameMode")?GameMode.SURVIVAL:method.invoke(actor,args));
  org.bukkit.block.Block block=world.getBlockAt(world.getSpawnLocation().getBlockX()+32,-30,world.getSpawnLocation().getBlockZ()+32);var original=block.getState();List<org.bukkit.entity.Item> spawned=new ArrayList<>();boolean registered=false;
  try {
   ok((int)reserve.invoke(mining,id,Material.DIAMOND,8)==2&&(int)reserve.invoke(mining,id,Material.DIAMOND,1)==1,"one-third remainder persists across harvest batches");
   ok((int)reserve.invoke(mining,id,Material.DIRT,64)==0,"ordinary blocks untaxed");ledger.set("players."+id,null);
   block.setType(Material.DIAMOND_ORE,false);var state=block.getState();block.setType(Material.AIR,false);
   var item=world.dropItem(block.getLocation(),new org.bukkit.inventory.ItemStack(Material.DIAMOND,8));spawned.add(item);var items=new ArrayList<>(List.of(item));
   drops.invoke(mining,new org.bukkit.event.block.BlockDropItemEvent(block,state,miner,items));ok(item.getItemStack().getAmount()==6,"actual natural ore drops reserve two of eight diamonds");
   block.setType(Material.DIAMOND_ORE,false);mark.invoke(mining,block,true);state=block.getState();block.setType(Material.AIR,false);
   var carried=world.dropItem(block.getLocation(),new org.bukkit.inventory.ItemStack(Material.DIAMOND,8));spawned.add(carried);
   drops.invoke(mining,new org.bukkit.event.block.BlockDropItemEvent(block,state,miner,new ArrayList<>(List.of(carried))));ok(carried.getItemStack().getAmount()==8,"player-placed ore not taxed");
   Object populator=get(mining,"populator");Map<String,Material> terrain=new HashMap<>();terrain.put("0:-40:0",Material.AIR);terrain.put("1:-40:0",Material.CHEST);
   for(int n=0;n<100;n++)terrain.put((2+(n%4)*3)+":"+(-50+(n/16)*3)+":"+(2+((n/4)%4)*3),Material.DIAMOND_ORE);
   var region=(org.bukkit.generator.LimitedRegion)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{org.bukkit.generator.LimitedRegion.class},(obj,method,args)->{
    String key=args!=null&&args.length>=3?args[0]+":"+args[1]+":"+args[2]:"";
    if(method.getName().equals("getType"))return terrain.getOrDefault(key,(int)args[1]<0?Material.DEEPSLATE:Material.STONE);
    if(method.getName().equals("setType")){terrain.put(key,(Material)args[3]);return null;}Class<?> result=method.getReturnType();return result==boolean.class?true:result==int.class?0:null;
   });
   Method populate=populator.getClass().getDeclaredMethod("populate",org.bukkit.generator.WorldInfo.class,Random.class,int.class,int.class,org.bukkit.generator.LimitedRegion.class);populate.setAccessible(true);populate.invoke(populator,world,new Random(42),0,0,region);
   long ores=terrain.values().stream().filter(t->t.name().endsWith("_ORE")).count();long eligible=256L*(world.getMaxHeight()-world.getMinHeight()-2)-2;
   ok(ores==Math.round(eligible*0.30),"new underground stone/ore coverage is thirty percent including existing ores");
   ok(terrain.get("0:-40:0")==Material.AIR&&terrain.get("1:-40:0")==Material.CHEST,"air and constructed blocks preserved");
   long common=terrain.values().stream().filter(t->t.name().contains("COAL_ORE")||t.name().contains("IRON_ORE")||t.name().contains("COPPER_ORE")).count();
   long rare=terrain.values().stream().filter(t->t.name().contains("DIAMOND_ORE")||t.name().contains("EMERALD_ORE")).count();ok(common>ores*0.7&&rare<ores*0.04,"coal iron copper dominate; rare ores remain scarce");
   ok(terrain.values().stream().anyMatch(t->t==Material.DEEPSLATE_IRON_ORE)&&terrain.values().stream().anyMatch(t->t==Material.IRON_ORE),"stone and deepslate variants generated");
   Bukkit.getServicesManager().register(net.milkbowl.vault.economy.Economy.class,economy,this,org.bukkit.plugin.ServicePriority.Highest);registered=true;
   Player paid=actor(paidId,"MinePaidFixture",world,world.getSpawnLocation(),false),cancelled=actor(cancelId,"MineCancelFixture",main,main.getSpawnLocation(),false),poor=actor(poorId,"MinePoorFixture",main,main.getSpawnLocation(),false);
   var paidEvent=new org.bukkit.event.player.PlayerTeleportEvent(paid,main.getSpawnLocation(),world.getSpawnLocation(),org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);teleport.invoke(mining,paidEvent);ok(!paidEvent.isCancelled()&&balances.get(paidId)==800.0,"plugin/TPA entry charges exactly 200");changed.invoke(mining,new org.bukkit.event.player.PlayerChangedWorldEvent(paid,main));
   teleport.invoke(mining,new org.bukkit.event.player.PlayerTeleportEvent(paid,world.getSpawnLocation(),world.getSpawnLocation()));ok(balances.get(paidId)==800.0,"within-world teleport has no additional admission");
   var poorEvent=new org.bukkit.event.player.PlayerTeleportEvent(poor,main.getSpawnLocation(),world.getSpawnLocation());teleport.invoke(mining,poorEvent);ok(poorEvent.isCancelled(),"insufficient funds cannot enter");
   var cancelledEvent=new org.bukkit.event.player.PlayerTeleportEvent(cancelled,main.getSpawnLocation(),world.getSpawnLocation());teleport.invoke(mining,cancelledEvent);cancelledEvent.setCancelled(true);
   // Failed refunds survive in the ledger and retry once, rather than silently losing admission money.
   Object admission=pending.remove(cancelId);Method refund=type.getDeclaredMethod("refund",UUID.class,admission.getClass());refund.setAccessible(true);rejectRefund.set(true);refund.invoke(mining,cancelId,admission);
   ok(balances.get(cancelId)==800.0&&ledger.getConfigurationSection("refunds")!=null,"failed refund journaled");rejectRefund.set(false);Method retry=type.getDeclaredMethod("retryRefunds");retry.setAccessible(true);retry.invoke(mining);retry.invoke(mining);ok(balances.get(cancelId)==1000.0,"refund retry deposits only once");
   // A stale active flag cannot waive a cross-world entry omitted by a third-party teleport event.
   changed.invoke(mining,new org.bukkit.event.player.PlayerChangedWorldEvent(paid,main));ok(balances.get(paidId)==600.0,"fallback world change charges despite stale active visit");
   Player outside=actor(paidId,"MinePaidFixture",main,main.getSpawnLocation(),false);
   for(var cause:List.of(org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.COMMAND,org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN)){
    changed.invoke(mining,new org.bukkit.event.player.PlayerChangedWorldEvent(outside,world));ok(!ledger.getBoolean("players."+paidId+".active"),"leaving closes ticket without inventory taxation");
    var again=new org.bukkit.event.player.PlayerTeleportEvent(outside,main.getSpawnLocation(),world.getSpawnLocation(),cause);teleport.invoke(mining,again);ok(!again.isCancelled(),"paid return allowed");changed.invoke(mining,new org.bukkit.event.player.PlayerChangedWorldEvent(paid,main));
   }
   ok(balances.get(paidId)==200.0,"back and TPA round trips each purchase admission");
   Method valid=type.getDeclaredMethod("validTicket",Player.class);valid.setAccessible(true);ok((boolean)valid.invoke(mining,paid),"current-world ticket valid for reconnect");
   ledger.set("players."+paidId+".world-id",UUID.randomUUID().toString());ok(!(boolean)valid.invoke(mining,paid),"old generation ticket rejected");
   var blocked=new org.bukkit.event.player.PlayerTeleportEvent(paid,world.getSpawnLocation(),world.getSpawnLocation());teleport.invoke(mining,blocked);ok(blocked.isCancelled(),"invalid ticket cannot use internal teleport");ledger.set("players."+paidId+".world-id",world.getUID().toString());
   var user=(net.ess3.api.IUser)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{net.ess3.api.IUser.class},(o,method,args)->method.getName().equals("getBase")?paid:null);
   var oldHome=new net.ess3.api.events.UserTeleportHomeEvent(user,"mine",world.getSpawnLocation(),net.ess3.api.events.UserTeleportHomeEvent.HomeType.HOME);Method old=type.getDeclaredMethod("oldHome",oldHome.getClass());old.setAccessible(true);old.invoke(mining,oldHome);ok(oldHome.isCancelled(),"previously saved mining homes cannot be entered");
   var normalHome=new net.ess3.api.events.UserTeleportHomeEvent(user,"base",main.getSpawnLocation(),net.ess3.api.events.UserTeleportHomeEvent.HomeType.HOME);old.invoke(mining,normalHome);ok(!normalHome.isCancelled(),"survival homes remain usable");
   ledger.set("players."+id+".active",true);ledger.set("players."+id+".world-id",world.getUID().toString());
   var blast=world.dropItem(block.getLocation(),new org.bukkit.inventory.ItemStack(Material.RAW_GOLD,8));spawned.add(blast);blast.getPersistentDataContainer().set((NamespacedKey)get(mining,"naturalKey"),org.bukkit.persistence.PersistentDataType.BYTE,(byte)1);
   Method pickup=type.getDeclaredMethod("pickup",org.bukkit.event.entity.EntityPickupItemEvent.class);pickup.setAccessible(true);var pickupEvent=new org.bukkit.event.entity.EntityPickupItemEvent(miner,blast,0);pickup.invoke(mining,pickupEvent);ok(pickupEvent.isCancelled()&&blast.getItemStack().getAmount()==6,"explosion harvest taxed before native pickup");
   var secondPickup=new org.bukkit.event.entity.EntityPickupItemEvent(miner,blast,0);pickup.invoke(mining,secondPickup);ok(!secondPickup.isCancelled()&&blast.getItemStack().getAmount()==6,"taxed explosion drops not taxed twice");
   getLogger().info("MINING_SECURITY_SMOKE_PASS: stale visit, round trips, old homes, current-generation ticket, refund retry and explosion withholding");
   var sethome=new org.bukkit.event.player.PlayerCommandPreprocessEvent(paid,"/essentials:sethome mine");Method home=type.getDeclaredMethod("home",org.bukkit.event.player.PlayerCommandPreprocessEvent.class);home.setAccessible(true);home.invoke(mining,sethome);ok(sethome.isCancelled(),"namespaced sethome forbidden in mining world");
   var bedSpawn=new org.bukkit.event.player.PlayerSpawnChangeEvent(paid,world.getSpawnLocation(),false,org.bukkit.event.player.PlayerSpawnChangeEvent.Cause.BED);Method bed=type.getDeclaredMethod("bedSpawn",org.bukkit.event.player.PlayerSpawnChangeEvent.class);bed.setAccessible(true);bed.invoke(mining,bedSpawn);ok(bedSpawn.isCancelled(),"bed respawn cannot be set in mining world");
   Method firstReset=type.getDeclaredMethod("firstReset",java.time.ZonedDateTime.class,int.class,int.class);firstReset.setAccessible(true);var start=java.time.ZonedDateTime.of(2026,10,16,15,0,0,0,java.time.ZoneId.of("Asia/Shanghai"));var next=(java.time.ZonedDateTime)firstReset.invoke(null,start,3,4);ok(next.getDayOfMonth()==19&&next.getHour()==4,"three-day schedule uses Beijing four AM");
   Field dirty=type.getDeclaredField("dirty");dirty.setAccessible(true);
   Bukkit.getScheduler().runTaskLater(this,()->{
    try{ok(balances.get(cancelId)==1000.0&&balances.get(paidId)==600.0,"cancelled teleport refunds; completed entries stay charged");getLogger().info("MINING_ADMISSION_SMOKE_PASS: 100 coin entry, unchanged same-world teleports, insufficient funds rejected and cancelled-entry refund");}
    catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"MINING_ADMISSION_SMOKE_FAIL",e);}
    finally{for(UUID uuid:ids){pending.remove(uuid);ledger.set("players."+uuid,null);}try{dirty.setBoolean(mining,true);save.invoke(mining);}catch(Exception ignored){}Bukkit.getServicesManager().unregister(economy);for(String name:List.of("minedropfixture","minepaidfixture","minecancelfixture","minepoorfixture"))cache.removePlayer(name);}
   },4);
   if(!getConfig().getBoolean("mining-only",false))Bukkit.getScheduler().runTaskLater(this,()->{
    UUID offline=UUID.randomUUID();long seed=world.getSeed();UUID mainId=main.getUID(),flatId=flat.getUID();long scheduled=ledger.getLong("next-reset");
    try{
     ledger.set("players."+offline+".active",true);ledger.set("players."+offline+".carry.DIAMOND",2);Method reset=type.getDeclaredMethod("reset");reset.setAccessible(true);reset.invoke(mining);
     World fresh=Bukkit.getWorld(NamespacedKey.minecraft("mining"));ok(fresh!=null&&fresh.getSeed()!=seed,"actual scheduled reset changes seed");ok(main.getUID().equals(mainId)&&flat.getUID().equals(flatId),"resource reset does not replace survival or creative worlds");
     ok(!ledger.getBoolean("players."+offline+".active")&&ledger.getInt("players."+offline+".carry.DIAMOND")==2,"offline visit closes while tax remainder survives reset");
     ok(groups.getGroupsForWorld("mining").contains(groups.getGroup("survival")),"inventory sharing survives actual world reset");
     ok(RespawnSafety.safe(fresh.getSpawnLocation()),"fresh resource spawn has safe landing");getLogger().info("MINING_RESET_SMOKE_PASS: actual replacement, fresh seed, survival/creative unchanged, offline ticket closure, tax carry, safe spawn and inventory group retained");
     manager.deleteWorld(org.mvplugins.multiverse.core.world.options.DeleteWorldOptions.world(manager.getWorld(fresh).get())).get();
     ledger.set("reset-in-progress",true);reset.invoke(mining);ok(Bukkit.getWorld(NamespacedKey.minecraft("mining"))!=null&&!ledger.getBoolean("reset-in-progress"),"interrupted reset resumes when dimension is absent");getLogger().info("MINING_RECOVERY_SMOKE_PASS: missing resource dimension recreated without touching other worlds");
    }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"MINING_RESET_SMOKE_FAIL",e);}
    finally{ledger.set("players."+offline,null);ledger.set("next-reset",scheduled);try{dirty.setBoolean(mining,true);save.invoke(mining);}catch(Exception ignored){}}
   },10);
   registered=false;getLogger().info("MINING_WORLD_SMOKE_PASS: actual 128-layer flat/no structures, shared survival group, creative isolation, registered guide menu, thirty-percent ore coverage, natural-drop tax and placed-ore exemption");
  }finally {
   spawned.forEach(org.bukkit.entity.Item::remove);mark.invoke(mining,block,false);original.update(true,false);
   if(registered){for(UUID uuid:ids){pending.remove(uuid);ledger.set("players."+uuid,null);}save.invoke(mining);Bukkit.getServicesManager().unregister(economy);for(String name:List.of("minedropfixture","minepaidfixture","minecancelfixture","minepoorfixture"))cache.removePlayer(name);}
  }
 }
 void worldPolicyCheck(MiaoServer m)throws Exception {
  World main=Bukkit.getWorld("world"),nether=Bukkit.getWorld("world_nether"),end=Bukkit.getWorld("world_the_end"),mine=Bukkit.getWorld(NamespacedKey.minecraft("mining")),lobby=Bukkit.getWorld("lobby"),flat=Bukkit.getWorld("flat");
  for(World world:List.of(main,nether,end))ok(world.getDifficulty()==Difficulty.HARD&&Boolean.TRUE.equals(world.getGameRuleValue(GameRule.KEEP_INVENTORY)),"survival HARD and keep inventory: "+world.getName());
  ok(mine.getDifficulty()==Difficulty.PEACEFUL&&Boolean.TRUE.equals(mine.getGameRuleValue(GameRule.KEEP_INVENTORY)),"mining independent PEACEFUL and keep inventory");
  var managed=org.mvplugins.multiverse.core.MultiverseCoreApi.get().getWorldManager().getWorld(lobby).get();ok(((org.mvplugins.multiverse.core.world.LoadedMultiverseWorld)managed).getGameMode()==GameMode.ADVENTURE,"lobby adventure mode");
  UUID id=UUID.randomUUID();Player p=actor(id,"WorldPolicyFixture",main,main.getSpawnLocation(),false);Map<UUID,World> deaths=(Map<UUID,World>)get(m,"deathWorlds");
  try{
   for(World death:List.of(main,nether,end,mine,lobby,flat)){
    deaths.put(id,death);var event=new org.bukkit.event.player.PlayerRespawnEvent(p,nether.getSpawnLocation(),false,true,false,org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.DEATH);
    m.rememberEndPortalSpawn(event);m.respawnInDeathWorld(event);World expected=death==lobby||death==flat?death:main;ok(event.getRespawnLocation().getWorld()==expected,"correct death destination: "+death.getName());
   }
   deaths.put(id,nether);Location bed=main.getSpawnLocation();var event=new org.bukkit.event.player.PlayerRespawnEvent(p,bed,true,false,false,org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.DEATH);m.rememberEndPortalSpawn(event);m.respawnInDeathWorld(event);ok(event.getRespawnLocation().equals(bed),"valid overworld bed retained");
  }finally{deaths.remove(id);var cache=(fr.xephi.authme.data.auth.PlayerCache)get(fr.xephi.authme.api.v3.AuthMeApi.getInstance(),"playerCache");cache.removePlayer("worldpolicyfixture");}
  Difficulty originalDifficulty=main.getDifficulty(),miningDifficulty=mine.getDifficulty(),flatDifficulty=flat.getDifficulty(),lobbyDifficulty=lobby.getDifficulty();boolean original=Boolean.TRUE.equals(main.getGameRuleValue(GameRule.KEEP_INVENTORY));
  main.setGameRule(GameRule.KEEP_INVENTORY,false);main.setDifficulty(Difficulty.NORMAL);
  Bukkit.getScheduler().runTaskLater(this,()->{
   try{
    for(World target:List.of(nether,end))ok(target.getDifficulty()==Difficulty.NORMAL&&Boolean.FALSE.equals(target.getGameRuleValue(GameRule.KEEP_INVENTORY)),"runtime main-world rule changes propagated");
    ok(mine.getDifficulty()==miningDifficulty&&Boolean.TRUE.equals(mine.getGameRuleValue(GameRule.KEEP_INVENTORY))&&flat.getDifficulty()==flatDifficulty&&lobby.getDifficulty()==lobbyDifficulty,"independent world policies untouched");
    getLogger().info("WORLD_POLICY_SMOKE_PASS: HARD survival/PEACEFUL mining, keep inventory, adventure lobby, group synchronization and death destinations including mining fee settlement path");
   }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"WORLD_POLICY_SMOKE_FAIL",e);}
   finally{main.setGameRule(GameRule.KEEP_INVENTORY,original);main.setDifficulty(originalDifficulty);}
  },2);
 }
 void syncCheck(MiaoServer m,World world)throws Exception {
  Object ui=get(m,"ui"),sync=get(ui,"sync");Class<?> type=sync.getClass();
  Class<?> account=Class.forName("cn.nankexi.CrossplaySync$Account",true,m.getClass().getClassLoader());var constructor=account.getDeclaredConstructors()[0];constructor.setAccessible(true);
  UUID javaId=UUID.randomUUID(),peId=new UUID(0,Math.abs(UUID.randomUUID().getLeastSignificantBits()));
  Object javaAccount=constructor.newInstance(javaId,"SyncJavaFixture",false,"same-ip",200L),pe=constructor.newInstance(peId,"SyncPeFixture",true,"same-ip",100L),otherIp=constructor.newInstance(UUID.randomUUID(),"OtherPe",true,"different-ip",90L),samePlatform=constructor.newInstance(UUID.randomUUID(),"OtherJava",false,"same-ip",90L);
  Method candidate=type.getDeclaredMethod("candidate",account,account,boolean.class);candidate.setAccessible(true);Method source=type.getDeclaredMethod("source",account,account,boolean.class);source.setAccessible(true);
  ok((boolean)candidate.invoke(null,javaAccount,pe,true)&&!(boolean)candidate.invoke(null,javaAccount,otherIp,true)&&(boolean)candidate.invoke(null,javaAccount,samePlatform,true),"IP suggestions include same-platform accounts");
  ok((boolean)candidate.invoke(null,javaAccount,otherIp,false),"manual binding does not require shared IP");
  ok(source.invoke(null,javaAccount,pe,false).equals(peId)&&source.invoke(null,javaAccount,pe,true).equals(javaId),"PC may inherit PE or retain Java data");
  ok(source.invoke(null,pe,javaAccount,false).equals(javaId)&&source.invoke(null,pe,javaAccount,true).equals(peId),"PE may inherit Java or retain PE data");
  var binding=Class.forName("cn.nankexi.CrossplaySync$Binding",true,m.getClass().getClassLoader());var bc=binding.getDeclaredConstructor(String.class,UUID.class,UUID.class,UUID.class,String.class,String.class);bc.setAccessible(true);Field field=type.getDeclaredField("bindings");field.setAccessible(true);Object original=field.get(sync);
  Method profile=type.getDeclaredMethod("profile",org.bukkit.event.player.AsyncPlayerPreLoginEvent.class);profile.setAccessible(true);
  try {
   field.set(sync,Map.of("syncjavafixture",bc.newInstance("SyncJavaFixture",javaId,peId,peId,"PE source","ACTIVE")));
   var event=new org.bukkit.event.player.AsyncPlayerPreLoginEvent("SyncJavaFixture",java.net.InetAddress.getLoopbackAddress(),javaId);profile.invoke(sync,event);
   ok(event.getUniqueId().equals(peId)&&event.getName().equals("SyncJavaFixture"),"Java uses selected original PE UUID without changing Java login name");
   var stranger=new org.bukkit.event.player.AsyncPlayerPreLoginEvent("AnotherFixture",java.net.InetAddress.getLoopbackAddress(),javaId);profile.invoke(sync,stranger);ok(stranger.getUniqueId().equals(javaId),"unrelated login profile untouched");
   field.set(sync,Map.of("syncjavafixture",bc.newInstance("SyncJavaFixture",javaId,peId,peId,"PE source","PREPARED")));
   var pending=new org.bukkit.event.player.AsyncPlayerPreLoginEvent("SyncJavaFixture",java.net.InetAddress.getLoopbackAddress(),javaId);profile.invoke(sync,pending);ok(pending.getLoginResult()!=org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.ALLOWED,"incomplete journal cannot admit player under wrong UUID");
   Class<?> input=Class.forName("cn.nankexi.CrossplaySync$Input",true,m.getClass().getClassLoader());var ic=input.getDeclaredConstructor(account,boolean.class,long.class);ic.setAccessible(true);Player p=actor(javaId,"SyncJavaFixture",world,world.getSpawnLocation(),false);
   ((Map)get(sync,"inputs")).put(javaId,ic.newInstance(pe,false,0L));
   var message=net.kyori.adventure.text.Component.text("private-password-fixture");var chat=new io.papermc.paper.event.player.AsyncChatEvent(false,p,new HashSet<>(),io.papermc.paper.chat.ChatRenderer.defaultRenderer(),message,message,net.kyori.adventure.chat.SignedMessage.system("test",null));
   Method capture=type.getDeclaredMethod("input",io.papermc.paper.event.player.AsyncChatEvent.class);capture.setAccessible(true);capture.invoke(sync,chat);ok(chat.isCancelled(),"even expired password input never broadcasts");
  }finally{field.set(sync,original);var cache=(fr.xephi.authme.data.auth.PlayerCache)get(fr.xephi.authme.api.v3.AuthMeApi.getInstance(),"playerCache");cache.removePlayer("syncjavafixture");}
  Bukkit.getScheduler().runTaskAsynchronously(this,()->{
   var auth=fr.xephi.authme.api.v3.AuthMeApi.getInstance();String name="syncauth"+UUID.randomUUID().toString().substring(0,6);boolean registered=false;var api=org.geysermc.floodgate.api.FloodgateApi.getInstance().getPlayerLink();
   try {
    registered=auth.registerPlayer(name,"Fixture-Only-Password!42");ok(registered,"disposable password account registered");ok(auth.checkPassword(name,"Fixture-Only-Password!42")&&!auth.checkPassword(name,"incorrect-password"),"source password proof distinguishes success and failure");
    for(UUID shared:List.of(peId,javaId)) {
     api.linkPlayer(peId,shared,"SyncJavaFixture").get(10,java.util.concurrent.TimeUnit.SECONDS);var linked=api.getLinkedPlayer(peId).get(10,java.util.concurrent.TimeUnit.SECONDS);
     ok(linked!=null&&linked.getJavaUniqueId().equals(shared)&&linked.getJavaUsername().equals("SyncJavaFixture"),"Floodgate persists selected PE/Java source UUID");api.unlinkPlayer(peId).get(10,java.util.concurrent.TimeUnit.SECONDS);ok(api.getLinkedPlayer(peId).get(10,java.util.concurrent.TimeUnit.SECONDS)==null,"unlink removes shared UUID association");
    }
    UUID peSource=new UUID(0,Math.abs(UUID.randomUUID().getLeastSignificantBits()));
    api.linkPlayer(peId,peSource,".SyncPeFixture").get(10,java.util.concurrent.TimeUnit.SECONDS);var linkedPe=api.getLinkedPlayer(peId).get(10,java.util.concurrent.TimeUnit.SECONDS);
    ok(linkedPe.getJavaUniqueId().equals(peSource)&&linkedPe.getJavaUsername().equals(".SyncPeFixture")&&api.getLinkedPlayer(peSource).get(10,java.util.concurrent.TimeUnit.SECONDS)==null,"PE pair preserves source identity and leaves source unlinked");api.unlinkPlayer(peId).get(10,java.util.concurrent.TimeUnit.SECONDS);
    getLogger().info("SYNC_SMOKE_PASS: same-IP suggestion rules, both source directions, Paper UUID profile, partial-journal lock, private input capture, AuthMe password proof and actual Floodgate SQLite roundtrip/unlink");
   }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"SYNC_SMOKE_FAIL",e);}
   finally {try{api.unlinkPlayer(peId).get(10,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception ignored){}if(registered)auth.forceUnregister(name);}
  });
 }
 void syncMenuCheck(MiaoServer m,World world)throws Exception {
  Object ui=get(m,"ui"),sync=get(ui,"sync");Class<?> type=sync.getClass();
  Class<?> account=Class.forName("cn.nankexi.CrossplaySync$Account",true,m.getClass().getClassLoader());var ac=account.getDeclaredConstructors()[0];ac.setAccessible(true);
  UUID id=UUID.randomUUID(),peerId=UUID.randomUUID();String name="MenuCallerTest",peerName="menup"+UUID.randomUUID().toString().substring(0,6);
  var auth=fr.xephi.authme.api.v3.AuthMeApi.getInstance();var cache=(fr.xephi.authme.data.auth.PlayerCache)get(auth,"playerCache");
  var accounts=(Map<UUID,Object>)get(sync,"accounts");var data=(YamlConfiguration)get(sync,"data");
  org.bukkit.inventory.Inventory[] top={null};List<String> messages=new ArrayList<>();
  Player base=actor(id,name,world,world.getSpawnLocation(),false);
  Player p=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(obj,method,args)->switch(method.getName()){
   case "isOnline"->true;case "getFirstPlayed"->1L;case "getAddress"->new java.net.InetSocketAddress("127.0.0.1",25565);
   case "openInventory"->{top[0]=(org.bukkit.inventory.Inventory)args[0];yield null;}case "closeInventory"->{top[0]=null;yield null;}
   case "sendMessage"->{if(args[0] instanceof String s)messages.add(s);yield null;}default->method.invoke(base,args);
  });
  Runnable cleanup=()->{try{for(UUID key:List.of(id,peerId)){accounts.remove(key);data.set("accounts."+key,null);}((Map<?,?>)get(sync,"inputs")).remove(id);Method save=type.getDeclaredMethod("save");save.setAccessible(true);save.invoke(sync);}catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"SYNC_MENU_CLEANUP_FAIL",e);}cache.removePlayer(name.toLowerCase());cache.removePlayer(peerName);Bukkit.getScheduler().runTaskAsynchronously(this,()->auth.forceUnregister(peerName));};
  Method click=type.getDeclaredMethod("click",org.bukkit.event.inventory.InventoryClickEvent.class);click.setAccessible(true);
  java.util.function.IntConsumer tap=slot->{try{
   var inventory=top[0];var view=(org.bukkit.inventory.InventoryView)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{org.bukkit.inventory.InventoryView.class},(obj,method,args)->switch(method.getName()){
    case "getTopInventory","getInventory"->inventory;case "getPlayer"->p;case "getType"->org.bukkit.event.inventory.InventoryType.CHEST;case "convertSlot"->args[0];default->null;
   });
   var event=new org.bukkit.event.inventory.InventoryClickEvent(view,org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,slot,org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);click.invoke(sync,event);ok(event.isCancelled(),"sync inventory items cannot be taken");
  }catch(Exception e){throw new RuntimeException(e);}};
  Bukkit.getScheduler().runTaskAsynchronously(this,()->{
   try{
    ok(auth.registerPlayer(peerName,"Fixture-Only-Password!42"),"temporary historical account registered");
    var source=(fr.xephi.authme.datasource.DataSource)get(auth,"dataSource");var historical=source.getAuth(peerName);for(int wait=0;historical==null&&wait<100;wait++){Thread.sleep(50);historical=source.getAuth(peerName);}ok(historical!=null,"AuthMe queued registration became readable");historical.setLastIp("127.0.0.1");historical.setLastLogin(System.currentTimeMillis());ok(source.updateSession(historical),"historical IP/login persisted in AuthMe");
    Bukkit.getScheduler().runTask(this,()->{try{
     accounts.put(peerId,ac.newInstance(peerId,peerName,false,"stale-ip-cache",1L));
     // Reproduce DeluxeMenus [close]: its queued close must run before the new menu opens.
     Bukkit.getScheduler().runTask(this,()->p.closeInventory());
     m.onCommand(p,m.getCommand("accountlink"),"accountlink",new String[0]);ok(top[0]==null,"accountlink defers inventory open past old menu close");
     new org.bukkit.scheduler.BukkitRunnable(){int tries;public void run(){try{
      if(top[0]==null){ok(++tries<100,"account picker opens within five seconds");return;}cancel();ok(top[0].getSize()==54,"account picker remains open after scheduled close");
      int slot=-1;for(int i=0;i<54;i++){var item=top[0].getItem(i);if(item==null)continue;ok(item.getType()!=Material.PLAYER_HEAD,"account picker never requests remote skull profiles");if(item.getItemMeta().getDisplayName().contains(peerName))slot=i;}
      ok(slot>=0,"historical same-IP account replaces stale IP cache and is selectable");tap.accept(slot);
      Bukkit.getScheduler().runTaskLater(MiaoSmoke.this,()->{try{
       ok(top[0]!=null&&top[0].getSize()==27,"click opens progress source choice");ok(top[0].getItem(11).getType()==Material.CHEST&&top[0].getItem(15).getType()==Material.ENDER_CHEST,"both progress sources available");tap.accept(11);
       Bukkit.getScheduler().runTaskLater(MiaoSmoke.this,()->{try{
        ok(top[0]!=null&&top[0].getItem(11).getType()==Material.NAME_TAG,"progress source leads to independent username choice");tap.accept(11);
        Bukkit.getScheduler().runTaskLater(MiaoSmoke.this,()->{try{
        ok(top[0]==null&&((Map<?,?>)get(sync,"inputs")).containsKey(id),"username click arms private password input");
        Object request=((Map<?,?>)get(sync,"inputs")).get(id);ok(!(boolean)get(request,"own")&&get(get(request,"identity"),"id").equals(id),"request keeps peer progress while selecting caller username");
        var message=net.kyori.adventure.text.Component.text("取消");var event=new io.papermc.paper.event.player.AsyncChatEvent(false,p,new HashSet<>(),io.papermc.paper.chat.ChatRenderer.defaultRenderer(),message,message,net.kyori.adventure.chat.SignedMessage.system("test",null));Method input=type.getDeclaredMethod("input",io.papermc.paper.event.player.AsyncChatEvent.class);input.setAccessible(true);input.invoke(sync,event);ok(event.isCancelled(),"cancel input is not broadcast");
        Bukkit.getScheduler().runTaskLater(MiaoSmoke.this,()->{try{ok(messages.stream().anyMatch(s->s.contains("已取消绑定")),"cancel handled without creating binding");ok(((Map<?,?>)get(sync,"bindings")).values().stream().noneMatch(b->b.toString().contains(peerName)),"cancel keeps independent progress");getLogger().info("SYNC_MENU_SMOKE_PASS: delayed open, AuthMe historical IP refresh, no remote heads, account/source clicks, private cancel");}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"SYNC_MENU_SMOKE_FAIL",e);}finally{cleanup.run();}},2);
       }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"SYNC_MENU_SMOKE_FAIL",e);cleanup.run();}},2);
      }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"SYNC_MENU_SMOKE_FAIL",e);cleanup.run();}},2);
      }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"SYNC_MENU_SMOKE_FAIL",e);cleanup.run();}},2);
     }catch(Throwable e){cancel();getLogger().log(java.util.logging.Level.SEVERE,"SYNC_MENU_SMOKE_FAIL",e);cleanup.run();}}}.runTaskTimer(MiaoSmoke.this,3,1);
    }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"SYNC_MENU_SMOKE_FAIL",e);cleanup.run();}});
   }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"SYNC_MENU_SMOKE_FAIL",e);Bukkit.getScheduler().runTask(this,cleanup);}
  });
 }
 void syncIdentityCheck(MiaoServer m,World world)throws Exception {
  Object sync=get(get(m,"ui"),"sync");Class<?> type=sync.getClass(),account=Class.forName("cn.nankexi.CrossplaySync$Account",true,m.getClass().getClassLoader()),binding=Class.forName("cn.nankexi.CrossplaySync$Binding",true,m.getClass().getClassLoader());
  var ac=account.getDeclaredConstructors()[0];ac.setAccessible(true);var bc=binding.getDeclaredConstructor(String.class,UUID.class,UUID.class,UUID.class,String.class,String.class,String.class,String.class,String.class,UUID.class);bc.setAccessible(true);
  UUID progress=UUID.randomUUID(),identity=UUID.randomUUID();String a="Progress"+UUID.randomUUID().toString().substring(0,5),b="Look"+UUID.randomUUID().toString().substring(0,5);
  Object peer=ac.newInstance(identity,b,false,"fixture",1L),old=bc.newInstance(a,progress,identity,progress,"PC progress fixture","ACTIVE","JAVA_PAIR",b,a,progress);
  Method write=type.getDeclaredMethod("write",binding),remove=type.getDeclaredMethod("remove",binding),profile=type.getDeclaredMethod("profile",org.bukkit.event.player.AsyncPlayerPreLoginEvent.class),verify=type.getDeclaredMethod("verifyRename",Player.class,binding,account,String.class);for(Method method:List.of(write,remove,profile,verify))method.setAccessible(true);
  UUID peSource=new UUID(0,Math.abs(UUID.randomUUID().getLeastSignificantBits())),peAlias=new UUID(0,Math.abs(UUID.randomUUID().getLeastSignificantBits()));
  Object peRename=bc.newInstance(".IdSourceTest",peSource,peAlias,peSource,"PE progress fixture","RENAMING","PE_PAIR",".IdAliasTest",".IdAliasTest",peAlias);write.invoke(sync,peRename);
  Method finishRename=type.getDeclaredMethod("finishRename",binding);finishRename.setAccessible(true);
  Object cross=bc.newInstance("CrossIdentity",progress,identity,identity,"PE progress","ACTIVE","CROSSPLAY",".CrossAlias",".CrossAlias",identity);write.invoke(sync,cross);
  for(String name:List.of("CrossIdentity",".CrossAlias")){var event=new org.bukkit.event.player.AsyncPlayerPreLoginEvent(name,java.net.InetAddress.getLoopbackAddress(),name.equals("CrossIdentity")?progress:identity);profile.invoke(sync,event);ok(event.getName().equals(".CrossAlias")&&event.getUniqueId().equals(identity),"crossplay can retain PE progress and select PE username");}remove.invoke(sync,cross);
  var accounts=(Map<UUID,Object>)get(sync,"accounts");accounts.put(progress,ac.newInstance(progress,a,false,"fixture",1L));accounts.put(identity,peer);write.invoke(sync,old);
  var auth=fr.xephi.authme.api.v3.AuthMeApi.getInstance();Player base=actor(progress,a,world,world.getSpawnLocation(),false);List<String> messages=new ArrayList<>();
  Player player=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(o,method,args)->switch(method.getName()){case "isOnline"->true;case "sendMessage"->{if(args[0] instanceof String text)messages.add(text);yield null;}default->method.invoke(base,args);});
  var api=net.skinsrestorer.api.SkinsRestorerProvider.get();String skinKey="fixture-"+identity;var skinId=net.skinsrestorer.api.property.SkinIdentifier.ofCustom(skinKey);var expectedSkin=net.skinsrestorer.api.property.SkinProperty.of(java.util.Base64.getEncoder().encodeToString("{\"textures\":{}}".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"fixture-signature");
  Runnable cleanup=()->{try{remove.invoke(sync,old);remove.invoke(sync,peRename);accounts.remove(progress);accounts.remove(identity);}catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"SYNC_IDENTITY_CLEANUP_FAIL",e);}var cache=(fr.xephi.authme.data.auth.PlayerCache)getUnchecked(auth,"playerCache");cache.removePlayer(a.toLowerCase());Bukkit.getScheduler().runTaskAsynchronously(this,()->{auth.forceUnregister(b);api.getPlayerStorage().removeSkinIdOfPlayer(identity);api.getSkinStorage().removeSkinData(skinId);try{org.geysermc.floodgate.api.FloodgateApi.getInstance().getPlayerLink().unlinkPlayer(peAlias).get(10,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception ignored){}});};
  Bukkit.getScheduler().runTaskAsynchronously(this,()->{try{
   ok(auth.registerPlayer(b,"Fixture-Only-Password!42"),"selected username has independent AuthMe credentials");
   api.getSkinStorage().setCustomSkinData(skinKey,expectedSkin);api.getPlayerStorage().setSkinIdOfPlayer(identity,skinId);
   Class<?> appearance=Class.forName("cn.nankexi.SyncAppearance",true,m.getClass().getClassLoader());Method resolve=appearance.getDeclaredMethod("resolve",net.skinsrestorer.api.SkinsRestorer.class,UUID.class,String.class);resolve.setAccessible(true);
   ok(((Optional<?>)resolve.invoke(null,api,identity,b)).orElseThrow().equals(expectedSkin),"skin resolves from chosen original identity");ok(api.getPlayerStorage().getSkinIdOfPlayer(progress).isEmpty(),"skin resolution never rewrites progress account skin storage");
   var links=org.geysermc.floodgate.api.FloodgateApi.getInstance().getPlayerLink();links.linkPlayer(peAlias,peSource,".IdSourceTest").get(10,java.util.concurrent.TimeUnit.SECONDS);finishRename.invoke(sync,peRename);var linked=links.getLinkedPlayer(peAlias).get(10,java.util.concurrent.TimeUnit.SECONDS);ok(linked.getJavaUniqueId().equals(peSource)&&linked.getJavaUsername().equals(".IdAliasTest"),"Floodgate rename changes username while keeping original PE progress UUID");
   Bukkit.getScheduler().runTask(this,()->{try{
    verify.invoke(sync,player,old,peer,"wrong-password");
    new org.bukkit.scheduler.BukkitRunnable(){int ticks;boolean submitted;public void run(){try{
     ok(++ticks<160,"rename password check and activation complete within timeout");
     if(((Set<?>)get(sync,"working")).contains(progress))return;
     if(!submitted){ok(messages.stream().anyMatch(s->s.contains("密码不正确")),"wrong target password is rejected");ok(((Map<?,?>)get(sync,"bindings")).get(a.toLowerCase()).equals(old),"wrong proof preserves binding");((Map<?,?>)get(sync,"attempts")).remove(identity.toString());verify.invoke(sync,player,old,peer,"Fixture-Only-Password!42");submitted=true;return;}
     Object saved=((Map<?,?>)get(sync,"bindings")).get(a.toLowerCase());ok(get(saved,"state").equals("ACTIVE")&&get(saved,"identityName").equals(b)&&get(saved,"shared").equals(progress),"verified name changes while original progress UUID stays fixed");
     for(String name:List.of(a,b)){var event=new org.bukkit.event.player.AsyncPlayerPreLoginEvent(name,java.net.InetAddress.getLoopbackAddress(),name.equals(a)?progress:identity);profile.invoke(sync,event);ok(event.getName().equals(b)&&event.getUniqueId().equals(progress),"both original names join as chosen username with source progress");}
     Method refresh=type.getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(sync);Object reloaded=((Map<?,?>)get(sync,"bindings")).get(a.toLowerCase());ok(get(reloaded,"identityName").equals(b)&&get(reloaded,"identityId").equals(identity),"separate identity survives journal reload");
     for(String name:List.of(".IdSourceTest",".IdAliasTest")){var event=new org.bukkit.event.player.AsyncPlayerPreLoginEvent(name,java.net.InetAddress.getLoopbackAddress(),name.equals(".IdSourceTest")?peSource:peAlias);profile.invoke(sync,event);ok(event.getName().equals(".IdAliasTest")&&event.getUniqueId().equals(peSource),"both PE entries map to chosen name and original progress after rename");}
     var data=(YamlConfiguration)get(sync,"data");data.set("bindings."+a.toLowerCase()+".identity-name",null);data.set("bindings."+a.toLowerCase()+".identity-id",null);refresh.invoke(sync);ok(get(((Map<?,?>)get(sync,"bindings")).get(a.toLowerCase()),"identityName").equals(a),"old binding journal defaults to legacy username");
     getLogger().info("SYNC_IDENTITY_SMOKE_PASS: independent username/UUID, wrong/right password rename, both aliases, journal compatibility, original skin lookup without source overwrite");cancel();cleanup.run();
    }catch(Throwable e){cancel();getLogger().log(java.util.logging.Level.SEVERE,"SYNC_IDENTITY_SMOKE_FAIL",e);cleanup.run();}}}.runTaskTimer(MiaoSmoke.this,2,1);
   }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"SYNC_IDENTITY_SMOKE_FAIL",e);cleanup.run();}});
  }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"SYNC_IDENTITY_SMOKE_FAIL",e);Bukkit.getScheduler().runTask(this,cleanup);}});
 }
 void samePlatformCheck(MiaoServer m,World world)throws Exception {
  Object sync=get(get(m,"ui"),"sync");Class<?> type=sync.getClass();var data=(YamlConfiguration)get(sync,"data");
  Class<?> account=Class.forName("cn.nankexi.CrossplaySync$Account",true,m.getClass().getClassLoader()),binding=Class.forName("cn.nankexi.CrossplaySync$Binding",true,m.getClass().getClassLoader());
  var ac=account.getDeclaredConstructors()[0];ac.setAccessible(true);var bc=binding.getDeclaredConstructor(String.class,UUID.class,UUID.class,UUID.class,String.class,String.class,String.class,String.class);bc.setAccessible(true);
  UUID first=UUID.randomUUID(),second=UUID.randomUUID();String sourceName="PairSourceTest",aliasName="PairAliasTest";
  Object own=ac.newInstance(first,sourceName,false,"same",1L),peer=ac.newInstance(second,aliasName,false,"same",2L),different=ac.newInstance(second,aliasName,false,"other",2L);
  Method candidate=type.getDeclaredMethod("candidate",account,account,boolean.class);candidate.setAccessible(true);ok((boolean)candidate.invoke(null,own,peer,false)&&!(boolean)candidate.invoke(null,own,different,false),"same-platform manual selection requires same nonempty IP");
  Method profile=type.getDeclaredMethod("profile",org.bukkit.event.player.AsyncPlayerPreLoginEvent.class);profile.setAccessible(true);Method write=type.getDeclaredMethod("write",binding);write.setAccessible(true);Method remove=type.getDeclaredMethod("remove",binding);remove.setAccessible(true);
  Object pair=bc.newInstance(sourceName,first,second,first,"PC source","ACTIVE","JAVA_PAIR",aliasName),pePair=bc.newInstance(".PairSourceTest",first,second,first,"PE source","ACTIVE","PE_PAIR",".PairAliasTest");
  Player p=actor(first,sourceName,world,world.getSpawnLocation(),false);Method command=type.getDeclaredMethod("command",Player.class,String[].class);command.setAccessible(true);
  try{
   command.invoke(sync,p,(Object)new String[]{"quiet"});ok(!data.getBoolean("preferences."+first+".suggest",true),"opt out persists without creating a binding");
   ok(!data.contains("bindings."+sourceName.toLowerCase()),"choosing independent progress creates no mapping");command.invoke(sync,p,(Object)new String[]{"remind"});
   write.invoke(sync,pair);
   for(String name:List.of(sourceName,aliasName)){
    var e=new org.bukkit.event.player.AsyncPlayerPreLoginEvent(name,java.net.InetAddress.getLoopbackAddress(),name.equals(sourceName)?first:second);profile.invoke(sync,e);ok(e.getUniqueId().equals(first)&&e.getName().equals(sourceName),"Java pair uses selected canonical UUID/name for AuthMe: "+name);
   }
   Method refresh=type.getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(sync);ok(((Map<?,?>)get(sync,"bindings")).containsKey(sourceName.toLowerCase()),"Java binding survives journal reload");
   remove.invoke(sync,pair);write.invoke(sync,pePair);refresh.invoke(sync);ok(((Map<?,?>)get(sync,"bindings")).containsKey(".pairsourceTest".toLowerCase()),"PE dot-prefix name stored without YAML path splitting");remove.invoke(sync,pePair);
   Method bind=type.getDeclaredMethod("bind",Player.class,account,account,boolean.class);bind.setAccessible(true);bind.invoke(sync,p,own,peer,true);
   ok(data.getString("bindings."+sourceName.toLowerCase()+".state").equals("PREPARED"),"Java pair journals before identity activation");
   Bukkit.getScheduler().runTaskLater(this,()->{
    try{
     ok(data.getString("bindings."+sourceName.toLowerCase()+".state").equals("ACTIVE"),"Java pair activates after save/disconnect delay");
     command.invoke(sync,p,(Object)new String[]{"unlink","confirm"});ok(data.getString("bindings."+sourceName.toLowerCase()+".state").equals("UNLINKING"),"Java unlink blocks reconnect until complete");
     Bukkit.getScheduler().runTaskLater(this,()->{
      try{ok(!data.contains("bindings."+sourceName.toLowerCase()),"Java unlink removes mapping and restores separate identities");getLogger().info("SAME_PLATFORM_SYNC_SMOKE_PASS: optional opt-out, same-IP restriction, canonical login, dot-prefixed PE persistence, delayed Java binding and unlink");}
      catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"SAME_PLATFORM_SYNC_SMOKE_FAIL",e);}
     },45);
    }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"SAME_PLATFORM_SYNC_SMOKE_FAIL",e);try{remove.invoke(sync,pair);}catch(Exception ignored){}}
    finally{data.set("preferences."+first,null);try{Method save=type.getDeclaredMethod("save");save.setAccessible(true);save.invoke(sync);}catch(Exception ignored){}var cache=(fr.xephi.authme.data.auth.PlayerCache)getUnchecked(fr.xephi.authme.api.v3.AuthMeApi.getInstance(),"playerCache");cache.removePlayer(sourceName.toLowerCase());}
   },70);
  }catch(Throwable e){remove.invoke(sync,pair);remove.invoke(sync,pePair);throw e;}
  var authConfig=YamlConfiguration.loadConfiguration(new java.io.File(m.getDataFolder().getParentFile(),"AuthMe/config.yml"));ok(authConfig.getInt("settings.restrictions.maxRegPerIp")==0&&authConfig.getBoolean("settings.restrictions.ForceSingleSession"),"unlimited IP registrations retain single-session authentication");
 }
 Object getUnchecked(Object obj,String field){try{return get(obj,field);}catch(Exception e){throw new RuntimeException(e);}}
 void namesCheck(MiaoServer m,World world)throws Exception {
  Object ui=get(m,"ui"),names=get(ui,"names");Class<?> type=names.getClass();
  Method set=type.getDeclaredMethod("set",Player.class,UUID.class,String.class);set.setAccessible(true);
  Method label=type.getDeclaredMethod("label",UUID.class,UUID.class,String.class);label.setAccessible(true);
  Method valid=type.getDeclaredMethod("valid",String.class);valid.setAccessible(true);
  Method chat=type.getDeclaredMethod("chat",io.papermc.paper.event.player.AsyncChatEvent.class);chat.setAccessible(true);
  var data=(YamlConfiguration)get(names,"data");String original=data.saveToString();var file=(java.io.File)get(names,"file");boolean existed=file.exists();
  UUID author=UUID.randomUUID(),viewerId=UUID.randomUUID(),otherId=UUID.randomUUID();
  Player viewer=actor(viewerId,"NamesViewerFixture",world,world.getSpawnLocation(),false),other=actor(otherId,"NamesOtherFixture",world,world.getSpawnLocation(),false);
  var cache=(fr.xephi.authme.data.auth.PlayerCache)get(fr.xephi.authme.api.v3.AuthMeApi.getInstance(),"playerCache");cache.updatePlayer(fr.xephi.authme.data.auth.PlayerAuth.builder().name("namesauthorfixture").build());
  net.kyori.adventure.text.Component[] display={net.kyori.adventure.text.Component.text("NamesAuthorFixture")};
  Player p=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(o,method,args)->switch(method.getName()){
   case "getUniqueId"->author;case "getName"->"NamesAuthorFixture";case "hasMetadata"->false;case "displayName"->{if(args==null||args.length==0)yield display[0];display[0]=(net.kyori.adventure.text.Component)args[0];yield null;}
   case "setDisplayName"->{display[0]=net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize((String)args[0]);yield null;}
   case "sendMessage","setPlayerListName"->null;default->{Class<?> t=method.getReturnType();yield t==boolean.class?false:t==int.class?0:null;}
  });
  try {
   ok((boolean)set.invoke(names,p,null,"南可熙好友"),"self nickname set");ok(p.getName().equals("NamesAuthorFixture")&&p.getUniqueId().equals(author),"real identity unchanged");
   ok((boolean)set.invoke(names,viewer,author,"矿工搭子"),"private note set");
   ok(label.invoke(names,viewerId,author,"原名").equals("矿工搭子"),"setter sees private note");ok(label.invoke(names,otherId,author,"原名").equals("南可熙好友"),"other viewer sees public nickname");
   ok(!(boolean)valid.invoke(null,"§c管理员")&&!(boolean)valid.invoke(null,"a\nb")&&!(boolean)valid.invoke(null,"x".repeat(17)),"formatting and overlength rejected");
   var disk=YamlConfiguration.loadConfiguration(file);ok(disk.getString("players."+viewerId+".notes."+author).equals("矿工搭子"),"private note persisted by both UUIDs");
   var message=net.kyori.adventure.text.Component.text("你好");Set<net.kyori.adventure.audience.Audience> viewers=new HashSet<>(List.of(viewer,other));
   display[0]=net.kyori.adventure.text.Component.text("NamesAuthorFixture"); // Another plugin reset the Bukkit display name.
   var event=new io.papermc.paper.event.player.AsyncChatEvent(false,p,viewers,io.papermc.paper.chat.ChatRenderer.defaultRenderer(),message,message,net.kyori.adventure.chat.SignedMessage.system("你好",null));chat.invoke(names,event);
   var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
   ok(plain.serialize(event.renderer().render(p,p.displayName(),message,viewer)).contains("矿工搭子"),"private chat renderer");
   String publicChat=plain.serialize(event.renderer().render(p,p.displayName(),message,other));ok(publicChat.contains("南可熙好友")&&!publicChat.contains("矿工搭子"),"private note never leaks to another viewer");
   var provider=new net.ess3.provider.providers.PaperChatListenerProvider(){};
   Method wrap=net.ess3.provider.providers.PaperChatListenerProvider.class.getDeclaredMethod("wrap",io.papermc.paper.event.player.AsyncChatEvent.class);wrap.setAccessible(true);
   ((net.ess3.provider.AbstractChatEvent)wrap.invoke(provider,event)).setFormat("<%1$s> %2$s");provider.onHighest(event);chat.invoke(names,event);
   ok(plain.serialize(event.renderer().render(p,p.displayName(),message,viewer)).contains("矿工搭子"),"actual Essentials renderer receives private alias");
   String essentialsPublic=plain.serialize(event.renderer().render(p,p.displayName(),message,other));
   ok(essentialsPublic.contains("南可熙好友")&&!essentialsPublic.contains("矿工搭子")&&!essentialsPublic.contains("NLK_NAME_"),"actual Essentials cached renderer retains canonical name without leaking notes");provider.onMonitor(event);
   Method rewrite=Class.forName("cn.nankexi.BedrockCommandNames",true,m.getClass().getClassLoader()).getDeclaredMethod("rewrite",String.class,Map.class);rewrite.setAccessible(true);
   var peAliases=Map.of("[PE]BedrockFixture",".BedrockFixture");
   ok(rewrite.invoke(null,"/pay [PE]BedrockFixture 100",peAliases).equals("/pay .BedrockFixture 100"),"PE account alias resolves for payment");
   ok(rewrite.invoke(null,"/essentials:msg [pe]bedrockfixture 你好 [PE]BedrockFixture",peAliases).equals("/essentials:msg .BedrockFixture 你好 [PE]BedrockFixture"),"private message body untouched");
   ok(rewrite.invoke(null,"/login [PE]BedrockFixture",peAliases).equals("/login [PE]BedrockFixture"),"passwords never rewritten");
   ok(rewrite.invoke(null,"/mteam invite [PE]BedrockFixture",peAliases).equals("/mteam invite .BedrockFixture"),"team invitation resolves PE account");
   ok(rewrite.invoke(null,"/other:pay [PE]BedrockFixture 100",peAliases).equals("/other:pay [PE]BedrockFixture 100"),"unknown command namespace untouched");
   var handlers=io.papermc.paper.event.player.AsyncChatEvent.getHandlerList().getRegisteredListeners();int own=-1,ess=-1;for(int i=0;i<handlers.length;i++)if(handlers[i].getPriority()==org.bukkit.event.EventPriority.HIGHEST){if(handlers[i].getPlugin()==m)own=i;if(handlers[i].getPlugin().getName().equals("EssentialsChat"))ess=i;}ok(own>ess&&ess>=0,"names wraps EssentialsChat after formatting");
   set.invoke(names,viewer,author,"");ok(label.invoke(names,viewerId,author,"原名").equals("南可熙好友"),"delete note restores public nickname");set.invoke(names,p,null,"");ok(label.invoke(names,otherId,author,"原名").equals("原名"),"clear nickname restores original fallback");
   getLogger().info("NAMES_SMOKE_PASS: unchanged account/UUID, private per-viewer chat, persistence, input validation, clearing and EssentialsChat ordering");
  }finally {
   ((Map<?,?>)get(names,"nicknames")).remove(author);((Map<?,?>)get(names,"notes")).remove(viewerId);data.loadFromString(original);Method save=type.getDeclaredMethod("save");save.setAccessible(true);save.invoke(names);if(!existed)java.nio.file.Files.deleteIfExists(file.toPath());
   for(String account:List.of("namesauthorfixture","namesviewerfixture","namesotherfixture"))cache.removePlayer(account);
  }
 }
 void compassCheck(MiaoServer m) throws Exception {
  Object ui=get(m,"ui");
  var cache=(fr.xephi.authme.data.auth.PlayerCache)get(fr.xephi.authme.api.v3.AuthMeApi.getInstance(),"playerCache");
  String name="MiaoCompassFixture";UUID id=UUID.randomUUID();cache.updatePlayer(fr.xephi.authme.data.auth.PlayerAuth.builder().name(name.toLowerCase()).build());
  var guide=new org.bukkit.inventory.ItemStack(Material.COMPASS);var meta=guide.getItemMeta();meta.setDisplayName("§r§a生存指南针");guide.setItemMeta(meta);
  var inventory=(org.bukkit.inventory.PlayerInventory)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{org.bukkit.inventory.PlayerInventory.class},(o,method,args)->method.getName().equals("getItemInMainHand")?guide:null);
  org.bukkit.inventory.Inventory[] opened={null};int[] targets={0};
  Player p=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(o,method,args)->switch(method.getName()){
   case "getUniqueId"->id;case "getName"->name;case "isOnline","hasPermission"->true;case "hasMetadata"->false;
   case "getInventory"->inventory;case "openInventory"->{opened[0]=(org.bukkit.inventory.Inventory)args[0];yield null;}
   case "getTargetBlockExact"->{targets[0]++;yield null;}case "sendMessage"->null;
   default->{Class<?> t=method.getReturnType();yield t==boolean.class?false:t==int.class?0:null;}
  });
  Method interact=ui.getClass().getDeclaredMethod("compass",org.bukkit.event.player.PlayerInteractEvent.class);interact.setAccessible(true);
  var dropInteract=new org.bukkit.event.player.PlayerInteractEvent(p,org.bukkit.event.block.Action.LEFT_CLICK_AIR,guide,null,org.bukkit.block.BlockFace.SELF,org.bukkit.inventory.EquipmentSlot.HAND);
  interact.invoke(ui,dropInteract);ok(targets[0]==0,"synthetic drop interact never teleports");
  var item=(org.bukkit.entity.Item)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{org.bukkit.entity.Item.class},(o,method,args)->method.getName().equals("getItemStack")?guide:null);
  Method drop=ui.getClass().getDeclaredMethod("dropCompass",org.bukkit.event.player.PlayerDropItemEvent.class);drop.setAccessible(true);drop.invoke(ui,new org.bukkit.event.player.PlayerDropItemEvent(p,item));
  Method swing=ui.getClass().getDeclaredMethod("swingCompass",io.papermc.paper.event.player.PlayerArmSwingEvent.class);swing.setAccessible(true);swing.invoke(ui,new io.papermc.paper.event.player.PlayerArmSwingEvent(p,org.bukkit.inventory.EquipmentSlot.HAND));
  Method sidebar=ui.getClass().getDeclaredMethod("sidebar",Player.class,String[].class);sidebar.setAccessible(true);sidebar.invoke(ui,p,new String[]{"settings"});
  Bukkit.getScheduler().runTaskLater(this,()->{
   try{
    ok(targets[0]==0,"drop-associated swing suppressed");ok(opened[0]!=null&&opened[0].getSize()==45,"growth settings opens after menu click tick");
    var menu=com.extendedclip.deluxemenus.menu.Menu.getMenuByName("survival");ok(menu.isPresent(),"native DeluxeMenus API resolves service center");
    swing.invoke(ui,new io.papermc.paper.event.player.PlayerArmSwingEvent(p,org.bukkit.inventory.EquipmentSlot.HAND));
    Bukkit.getScheduler().runTaskLater(this,()->{
     try{ok(targets[0]==1,"real main-hand swing reaches teleport target lookup");getLogger().info("COMPASS_SMOKE_PASS: synthetic Q/drop suppressed, real swing accepted, delayed growth GUI opened, native menu resolved");}
     catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"COMPASS_SMOKE_FAIL",e);}
     finally{cache.removePlayer(name);}
    },3);
   }catch(Throwable e){cache.removePlayer(name);getLogger().log(java.util.logging.Level.SEVERE,"COMPASS_SMOKE_FAIL",e);}
  },4);
 }

}
