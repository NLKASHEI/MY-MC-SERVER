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
    private record Admission(UUID transaction,double amount){}
    private static final UUID AUTOMATION=UUID.nameUUIDFromBytes("NLK-mining-automation".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    static final Set<Material> ORES=Collections.unmodifiableSet(EnumSet.of(Material.COAL_ORE,Material.DEEPSLATE_COAL_ORE,Material.IRON_ORE,Material.DEEPSLATE_IRON_ORE,Material.COPPER_ORE,Material.DEEPSLATE_COPPER_ORE,Material.GOLD_ORE,Material.DEEPSLATE_GOLD_ORE,Material.REDSTONE_ORE,Material.DEEPSLATE_REDSTONE_ORE,Material.LAPIS_ORE,Material.DEEPSLATE_LAPIS_ORE,Material.DIAMOND_ORE,Material.DEEPSLATE_DIAMOND_ORE,Material.EMERALD_ORE,Material.DEEPSLATE_EMERALD_ORE));
    static final Set<Material> MINERALS;
    static {var set=EnumSet.copyOf(ORES);set.addAll(List.of(Material.COAL,Material.RAW_IRON,Material.RAW_COPPER,Material.RAW_GOLD,Material.REDSTONE,Material.LAPIS_LAZULI,Material.DIAMOND,Material.EMERALD));MINERALS=Collections.unmodifiableSet(set);}
    MiningWorld(MiaoServer plugin,MiaoInterface ui){
        this.plugin=plugin;this.ui=ui;file=new File(plugin.getDataFolder(),"mining-ledger.yml");
        placedKey=new NamespacedKey(plugin,"mining-placed-ores");naturalKey=new NamespacedKey(plugin,"mining-natural-drop");
        try{if(file.exists())ledger.load(file);}catch(Exception e){throw new IllegalStateException("Refusing to overwrite unreadable mining ledger",e);}
        int bonus=plugin.getConfig().getInt("resource-world.ore-bonus-percent",50);
        if(bonus<0||bonus>100||divisor()<2||fee()<0||!Double.isFinite(fee()))throw new IllegalArgumentException("Invalid mining economy settings");
        populator=new OreBonus(bonus);Bukkit.getPluginManager().registerEvents(this,plugin);
        if(!ledger.contains("next-reset")){ledger.set("next-reset",firstReset(ZonedDateTime.now(ZONE),resetDays(),resetHour()).toInstant().toEpochMilli());dirty=true;save();}
        for(World world:Bukkit.getWorlds())attach(world);
        Bukkit.getScheduler().runTaskTimer(plugin,this::save,100,100);
        Bukkit.getScheduler().runTaskTimer(plugin,this::clock,1,20);
    }
    static boolean mine(World world){return world!=null&&world.getKey().equals(NamespacedKey.minecraft("mining"));}
    private int divisor(){return plugin.getConfig().getInt("resource-world.tax-divisor",3);}
    private double fee(){return plugin.getConfig().getDouble("resource-world.entry-fee",100);}
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
            for(Player p:world==null?List.<Player>of():new ArrayList<>(world.getPlayers())){
                if(p.isDead())p.spigot().respawn();
                if(mine(p.getWorld())&&!p.teleport(safe))throw new IllegalStateException("Cannot evacuate player "+p.getUniqueId());
            }
            if(world!=null&&!world.getPlayers().isEmpty())throw new IllegalStateException("Mining world still contains players");
            for(var entry:new ArrayList<>(pending.entrySet())){if(pending.remove(entry.getKey(),entry.getValue()))refund(entry.getKey(),entry.getValue());}
            ledger.set("reset-in-progress",true);dirty=true;save();
            if(world==null)WorldExpansion.start(plugin);else WorldExpansion.resetMining(plugin);
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
    private void attach(World world){if(mine(world)&&!world.getPopulators().contains(populator))world.getPopulators().add(populator);}
    @EventHandler public void initialize(WorldInitEvent event){if(mine(event.getWorld())){DhWorldLifecycle.initialized(event.getWorld());attach(event.getWorld());}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent event){
        if(!mine(event.getTo().getWorld())||mine(event.getFrom().getWorld()))return;
        Player p=event.getPlayer();
        if(resetting||System.currentTimeMillis()>=ledger.getLong("next-reset")){event.setCancelled(true);say(p,"矿界正在重置，请稍后再进入。");return;}
        if(!ui.authenticated(p)||pending.containsKey(p.getUniqueId())){event.setCancelled(true);return;}
        var result=economy().withdrawPlayer(p,fee());
        if(!result.transactionSuccess()){event.setCancelled(true);say(p,"进入丰饶矿界需要 "+fee()+" 金币，余额不足或扣款失败。");return;}
        Admission admission=new Admission(UUID.randomUUID(),fee());pending.put(p.getUniqueId(),admission);
        Bukkit.getScheduler().runTaskLater(plugin,()->{if(pending.remove(p.getUniqueId(),admission))refund(p.getUniqueId(),admission);},2);
    }
    private void refund(UUID id,Admission admission){
        var result=economy().depositPlayer(Bukkit.getOfflinePlayer(id),admission.amount());
        if(!result.transactionSuccess())plugin.getLogger().severe("Mining admission refund failed for "+id+", amount="+admission.amount()+"; transaction="+admission.transaction());
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void changed(PlayerChangedWorldEvent event){
        Player p=event.getPlayer();
        if(mine(p.getWorld())){
            if(resetting||System.currentTimeMillis()>=ledger.getLong("next-reset")){p.teleport(RespawnSafety.spawn(Objects.requireNonNull(Bukkit.getWorld("world"))));return;}
            // Also guards world transfers not represented by PlayerTeleportEvent.
            Admission admission=pending.remove(p.getUniqueId());
            if(admission==null&&!ledger.getBoolean(path(p.getUniqueId())+".active")){
                if(!ui.authenticated(p)||!economy().withdrawPlayer(p,fee()).transactionSuccess()){
                    say(p,"没有有效矿界门票，已返回生存世界。");p.teleport(RespawnSafety.spawn(Objects.requireNonNull(Bukkit.getWorld("world"))));return;
                }
            }
            begin(p);p.setGameMode(GameMode.SURVIVAL);
        }else if(mine(event.getFrom()))finish(p);
    }
    private void begin(Player p){
        String key=path(p.getUniqueId());
        if(!ledger.getBoolean(key+".active")){ledger.set(key+".visit",null);ledger.set(key+".active",true);dirty=true;save();say(p,"已进入丰饶矿界，门票 "+fee()+" 金币；天然矿产每累计 "+divisor()+" 份预留 1 份，离开显示结算。重连不重复购票。");say(p,"此处不能建家，建筑和箱子会被重置清空！下次重置："+resetTime());}
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
            if(ledger.getBoolean(path(p.getUniqueId())+".active"))say(p,"欢迎回到矿界，本次门票继续有效；采集余数和结算记录已保留。");
            else {say(p,"请从指南针购买矿界门票后进入。");p.teleport(RespawnSafety.spawn(Objects.requireNonNull(Bukkit.getWorld("world"))));}
        }else if(ledger.getBoolean(path(p.getUniqueId())+".active"))finish(p);
    }
    @EventHandler public void quit(PlayerQuitEvent event){Admission admission=pending.remove(event.getPlayer().getUniqueId());if(admission!=null)refund(event.getPlayer().getUniqueId(),admission);save();}
    void command(Player p,String action){
        if(!ui.authenticated(p))return;
        if(action.equals("enter")){
            if(mine(p.getWorld())){say(p,"你已在丰饶矿界，无需再次购票。");return;}
            World target=Bukkit.getWorld(NamespacedKey.minecraft("mining"));
            if(target==null){say(p,"矿界尚未就绪，请联系管理员。");return;}
            p.teleport(RespawnSafety.spawn(target));
        }else if(action.equals("leave")){
            if(!mine(p.getWorld())){say(p,"你目前不在矿界。");return;}
            p.teleport(RespawnSafety.spawn(Objects.requireNonNull(Bukkit.getWorld("world"))));
        }else {
            String key=path(p.getUniqueId());say(p,"丰饶矿界门票 "+fee()+" 金币；天然矿产 +"+plugin.getConfig().getInt("resource-world.ore-bonus-percent",50)+"% 左右（新增区块），资源费 1/"+divisor()+"。本次总产出 "+ledger.getLong(key+".visit.gross")+"，已预留 "+ledger.getLong(key+".visit.tax")+"。自己带来的物资、石头和泥土不收费。");say(p,"每 "+resetDays()+" 天凌晨 "+resetHour()+" 点重置并换种子，不要在此建家。下次："+resetTime());
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
        if(!placed&&ui.authenticated(event.getPlayer())&&event.getPlayer().getGameMode()==GameMode.SURVIVAL)withhold(event.getPlayer().getUniqueId(),event.getItems());
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
        if(!ui.authenticated(event.getPlayer())||event.getPlayer().getGameMode()!=GameMode.SURVIVAL){event.setCancelled(true);return;}
        Block block=event.getBlock();if(placed(block))Bukkit.getScheduler().runTask(plugin,()->{if(block.getType().isAir())mark(block,false);});
    }
    private void moved(List<Block> blocks,org.bukkit.block.BlockFace direction){
        var from=blocks.stream().filter(b->mine(b.getWorld())&&placed(b)).toList();
        var to=from.stream().map(b->b.getRelative(direction)).toList();
        // Move provenance after physics; snapshot first so adjacent ores do not overwrite each other.
        Bukkit.getScheduler().runTask(plugin,()->{from.forEach(b->mark(b,false));to.forEach(b->mark(b,true));});
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void extend(BlockPistonExtendEvent e){if(mine(e.getBlock().getWorld()))moved(e.getBlocks(),e.getDirection());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void retract(BlockPistonRetractEvent e){if(mine(e.getBlock().getWorld()))moved(e.getBlocks(),e.getDirection().getOppositeFace());}
    private String source(Block block){return block.getX()+":"+block.getY()+":"+block.getZ();}
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
        if(!mine(e.getItem().getWorld())||!e.getItem().getPersistentDataContainer().has(naturalKey)||e.getRemaining()>=e.getItem().getItemStack().getAmount())return;
        if(e.getEntity() instanceof Player p&&!ui.authenticated(p)){e.setCancelled(true);return;}
        explosionPickup(e.getItem(),e.getEntity() instanceof Player p?p.getUniqueId():AUTOMATION);
        // Retry native pickup next tick with the reduced stack, avoiding stale remaining counts.
        e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hopper(InventoryPickupItemEvent e){if(mine(e.getItem().getWorld())&&e.getItem().getPersistentDataContainer().has(naturalKey)){explosionPickup(e.getItem(),AUTOMATION);e.setCancelled(true);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void merging(ItemMergeEvent e){if(mine(e.getEntity().getWorld())&&e.getEntity().getPersistentDataContainer().has(naturalKey)!=e.getTarget().getPersistentDataContainer().has(naturalKey))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void dropped(PlayerDropItemEvent e){if(mine(e.getPlayer().getWorld()))e.getItemDrop().getPersistentDataContainer().remove(naturalKey);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void portal(PlayerPortalEvent e){if(mine(e.getFrom().getWorld())){e.setCancelled(true);say(e.getPlayer(),"矿界不通往下界或末地，请用指南针返回生存。");}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void portal(EntityPortalEvent e){if(mine(e.getFrom().getWorld()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void home(PlayerCommandPreprocessEvent e){
        if(!mine(e.getPlayer().getWorld()))return;String root=e.getMessage().split("\\s+",2)[0].toLowerCase(Locale.ROOT);root=root.substring(root.lastIndexOf(':')+1).replaceFirst("^/","");
        if(Set.of("sethome","esethome","createhome","ecreatehome","setwarp","esetwarp").contains(root)){e.setCancelled(true);say(e.getPlayer(),"矿界每 "+resetDays()+" 天重置，不能在这里设置家园或公共传送点，请回生存世界建设。");}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void bedSpawn(PlayerSpawnChangeEvent e){if(e.getNewSpawn()!=null&&mine(e.getNewSpawn().getWorld())){e.setCancelled(true);say(e.getPlayer(),"矿界不能设置重生点，请在生存世界安家。");}}
    private void save(){
        if(!dirty)return;try{file.getParentFile().mkdirs();File tmp=new File(file.getParentFile(),file.getName()+".tmp");ledger.save(tmp);Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);dirty=false;}
        catch(IOException e){throw new IllegalStateException("Cannot persist mining ledger",e);}
    }
    void stop(){for(var entry:pending.entrySet())refund(entry.getKey(),entry.getValue());pending.clear();save();}
    /** Expand existing vanilla ore deposits; never revisit a generated chunk or replace air/structures. */
    private static final class OreBonus extends BlockPopulator {
        private final int percent;OreBonus(int percent){this.percent=percent;}
        @Override public void populate(WorldInfo info,Random random,int chunkX,int chunkZ,LimitedRegion region){
            int baseX=chunkX<<4,baseZ=chunkZ<<4;List<int[]> candidates=new ArrayList<>();
            for(int x=baseX;x<baseX+16;x++)for(int z=baseZ;z<baseZ+16;z++)for(int y=info.getMinHeight()+1;y<info.getMaxHeight()-1;y++){
                if(random.nextInt(100)>=percent)continue;Material material=region.getType(x,y,z);
                if(ORES.contains(material))candidates.add(new int[]{x,y,z,material.ordinal()});
            }
            int[][] offsets={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
            for(int[] pos:candidates){int start=random.nextInt(6);for(int i=0;i<6;i++){
                int[] d=offsets[(i+start)%6];int x=pos[0]+d[0],y=pos[1]+d[1],z=pos[2]+d[2];
                // Stay in the owning chunk to avoid neighboring generation races.
                if(x<baseX||x>=baseX+16||z<baseZ||z>=baseZ+16||y<=info.getMinHeight()||y>=info.getMaxHeight())continue;
                Material ground=region.getType(x,y,z);if(ground!=Material.STONE&&ground!=Material.DEEPSLATE)continue;
                String name=Material.values()[pos[3]].name().replace("DEEPSLATE_","");if(ground==Material.DEEPSLATE)name="DEEPSLATE_"+name;
                region.setType(x,y,z,Material.valueOf(name));break;
            }}
        }
    }
}
