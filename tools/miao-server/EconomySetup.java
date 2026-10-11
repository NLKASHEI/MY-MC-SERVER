package cn.nankexi;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Narrow one-time economy migration; never writes balances or skills profiles. */
final class EconomySetup {
    static void apply(MiaoServer plugin) {
        panels(plugin);
        File marker=new File(plugin.getDataFolder(),"economy-v098.done");
        if(marker.exists())return;
        try {
            File plugins=plugin.getDataFolder().getParentFile();
            var policy=resource(plugin,"economy-policy.yml");
            File file=new File(plugins,"AuraSkills/config.yml");var y=load(file);
            for(String key:policy.getConfigurationSection("config").getKeys(true))
                if(!policy.isConfigurationSection("config."+key))y.set(key,policy.get("config."+key));
            save(file,y);
            for(String skill:policy.getConfigurationSection("sources").getKeys(false)) {
                file=new File(plugins,"AuraSkills/sources/"+skill+".yml");
                if(!file.exists())copy(plugin,"income-"+skill+".yml",file);
                y=load(file);y.set("default.income",0);
                for(String source:policy.getConfigurationSection("sources."+skill).getKeys(false))
                    if(y.contains("sources."+source))y.set("sources."+source+".income",policy.get("sources."+skill+"."+source));
                if(!skill.equals("mining"))y.set("sources.player.income",0);
                save(file,y);
            }
            for(String category:List.of("nlk_resources","nlk_farming","nlk_drops"))
                for(String folder:List.of("shops","sections")) {
                    file=new File(plugins,"EconomyShopGUI/"+folder+"/"+category+".yml");
                    if(!file.exists())copy(plugin,"economy-"+folder+"-"+category+".yml",file);
                }
            file=new File(plugins.getParentFile(),"permissions.yml");y=new YamlConfiguration();y.options().pathSeparator('|');y.load(file);
            for(String category:List.of("supplies","nlk_resources","nlk_farming","nlk_drops"))
                for(String action:List.of("shop","sellgui"))y.set("survival.player|children|economyshopgui."+action+"."+category,true);
            y.set("survival.player|children|economyshopgui.sellgui",true);save(file,y);
            file=new File(plugins,"DeluxeMenus/gui_menus/market.yml");y=load(file);
            var menu=resource(plugin,"economy-market.yml");
            for(String field:List.of("display_name","lore"))y.set("items.supplies."+field,menu.get("items.supplies."+field));
            for(String name:List.of("sell","income")) {
                int slot=menu.getInt("items."+name+".slot");boolean occupied=false;
                var items=y.getConfigurationSection("items");
                if(items!=null)for(String key:items.getKeys(false))
                    if(!key.equals(name)&&!key.startsWith("border_")&&y.getInt("items."+key+".slot",-1)==slot)occupied=true;
                if(!occupied&&!y.contains("items."+name)) {
                    y.set("items.border_"+slot,null);
                    y.set("items."+name,menu.getConfigurationSection("items."+name).getValues(true));
                }
            }
            save(file,y);
            file=new File(plugins,"Skript/scripts/survival.sk");
            String text=Files.readString(file.toPath(),StandardCharsets.UTF_8);
            String updated=text.replace("主城与生存共用钱包，创造世界禁止交易。","挖天然矿石、亲手打怪自动赚金币；交易中心可出售资源，在线奖励也发金币。");
            if(!updated.equals(text)) {
                backup(file);Files.writeString(file.toPath(),updated,StandardCharsets.UTF_8);
            }
            plugin.getDataFolder().mkdirs();Files.writeString(marker.toPath(),"Source income and three system shop categories installed; balances preserved\n");
            plugin.getLogger().info("挖矿与打怪金币收入、系统收购和手机出售入口已启用。");
        }catch(Exception e){throw new IllegalStateException("Cannot migrate survival economy",e);}
    }
    private static void panels(MiaoServer plugin) {
        File marker=new File(plugin.getDataFolder(),"economy-panels-v098.done");
        if(marker.exists())return;
        try {
            File plugins=plugin.getDataFolder().getParentFile();
            File file=new File(plugins,"DeluxeMenus/gui_menus/systemshop.yml");
            if(!file.exists())copy(plugin,"systemshop-menu.yml",file);
            file=new File(plugins,"DeluxeMenus/config.yml");var y=load(file);
            y.set("gui_menus.systemshop.file","systemshop.yml");save(file,y);
            file=new File(plugins,"DeluxeMenus/gui_menus/survival.yml");y=load(file);
            y.set("items.shop.display_name","&6系统商店");
            y.set("items.shop.lore",List.of("&7基础补给 · 矿产交易 · 农渔收购","&7点击商品购买或出售，玩家集市在商店内","&f点击打开可视化商店"));
            y.set("items.shop.left_click_commands",List.of("[close]","[player] miao shop"));
            y.set("items.shop.right_click_commands",List.of("[close]","[player] miao shop"));save(file,y);
            file=new File(plugins,"DeluxeMenus/gui_menus/market.yml");y=load(file);
            y.set("items.supplies.left_click_commands",List.of("[close]","[player] miao shop"));
            y.set("items.supplies.right_click_commands",List.of("[close]","[player] miao shop"));save(file,y);
            file=new File(plugins,"EconomyShopGUI/config.yml");y=load(file);
            var aliases=new ArrayList<>(y.getStringList("shop-commands"));aliases.removeIf(a->a.equalsIgnoreCase("market"));
            if(!aliases.contains("shop"))aliases.add("shop");y.set("shop-commands",aliases);save(file,y);
            file=new File(plugins,"DeluxeMenus/gui_menus/travel.yml");y=load(file);
            var menu=resource(plugin,"economy-travel.yml");boolean occupied=false;
            for(String key:y.getConfigurationSection("items").getKeys(false))
                if(!key.equals("deathback")&&!key.startsWith("frame_")&&y.getInt("items."+key+".slot",-1)==21)occupied=true;
            if(!occupied&&!y.contains("items.deathback")) {
                y.set("items.frame_21",null);y.set("items.deathback",menu.getConfigurationSection("items.deathback").getValues(true));save(file,y);
            }
            file=new File(plugins.getParentFile(),"permissions.yml");y=new YamlConfiguration();y.options().pathSeparator('|');y.load(file);
            y.set("survival.player|children|essentials.back",true);y.set("survival.player|children|essentials.back.ondeath",true);save(file,y);
            file=new File(plugin.getDataFolder(),"config.yml");y=load(file);
            if(!y.contains("spawn-protection.radius"))y.set("spawn-protection.radius",8);
            if(!y.contains("spawn-protection.worlds"))y.set("spawn-protection.worlds",List.of("world","world_nether","world_the_end","flat","lobby"));save(file,y);
            plugin.getDataFolder().mkdirs();Files.writeString(marker.toPath(),"Visual shop entry, death return and small public spawn protection\n");
        }catch(Exception e){throw new IllegalStateException("Cannot migrate visual shop and survival safety",e);}
    }
    private static YamlConfiguration load(File file)throws Exception {
        var y=new YamlConfiguration();y.load(file);return y;
    }
    private static YamlConfiguration resource(MiaoServer plugin,String name)throws Exception {
        try(InputStream in=plugin.getResource(name)) {
            if(in==null)throw new IOException("Missing resource: "+name);
            var y=new YamlConfiguration();y.load(new InputStreamReader(in,StandardCharsets.UTF_8));return y;
        }
    }
    private static void copy(MiaoServer plugin,String name,File target)throws IOException {
        target.getParentFile().mkdirs();
        try(InputStream in=plugin.getResource(name)) {
            if(in==null)throw new IOException("Missing resource: "+name);
            Files.copy(in,target.toPath());
        }
    }
    private static void backup(File file)throws IOException {
        File old=new File(file.getParentFile(),file.getName()+".before-v098");
        if(file.exists()&&!old.exists())Files.copy(file.toPath(),old.toPath());
    }
    private static void save(File file,YamlConfiguration y)throws IOException {
        backup(file);File temp=new File(file.getParentFile(),file.getName()+".nlk-tmp");
        y.save(temp);Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
}
