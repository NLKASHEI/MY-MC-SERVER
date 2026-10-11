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
 public void onEnable(){Bukkit.getScheduler().runTaskLater(this,this::check,40);}
 Object get(Object o,String field) throws Exception {Field f=o.getClass().getDeclaredField(field);f.setAccessible(true);return f.get(o);}
 void ok(boolean b,String s){if(!b)throw new AssertionError(s);}
 void check(){
  MiaoServer m=(MiaoServer)Bukkit.getPluginManager().getPlugin("MiaoServer");
  Claim claim=null;YamlConfiguration original=null;Field f=null;
  try{
   f=MiaoServer.class.getDeclaredField("data");f.setAccessible(true);original=(YamlConfiguration)f.get(m);
   YamlConfiguration test=new YamlConfiguration();f.set(m,test);
   UUID owner=UUID.randomUUID(), member=UUID.randomUUID(), manual=UUID.randomUUID();
   World w=Bukkit.getWorld("world");
   claim=GriefPrevention.instance.dataStore.createClaim(w,1000000,1000010,-60,319,1000000,1000010,owner,null,null,null).claim;
   ok(claim!=null,"fixture claim");
   claim.setPermission(member.toString(),ClaimPermission.Container);
   claim.setPermission(manual.toString(),ClaimPermission.Build);
   var location=new Location(w,1000005,70,1000005);Scoreboard[] board={Bukkit.getScoreboardManager().getMainScoreboard()};
   Player p=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(obj,method,a)->switch(method.getName()){
     case "getUniqueId"->owner;case "getName"->"MiaoSmokeOwner";case "getLocation"->location;
     case "getWorld"->w;case "getScoreboard"->board[0];case "setScoreboard"->{board[0]=(Scoreboard)a[0];yield null;}
     case "getStatistic"->72000;case "getPing"->42;case "hasMetadata"->false;case "isOp"->true;
     case "sendMessage"->null;case "hashCode"->owner.hashCode();case "equals"->obj==a[0];
     default->{Class<?> c=method.getReturnType();yield c==boolean.class?false:c==int.class?0:null;}
   });
   m.onCommand(p,m.getCommand("mteam"),"team",new String[]{"create","温馨伙伴"});
   String team=test.getConfigurationSection("teams").getKeys(false).iterator().next();
   test.set("teams."+team+".members",List.of(owner.toString(),member.toString(),manual.toString()));
   m.onCommand(p,m.getCommand("mteam"),"team",new String[]{"share"});
   ok(claim.getPermission(member.toString())==ClaimPermission.Build,"grant build");
   Method sync=MiaoServer.class.getDeclaredMethod("reconcile");sync.setAccessible(true);
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
  finally{try{if(claim!=null)GriefPrevention.instance.dataStore.deleteClaim(claim);if(original!=null){f.set(m,original);Method save=MiaoServer.class.getDeclaredMethod("save");save.setAccessible(true);save.invoke(m);}}catch(Exception e){throw new RuntimeException(e);}}
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
