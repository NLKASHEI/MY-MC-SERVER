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
 void check(){
  MiaoServer m=(MiaoServer)Bukkit.getPluginManager().getPlugin("MiaoServer");
  Claim claim=null;YamlConfiguration original=null;Field f=null;
  try{
   f=MiaoServer.class.getDeclaredField("data");f.setAccessible(true);original=(YamlConfiguration)f.get(m);
   YamlConfiguration test=new YamlConfiguration();f.set(m,test);
   UUID owner=UUID.randomUUID(), member=UUID.randomUUID(), manual=UUID.randomUUID();
   World w=Bukkit.getWorld("world");
   cuboidCheck(m,test,w);
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
   ok(sellMenu.getStringList("items.sell.left_click_commands").contains("[player] sellgui"),"PE usable sell menu installed");
   var sellPermission=new YamlConfiguration();sellPermission.options().pathSeparator('|');sellPermission.load(new java.io.File(m.getDataFolder().getParentFile().getParentFile(),"permissions.yml"));
   ok(sellPermission.getBoolean("survival.player|children|economyshopgui.sellgui.nlk_resources"),"sale permission literal key");
   ok(me.gypopo.economyshopgui.api.EconomyShopGUIHook.getShopSections().contains("nlk_resources"),"new system shop loaded");
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
   var spawnBreak=new org.bukkit.event.block.BlockBreakEvent(publicSpawn.getBlock(),p);var spawnBreaking=spawnClass.getDeclaredMethod("breaking",org.bukkit.event.block.BlockBreakEvent.class);spawnBreaking.setAccessible(true);spawnBreaking.invoke(spawnGuard,spawnBreak);ok(spawnBreak.isCancelled(),"OP spawn protection");
   @SuppressWarnings("unchecked") Map<UUID,World> deaths=(Map<UUID,World>)get(m,"deathWorlds");
   var reason=org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.DEATH;
   World lobbyWorld=Bukkit.getWorld("lobby");
   for(String name:List.of("world","flat","world_nether","world_the_end","lobby")) {
     World deathWorld=Bukkit.getWorld(name);ok(deathWorld!=null,"respawn fixture world loaded");deaths.put(owner,deathWorld);
     var respawn=new org.bukkit.event.player.PlayerRespawnEvent(p,lobbyWorld.getSpawnLocation(),false,false,false,reason);
     m.respawnInDeathWorld(respawn);ok(respawn.getRespawnLocation().getWorld().equals(deathWorld),"death remains in "+name);
   }
   Location bed=w.getSpawnLocation().clone().add(3,0,3);deaths.put(owner,w);
   var bedEvent=new org.bukkit.event.player.PlayerRespawnEvent(p,bed,true,false,false,reason);m.respawnInDeathWorld(bedEvent);
   ok(bedEvent.getRespawnLocation().equals(bed),"same-world bed retained");
   World nether=Bukkit.getWorld("world_nether");Location anchor=nether.getSpawnLocation().clone().add(2,0,2);deaths.put(owner,nether);
   var anchorEvent=new org.bukkit.event.player.PlayerRespawnEvent(p,anchor,false,true,false,reason);m.respawnInDeathWorld(anchorEvent);
   ok(anchorEvent.getRespawnLocation().equals(anchor),"same-world anchor retained");
   deaths.put(owner,nether);var crossBed=new org.bukkit.event.player.PlayerRespawnEvent(p,bed,true,false,false,reason);m.respawnInDeathWorld(crossBed);
   ok(crossBed.getRespawnLocation().getWorld().equals(nether),"bed in another world does not override death world");
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
 Player actor(UUID id,String name,World world,Location location,boolean op)throws Exception {
  var cache=(fr.xephi.authme.data.auth.PlayerCache)get(fr.xephi.authme.api.v3.AuthMeApi.getInstance(),"playerCache");cache.updatePlayer(fr.xephi.authme.data.auth.PlayerAuth.builder().name(name.toLowerCase()).build());
  return (Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(obj,method,args)->switch(method.getName()) {
   case "getUniqueId"->id;case "getName"->name;case "getWorld"->world;case "getLocation"->location;case "isOp"->op;case "hasPermission"->true;case "hasMetadata"->false;case "sendMessage"->null;case "getServer"->Bukkit.getServer();
   default->{Class<?> c=method.getReturnType();yield c==boolean.class?false:c==int.class?0:null;}
  });
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
