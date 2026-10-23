package cn.nankexi;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

final class RelicShopSetup {
    static void apply(MiaoServer plugin){
        File marker=new File(plugin.getDataFolder(),"relic-shop-v0926.done");if(marker.exists())return;
        try{
            File file=new File(plugin.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus/systemshop.yml");var y=new YamlConfiguration();y.load(file);var source=new YamlConfiguration();try(InputStream in=plugin.getResource("systemshop-menu.yml")){source.load(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8));}
            Map<String,String> categories=Map.of("supplies","supplies","resources","nlk_resources","farming","nlk_farming","drops","nlk_drops","technical","nlk_technical");Map<String,Object> moved=new LinkedHashMap<>();
            for(var category:categories.entrySet()){String k="items."+category.getKey();if(y.getStringList(k+".left_click_commands").equals(List.of("[close]","[player] miao shop "+category.getValue()))){moved.put(category.getKey(),y.getConfigurationSection(k).getValues(true));y.set(k,null);}}
            for(String key:new ArrayList<>(y.getConfigurationSection("items").getKeys(false)))if(y.getInt("items."+key+".slot",-1)>=10&&y.getInt("items."+key+".slot",-1)<=16&&" ".equals(y.getString("items."+key+".display_name"))&&y.getStringList("items."+key+".left_click_commands").isEmpty()&&y.getStringList("items."+key+".right_click_commands").isEmpty())y.set("items."+key,null);
            for(var item:moved.entrySet()){int slot=source.getInt("items."+item.getKey()+".slot");ensureFree(y,slot);y.createSection("items."+item.getKey(),(Map<?,?>)item.getValue());y.set("items."+item.getKey()+".slot",slot);}
            if(!y.contains("items.relics")){ensureFree(y,16);y.createSection("items.relics",source.getConfigurationSection("items.relics").getValues(true));}
            for(String name:List.of("systemshop","market")){
                File sellFile=new File(file.getParentFile(),name+".yml");var sell=name.equals("systemshop")?y:new YamlConfiguration();if(!name.equals("systemshop"))sell.load(sellFile);
                var items=sell.getConfigurationSection("items");if(items!=null)for(String item:items.getKeys(false))for(String click:List.of("left_click_commands","right_click_commands")){String path="items."+item+"."+click;List<String> commands=new ArrayList<>(sell.getStringList(path));commands.replaceAll(command->command.equals("[player] sellgui")?"[player] miao sell":command);sell.set(path,commands);}if(!name.equals("systemshop")){Path before=sellFile.toPath().resolveSibling(sellFile.getName()+".before-sell-v0926");if(!Files.exists(before))Files.copy(sellFile.toPath(),before);sell.save(sellFile);}
            }
            Path backup=file.toPath().resolveSibling(file.getName()+".before-relics-v0926");if(!Files.exists(backup))Files.copy(file.toPath(),backup);y.save(file);Files.writeString(marker.toPath(),"Relic shop entry merged; existing prices and player data preserved\n");
        }catch(Exception e){throw new IllegalStateException("Cannot add relic shop entry",e);}
    }
    private static void ensureFree(YamlConfiguration y,int slot)throws IOException{for(String key:y.getConfigurationSection("items").getKeys(false))if(y.getInt("items."+key+".slot",-1)==slot)throw new IOException("Custom shop menu occupies slot "+slot);}
}
