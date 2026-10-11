package cn.nankexi;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.configuration.file.YamlConfiguration;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.*;
import com.sk89q.worldguard.protection.flags.*;
import me.ryanhamshire.GriefPrevention.GriefPrevention;

/** WorldGuard owns protection; this bridge handles selection and explicit ownership operations. */
public final class CuboidClaims implements Listener {
    private final MiaoServer plugin;
    private final File file;
    private YamlConfiguration data=new YamlConfiguration();
    private final Map<UUID,Selection> selections=new HashMap<>();
    private record Selection(Location first,Location second,long expires) {}
    public CuboidClaims(MiaoServer plugin) {
        this.plugin=plugin;file=new File(plugin.getDataFolder(),"claims-3d.yml");
        try{if(file.exists())data.load(file);}catch(Exception e){throw new IllegalStateException("Refusing unreadable 3D claim data",e);}
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Bukkit.getScheduler().runTaskTimer(plugin,this::particles,20,20);
    }
    private void save() {
        try{file.getParentFile().mkdirs();File temp=new File(file.getParentFile(),file.getName()+".tmp");data.save(temp);Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        catch(IOException e){throw new IllegalStateException("Cannot save 3D claims",e);}
    }
    private Set<String> keys(String path){var section=data.getConfigurationSection(path);return section==null?Set.of():new HashSet<>(section.getKeys(false));}
    private static RegionManager manager(World world){return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));}
    private ProtectedRegion region(String id) {
        World world=Bukkit.getWorld(data.getString("lands."+id+".world",""));
        var manager=world==null?null:manager(world);return manager==null?null:manager.getRegion(id);
    }
    public String at(Location location) {
        for(String id:keys("lands"))if(location.getWorld().getName().equals(data.getString("lands."+id+".world"))) {
            var region=region(id);if(region!=null&&region.contains(location.getBlockX(),location.getBlockY(),location.getBlockZ()))return id;
        }
        return null;
    }
    private static BlockVector3 vector(Location point){return BlockVector3.at(point.getBlockX(),point.getBlockY(),point.getBlockZ());}
    public static boolean withinLimit(Location a,Location b,int limit) {
        return a.getWorld().equals(b.getWorld())&&Math.abs((long)a.getBlockX()-b.getBlockX())+1<=limit&&Math.abs((long)a.getBlockY()-b.getBlockY())+1<=limit&&Math.abs((long)a.getBlockZ()-b.getBlockZ())+1<=limit;
    }
    private ProtectedCuboidRegion box(String id,Location a,Location b){return new ProtectedCuboidRegion(id,vector(a).getMinimum(vector(b)),vector(a).getMaximum(vector(b)));}
    private void say(Player p,String text){p.sendMessage("§6南可熙提醒您： §f"+text);}
    private boolean allowedWorld(World world){return plugin.getConfig().getStringList("claims-3d.worlds").contains(world.getName());}
    private String conflict(ProtectedCuboidRegion box,World world) {
        if(!allowedWorld(world))return "只能在三个生存维度创建立体领地。";
        var manager=manager(world);if(manager==null)return "世界保护服务尚未就绪。";
        Location spawn=world.getSpawnLocation();int radius=plugin.getConfig().getInt("spawn-protection.radius",8);
        if(radius>=0&&box.getMinimumPoint().x()<=spawn.getBlockX()+radius&&box.getMaximumPoint().x()>=spawn.getBlockX()-radius&&box.getMinimumPoint().z()<=spawn.getBlockZ()+radius&&box.getMaximumPoint().z()>=spawn.getBlockZ()-radius)return "选区与公共出生点保护区重叠。";
        var regions=new ArrayList<>(manager.getRegions().values());regions.removeIf(r->r.getId().equals("__global__")||r.getId().equals(box.getId()));
        if(!box.getIntersectingRegions(regions).isEmpty())return "选区与现有保护区域重叠。";
        for(var claim:GriefPrevention.instance.dataStore.getClaims()) {
            Location min=claim.getLesserBoundaryCorner(),max=claim.getGreaterBoundaryCorner();
            if(min.getWorld().equals(world)&&min.getBlockX()<=box.getMaximumPoint().x()&&max.getBlockX()>=box.getMinimumPoint().x()&&min.getBlockZ()<=box.getMaximumPoint().z()&&max.getBlockZ()>=box.getMinimumPoint().z())return "选区与旧领地重叠，旧领地继续由原插件保护。";
        }
        return null;
    }
    private static void nonmember(ProtectedRegion region,StateFlag flag) {
        region.setFlag(flag,StateFlag.State.DENY);region.setFlag(flag.getRegionGroupFlag(),RegionGroup.NON_MEMBERS);
    }
    public String create(UUID owner,String name,Location a,Location b,boolean approved) {
        if(a==null||b==null||!a.getWorld().equals(b.getWorld()))throw new IllegalArgumentException("两个角点必须在同一世界。");
        int limit=plugin.getConfig().getInt("claims-3d.max-side",25);
        if(!approved&&!withinLimit(a,b,limit))throw new IllegalArgumentException("长、宽、高每边最多 "+limit+" 格，超过请申请 OP 批准。");
        String id="nlk_"+UUID.randomUUID().toString().replace("-","");var region=box(id,a,b);
        String problem=conflict(region,a.getWorld());if(problem!=null)throw new IllegalArgumentException(problem);
        region.getOwners().addPlayer(owner);
        for(StateFlag flag:List.of(Flags.BLOCK_BREAK,Flags.BLOCK_PLACE,Flags.CHEST_ACCESS,Flags.USE,Flags.INTERACT,Flags.DAMAGE_ANIMALS))nonmember(region,flag);
        for(StateFlag flag:List.of(Flags.TNT,Flags.CREEPER_EXPLOSION,Flags.OTHER_EXPLOSION,Flags.ENDERDRAGON_BLOCK_DAMAGE))region.setFlag(flag,StateFlag.State.DENY);
        String root="lands."+id;data.set(root+".world",a.getWorld().getName());data.set(root+".owner",owner.toString());data.set(root+".name",name);data.set(root+".manual",List.of());data.set(root+".shared",List.of());
        var manager=manager(a.getWorld());manager.addRegion(region);
        try{manager.save();save();}catch(Exception e){manager.removeRegion(id);data.set(root,null);throw new IllegalStateException("Cannot persist 3D claim",e);}
        return id;
    }
    private boolean owner(Player player,String id){return player.getUniqueId().toString().equals(data.getString("lands."+id+".owner"))&&region(id).getOwners().contains(player.getUniqueId());}
    public boolean sharing(Player p,String team,boolean cancel) {
        String id=at(p.getLocation());if(id==null)return false;
        if(!owner(p,id)&&!(cancel&&p.isOp())){say(p,cancel?"只有地主或 OP 可以取消共享。":"只能共享自己拥有的立体领地，队长不会取得他人土地。");return true;}
        if(!cancel&&team==null){say(p,"请先加入队伍。");return true;}
        data.set("lands."+id+".team",cancel?null:team);syncTeams();save();
        say(p,cancel?"已取消队伍共享，原有手动信任保留。":"立体领地已共享，队友可建造和使用箱子，不能转移归属。");return true;
    }
    public void syncTeams() {
        boolean changed=false;
        for(String id:keys("lands")) {
            var region=region(id);if(region==null)continue;
            String root="lands."+id,owner=data.getString(root+".owner"),team=data.getString(root+".team");
            Set<String> manual=new HashSet<>(data.getStringList(root+".manual"));
            boolean valid=team!=null&&team.equals(plugin.team(UUID.fromString(owner)))&&region.getOwners().contains(UUID.fromString(owner));
            Set<String> wanted=valid?new HashSet<>(plugin.members(team)):new HashSet<>();wanted.remove(owner);
            Set<String> old=new HashSet<>(data.getStringList(root+".shared"));
            boolean missing=wanted.stream().anyMatch(value->!region.getMembers().contains(UUID.fromString(value)));
            if(!wanted.equals(old)||missing||(!valid&&team!=null)) {
                for(String value:old)if(!wanted.contains(value)&&!manual.contains(value))region.getMembers().removePlayer(UUID.fromString(value));
                for(String value:wanted)region.getMembers().addPlayer(UUID.fromString(value));
                data.set(root+".shared",new ArrayList<>(wanted));if(!valid)data.set(root+".team",null);
                try{manager(Bukkit.getWorld(data.getString(root+".world"))).save();}catch(Exception e){throw new IllegalStateException("Cannot sync shared 3D region",e);}
                changed=true;
            }
        }
        if(changed)save();
    }
    private UUID target(String name) {
        var player=Bukkit.getOfflinePlayer(name);if(!player.hasPlayedBefore()&&!player.isOnline())throw new IllegalArgumentException("该玩家还未进入服务器，请填写服内完整账号名。");return player.getUniqueId();
    }
    public boolean command(Player p,String[] args) {
        if(!plugin.authenticated(p)||!p.hasPermission("survival.claims")){say(p,"请先登录，并确认拥有圈地权限。");return true;}
        String action=args.length==0?"info":args[0].toLowerCase(Locale.ROOT);
        try {
            switch(action) {
                case "point1","point2"->{Block block=p.getTargetBlockExact(12);if(block==null)throw new IllegalArgumentException("请看向要选择的角点方块。");select(p,block.getLocation(),action.equals("point1"));}
                case "cancel"->{selections.remove(p.getUniqueId());say(p,"选区已取消。");}
                case "create"->{Selection selection=selected(p);String id=create(p.getUniqueId(),args.length>1?args[1]:"我的领地",selection.first,selection.second,p.isOp());selections.remove(p.getUniqueId());say(p,"立体领地创建成功，只有盒子内受保护。");show(p,id);}
                case "request"->{Selection selection=selected(p);String problem=conflict(box("preview",selection.first,selection.second),selection.first.getWorld());if(problem!=null)throw new IllegalArgumentException(problem);
                    String id=UUID.randomUUID().toString().substring(0,8);String root="requests."+id;
                    data.set(root+".owner",p.getUniqueId().toString());data.set(root+".first",selection.first);data.set(root+".second",selection.second);data.set(root+".reason",args.length>1?String.join(" ",Arrays.copyOfRange(args,1,args.length)):"申请扩大领地");save();say(p,"申请编号 "+id+"，请让 OP 使用 /land approve "+id+" 审批。");}
                case "approve","deny"->{if(!p.isOp()||args.length!=2)throw new IllegalArgumentException("只有 OP 可用 /land approve 或 deny 申请编号。");String root="requests."+args[1];if(!data.contains(root))throw new IllegalArgumentException("申请不存在。");
                    if(action.equals("approve"))create(UUID.fromString(data.getString(root+".owner")),"已批准领地",data.getLocation(root+".first"),data.getLocation(root+".second"),true);
                    data.set(root,null);save();say(p,"申请已处理。");}
                case "requests"->{if(!p.isOp())throw new IllegalArgumentException("只有 OP 可以查看申请。");for(String id:keys("requests"))say(p,id+"："+data.getString("requests."+id+".reason"));}
                case "list"->{for(String id:keys("lands"))if(owner(p,id))say(p,data.getString("lands."+id+".name")+" · "+data.getString("lands."+id+".world"));}
                case "help"->{say(p,"金铲右键两个对角点，长宽高均最多 25 格；左键重选角点一。粒子仅自己可见。");say(p,"选好后在领地管家确认；更大选区点申请，由 OP /land requests 和 /land approve 编号处理。");}
                case "trusthelp"->{say(p,"站在自己的领地内：/land trust 完整玩家名；旧领地用 /trust 玩家名。手动信任不会随取消队伍共享而消失。");}
                case "untrusthelp"->{say(p,"站在自己的领地内：/land untrust 完整玩家名；旧领地用 /untrust 玩家名。队友仍有自动授权时，先取消队伍共享。");}
                case "abandon"->{if(at(p.getLocation())!=null)return command(p,new String[]{"delete","confirm"});var legacy=GriefPrevention.instance.dataStore.getClaimAt(p.getLocation(),true,null);if(legacy==null||legacy.parent!=null||!p.getUniqueId().equals(legacy.getOwnerID()))throw new IllegalArgumentException("只能放弃自己拥有的旧领地。");p.performCommand("griefprevention:abandonclaim");}
                case "share","unshare"->{if(!sharing(p,plugin.team(p.getUniqueId()),action.equals("unshare")))throw new IllegalArgumentException("这里没有立体领地，旧领地请用 /mteam "+action+"。");}
                case "trust","untrust","transfer","delete"->{String id=at(p.getLocation());if(id==null)throw new IllegalArgumentException("请站在要管理的立体领地内。");if(!owner(p,id)&&!p.isOp())throw new IllegalArgumentException("只有地主或 OP 能管理此领地。");
                    var region=region(id);String root="lands."+id;var manager=manager(p.getWorld());
                    if(action.equals("delete")){if(args.length!=2||!args[1].equals("confirm"))throw new IllegalArgumentException("确认删除保护：/land delete confirm，建筑保留。");manager.removeRegion(id);data.set(root,null);}
                    else {if(args.length!=2)throw new IllegalArgumentException("请填写完整玩家名。");UUID target=target(args[1]);
                        if(action.equals("transfer")){data.set(root+".team",null);syncTeams();region.getOwners().clear();region.getOwners().addPlayer(target);data.set(root+".owner",target.toString());}
                        else {Set<String> manual=new HashSet<>(data.getStringList(root+".manual"));if(action.equals("trust")){manual.add(target.toString());region.getMembers().addPlayer(target);}else{manual.remove(target.toString());if(!data.getStringList(root+".shared").contains(target.toString()))region.getMembers().removePlayer(target);else say(p,"该玩家仍由队伍共享授权；取消共享后才会失去建造权限。");}data.set(root+".manual",new ArrayList<>(manual));}
                    }
                    manager.save();save();say(p,"领地设置已更新，归属和手动信任按当前操作保存。");}
                default->{String id=at(p.getLocation());if(id==null)throw new IllegalArgumentException("当前位置没有立体领地。金铲右键两角，指南针领地管家确认创建。");show(p,id);say(p,"地主："+Bukkit.getOfflinePlayer(UUID.fromString(data.getString("lands."+id+".owner"))).getName()+"；长宽高："+size(region(id)));if(owner(p,id)||p.isOp()){say(p,"手动信任："+names(data.getStringList("lands."+id+".manual")));say(p,"队伍共享："+names(data.getStringList("lands."+id+".shared")));}}
            }
        }catch(Exception e){say(p,e.getMessage()==null?"操作失败，请联系管理员。":e.getMessage());if(!(e instanceof IllegalArgumentException))plugin.getLogger().log(java.util.logging.Level.SEVERE,"立体领地操作失败",e);}
        return true;
    }
    private String names(List<String> ids){return String.join("、",ids.stream().map(id->Objects.toString(Bukkit.getOfflinePlayer(UUID.fromString(id)).getName(),id)).toList());}
    private String size(ProtectedRegion r){var a=r.getMinimumPoint();var b=r.getMaximumPoint();return (b.x()-a.x()+1)+"×"+(b.y()-a.y()+1)+"×"+(b.z()-a.z()+1);}
    private Selection selected(Player p){Selection s=selections.get(p.getUniqueId());if(s==null||s.first==null||s.second==null||s.expires<System.currentTimeMillis())throw new IllegalArgumentException("请先用金铲在同一世界选择两个对角点。");return s;}
    private void select(Player p,Location point,boolean first) {
        if(!allowedWorld(point.getWorld())){say(p,"只能在生存维度选择立体领地。");return;}
        Selection old=selections.get(p.getUniqueId());Location a=first||old==null||old.first==null||!old.first.getWorld().equals(point.getWorld())?point:old.first;
        Location b=first||old==null||old.first==null||!old.first.getWorld().equals(point.getWorld())?null:point;
        selections.put(p.getUniqueId(),new Selection(a,b,System.currentTimeMillis()+120000));
        say(p,b==null?"角点一已选，请右键另一个对角点（包含高度）。":"选区 "+size(box("preview",a,b))+"，粒子仅你可见。指南针中确认创建；超过 25 格请申请 OP。");
    }
    public void show(Player p,String id){var r=region(id);if(r==null)return;World world=Bukkit.getWorld(data.getString("lands."+id+".world"));var a=r.getMinimumPoint();var b=r.getMaximumPoint();selections.put(p.getUniqueId(),new Selection(new Location(world,a.x(),a.y(),a.z()),new Location(world,b.x(),b.y(),b.z()),System.currentTimeMillis()+30000));}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(PlayerInteractEvent e) {
        if(e.getHand()!=EquipmentSlot.HAND||e.getClickedBlock()==null||!plugin.authenticated(e.getPlayer()))return;
        Player p=e.getPlayer();Material item=p.getInventory().getItemInMainHand().getType();
        if(item==Material.GOLDEN_SHOVEL&&(e.getAction()==org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK||e.getAction()==org.bukkit.event.block.Action.LEFT_CLICK_BLOCK)) {
            e.setCancelled(true);if(!p.hasPermission("survival.claims"))return;
            var old=selections.get(p.getUniqueId());boolean first=e.getAction()==org.bukkit.event.block.Action.LEFT_CLICK_BLOCK||old==null||old.second!=null;
            select(p,e.getClickedBlock().getLocation(),first);
        }else if(item==Material.STICK&&e.getAction()==org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK){String id=at(e.getClickedBlock().getLocation());if(id!=null){e.setCancelled(true);show(p,id);say(p,"立体领地："+data.getString("lands."+id+".name")+" · "+size(region(id)));}}
    }
    @EventHandler public void quit(PlayerQuitEvent e){selections.remove(e.getPlayer().getUniqueId());}
    private void particles() {
        long now=System.currentTimeMillis();selections.entrySet().removeIf(e->e.getValue().expires<now);
        for(var entry:selections.entrySet()) {
            Player p=Bukkit.getPlayer(entry.getKey());Selection s=entry.getValue();if(p==null||!plugin.authenticated(p)||!p.getWorld().equals(s.first.getWorld()))continue;
            Location a=s.first,b=s.second==null?s.first:s.second;double[] low={Math.min(a.getBlockX(),b.getBlockX()),Math.min(a.getBlockY(),b.getBlockY()),Math.min(a.getBlockZ(),b.getBlockZ())};double[] high={Math.max(a.getBlockX(),b.getBlockX())+1.,Math.max(a.getBlockY(),b.getBlockY())+1.,Math.max(a.getBlockZ(),b.getBlockZ())+1.};
            var dust=new Particle.DustOptions(withinLimit(a,b,plugin.getConfig().getInt("claims-3d.max-side",25))?Color.LIME:Color.RED,1f);
            for(int axis=0;axis<3;axis++)for(int corner=0;corner<4;corner++) {
                int steps=Math.min(24,Math.max(1,(int)(high[axis]-low[axis])));
                for(int step=0;step<=steps;step++){double[] pos=low.clone();pos[axis]=low[axis]+(high[axis]-low[axis])*step/steps;int other=(axis+1)%3,third=(axis+2)%3;pos[other]=(corner&1)==0?low[other]:high[other];pos[third]=(corner&2)==0?low[third]:high[third];Location point=new Location(p.getWorld(),pos[0],pos[1],pos[2]);if(point.distanceSquared(p.getLocation())<=4096)p.spawnParticle(Particle.DUST,point,1,0,0,0,0,dust);}
            }
        }
    }
}
