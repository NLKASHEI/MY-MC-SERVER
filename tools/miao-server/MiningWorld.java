package cn.nankexi;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.generator.*;
import org.bukkit.persistence.PersistentDataType;
import net.milkbowl.vault.economy.Economy;

/** Admission on every cross-world entry; source-only mineral withholding, never bag taxation. */
final class MiningWorld implements Listener {
    private final MiaoServer plugin;private final MiaoInterface ui;
    private final YamlConfiguration ledger=new YamlConfiguration();private final File file;
    private final NamespacedKey placedKey,naturalKey;
    private final Map<UUID,Admission> pending=new HashMap<>();
    private final Set<String> explosionSources=new HashSet<>();
    private final BlockPopulator populator;private boolean dirty;
    private boolean resetting;private long retryResetAfter;
    private final Set<Integer> warned=new HashSet<>();
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private record Admission(UUID transaction,double amount,UUID world){}
    private static final UUID AUTOMATION=UUID.nameUUIDFromBytes("NLK-mining-automation".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    static final Set<Material> ORES=Collections.unmodifiableSet(EnumSet.of(Material.COAL_ORE,Material.DEEPSLATE_COAL_ORE,Material.IRON_ORE,Material.DEEPSLATE_IRON_ORE,Material.COPPER_ORE,Material.DEEPSLATE_COPPER_ORE,Material.GOLD_ORE,Material.DEEPSLATE_GOLD_ORE,Material.REDSTONE_ORE,Material.DEEPSLATE_REDSTONE_ORE,Material.LAPIS_ORE,Material.DEEPSLATE_LAPIS_ORE,Material.DIAMOND_ORE,Material.DEEPSLATE_DIAMOND_ORE,Material.EMERALD_ORE,Material.DEEPSLATE_EMERALD_ORE,Material.NETHER_QUARTZ_ORE,Material.NETHER_GOLD_ORE,Material.ANCIENT_DEBRIS));
    static final Set<Material> MINERALS;
    static {var set=EnumSet.copyOf(ORES);set.addAll(List.of(Material.COAL,Material.RAW_IRON,Material.RAW_COPPER,Material.RAW_GOLD,Material.REDSTONE,Material.LAPIS_LAZULI,Material.DIAMOND,Material.EMERALD,Material.QUARTZ,Material.GOLD_NUGGET));MINERALS=Collections.unmodifiableSet(set);}
    MiningWorld(MiaoServer plugin,MiaoInterface ui){
        this.plugin=plugin;this.ui=ui;file=new File(plugin.getDataFolder(),"mining-ledger.yml");
        placedKey=new NamespacedKey(plugin,"mining-placed-ores");naturalKey=new NamespacedKey(plugin,"mining-natural-drop");
        try{if(file.exists())ledger.load(file);}catch(Exception e){throw new IllegalStateException("Refusing to overwrite unreadable mining ledger",e);}
        int coverage=plugin.getConfig().getInt("resource-world.ore-coverage-percent",30);
        if(coverage<0||coverage>100||divisor()<2||fee()<0||!Double.isFinite(fee()))throw new IllegalArgumentException("Invalid mining economy settings");
        populator=new OreBonus(coverage);Bukkit.getPluginManager().registerEvents(this,plugin);
        if(!ledger.contains("next-reset")){ledger.set("next-reset",firstReset(ZonedDateTime.now(ZONE),resetDays(),resetHour()).toInstant().toEpochMilli());dirty=true;save();}
        for(World world:Bukkit.getWorlds())attach(world);
        Bukkit.getScheduler().runTaskTimer(plugin,()->{retryRefunds();save();},100,100);
        Bukkit.getScheduler().runTaskTimer(plugin,this::clock,1,20);
    }
    static boolean mine(World world){return world!=null&&world.getKey().getNamespace().equals("minecraft")&&MiningDimensions.NAMES.contains(world.getKey().getKey());}
    private UUID generation(){World main=Bukkit.getWorld(NamespacedKey.minecraft("mining"));return main==null?null:main.getUID();}
    private int divisor(){return plugin.getConfig().getInt("resource-world.tax-divisor",3);}
    private double fee(){return plugin.getConfig().getDouble("resource-world.entry-fee",200);}
    private int resetDays(){return Math.max(1,plugin.getConfig().getInt("resource-world.reset-period-days",3));}
    private int resetHour(){return Math.max(0,Math.min(23,plugin.getConfig().getInt("resource-world.reset-hour",4)));}
    static ZonedDateTime firstReset(ZonedDateTime now,int days,int hour){return now.withZoneSameInstant(ZONE).toLocalDate().plusDays(days).atTime(hour,0).atZone(ZONE);}
    String resetTime(){return Instant.ofEpochMilli(ledger.getLong("next-reset")).atZone(ZONE).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))+"（北京时间）";}
    private void clock(){
        if(resetting||(Bukkit.getWorld(NamespacedKey.minecraft("mining"))==null&&!ledger.getBoolean("reset-in-progress")))return;
        long now=System.currentTimeMillis(),remaining=(ledger.getLong("next-reset")-now+999)/1000;
        if(remaining<=0){if(now>=retryResetAfter)reset();return;}
        for(int threshold:new int[]{600,300,60,10})if(remaining<=threshold&&warned.add(threshold)){
            for(Player p:Bukkit.getOnlinePlayers())if(ui.authenticated(p))say(p,"丰饶矿界将在约 "+remaining+" 秒后重置并更换种子。请带走物资，不要在矿界建家；届时自动返回生存。");
        }
    }
    void reset(){
        if(resetting)return;resetting=true;
        try{
            World world=Bukkit.getWorld(NamespacedKey.minecraft("mining"));
            Location safe=RespawnSafety.spawn(Objects.requireNonNull(Bukkit.getWorld("world")));
            for(Player p:Bukkit.getWorlds().stream().filter(MiningWorld::mine).flatMap(w->w.getPlayers().stream()).toList()){
                if(p.isDead())p.spigot().respawn();
                if(mine(p.getWorld())&&!p.teleport(safe))throw new IllegalStateException("Cannot evacuate player "+p.getUniqueId());
            }
            if(Bukkit.getWorlds().stream().filter(MiningWorld::mine).anyMatch(w->!w.getPlayers().isEmpty()))throw new IllegalStateException("Mining family still contains players");
            for(var entry:new ArrayList<>(pending.entrySet())){if(pending.remove(entry.getKey(),entry.getValue()))refund(entry.getKey(),entry.getValue());}
            ledger.set("reset-in-progress",true);dirty=true;save();
            WorldExpansion.resetMining(plugin);
            var players=ledger.getConfigurationSection("players");
            if(players!=null)for(String id:players.getKeys(false))if(ledger.getBoolean("players."+id+".active")){
                ledger.set("players."+id+".active",false);ledger.set("players."+id+".reset-notice",true);
            }
            // Advance from the stored schedule; missed cycles coalesce into one reset.
            ZonedDateTime next=Instant.ofEpochMilli(ledger.getLong("next-reset")).atZone(ZONE);
            do{next=next.plusDays(resetDays()).withHour(resetHour()).withMinute(0).withSecond(0).withNano(0);}while(!next.toInstant().isAfter(Instant.now()));
            ledger.set("next-reset",next.toInstant().toEpochMilli());ledger.set("reset-in-progress",false);dirty=true;save();warned.clear();
            for(Player p:Bukkit.getOnlinePlayers())if(ui.authenticated(p))say(p,"丰饶矿界已重置并更换随机种子，下次重置："+resetTime()+"。背包、金币和生存世界保留，请勿在矿界建家。");
            plugin.getLogger().info("矿界定时重置完成；新随机种子；下次："+resetTime());
        }catch(Exception e){retryResetAfter=System.currentTimeMillis()+60000;plugin.getLogger().log(java.util.logging.Level.SEVERE,"矿界重置未完成，已暂停入场；一分钟后重试，请检查日志。",e);}
        finally{resetting=false;}
    }
    private void say(Player p,String text){p.sendMessage("§6南可熙提醒您： §f"+text);}
    private String path(UUID id){return "players."+id;}
    private Economy economy(){var service=Bukkit.getServicesManager().getRegistration(Economy.class);if(service==null)throw new IllegalStateException("Vault economy unavailable");return service.getProvider();}
    private boolean charge(Player p){try{return economy().withdrawPlayer(p,fee()).transactionSuccess();}catch(Exception e){plugin.getLogger().warning("矿界扣款失败，已拒绝入场："+p.getUniqueId()+" / "+e.getClass().getSimpleName());return false;}}
    private void attach(World world){
        if(!mine(world))return;
        if(world.getEnvironment()!=World.Environment.THE_END&&!world.getPopulators().contains(populator))world.getPopulators().add(populator);
        // Upgrade existing visits once; never bless a ticket after an interrupted/due reset.
        if(world.getKey().equals(NamespacedKey.minecraft("mining"))&&!ledger.getBoolean("ticket-world-migrated")){
            var players=ledger.getConfigurationSection("players");
            if(players!=null)for(String id:players.getKeys(false)){
                String key="players."+id;
                if(ledger.getBoolean(key+".active")&&!ledger.contains(key+".world-id")&&!ledger.getBoolean("reset-in-progress")&&System.currentTimeMillis()<ledger.getLong("next-reset"))ledger.set(key+".world-id",world.getUID().toString());
            }
            ledger.set("ticket-world-migrated",true);dirty=true;save();
        }
    }
    private boolean validTicket(Player p){return mine(p.getWorld())&&ledger.getBoolean(path(p.getUniqueId())+".active")&&generation()!=null&&generation().toString().equals(ledger.getString(path(p.getUniqueId())+".world-id"));}
    private boolean allowed(Player p){return ui.authenticated(p)&&validTicket(p)&&!resetting&&!ledger.getBoolean("reset-in-progress")&&System.currentTimeMillis()<ledger.getLong("next-reset");}
    @EventHandler public void initialize(WorldInitEvent event){if(mine(event.getWorld())){DhWorldLifecycle.initialized(event.getWorld());attach(event.getWorld());}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent event){
        if(event.getTo()==null||!mine(event.getTo().getWorld()))return;
        Player p=event.getPlayer();
        if(mine(event.getFrom().getWorld())){if(!allowed(p))event.setCancelled(true);return;}
        if(resetting||ledger.getBoolean("reset-in-progress")||System.currentTimeMillis()>=ledger.getLong("next-reset")){event.setCancelled(true);say(p,"矿界正在重置，请稍后再进入。");return;}
        if(!ui.authenticated(p)||pending.containsKey(p.getUniqueId())){event.setCancelled(true);return;}
        if(!charge(p)){event.setCancelled(true);say(p,"进入丰饶矿界需要 "+fee()+" 金币，余额不足或扣款失败。");return;}
        Admission admission=new Admission(UUID.randomUUID(),fee(),event.getTo().getWorld().getUID());pending.put(p.getUniqueId(),admission);
        Bukkit.getScheduler().runTaskLater(plugin,()->{if(pending.remove(p.getUniqueId(),admission))refund(p.getUniqueId(),admission);},2);
    }
    private void refund(UUID id,Admission admission){
        String key="refunds."+admission.transaction();ledger.set(key+".player",id.toString());ledger.set(key+".amount",admission.amount());dirty=true;save();retryRefunds();
    }
    private void retryRefunds(){
        var refunds=ledger.getConfigurationSection("refunds");if(refunds==null)return;
        for(String transaction:new HashSet<>(refunds.getKeys(false)))try{
            String key="refunds."+transaction;
            var result=economy().depositPlayer(Bukkit.getOfflinePlayer(UUID.fromString(ledger.getString(key+".player"))),ledger.getDouble(key+".amount"));
            if(result.transactionSuccess()){ledger.set(key,null);dirty=true;save();}
        }catch(Exception e){plugin.getLogger().warning("矿界退款暂未完成，将重试；交易 "+transaction+": "+e.getClass().getSimpleName());}
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void changed(PlayerChangedWorldEvent event){
        Player p=event.getPlayer();
        if(mine(p.getWorld())){
            if(resetting||ledger.getBoolean("reset-in-progress")||System.currentTimeMillis()>=ledger.getLong("next-reset")){p.teleport(RespawnSafety.spawn(Objects.requireNonNull(Bukkit.getWorld("world"))));return;}
            if(mine(event.getFrom())&&allowed(p)){p.setGameMode(GameMode.SURVIVAL);return;}
            // Also guards world transfers not represented by PlayerTeleportEvent.
            Admission admission=pending.remove(p.getUniqueId());
            if(admission!=null&&!admission.world().equals(p.getWorld().getUID())){refund(p.getUniqueId(),admission);admission=null;}
            if(admission==null){
                ledger.set(path(p.getUniqueId())+".active",false);dirty=true;save();
                if(!ui.authenticated(p)||!charge(p)){
                    say(p,"没有有效矿界门票，已返回生存世界。");p.teleport(RespawnSafety.spawn(Objects.requireNonNull(Bukkit.getWorld("world"))));return;
                }
            }
            begin(p);p.setGameMode(GameMode.SURVIVAL);
        }else if(mine(event.getFrom()))finish(p);
    }
    private void begin(Player p){
        String key=path(p.getUniqueId());
        ledger.set(key+".visit",null);ledger.set(key+".active",true);ledger.set(key+".world-id",Objects.requireNonNull(generation()).toString());dirty=true;save();say(p,"已进入丰饶矿界，门票 "+fee()+" 金币；天然矿产每累计 "+divisor()+" 份预留 1 份，离开显示结算。重连不重复购票。");say(p,"此处不能建家，建筑和箱子会被重置清空！下次重置："+resetTime());
    }
    private void finish(Player p){
        String key=path(p.getUniqueId());if(!ledger.getBoolean(key+".active"))return;
        say(p,"矿界本次矿产总产出 "+ledger.getLong(key+".visit.gross")+" 份，已预留资源费 "+ledger.getLong(key+".visit.tax")+" 份；其余资源可带回生存。未满 "+divisor()+" 份的余数累计到下次，不再扣背包。");
        ledger.set(key+".active",false);dirty=true;save();
    }
    @EventHandler(priority=EventPriority.MONITOR) public void login(fr.xephi.authme.events.LoginEvent event){
        Player p=event.getPlayer();String key=path(p.getUniqueId());
        if(ledger.getBoolean(key+".reset-notice")){say(p,"你上次停留的矿界已重置并更换种子，物品进度保留；再次入场需要购买新门票。");ledger.set(key+".reset-notice",false);dirty=true;}
        if(mine(p.getWorld())){
            if(allowed(p))say(p,"欢迎回到矿界，本次门票继续有效；采集余数和结算记录已保留。");
            else {say(p,"请从指南针购买矿界门票后进入。");p.teleport(RespawnSafety.spawn(Objects.requireNonNull(Bukkit.getWorld("world"))));}
        }else if(ledger.getBoolean(path(p.getUniqueId())+".active"))finish(p);
    }
    @EventHandler public void quit(PlayerQuitEvent event){Admission admission=pending.remove(event.getPlayer().getUniqueId());if(admission!=null)refund(event.getPlayer().getUniqueId(),admission);save();}
    @EventHandler(priority=EventPriority.MONITOR) public void respawn(PlayerRespawnEvent e){if(mine(e.getPlayer().getWorld())&&!mine(e.getRespawnLocation().getWorld()))Bukkit.getScheduler().runTask(plugin,()->{if(!mine(e.getPlayer().getWorld()))finish(e.getPlayer());});}
    void command(Player p,String action){
        if(!ui.authenticated(p))return;
        if(Set.of("enter","overworld","nether","end").contains(action)){
            String destination=action.equals("nether")?"mining_nether":action.equals("end")?"mining_the_end":"mining";
            if(mine(p.getWorld())&&!allowed(p)){say(p,"请先重新购买门票。");return;}
            World target=Bukkit.getWorld(NamespacedKey.minecraft(destination));
            if(target==p.getWorld()){say(p,"你已在这里。");return;}
            if(target==null){say(p,"矿界尚未就绪，请联系管理员。");return;}
            p.teleport(RespawnSafety.spawn(target));
        }else if(action.equals("leave")){
            if(!mine(p.getWorld())){say(p,"你目前不在矿界。");return;}
            p.teleport(RespawnSafety.spawn(Objects.requireNonNull(Bukkit.getWorld("world"))));
        }else {
            String key=path(p.getUniqueId());say(p,"丰饶矿界门票 "+fee()+" 金币；新区块地下石质层矿石覆盖约 "+plugin.getConfig().getInt("resource-world.ore-coverage-percent",30)+"%，以煤铁铜为主，资源费 1/"+divisor()+"。本次总产出 "+ledger.getLong(key+".visit.gross")+"，已预留 "+ledger.getLong(key+".visit.tax")+"。自己带来的物资、石头和泥土不收费。");say(p,"每 "+resetDays()+" 天凌晨 "+resetHour()+" 点重置并换种子，不要在此建家。下次："+resetTime());
        }
    }
    // Per material, retain the remainder across visits/restarts: 8 => 2, then 1 => 1.
    int reserve(UUID id,Material material,int gross){
        if(gross<=0||!MINERALS.contains(material))return 0;
        String key=path(id),carry=key+".carry."+material.name();long total=ledger.getLong(carry)+gross;
        int tax=Math.toIntExact(total/divisor());ledger.set(carry,total%divisor());
        ledger.set(key+".visit.gross",ledger.getLong(key+".visit.gross")+gross);ledger.set(key+".visit.tax",ledger.getLong(key+".visit.tax")+tax);dirty=true;return tax;
    }
    private void withhold(UUID id,List<Item> items){
        for(Iterator<Item> iterator=items.iterator();iterator.hasNext();){
            Item item=iterator.next();var stack=item.getItemStack();int tax=reserve(id,stack.getType(),stack.getAmount());
            if(tax==0)continue;
            if(tax>=stack.getAmount()){iterator.remove();item.remove();}
            else {stack.setAmount(stack.getAmount()-tax);item.setItemStack(stack);}
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drops(BlockDropItemEvent event){
        Block block=event.getBlockState().getBlock();if(!mine(block.getWorld())||!ORES.contains(event.getBlockState().getType()))return;
        boolean placed=placed(block);mark(block,false);
        if(!placed)withhold(event.getPlayer().getUniqueId(),event.getItems());
    }
    private long position(Block block){return ((long)(block.getY()-block.getWorld().getMinHeight())<<8)|((block.getX()&15)<<4)|(block.getZ()&15);}
    private boolean placed(Block block){long position=position(block);long[] values=block.getChunk().getPersistentDataContainer().get(placedKey,PersistentDataType.LONG_ARRAY);return values!=null&&Arrays.stream(values).anyMatch(v->v==position);}
    private void mark(Block block,boolean placed){
        var pdc=block.getChunk().getPersistentDataContainer();long[] values=pdc.getOrDefault(placedKey,PersistentDataType.LONG_ARRAY,new long[0]);long position=position(block);
        if(placed){if(Arrays.stream(values).anyMatch(v->v==position))return;values=Arrays.copyOf(values,values.length+1);values[values.length-1]=position;}
        else values=Arrays.stream(values).filter(v->v!=position).toArray();
        if(values.length==0)pdc.remove(placedKey);else pdc.set(placedKey,PersistentDataType.LONG_ARRAY,values);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void placed(BlockPlaceEvent event){if(mine(event.getBlock().getWorld())&&ORES.contains(event.getBlock().getType()))mark(event.getBlock(),true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breaking(BlockBreakEvent event){
        if(!mine(event.getBlock().getWorld()))return;
        if(!allowed(event.getPlayer())||event.getPlayer().getGameMode()!=GameMode.SURVIVAL){event.setCancelled(true);return;}
        Block block=event.getBlock();if(placed(block))Bukkit.getScheduler().runTask(plugin,()->{if(block.getType().isAir())mark(block,false);});
    }
    private void moved(List<Block> blocks,org.bukkit.block.BlockFace direction){
        var from=blocks.stream().filter(b->mine(b.getWorld())&&placed(b)).toList();
        var to=from.stream().map(b->b.getRelative(direction)).toList();
        // Snapshot and move together in the successful event, before another action can break the destination.
        blocks.forEach(b->{mark(b,false);mark(b.getRelative(direction),false);});to.forEach(b->mark(b,true));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void extend(BlockPistonExtendEvent e){if(mine(e.getBlock().getWorld()))moved(e.getBlocks(),e.getDirection());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void retract(BlockPistonRetractEvent e){if(mine(e.getBlock().getWorld()))moved(e.getBlocks(),e.getDirection().getOppositeFace());}
    private String source(Block block){return block.getWorld().getUID()+":"+block.getX()+":"+block.getY()+":"+block.getZ();}
    private void explosion(List<Block> blocks){
        var keys=new ArrayList<String>();for(Block b:blocks)if(mine(b.getWorld())&&ORES.contains(b.getType())){
            if(!placed(b)){String key=source(b);explosionSources.add(key);keys.add(key);}else mark(b,false);
        }
        Bukkit.getScheduler().runTask(plugin,()->explosionSources.removeAll(keys));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void explosion(EntityExplodeEvent e){if(mine(e.getLocation().getWorld()))explosion(e.blockList());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void explosion(BlockExplodeEvent e){if(mine(e.getBlock().getWorld()))explosion(e.blockList());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void spawn(ItemSpawnEvent e){if(mine(e.getLocation().getWorld())&&MINERALS.contains(e.getEntity().getItemStack().getType())&&explosionSources.contains(source(e.getLocation().getBlock())))e.getEntity().getPersistentDataContainer().set(naturalKey,PersistentDataType.BYTE,(byte)1);}
    private void explosionPickup(Item item,UUID id){
        if(!item.getPersistentDataContainer().has(naturalKey))return;
        item.getPersistentDataContainer().remove(naturalKey);var stack=item.getItemStack();int tax=reserve(id,stack.getType(),stack.getAmount());
        if(tax>=stack.getAmount())item.remove();else if(tax>0){stack.setAmount(stack.getAmount()-tax);item.setItemStack(stack);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pickup(EntityPickupItemEvent e){
        if(mine(e.getItem().getWorld())&&e.getEntity() instanceof Player p&&!allowed(p)){e.setCancelled(true);return;}
        if(!e.getItem().getPersistentDataContainer().has(naturalKey)||e.getRemaining()>=e.getItem().getItemStack().getAmount())return;
        explosionPickup(e.getItem(),e.getEntity() instanceof Player p?p.getUniqueId():AUTOMATION);
        // Retry native pickup next tick with the reduced stack, avoiding stale remaining counts.
        e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hopper(InventoryPickupItemEvent e){if(e.getItem().getPersistentDataContainer().has(naturalKey)){explosionPickup(e.getItem(),AUTOMATION);e.setCancelled(true);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void merging(ItemMergeEvent e){if(mine(e.getEntity().getWorld())&&e.getEntity().getPersistentDataContainer().has(naturalKey)!=e.getTarget().getPersistentDataContainer().has(naturalKey))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void dropped(PlayerDropItemEvent e){if(mine(e.getPlayer().getWorld()))e.getItemDrop().getPersistentDataContainer().remove(naturalKey);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void portal(PlayerPortalEvent e){
        if(!mine(e.getFrom().getWorld()))return;e.setCancelled(true);if(!allowed(e.getPlayer()))return;
        String target=e.getFrom().getWorld().getEnvironment()!=World.Environment.NORMAL?"mining":e.getCause()==PlayerTeleportEvent.TeleportCause.END_PORTAL?"mining_the_end":"mining_nether";
        Bukkit.getScheduler().runTask(plugin,()->{if(e.getPlayer().isOnline()&&allowed(e.getPlayer())){World world=Bukkit.getWorld(NamespacedKey.minecraft(target));if(world!=null)e.getPlayer().teleport(RespawnSafety.spawn(world));}});
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void portal(EntityPortalEvent e){if(mine(e.getFrom().getWorld()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void export(EntityTeleportEvent e){if(e.getEntity() instanceof Item item&&e.getTo()!=null&&!Objects.equals(e.getFrom().getWorld(),e.getTo().getWorld())&&item.getPersistentDataContainer().has(naturalKey)){explosionPickup(item,AUTOMATION);if(!item.isValid())e.setCancelled(true);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent e){if(mine(e.getPlayer().getWorld())&&!allowed(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void oldHome(net.ess3.api.events.UserTeleportHomeEvent e){
        if(e.getHomeLocation()!=null&&mine(e.getHomeLocation().getWorld())){e.setCancelled(true);say(e.getUser().getBase(),"矿界的旧家园和床坐标已停用，请从指南针购票进入；每次重置会清空建筑。");}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void home(PlayerCommandPreprocessEvent e){
        if(!mine(e.getPlayer().getWorld()))return;String root=e.getMessage().split("\\s+",2)[0].toLowerCase(Locale.ROOT);root=root.substring(root.lastIndexOf(':')+1).replaceFirst("^/","");
        var command=Bukkit.getPluginCommand(root);if(command!=null)root=command.getName().toLowerCase(Locale.ROOT);
        if(Set.of("sethome","esethome","createhome","ecreatehome","setwarp","esetwarp").contains(root)){e.setCancelled(true);say(e.getPlayer(),"矿界每 "+resetDays()+" 天重置，不能在这里设置家园或公共传送点，请回生存世界建设。");}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void bedSpawn(PlayerSpawnChangeEvent e){if(e.getNewSpawn()!=null&&mine(e.getNewSpawn().getWorld())){e.setCancelled(true);say(e.getPlayer(),"矿界不能设置重生点，请在生存世界安家。");}}
    private void save(){
        if(!dirty)return;try{file.getParentFile().mkdirs();File tmp=new File(file.getParentFile(),file.getName()+".tmp");ledger.save(tmp);Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);dirty=false;}
        catch(IOException e){throw new IllegalStateException("Cannot persist mining ledger",e);}
    }
    void stop(){for(var entry:pending.entrySet())refund(entry.getKey(),entry.getValue());pending.clear();save();}
    /** Exact coverage budget, spent in connected same-material veins; never replaces existing ore. */
    private static final class OreBonus extends BlockPopulator {
        private final int percent;OreBonus(int percent){this.percent=percent;}
        private String ore(Random random,int y,boolean nether){
            int roll=random.nextInt(10000);
            if(nether)return roll<10&&y<24?"ANCIENT_DEBRIS":roll<6500?"NETHER_QUARTZ_ORE":"NETHER_GOLD_ORE";
            return roll<100?(y<0?"DIAMOND_ORE":"IRON_ORE"):roll<150?(y>=0?"EMERALD_ORE":"IRON_ORE"):roll<700?"GOLD_ORE":roll<1700?"REDSTONE_ORE":roll<2400?"LAPIS_ORE":roll<4600?"COPPER_ORE":roll<7400?"IRON_ORE":"COAL_ORE";
        }
        @Override public void populate(WorldInfo info,Random random,int chunkX,int chunkZ,LimitedRegion region){
            int baseX=chunkX<<4,baseZ=chunkZ<<4,min=info.getMinHeight(),capacity=256*(info.getMaxHeight()-min);int[] pool=new int[capacity],index=new int[capacity];Arrays.fill(index,-1);int count=0,existing=0;boolean nether=info.getEnvironment()==World.Environment.NETHER;
            if(info.getEnvironment()==World.Environment.THE_END)return;
            for(int x=baseX;x<baseX+16;x++)for(int z=baseZ;z<baseZ+16;z++)for(int y=min+1;y<info.getMaxHeight()-1;y++){
                Material material=region.getType(x,y,z);if(ORES.contains(material))existing++;
                else if(nether?material==Material.NETHERRACK:material==Material.STONE||material==Material.DEEPSLATE){int pos=((y-min)<<8)|((x&15)<<4)|(z&15);index[pos]=count;pool[count++]=pos;}
            }
            int needed=Math.min(count,Math.max(0,(int)Math.round((count+existing)*percent/100.0)-existing)),added=0;
            while(added<needed&&count>0){
                int seed=pool[random.nextInt(count)];String ore=ore(random,min+(seed>>8),nether);int vein=ore.equals("ANCIENT_DEBRIS")?2+random.nextInt(2):ore.equals("DIAMOND_ORE")||ore.equals("EMERALD_ORE")?3+random.nextInt(4):12+random.nextInt(25);List<Integer> frontier=new ArrayList<>();frontier.add(seed);int grown=0;
                while(!frontier.isEmpty()&&grown<vein&&added<needed){
                    int pick=random.nextInt(frontier.size()),pos=frontier.get(pick);frontier.set(pick,frontier.getLast());frontier.removeLast();if(pos<0||pos>=index.length||index[pos]<0)continue;
                    int slot=index[pos],last=pool[--count];pool[slot]=last;index[last]=slot;index[pos]=-1;
                    int lx=(pos>>4)&15,lz=pos&15,y=min+(pos>>8),x=baseX+lx,z=baseZ+lz;Material ground=region.getType(x,y,z);
                    region.setType(x,y,z,Material.valueOf(!nether&&ground==Material.DEEPSLATE?"DEEPSLATE_"+ore:ore));added++;grown++;
                    if(lx>0)frontier.add(pos-16);if(lx<15)frontier.add(pos+16);if(lz>0)frontier.add(pos-1);if(lz<15)frontier.add(pos+1);frontier.add(pos-256);frontier.add(pos+256);
                }
            }
        }
    }
}
