package cn.nankexi;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;

/** Opt-in public waypoints; publishing never changes land ownership or protection. */
final class CommunityLandmarks implements Listener {
    private final CommunityLife life;
    private record Travel(String landmark,Location start,UUID token){}
    private final Map<UUID,Travel> travels=new HashMap<>();
    CommunityLandmarks(CommunityLife life){this.life=life;Bukkit.getPluginManager().registerEvents(this,life.plugin);}
    private String key(String id){return "landmarks."+id;}
    private List<String> all(){var section=life.data.getConfigurationSection("landmarks");return section==null?List.of():new ArrayList<>(section.getKeys(false));}
    private boolean owner(Player p,String id){return p.getUniqueId().toString().equals(life.data.getString(key(id)+".owner"));}
    private boolean manage(Player p,String id){return life.ui.authenticated(p)&&life.data.contains(key(id))&&(owner(p,id)||p.hasPermission("miao.community.admin"));}
    private boolean permanent(World w){return Set.of("world","flat","architect").contains(w.getName());}
    Location location(String id){String k=key(id);try{World world=Bukkit.getWorld(UUID.fromString(life.data.getString(k+".world","")));if(world==null)return null;return new Location(world,life.data.getDouble(k+".x"),life.data.getDouble(k+".y"),life.data.getDouble(k+".z"),(float)life.data.getDouble(k+".yaw"),0);}catch(IllegalArgumentException e){return null;}}
    private boolean point(Location at){return at!=null&&permanent(at.getWorld())&&at.getWorld().getWorldBorder().isInside(at)&&at.getY()>at.getWorld().getMinHeight()&&at.getY()<at.getWorld().getMaxHeight()-2;}
    private void coordinates(String id,Location at){String k=key(id);life.data.set(k+".world",at.getWorld().getUID().toString());life.data.set(k+".world-name",at.getWorld().getName());life.data.set(k+".x",at.getBlockX()+0.5);life.data.set(k+".y",(double)at.getBlockY());life.data.set(k+".z",at.getBlockZ()+0.5);life.data.set(k+".yaw",at.getYaw());}
    String create(Player p,String name,String description,Location at){
        if(!life.ui.authenticated(p)||!CommunityLife.text(name,16,false)||!CommunityLife.text(description,60,true)){life.say(p,"名称 1–16 字，介绍最多 60 字。");return null;}
        if(!point(at)){life.say(p,"请在主世界或建筑世界登记。");return null;}
        long count=all().stream().filter(id->owner(p,id)).count();if(count>=life.config.getInt("landmarks.per-player",3)||all().size()>=life.config.getInt("landmarks.total-limit",300)){life.say(p,"地标名额已满。");return null;}
        if(all().stream().anyMatch(id->name.equalsIgnoreCase(life.data.getString(key(id)+".name")))){life.say(p,"已有同名地标，请换个名称。");return null;}
        String id=UUID.randomUUID().toString();life.change(()->{String k=key(id);life.data.set(k+".owner",p.getUniqueId().toString());life.data.set(k+".author",p.getName());life.data.set(k+".name",name);life.data.set(k+".description",description);life.data.set(k+".public",true);life.data.set(k+".created",System.currentTimeMillis());coordinates(id,at);});life.say(p,"地标已发布。");return id;
    }
    void list(Player p,int page){
        List<CommunityMenu.Entry> entries=new ArrayList<>();for(String id:all())if(life.data.getBoolean(key(id)+".public")||manage(p,id)){
            String k=key(id);entries.add(new CommunityMenu.Entry(Material.LODESTONE,"§b"+life.data.getString(k+".name"),List.of("§7"+life.data.getString(k+".description",""),"§7主人："+life.ui.names.nickname(UUID.fromString(life.data.getString(k+".owner")),life.data.getString(k+".author")),"§7"+(life.data.getBoolean(k+".public")?"开放参观":"已收起")),()->detail(p,id)));
        }
        life.menus.show(p,"地标导航","看看大家的建筑。",entries,page,()->life.home(p),List.of(new CommunityMenu.Entry(Material.WRITABLE_BOOK,"§a登记地标",List.of("§7使用当前位置"),()->draft(p))));
    }
    private void draft(Player p){Location at=p.getLocation().clone();if(!point(at)){life.say(p,"请在主世界或建筑世界登记。");return;}life.input(p,"地标名称","1–16 字","",name->{if(!CommunityLife.text(name,16,false)){life.say(p,"名称不合适。");return;}life.input(p,"一句介绍","最多 60 字，填 - 可留空","",text->{String description=text.equals("-")?"":text;if(!CommunityLife.text(description,60,true)){life.say(p,"介绍不合适。");return;}life.menus.confirm(p,"发布地标",name+"\n"+description+"\n落点："+at.getBlockX()+", "+at.getBlockY()+", "+at.getBlockZ(),()->{create(p,name,description,at);list(p,0);},()->list(p,0));});});}
    private void detail(Player p,String id){
        if(!life.data.contains(key(id))||(!life.data.getBoolean(key(id)+".public")&&!manage(p,id))){list(p,0);return;}
        List<CommunityMenu.Entry> entries=new ArrayList<>();String k=key(id);
        entries.add(new CommunityMenu.Entry(Material.ENDER_PEARL,"§b前往参观",List.of("§7三秒后出发，移动取消"),()->visit(p,id)));
        if(manage(p,id)){
            entries.add(new CommunityMenu.Entry(Material.LIME_DYE,life.data.getBoolean(k+".public")?"§e收起地标":"§a开放参观",List.of(),()->{if(manage(p,id)){life.change(()->life.data.set(k+".public",!life.data.getBoolean(k+".public")));detail(p,id);}}));
            entries.add(new CommunityMenu.Entry(Material.COMPASS,"§f落点设为脚下",List.of(),()->{if(manage(p,id)&&point(p.getLocation())){life.change(()->coordinates(id,p.getLocation()));life.say(p,"落点已更新。");detail(p,id);}else life.say(p,"当前位置不可用。");}));
            entries.add(new CommunityMenu.Entry(Material.WRITABLE_BOOK,"§f修改介绍",List.of(),()->life.input(p,"修改介绍","最多 60 字，填 - 可清空",life.data.getString(k+".description",""),text->{String description=text.equals("-")?"":text;if(manage(p,id)&&CommunityLife.text(description,60,true)){life.change(()->life.data.set(k+".description",description));detail(p,id);}})));
            entries.add(new CommunityMenu.Entry(Material.BARRIER,"§c删除地标",List.of(),()->life.menus.confirm(p,"删除地标","只删除导航记录。",()->{if(manage(p,id))life.change(()->life.data.set(k,null));list(p,0);},()->detail(p,id))));
        }
        life.menus.show(p,life.data.getString(k+".name"),life.data.getString(k+".description",""),entries,0,()->list(p,0),List.of());
    }
    private boolean safe(Location at){
        return point(at)&&RespawnSafety.safe(at);
    }
    void visit(Player p,String id){
        if(!life.ui.authenticated(p)||!life.data.getBoolean(key(id)+".public")){life.say(p,"地标已收起。");return;}
        Location target=location(id);if(!point(target)){life.say(p,"地标暂不可用。");return;}
        if(CreativeTools.building(p.getWorld())&&!p.getWorld().equals(target.getWorld())){life.say(p,"请先通过世界探索离开建筑世界。");return;}
        UUID token=UUID.randomUUID();travels.put(p.getUniqueId(),new Travel(id,p.getLocation().clone(),token));p.closeInventory();life.say(p,"三秒后前往地标。");
        Bukkit.getScheduler().runTaskLater(life.plugin,()->{
            Travel request=travels.get(p.getUniqueId());if(request==null||!request.token().equals(token))return;
            if(!p.isOnline()||!life.ui.authenticated(p)||!life.data.getBoolean(key(id)+".public")){travels.remove(p.getUniqueId(),request);return;}
            Location now=location(id);if(!point(now)||p.getWorld()!=request.start().getWorld()||p.getLocation().distanceSquared(request.start())>0.04){travels.remove(p.getUniqueId(),request);life.say(p,"传送已取消。");return;}
            // Load only this destination chunk, then recheck its floor and the still-current request.
            now.getWorld().getChunkAtAsync(now).thenAccept(chunk->life.later(p,()->{
                if(!travels.remove(p.getUniqueId(),request))return;
                if(!life.data.getBoolean(key(id)+".public")||!now.equals(location(id))||p.getWorld()!=request.start().getWorld()||p.getLocation().distanceSquared(request.start())>0.04||!safe(now)){life.say(p,"落点不安全或传送已取消。");return;}
                p.teleport(now,PlayerTeleportEvent.TeleportCause.PLUGIN);
            })).exceptionally(error->{life.later(p,()->{travels.remove(p.getUniqueId(),request);life.say(p,"地标暂不可用。");});return null;});
        },60);
    }
    @EventHandler(ignoreCancelled=true) public void move(PlayerMoveEvent e){Travel request=travels.get(e.getPlayer().getUniqueId());if(request!=null&&(e.getTo().getWorld()!=request.start().getWorld()||e.getTo().distanceSquared(request.start())>0.04)){travels.remove(e.getPlayer().getUniqueId());life.say(e.getPlayer(),"传送已取消。");}}
    @EventHandler(ignoreCancelled=true) public void damage(EntityDamageEvent e){if(e.getEntity() instanceof Player p)travels.remove(p.getUniqueId());}
    @EventHandler public void quit(PlayerQuitEvent e){travels.remove(e.getPlayer().getUniqueId());}
}
