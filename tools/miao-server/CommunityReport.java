package cn.nankexi;

import java.time.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;

/** Daily deltas start at deployment. Never invent past income or overwrite vanilla statistics. */
final class CommunityReport {
    private final CommunityLife life;
    private final Set<UUID> active=new HashSet<>();
    private static final List<String> METRICS=List.of("seconds","mined","placed","kills","fish","walk_cm");
    CommunityReport(CommunityLife life){this.life=life;}
    private Map<String,Long> values(Player p){return Map.of("seconds",life.ui.titleMetric(p,"seconds"),"mined",life.ui.titleMetric(p,"mined"),"placed",life.ui.titleMetric(p,"placed"),"kills",Math.max(0L,p.getStatistic(Statistic.MOB_KILLS)),"fish",Math.max(0L,p.getStatistic(Statistic.FISH_CAUGHT)),"walk_cm",Math.max(0L,p.getStatistic(Statistic.WALK_ONE_CM)));}
    void baseline(Player p){if(!life.ui.authenticated(p))return;String base=life.player(p.getUniqueId())+".sample.";values(p).forEach((metric,value)->life.data.set(base+metric,Math.max(value,life.data.getLong(base+metric))));active.add(p.getUniqueId());}
    void forget(Player p){active.remove(p.getUniqueId());}
    String daily(Player p){return life.player(p.getUniqueId())+".days."+life.day();}
    void sample(Player p){
        if(!life.ui.authenticated(p))return;
        if(!active.contains(p.getUniqueId())){baseline(p);return;}
        String sample=life.player(p.getUniqueId())+".sample.",day=daily(p);boolean survival=life.survival(p);
        values(p).forEach((metric,value)->{long before=life.data.getLong(sample+metric),delta=Math.max(0,value-before);if(value>before)life.data.set(sample+metric,value);
            if(delta>0&&(Set.of("seconds","mined","placed").contains(metric)||survival))life.data.set(day+"."+metric,life.data.getLong(day+"."+metric)+delta);
        });
        if(survival){List<String> biomes=new ArrayList<>(life.data.getStringList(day+".biome-keys"));String biome=p.getLocation().getBlock().getBiome().getKey().toString();if(biomes.size()<128&&!biomes.contains(biome)){biomes.add(biome);life.data.set(day+".biome-keys",biomes);life.data.set(day+".explore",biomes.size());}}
        // Keep a bounded diary; owned landmarks, escrow and unresolved transactions are never pruned.
        var days=life.data.getConfigurationSection(life.player(p.getUniqueId())+".days");if(days!=null)for(String key:new ArrayList<>(days.getKeys(false)))try{if(LocalDate.parse(key).isBefore(life.today().minusDays(90)))days.set(key,null);}catch(DateTimeException ignored){}
    }
    long current(Player p,String metric){return life.data.getLong(daily(p)+"."+metric);}
    long sum(Player p,int length,String metric){long result=0;for(int i=0;i<length;i++)result+=life.data.getLong(life.player(p.getUniqueId())+".days."+life.today().minusDays(i)+"."+metric);return result;}
    void income(UUID id,double amount){String key=life.player(id)+".days."+life.day()+".earned";life.data.set(key,life.data.getDouble(key)+amount);}
    void completed(UUID id){String key=life.player(id)+".days."+life.day()+".completed";life.data.set(key,life.data.getLong(key)+1);}
    void menu(Player p,int length){
        sample(p);String period=length==1?"今日":length==30?"近 30 天":"近 7 天";List<CommunityMenu.Entry> entries=new ArrayList<>();
        entries.add(new CommunityMenu.Entry(Material.CLOCK,"§b游玩时长",List.of("§f"+hours(sum(p,length,"seconds")),"§7累计："+hours(life.ui.titleMetric(p,"seconds"))),()->{}));
        entries.add(new CommunityMenu.Entry(Material.IRON_PICKAXE,"§a挖掘与建造",List.of("§7挖掘 §f"+sum(p,length,"mined"),"§7放置 §f"+sum(p,length,"placed")),()->{}));
        entries.add(new CommunityMenu.Entry(Material.IRON_SWORD,"§d冒险收获",List.of("§7击败生物 §f"+sum(p,length,"kills"),"§7钓鱼 §f"+sum(p,length,"fish"),String.format(Locale.ROOT,"§7步行 §f%.2f km",sum(p,length,"walk_cm")/100000.0)),()->{}));
        entries.add(new CommunityMenu.Entry(Material.GOLD_INGOT,"§6委托收入",List.of("§f"+sum(p,length,"earned")+" 金币","§7完成 §f"+sum(p,length,"completed")+" 笔"),()->{}));
        long best=0;var days=life.data.getConfigurationSection(life.player(p.getUniqueId())+".days");if(days!=null)for(String day:days.getKeys(false))best=Math.max(best,days.getLong(day+".mined"));
        entries.add(new CommunityMenu.Entry(Material.WRITABLE_BOOK,"§e我的纪录",List.of("§7最高单日挖掘 §f"+best,"§7称号 §f"+life.ui.titles.placeholder(p.getUniqueId(),"owned")),()->{}));
        for(int i=0;i<entries.size();i++){var card=entries.get(i);entries.set(i,new CommunityMenu.Entry(card.icon(),card.name(),card.lore(),()->life.menus.info(p,card.name(),card.lore(),()->menu(p,length))));}
        life.menus.show(p,"个人小报 · "+period,"从本功能上线起记录。",entries,0,()->life.ui.command(p,new String[]{"menu"}),List.of(new CommunityMenu.Entry(Material.SUNFLOWER,"§f今日",List.of(),()->menu(p,1)),new CommunityMenu.Entry(Material.PAPER,"§f近 7 天",List.of(),()->menu(p,7)),new CommunityMenu.Entry(Material.MAP,"§f近 30 天",List.of(),()->menu(p,30))));
    }
    private String hours(long seconds){return seconds/3600+" 小时 "+seconds/60%60+" 分";}
}
