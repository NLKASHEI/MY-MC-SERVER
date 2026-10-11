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
   ok(board[0].getTeam("row3").getPrefix().contains("42 ms"),"ping sidebar");
   ok(board[0].getTeam("row4").getPrefix().contains("1小时0分"),"play time sidebar");
   ok(board[0].getObjective(DisplaySlot.SIDEBAR).getDisplayName().contains("南可熙的神奇妙妙服"),"Chinese server title");
   Object ui=get(m,"ui");YamlConfiguration preferences=(YamlConfiguration)get(ui,"prefs");
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
   getLogger().info("MIAO_SMOKE_PASS: sidebar disable/enable, OP lobby guard, WorldEdit readonly;  share, leave, original trust, disband, claim preservation, sidebar Chinese/ping/time");
  }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"MIAO_SMOKE_FAIL",e);}
  finally{try{if(claim!=null)GriefPrevention.instance.dataStore.deleteClaim(claim);if(original!=null){f.set(m,original);Method save=MiaoServer.class.getDeclaredMethod("save");save.setAccessible(true);save.invoke(m);}}catch(Exception e){throw new RuntimeException(e);}}
 }
}
