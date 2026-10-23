package cn.nankexi;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.configuration.file.YamlConfiguration;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerManager;
import io.github.hello09x.fakeplayer.core.manager.action.ActionManager;
import io.github.hello09x.fakeplayer.api.spi.*;

/** Owned bot controls. No console escalation, teleports or player identity changes. */
final class DummyControls implements Listener {
    static final int LIMIT=5;
    final MiaoServer plugin; final MiaoInterface ui;
    final Map<UUID,UUID> automatic=new HashMap<>();
    final Set<UUID> creating=new HashSet<>();
    final Map<UUID,Input> inputs=new java.util.concurrent.ConcurrentHashMap<>();
    record Input(UUID bot,String action,long expires){}
    record Button(Material icon,String name,String detail,Runnable action,Player head) {
        Button(Material icon,String name,String detail,Runnable action){this(icon,name,detail,action,null);}
    }
    static final class Panel implements InventoryHolder {
        final UUID owner; final Map<Integer,Runnable> actions=new HashMap<>(); Inventory inventory;
        Panel(UUID owner){this.owner=owner;} public Inventory getInventory(){return inventory;}
    }
    DummyControls(MiaoServer plugin,MiaoInterface ui){this.plugin=plugin;this.ui=ui;Bukkit.getPluginManager().registerEvents(this,plugin);Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);}
    FakeplayerManager manager(){return Main.getInjector().getInstance(FakeplayerManager.class);}
    ActionManager actions(){return Main.getInjector().getInstance(ActionManager.class);}
    boolean ready(Player p){return p.isOnline()&&ui.authenticated(p)&&Bukkit.getPluginManager().isPluginEnabled("FakePlayer-CE");}
    List<Player> owned(Player p){return manager().getAll(p).stream().filter(Player::isOnline).sorted(Comparator.comparing(Player::getName)).toList();}
    Player target(Player p,UUID id){if(!ready(p))return null;return owned(p).stream().filter(b->b.getUniqueId().equals(id)).findFirst().orElse(null);}
    void later(Player p,Runnable r){Bukkit.getScheduler().runTaskLater(plugin,()->{if(ready(p))r.run();},2);}
    void say(Player p,String text){p.sendMessage("§b南可熙提醒您：§f"+text);}
    boolean command(Player p,String[] args){later(p,()->{if(args.length>0&&args[0].equals("create"))spawn(p);else list(p);});return true;}
    void list(Player p){
        if(!ready(p)){say(p,"假人服务暂不可用。");return;}
        var buttons=new ArrayList<Button>();
        for(Player bot:owned(p)){UUID id=bot.getUniqueId();buttons.add(new Button(Material.PLAYER_HEAD,bot.getName(),location(bot)+" · "+(automatic.containsKey(id)?"自动蓄力攻击":"点击分别管理"),()->detail(p,id),bot));}
        buttons.add(new Button(Material.ARMOR_STAND,"创建假人 · "+owned(p).size()+" / 5","在当前位置创建；全服最多20个",()->spawn(p)));
        buttons.add(new Button(Material.ENDER_EYE,"刷新附近假人显示","重新发送外观，不重建实体或清空背包",()->{for(Player b:owned(p))refresh(b);say(p,"已刷新附近假人显示。");list(p);}));
        panel(p,"我的假人", "每人最多5个 · 先选假人，再设置操作",buttons,()->ui.command(p,new String[]{"menu","technical"}));
    }
    String location(Player b){return b.getWorld().getName()+" "+b.getLocation().getBlockX()+", "+b.getLocation().getBlockY()+", "+b.getLocation().getBlockZ();}
    void spawn(Player p){
        if(!ready(p)||!p.hasPermission("fakeplayer.command.spawn"))return;
        if(creating.contains(p.getUniqueId())){say(p,"上一位假人正在创建，请稍等。");return;}
        if(owned(p).size()>=LIMIT){say(p,"已有5个假人，请先收回一个。");list(p);return;}
        if(manager().getSize()>=20){say(p,"全服已有20个假人，请稍后再试。");return;}
        creating.add(p.getUniqueId());p.closeInventory();
        try{manager().spawnAsync(p,null,p.getLocation().clone(),-1).whenComplete((bot,error)->Bukkit.getScheduler().runTask(plugin,()->{
            if(error==null&&bot!=null){manager().setSelection(p,bot);settle(bot);}
            if(p.isOnline())say(p,error==null?"假人已创建，可从“我的假人”分别管理。":"创建失败，可能达到全服上限；请查看控制台具体原因。");
            if(error!=null)plugin.getLogger().log(java.util.logging.Level.WARNING,"假人创建失败",error);
            Bukkit.getScheduler().runTaskLater(plugin,()->creating.remove(p.getUniqueId()),80);
        }));}catch(Exception e){creating.remove(p.getUniqueId());say(p,"暂时无法创建：可能已达到假人上限。");}
    }
    void detail(Player p,UUID id){
        Player b=target(p,id);if(b==null){say(p,"该假人已离线或不属于你。");list(p);return;}
        manager().setSelection(p,b);
        var buttons=new ArrayList<Button>();
        buttons.add(new Button(Material.IRON_SWORD,"攻击设置",automatic.containsKey(id)?"当前：按手持物品蓄力":"单次 / 自动蓄力 / 指定间隔",()->modes(p,id,"attack")));
        buttons.add(new Button(Material.IRON_PICKAXE,"挖掘设置","单次 / 连续 / 指定间隔",()->modes(p,id,"mine")));
        buttons.add(new Button(Material.LEVER,"使用设置","右键操作：单次 / 连续 / 指定间隔",()->modes(p,id,"use")));
        buttons.add(new Button(Material.REDSTONE_BLOCK,"停止所有动作","停止攻击、使用、挖掘、移动及自动蓄力",()->{run(p,id,"stop");detail(p,id);}));
        buttons.add(new Button(Material.CHEST,"假人背包","放入工具、食物与物品",()->run(p,id,"invsee")));
        buttons.add(new Button(Material.ENDER_CHEST,"末影箱","管理这个假人的末影箱",()->run(p,id,"enderchest")));
        buttons.add(new Button(Material.COMPASS,"朝向与移动","看向自己、方位、转身与行走",()->movement(p,id)));
        buttons.add(new Button(Material.IRON_BOOTS,"姿态与跳跃","潜行、疾跑、单次或连续跳跃",()->posture(p,id)));
        buttons.add(new Button(Material.HOPPER,"手持与物品","选择快捷栏、交换副手、丢弃与经验",()->items(p,id)));
        buttons.add(new Button(Material.COMPARATOR,"实体设置","碰撞、拾取开关；不提供无敌或无限补货",()->settings(p,id)));
        buttons.add(new Button(Material.BOOK,"查看完整状态","显示插件状态、所在位置及手持物品",()->{run(p,id,"status");say(p,location(b));}));
        buttons.add(new Button(Material.ENDER_EYE,"刷新外观","假人看不见时重新发送实体显示",()->{Player q=target(p,id);if(q!=null)refresh(q);detail(p,id);}));
        buttons.add(new Button(Material.BARRIER,"收回此假人","保留背包；需要确认",()->confirm(p,id,"收回此假人",()->run(p,id,"kick"))));
        panel(p,b.getName()+" · 管理",location(b)+" · 设置只作用于这一个假人",buttons,()->list(p));
    }
    void modes(Player p,UUID id,String action){
        if(target(p,id)==null)return;
        String label=switch(action){case "attack"->"攻击";case "mine"->"挖掘";default->"使用";};
        var buttons=new ArrayList<Button>();
        buttons.add(new Button(Material.STONE_BUTTON,"执行一次",label+"一次后停止",()->{run(p,id,action+" once");detail(p,id);}));
        if(action.equals("attack"))buttons.add(new Button(Material.DIAMOND_SWORD,"自动蓄力攻击 · 推荐","每次等待手持武器完全蓄力，换武器后自动适应",()->{Player b=target(p,id);if(b!=null&&p.hasPermission("fakeplayer.command.attack")){actions().setAction(b,ActionType.ATTACK,ActionSetting.stop());automatic.put(id,p.getUniqueId());say(p,"已开启按武器蓄力攻击。");detail(p,id);}}));
        buttons.add(new Button(Material.REPEATER,"每tick连续尝试",action.equals("attack")?"快速点击不会跳过原版伤害冷却，不推荐持剑刷怪":"持续尝试，保留原版交互与挖掘限制",()->{run(p,id,action+" continuous");detail(p,id);}));
        for(int ticks:new int[]{5,10,13,20,40,100})buttons.add(new Button(Material.CLOCK,"间隔 "+ticks+" tick",String.format(Locale.ROOT,"约 %.2f 秒；TPS20时",ticks/20.0),()->{run(p,id,action+" interval "+ticks);detail(p,id);}));
        buttons.add(new Button(Material.NAME_TAG,"自定义间隔","输入1–1200 tick；只对本假人本动作生效",()->{if(target(p,id)==null)return;inputs.put(p.getUniqueId(),new Input(id,action,System.currentTimeMillis()+60000));p.closeInventory();say(p,"请在聊天输入间隔tick（1–1200），或输入取消；这条输入不会公开发送。");}));
        buttons.add(new Button(Material.BARRIER,"停止"+label,"其他动作继续",()->{run(p,id,action+" stop");detail(p,id);}));
        panel(p,label+"设置",Objects.requireNonNull(target(p,id)).getName()+" · 设置不跨重启保留",buttons,()->detail(p,id));
    }
    void movement(Player p,UUID id){
        var buttons=new ArrayList<Button>();
        for(String[] entry:new String[][]{{"看向我","look me"},{"向北看","look north"},{"向南看","look south"},{"向西看","look west"},{"向东看","look east"},{"向上看","look up"},{"向下看","look down"},{"左转90°","turn left"},{"右转90°","turn right"},{"向后转","turn back"},{"向前走","move forward"},{"向后走","move backward"},{"向左走","move left"},{"向右走","move right"},{"停止移动与动作","stop"}})buttons.add(new Button(Material.COMPASS,entry[0],"仅操作选中的假人",()->{run(p,id,entry[1]);movement(p,id);}));
        panel(p,"朝向与移动",name(p,id),buttons,()->detail(p,id));
    }
    void posture(Player p,UUID id){
        var buttons=new ArrayList<Button>();
        for(String[] e:new String[][]{{"潜行开关","sneak"},{"疾跑开关","sprint"},{"跳跃一次","jump once"},{"连续跳跃","jump continuous"},{"停止跳跃","jump stop"},{"睡觉","sleep"},{"起床","wakeup"}})buttons.add(new Button(Material.IRON_BOOTS,e[0],"仅操作选中的假人",()->{run(p,id,e[1]);posture(p,id);}));
        panel(p,"姿态与跳跃",name(p,id),buttons,()->detail(p,id));
    }
    void items(Player p,UUID id){
        var buttons=new ArrayList<Button>();Player b=target(p,id);if(b==null)return;
        for(int slot=1;slot<=9;slot++){int n=slot;ItemStack held=b.getInventory().getItem(slot-1);buttons.add(new Button(held==null||held.getType().isAir()?Material.LIGHT_GRAY_STAINED_GLASS_PANE:held.getType(),"手持快捷栏 "+slot,(b.getInventory().getHeldItemSlot()==slot-1?"当前手持 · ":"")+"切换至此栏位",()->{run(p,id,"hold "+n);items(p,id);}));}
        buttons.add(new Button(Material.SHIELD,"交换主副手","与原版F键相同",()->{run(p,id,"swap");items(p,id);}));
        buttons.add(new Button(Material.DROPPER,"丢弃手持一个","需要确认，物品掉落在假人脚下",()->confirm(p,id,"丢弃手持一个",()->run(p,id,"drop once"))));
        buttons.add(new Button(Material.HOPPER,"丢弃手持一组","需要确认，不会清空整个背包",()->confirm(p,id,"丢弃手持一组",()->run(p,id,"dropstack once"))));
        buttons.add(new Button(Material.EXPERIENCE_BOTTLE,"取回经验","使用插件原有经验转移规则",()->run(p,id,"expme")));
        panel(p,"手持与物品",b.getName(),buttons,()->detail(p,id));
    }
    void settings(Player p,UUID id){Player b=target(p,id);if(b==null)return;panel(p,"实体设置",b.getName(),List.of(
        new Button(Material.SLIME_BALL,"实体碰撞 · "+(b.isCollidable()?"开":"关"),"仅控制此假人的实体碰撞",()->{Player q=target(p,id);if(q!=null)q.setCollidable(!q.isCollidable());settings(p,id);}),
        new Button(Material.HOPPER,"拾取物品 · "+(b.getCanPickupItems()?"开":"关"),"仅控制此假人的掉落物拾取",()->{Player q=target(p,id);if(q!=null)q.setCanPickupItems(!q.getCanPickupItems());settings(p,id);})
        ),()->detail(p,id));}
    String name(Player p,UUID id){Player b=target(p,id);return b==null?"假人已离线":b.getName();}
    void confirm(Player p,UUID id,String title,Runnable action){var used=new java.util.concurrent.atomic.AtomicBoolean();panel(p,title,name(p,id),List.of(new Button(Material.LIME_CONCRETE,"确认","只影响当前假人",()->{if(used.compareAndSet(false,true)&&target(p,id)!=null){action.run();list(p);}})),()->detail(p,id));}
    boolean run(Player p,UUID id,String command){
        Player b=target(p,id);if(b==null){say(p,"假人已离线或不属于你。");return false;}
        String verb=command.split(" ")[0];if(!p.hasPermission("fakeplayer.command."+verb)){say(p,"你没有此操作权限。");return false;}
        if(verb.equals("attack")||verb.equals("stop")||verb.equals("kick"))automatic.remove(id);
        manager().setSelection(p,b);p.closeInventory();return p.performCommand("fp "+command+" "+b.getName());
    }
    void tick(){
        for(var entry:new ArrayList<>(automatic.entrySet()))tickBot(entry.getKey(),Bukkit.getPlayer(entry.getValue()));
    }
    void tickBot(UUID id,Player owner){
        Player b=owner==null?null:target(owner,id);
        if(b==null||!owner.hasPermission("fakeplayer.command.attack")){automatic.remove(id);return;}
        if(b.getAttackCooldown()>=1.0f)actions().setAction(b,ActionType.ATTACK,ActionSetting.once());
    }
    void panel(Player p,String title,String description,List<Button> buttons,Runnable back){
        if(!ready(p))return;
        if(ui.bedrock(p)){var f=SimpleForm.builder().title(title).content(description);var callbacks=new ArrayList<Runnable>();for(Button b:buttons){f.button(b.name()+"\n"+b.detail());callbacks.add(b.action());}f.button("返回");callbacks.add(back);f.validResultHandler(r->later(p,callbacks.get(r.clickedButtonId())));FloodgateApi.getInstance().sendForm(p.getUniqueId(),f.build());return;}
        Panel h=new Panel(p.getUniqueId());h.inventory=Bukkit.createInventory(h,54,"§8"+title);
        for(int i=0;i<54;i++){ItemStack glass=new ItemStack(Material.GRAY_STAINED_GLASS_PANE);var m=glass.getItemMeta();m.setDisplayName(" ");glass.setItemMeta(m);h.inventory.setItem(i,glass);}
        for(int i=0;i<buttons.size()&&i<28;i++)put(h,10+i/7*9+i%7,buttons.get(i));
        put(h,4,new Button(Material.BOOK,title,description,()->{}));put(h,49,new Button(Material.ARROW,"返回","回到上一层",back));p.openInventory(h.inventory);
    }
    void put(Panel h,int slot,Button b){ItemStack item=new ItemStack(b.icon().isItem()?b.icon():Material.PAPER);var meta=item.getItemMeta();meta.setDisplayName("§b"+b.name());meta.setLore(List.of("§7"+b.detail()));if(meta instanceof SkullMeta skull&&b.head()!=null)skull.setPlayerProfile(b.head().getPlayerProfile());item.setItemMeta(meta);h.inventory.setItem(slot,item);h.actions.put(slot,b.action());}
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getInventory().getHolder() instanceof Panel h))return;e.setCancelled(true);if(e.getWhoClicked() instanceof Player p&&h.owner.equals(p.getUniqueId())&&e.getRawSlot()>=0&&e.getRawSlot()<54){Runnable r=h.actions.get(e.getRawSlot());if(r!=null)later(p,r);}}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getInventory().getHolder() instanceof Panel)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void input(io.papermc.paper.event.player.AsyncChatEvent e){Input in=inputs.remove(e.getPlayer().getUniqueId());if(in==null)return;e.setCancelled(true);String text=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.message()).trim();later(e.getPlayer(),()->{if(text.equals("取消")){detail(e.getPlayer(),in.bot());return;}try{int n=Integer.parseInt(text);if(n<1||n>1200||System.currentTimeMillis()>in.expires())throw new IllegalArgumentException();run(e.getPlayer(),in.bot(),in.action()+" interval "+n);detail(e.getPlayer(),in.bot());}catch(IllegalArgumentException ex){say(e.getPlayer(),"请输入1–1200的整数；超时后请重新打开设置。");}});}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void manual(PlayerCommandPreprocessEvent e){if(!Bukkit.getPluginManager().isPluginEnabled("FakePlayer-CE"))return;String[] a=e.getMessage().substring(1).split(" +");String root=a[0].toLowerCase(Locale.ROOT);if(root.contains(":"))root=root.substring(root.indexOf(':')+1);if(!Set.of("fp","fakeplayer").contains(root)||a.length<2||!Set.of("attack","stop","kick").contains(a[1]))return;boolean explicit=false;for(Player b:owned(e.getPlayer()))if(a[a.length-1].equalsIgnoreCase(b.getName())){automatic.remove(b.getUniqueId());explicit=true;}if(!explicit){Player selected=manager().getSelection(e.getPlayer());if(selected!=null)automatic.remove(selected.getUniqueId());}}
    @EventHandler public void quit(PlayerQuitEvent e){inputs.remove(e.getPlayer().getUniqueId());automatic.remove(e.getPlayer().getUniqueId());automatic.values().removeIf(e.getPlayer().getUniqueId()::equals);}
    void settle(Player b){for(long delay:new long[]{80,160})Bukkit.getScheduler().runTaskLater(plugin,()->{if(b.isOnline())refresh(b);},delay);}
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTaskLater(plugin,()->{if(e.getPlayer().hasMetadata("fakeplayer:spawned_at"))settle(e.getPlayer());},1);}
    @EventHandler(priority=EventPriority.MONITOR) public void world(PlayerChangedWorldEvent e){if(e.getPlayer().hasMetadata("fakeplayer:spawned_at"))settle(e.getPlayer());}
    void refresh(Player b){
        if(!b.isOnline()||!b.hasMetadata("fakeplayer:spawned_at"))return;
        for(Player viewer:Bukkit.getOnlinePlayers())if(!viewer.hasMetadata("fakeplayer:spawned_at")&&viewer.getWorld().equals(b.getWorld())&&viewer.getLocation().distanceSquared(b.getLocation())<=128*128&&viewer.canSee(b)){
            // Re-register through Paper's tracker after the plugin's login/skin/world teleports settle.
            // The visibility check preserves hides owned by other plugins.
            viewer.hidePlayer(plugin,b);
            Bukkit.getScheduler().runTaskLater(plugin,()->{if(viewer.isOnline())viewer.showPlayer(plugin,b);},2);
        }
    }
    static void migrate(MiaoServer plugin){
        File marker=new File(plugin.getDataFolder(),"owned-dummies-v0927.done");if(marker.exists())return;
        try{
            File parent=plugin.getDataFolder().getParentFile(),file=new File(parent,"FakePlayer-CE/config.yml");
            if(file.exists()){YamlConfiguration y=new YamlConfiguration();y.load(file);y.set("player-limit",5);write(y,file);}
            File menu=new File(parent,"DeluxeMenus/gui_menus/technical.yml");if(menu.exists()){
                YamlConfiguration y=new YamlConfiguration();y.load(menu);
                var source=new YamlConfiguration();try(var in=plugin.getResource("technical-menu.yml")){source.load(new InputStreamReader(Objects.requireNonNull(in),java.nio.charset.StandardCharsets.UTF_8));}
                for(String key:List.of("spawn","inventory","attack","use","stop","kick","manage","heading"))y.set("items."+key,null);
                for(String key:List.of("spawn","manage","heading"))y.createSection("items."+key,Objects.requireNonNull(source.getConfigurationSection("items."+key)).getValues(false));
                for(int slot:new int[]{12,14,15,20}){String key="items.bot_space_"+slot;y.set(key+".material","GRAY_STAINED_GLASS_PANE");y.set(key+".slot",slot);y.set(key+".display_name"," ");}
                write(y,menu);
            }
            File mainMenu=new File(parent,"DeluxeMenus/gui_menus/survival.yml");if(mainMenu.exists()){
                var y=new YamlConfiguration();y.load(mainMenu);if(y.contains("items.technical")){
                    var lore=y.getStringList("items.technical.lore");lore.replaceAll(s->s.replace("每人一个，仅生存世界使用","每人最多5个，选中后分别管理"));y.set("items.technical.lore",lore);write(y,mainMenu);
                }
            }
            File guide=new File(parent,"Skript/scripts/survival.sk");if(guide.exists()){
                String text=Files.readString(guide.toPath(),java.nio.charset.StandardCharsets.UTF_8);String changed=text.replace("每人限一个。","每人最多5个；指南针→生电工具箱→我的假人分别管理。");
                if(!changed.equals(text)){File temp=new File(guide.getParentFile(),"survival.sk.bots-tmp");Files.writeString(temp.toPath(),changed,java.nio.charset.StandardCharsets.UTF_8);Files.move(temp.toPath(),guide.toPath(),StandardCopyOption.REPLACE_EXISTING);}
            }
            plugin.getDataFolder().mkdirs();Files.writeString(marker.toPath(),"5 bots per creator; owned bot menus\n");
        }catch(Exception e){throw new IllegalStateException("Cannot migrate owned bot controls",e);}
    }
    static void write(YamlConfiguration y,File file)throws IOException{File temp=new File(file.getParentFile(),file.getName()+".bots-tmp");y.save(temp);Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
}
