package cn.nankexi;

import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.milkbowl.vault.economy.*;

/** Finite system rewards and fully funded player orders. Ambiguous external transfers require review. */
final class CommunityJobs {
    private final CommunityLife life;
    CommunityJobs(CommunityLife life){this.life=life;validate();}
    private void validate(){
        for(String section:List.of("system-orders","quests")){var definitions=life.config.getConfigurationSection(section);if(definitions==null||definitions.getKeys(false).isEmpty())throw new IllegalStateException("Missing community "+section);
            for(String id:definitions.getKeys(false)){String k=section+"."+id;if(!id.matches("[a-z0-9_]{1,32}")||!CommunityLife.text(life.config.getString(k+".name"),24,false)||!Double.isFinite(life.config.getDouble(k+".reward"))||life.config.getDouble(k+".reward")<=0||life.config.getDouble(k+".reward")>1000000)throw new IllegalStateException("Invalid community reward "+id);
                if(section.equals("system-orders")&&(material(k)==null||life.config.getInt(k+".quantity")<1||life.config.getInt(k+".quantity")>2304))throw new IllegalStateException("Invalid community goods "+id);
                if(section.equals("quests")&&(!Set.of("mined","placed","kills","fish","walk_cm","explore").contains(life.config.getString(k+".metric"))||life.config.getLong(k+".target")<1))throw new IllegalStateException("Invalid community quest "+id);
            }
        }
    }
    private Material material(String key){Material material=Material.matchMaterial(life.config.getString(key+".material",""));return material!=null&&material.isItem()&&!material.isAir()&&material.getMaxStackSize()==64?material:null;}
    private Economy economy(){var service=Bukkit.getServicesManager().getRegistration(Economy.class);return service==null?null:service.getProvider();}
    private List<String> keys(String key){var section=life.data.getConfigurationSection(key);return section==null?List.of():new ArrayList<>(section.getKeys(false));}
    private List<String> dailyIds(String type,int count){var section=life.config.getConfigurationSection(type);List<String> list=new ArrayList<>(section.getKeys(false));Collections.shuffle(list,new Random(life.today().toEpochDay()^type.hashCode()));return List.copyOf(list.subList(0,Math.min(count,list.size())));}
    private String tx(String id){return "transfers."+id;}
    private String order(String id){return "orders."+id;}
    private String money(double value){return String.format(Locale.ROOT,"%,.0f",value);}
    private boolean allowed(Player p){if(!life.trading(p)){life.say(p,"请回生存或主城办理。");return false;}return true;}
    private boolean hasPending(UUID id){return keys("transfers").stream().anyMatch(key->id.toString().equals(life.data.getString(tx(key)+".player")));}
    private void transfer(String id,String type,Player p,double amount){String k=tx(id);life.data.set(k+".type",type);life.data.set(k+".player",p.getUniqueId().toString());life.data.set(k+".account",p.getName());life.data.set(k+".amount",amount);life.data.set(k+".state","ready");life.data.set(k+".at",System.currentTimeMillis());}
    private boolean fundedBySystem(double amount){return life.data.getDouble("budget."+life.day())+amount<=life.config.getDouble("limits.system-coins-per-day",10000);}
    private void reserveBudget(String day,double amount){life.data.set("budget."+day,life.data.getDouble("budget."+day)+amount);}
    private void finishMoney(String id){
        String k=tx(id),type=life.data.getString(k+".type");UUID player=UUID.fromString(life.data.getString(k+".player"));double amount=life.data.getDouble(k+".amount");
        life.change(()->{if("FUND".equals(type))life.data.set(order(life.data.getString(k+".order"))+".state","open");else if("BUY".equals(type)){String mail="deliveries."+id;life.data.set(mail+".owner",player.toString());ItemStack item=life.data.getItemStack(k+".item");if(item==null)throw new IllegalStateException("Missing paid relic snapshot");life.data.set(mail+".item",item);life.data.set(mail+".material",item.getType().name());life.data.set(mail+".quantity",1);}else if(life.data.getBoolean(k+".income"))life.reports.income(player,amount);life.data.set(k,null);});
    }
    private void runMoney(String id){
        String k=tx(id),type=life.data.getString(k+".type"),state=life.data.getString(k+".state");if(!Set.of("FUND","BUY","CREDIT").contains(type)||!Set.of("ready","failed").contains(state))return;
        Economy economy=economy();if(economy==null)return;UUID owner=UUID.fromString(life.data.getString(k+".player"));double amount=life.data.getDouble(k+".amount");if(!Double.isFinite(amount)||amount<=0)throw new IllegalStateException("Invalid pending community amount");
        life.change(()->life.data.set(k+".state","pending"));EconomyResponse result;
        try{OfflinePlayer player=Bukkit.getOfflinePlayer(owner);result=type.equals("CREDIT")?economy.depositPlayer(player,amount):economy.withdrawPlayer(player,amount);}catch(Exception e){life.plugin.getLogger().warning("Community money transfer requires review: "+id);return;}
        if(result==null)return;
        if(result.transactionSuccess())finishMoney(id);
        else if(type.equals("FUND")||type.equals("BUY"))life.change(()->{if(type.equals("FUND"))life.data.set(order(life.data.getString(k+".order")),null);life.data.set(k,null);});
        else life.change(()->life.data.set(k+".state","failed"));
    }
    void login(Player p){
        for(String id:keys("transfers"))if(p.getUniqueId().toString().equals(life.data.getString(tx(id)+".player"))&&Set.of("ready","failed").contains(life.data.getString(tx(id)+".state"))&&"CREDIT".equals(life.data.getString(tx(id)+".type")))runMoney(id);
        if(hasPending(p.getUniqueId()))life.say(p,"有社区交付待处理，请联系管理员。");
    }
    String createOrder(Player p,Material material,int quantity,double reward){
        if(!allowed(p)||hasPending(p.getUniqueId())){life.say(p,"请先处理待核对交付。");return null;}
        if(!life.config.getStringList("player-orders.materials").contains(material.name())||quantity<1||quantity>2304||!Double.isFinite(reward)||reward<1||reward>life.config.getDouble("limits.max-player-reward",1000000)||reward!=Math.rint(reward)){life.say(p,"物品、数量或奖励不合适。");return null;}
        long own=keys("orders").stream().filter(id->p.getUniqueId().toString().equals(life.data.getString(order(id)+".owner"))&&Set.of("open","funding","delivering").contains(life.data.getString(order(id)+".state"))).count();
        if(own>=life.config.getInt("limits.player-open-orders",5)||keys("orders").stream().filter(id->Set.of("open","funding","delivering").contains(life.data.getString(order(id)+".state"))).count()>=life.config.getInt("limits.total-open-orders",300)){life.say(p,"悬赏名额已满。");return null;}
        Economy economy=economy();if(economy==null||!economy.has(p,reward)){life.say(p,"金币不足。");return null;}
        String id=UUID.randomUUID().toString();life.change(()->{String k=order(id);life.data.set(k+".owner",p.getUniqueId().toString());life.data.set(k+".author",p.getName());life.data.set(k+".material",material.name());life.data.set(k+".quantity",quantity);life.data.set(k+".reward",reward);life.data.set(k+".state","funding");life.data.set(k+".created",System.currentTimeMillis());transfer(id,"FUND",p,reward);life.data.set(tx(id)+".order",id);});runMoney(id);
        life.say(p,"open".equals(life.data.getString(order(id)+".state"))?"悬赏已发布，奖励已托管。":life.data.contains(tx(id))?"扣款待核对，暂未发布。":"扣款未成功。");return id;
    }
    void cancelOrder(Player p,String id){
        String k=order(id);if(!allowed(p)||!p.getUniqueId().toString().equals(life.data.getString(k+".owner"))||!"open".equals(life.data.getString(k+".state")))return;
        String receipt=UUID.randomUUID().toString();double reward=life.data.getDouble(k+".reward");life.change(()->{life.data.set(k+".state","cancelled");transfer(receipt,"CREDIT",p,reward);});runMoney(receipt);life.say(p,life.data.contains(tx(receipt))?"悬赏已取消，退款待补发。":"悬赏已取消，金币已退回。");
    }
    boolean buyRelic(Player p,String id){
        if(!allowed(p)||hasPending(p.getUniqueId()))return false;ItemStack item=life.relics.item(id);if(item==null)return false;double price=life.config.getDouble("relics."+id+".price");Economy economy=economy();if(economy==null||!economy.has(p,price)){life.say(p,"金币不足。");return false;}
        String receipt=UUID.randomUUID().toString();life.change(()->{transfer(receipt,"BUY",p,price);life.data.set(tx(receipt)+".item",item);});runMoney(receipt);boolean success=life.data.contains("deliveries."+receipt);life.say(p,success?"购买成功，请到待领物资领取。":life.data.contains(tx(receipt))?"扣款待核对，暂不重复购买。":"扣款未成功。");return success;
    }
    private boolean plain(ItemStack stack,Material material){return stack!=null&&stack.getType()==material&&!stack.hasItemMeta();}
    int amount(Player p,Material material){int total=0;for(ItemStack item:p.getInventory().getStorageContents())if(plain(item,material))total+=item.getAmount();return total;}
    private void remove(Player p,Material material,int quantity){ItemStack[] contents=p.getInventory().getStorageContents();for(int i=0;i<contents.length&&quantity>0;i++)if(plain(contents[i],material)){int n=Math.min(quantity,contents[i].getAmount());quantity-=n;contents[i]=contents[i].clone();contents[i].setAmount(contents[i].getAmount()-n);if(contents[i].getAmount()==0)contents[i]=null;}if(quantity!=0)throw new IllegalStateException("Community inventory changed during delivery");p.getInventory().setStorageContents(contents);p.saveData();}
    private boolean room(Player p,Material material,int quantity){int space=0;for(ItemStack item:p.getInventory().getStorageContents())if(item==null||item.getType().isAir())space+=material.getMaxStackSize();else if(plain(item,material))space+=material.getMaxStackSize()-item.getAmount();return space>=quantity;}
    private String systemClaim(UUID player,String day,String id){return life.player(player)+".orders."+day+"."+id;}
    private void finishTake(String receipt){
        String k=tx(receipt),source=life.data.getString(k+".source"),id=life.data.getString(k+".source-id"),day=life.data.getString(k+".day");UUID player=UUID.fromString(life.data.getString(k+".player"));
        life.change(()->{
            if(source.equals("SYSTEM"))life.data.set(systemClaim(player,day,id),"done");
            else{String o=order(id);life.data.set(o+".state","completed");life.data.set(o+".worker",player.toString());String mail="deliveries."+id;life.data.set(mail+".owner",life.data.getString(o+".owner"));life.data.set(mail+".material",life.data.getString(k+".material"));life.data.set(mail+".quantity",life.data.getInt(k+".quantity"));}
            life.reports.completed(player);life.data.set(k+".type","CREDIT");life.data.set(k+".state","ready");life.data.set(k+".income",true);
        });runMoney(receipt);
    }
    boolean deliver(Player p,String source,String id){
        if(!allowed(p)||hasPending(p.getUniqueId())){life.say(p,"请先处理待核对交付。");return false;}
        String day=life.day(),path;Material material;int quantity;double reward;
        if(source.equals("SYSTEM")){
            if(!dailyIds("system-orders",3).contains(id)||life.data.contains(systemClaim(p.getUniqueId(),day,id)))return false;path="system-orders."+id;material=material(path);quantity=life.config.getInt(path+".quantity");reward=life.config.getDouble(path+".reward");if(!fundedBySystem(reward)){life.say(p,"今日系统奖金已发完。");return false;}
        }else if(source.equals("PLAYER")){
            path=order(id);if(!"open".equals(life.data.getString(path+".state"))||p.getUniqueId().toString().equals(life.data.getString(path+".owner")))return false;material=Material.matchMaterial(life.data.getString(path+".material",""));quantity=life.data.getInt(path+".quantity");reward=life.data.getDouble(path+".reward");
        }else return false;
        if(material==null||amount(p,material)<quantity){life.say(p,"普通物品数量不足。");return false;}
        String receipt=UUID.randomUUID().toString();Material goods=material;int needed=quantity;double payment=reward;
        life.change(()->{transfer(receipt,"TAKE",p,payment);String k=tx(receipt);life.data.set(k+".state","item-review");life.data.set(k+".source",source);life.data.set(k+".source-id",id);life.data.set(k+".day",day);life.data.set(k+".material",goods.name());life.data.set(k+".quantity",needed);life.data.set(k+".before-count",amount(p,goods));
            if(source.equals("SYSTEM")){life.data.set(systemClaim(p.getUniqueId(),day,id),"pending");reserveBudget(day,payment);}else life.data.set(order(id)+".state","delivering");
        });
        // An inventory save and Vault cannot share a transaction. Persist the intent, never blindly replay it.
        try{remove(p,goods,needed);}catch(Exception e){life.plugin.getLogger().log(java.util.logging.Level.SEVERE,"Community inventory delivery requires review: "+receipt,e);life.say(p,"交付待核对，请联系管理员。");return false;}
        finishTake(receipt);life.say(p,life.data.contains(tx(receipt))?"交货完成，金币待补发。":"交货完成，获得 "+money(payment)+" 金币。");return true;
    }
    void collect(Player p,String id){
        String k="deliveries."+id;if(!allowed(p)||hasPending(p.getUniqueId())||!p.getUniqueId().toString().equals(life.data.getString(k+".owner")))return;
        Material material=Material.matchMaterial(life.data.getString(k+".material",""));ItemStack special=life.data.getItemStack(k+".item");int quantity=life.data.getInt(k+".quantity");if(material==null||quantity<1)return;if(special==null?!room(p,material,quantity):p.getInventory().firstEmpty()<0){life.say(p,"背包空间不足。");return;}
        String receipt=UUID.randomUUID().toString();life.change(()->{transfer(receipt,"GIVE",p,0);life.data.set(tx(receipt)+".state","item-review");life.data.set(tx(receipt)+".delivery",id);life.data.set(tx(receipt)+".material",material.name());life.data.set(tx(receipt)+".quantity",quantity);life.data.set(k+".quantity",0);});
        try{int left=quantity;while(left>0){int n=Math.min(material.getMaxStackSize(),left);if(!p.getInventory().addItem(special==null?new ItemStack(material,n):special.clone()).isEmpty())throw new IllegalStateException("Community collection inventory changed");left-=n;}p.saveData();}catch(Exception e){life.plugin.getLogger().log(java.util.logging.Level.SEVERE,"Community collection requires review: "+receipt,e);life.say(p,"领取待核对，请联系管理员。");return;}
        life.change(()->{life.data.set(k,null);life.data.set(tx(receipt),null);});life.say(p,"物资已领取。");
    }
    void claimQuest(Player p,String id){
        if(!allowed(p)||hasPending(p.getUniqueId())||!dailyIds("quests",3).contains(id))return;life.reports.sample(p);String path="quests."+id,key=life.player(p.getUniqueId())+".quests."+life.day()+"."+id;
        double reward=life.config.getDouble(path+".reward");if(life.data.getBoolean(key)||life.reports.current(p,life.config.getString(path+".metric"))<life.config.getLong(path+".target"))return;
        if(!fundedBySystem(reward)){life.say(p,"今日系统奖金已发完。");return;}
        String receipt=UUID.randomUUID().toString();life.change(()->{life.data.set(key,true);reserveBudget(life.day(),reward);transfer(receipt,"CREDIT",p,reward);life.data.set(tx(receipt)+".income",true);life.reports.completed(p.getUniqueId());});runMoney(receipt);life.say(p,life.data.contains(tx(receipt))?"委托完成，金币待补发。":"委托完成，获得 "+money(reward)+" 金币。");
    }
    void quests(Player p){
        life.reports.sample(p);List<CommunityMenu.Entry> entries=new ArrayList<>();for(String id:dailyIds("quests",3)){
            String k="quests."+id;long current=life.reports.current(p,life.config.getString(k+".metric")),target=life.config.getLong(k+".target");boolean claimed=life.data.getBoolean(life.player(p.getUniqueId())+".quests."+life.day()+"."+id);
            entries.add(new CommunityMenu.Entry(claimed?Material.GRAY_DYE:current>=target?Material.LIME_DYE:Material.TARGET,"§a"+life.config.getString(k+".name"),List.of("§7"+Math.min(current,target)+" / "+target+" · "+money(life.config.getDouble(k+".reward"))+" 金币",claimed?"§7已领取":"§f完成后点击领取"),()->{claimQuest(p,id);quests(p);}));
        }
        life.menus.show(p,"趣味委托","每日零点换新，自由完成。",entries,0,()->life.home(p),List.of(new CommunityMenu.Entry(Material.CLOCK,"§f刷新进度",List.of(),()->quests(p))));
    }
    void orders(Player p,int page,boolean own){
        List<CommunityMenu.Entry> entries=new ArrayList<>();
        if(!own)for(String id:dailyIds("system-orders",3)){String k="system-orders."+id;boolean done=life.data.contains(systemClaim(p.getUniqueId(),life.day(),id));entries.add(new CommunityMenu.Entry(done?Material.GRAY_DYE:material(k),"§6系统 · "+life.config.getString(k+".name"),List.of("§7"+life.config.getInt(k+".quantity")+" 个 · "+money(life.config.getDouble(k+".reward"))+" 金币",done?"§7今日已交付":"§f点击交货"),()->{if(!done)confirmDelivery(p,"SYSTEM",id);else orders(p,page,false);}));}
        for(String id:keys("orders")){String k=order(id);if(!"open".equals(life.data.getString(k+".state")))continue;if(own&&!p.getUniqueId().toString().equals(life.data.getString(k+".owner")))continue;
            Material material=Material.matchMaterial(life.data.getString(k+".material",""));if(material==null)continue;boolean owner=p.getUniqueId().toString().equals(life.data.getString(k+".owner"));
            entries.add(new CommunityMenu.Entry(material,"§b"+life.data.getString(k+".author")+"的收购",List.of("§7"+label(material)+" × "+life.data.getInt(k+".quantity"),"§6奖励 "+money(life.data.getDouble(k+".reward"))+" 金币",owner?"§7点击撤单":"§f点击交货"),()->{if(owner)life.menus.confirm(p,"撤销悬赏","未交付的悬赏退回全部奖励。",()->{cancelOrder(p,id);orders(p,0,true);},()->orders(p,page,own));else confirmDelivery(p,"PLAYER",id);}));
        }
        life.menus.show(p,own?"我的悬赏":"悬赏板","交普通物品，奖励已托管。",entries,page,()->life.home(p),List.of(new CommunityMenu.Entry(Material.WRITABLE_BOOK,"§a发布收购",List.of("§7手持要收购的普通物品"),()->draft(p)),new CommunityMenu.Entry(Material.BOOK,"§f"+(own?"全部悬赏":"我的悬赏"),List.of(),()->orders(p,0,!own)),new CommunityMenu.Entry(Material.CHEST,"§e领取物资",List.of(),()->mail(p)),new CommunityMenu.Entry(Material.GOLD_NUGGET,"§6领取待发金币",List.of(),()->{login(p);orders(p,0,own);})));
    }
    private void confirmDelivery(Player p,String source,String id){String k=source.equals("SYSTEM")?"system-orders."+id:order(id);Material material=source.equals("SYSTEM")?material(k):Material.matchMaterial(life.data.getString(k+".material",""));if(material==null)return;int quantity=source.equals("SYSTEM")?life.config.getInt(k+".quantity"):life.data.getInt(k+".quantity");double reward=source.equals("SYSTEM")?life.config.getDouble(k+".reward"):life.data.getDouble(k+".reward");life.menus.confirm(p,"确认交货",label(material)+" × "+quantity+"\n获得 "+money(reward)+" 金币",()->{deliver(p,source,id);orders(p,0,false);},()->orders(p,0,false));}
    private void draft(Player p){
        if(!allowed(p))return;ItemStack held=p.getInventory().getItemInMainHand();Material material=held.getType();if(!plain(held,material)||!life.config.getStringList("player-orders.materials").contains(material.name())){life.say(p,"请手持可收购的普通材料。");return;}
        life.input(p,"收购数量","1–2304 个","64",number->{int quantity;try{quantity=Integer.parseInt(number);if(quantity<1||quantity>2304)throw new NumberFormatException();}catch(NumberFormatException e){life.say(p,"数量不合适。");return;}
            life.input(p,"总奖励","整数金币，发布时托管","100",value->{double reward;try{reward=Double.parseDouble(value);if(!Double.isFinite(reward)||reward<1||reward>life.config.getDouble("limits.max-player-reward",1000000)||reward!=Math.rint(reward))throw new NumberFormatException();}catch(NumberFormatException e){life.say(p,"奖励不合适。");return;}
                life.menus.confirm(p,"发布收购",label(material)+" × "+quantity+"\n托管 "+money(reward)+" 金币",()->{createOrder(p,material,quantity,reward);orders(p,0,true);},()->orders(p,0,false));});});
    }
    void mail(Player p){List<CommunityMenu.Entry> entries=new ArrayList<>();for(String id:keys("deliveries")){String k="deliveries."+id;if(!p.getUniqueId().toString().equals(life.data.getString(k+".owner"))||life.data.getInt(k+".quantity")<1)continue;Material material=Material.matchMaterial(life.data.getString(k+".material",""));ItemStack special=life.data.getItemStack(k+".item");if(material!=null)entries.add(new CommunityMenu.Entry(material,special==null?"§e"+label(material):special.getItemMeta().getDisplayName(),List.of("§7"+(special==null?life.data.getInt(k+".quantity"):special.getAmount())+" 个 · 点击领取"),()->{collect(p,id);mail(p);}));}life.menus.show(p,"待领物资","留出背包空间。",entries,0,()->orders(p,0,true),List.of());}
    private String label(Material material){return life.config.getString("material-names."+material.name(),MiaoInterface.blockName(material));}
    boolean admin(org.bukkit.command.CommandSender sender,String[] args){
        if(!sender.hasPermission("miao.community.admin")||(sender instanceof Player p&&!life.ui.authenticated(p)))return true;
        if(args.length==0){sender.sendMessage("/communityadmin list | review 交易ID | resolve 交易ID paid|unpaid|consumed|untouched|given");return true;}
        if(args[0].equals("list")){for(String id:keys("transfers"))sender.sendMessage(id+" / "+life.data.getString(tx(id)+".type")+" / "+life.data.getString(tx(id)+".state")+" / "+life.data.getString(tx(id)+".account"));return true;}
        if(args.length<2||!args[1].matches("[0-9a-f-]{36}")||!life.data.contains(tx(args[1])))return true;String id=args[1],k=tx(id),type=life.data.getString(k+".type");
        if(args[0].equals("review")){sender.sendMessage(life.data.getConfigurationSection(k).getValues(false).toString());return true;}
        if(!args[0].equals("resolve")||args.length!=3)return true;String decision=args[2];
        if(Set.of("FUND","BUY","CREDIT").contains(type)&&decision.equals("paid"))finishMoney(id);
        else if(type.equals("BUY")&&decision.equals("unpaid"))life.change(()->life.data.set(k,null));
        else if(type.equals("FUND")&&decision.equals("unpaid"))life.change(()->{life.data.set(order(life.data.getString(k+".order")),null);life.data.set(k,null);});
        else if(type.equals("CREDIT")&&decision.equals("unpaid"))life.change(()->life.data.set(k+".state","ready"));
        else if(type.equals("TAKE")&&decision.equals("consumed"))finishTake(id);
        else if(type.equals("TAKE")&&decision.equals("untouched"))life.change(()->{String source=life.data.getString(k+".source"),sourceId=life.data.getString(k+".source-id");UUID player=UUID.fromString(life.data.getString(k+".player"));if(source.equals("SYSTEM")){life.data.set(systemClaim(player,life.data.getString(k+".day"),sourceId),null);reserveBudget(life.data.getString(k+".day"),-life.data.getDouble(k+".amount"));}else life.data.set(order(sourceId)+".state","open");life.data.set(k,null);});
        else if(type.equals("GIVE")&&Set.of("given","untouched").contains(decision))life.change(()->{String delivery="deliveries."+life.data.getString(k+".delivery");if(decision.equals("given"))life.data.set(delivery,null);else life.data.set(delivery+".quantity",life.data.getInt(k+".quantity"));life.data.set(k,null);});
        else{sender.sendMessage("处理类型不匹配。");return true;}sender.sendMessage("已记录人工核对结果。");return true;
    }
}
