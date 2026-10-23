package cn.nankexi;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Fold direct lobby/survival buttons into world exploration, preserving custom actions. */
final class MenuHierarchyRepair {
    static void apply(MiaoServer p){
        File marker=new File(p.getDataFolder(),"menu-hierarchy-v0926.done");if(marker.exists())return;
        File file=new File(p.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus/survival.yml");
        try{
            var y=YamlConfiguration.loadConfiguration(file);var source=YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(p.getResource("menu-layout.yml")),StandardCharsets.UTF_8));
            for(String key:List.of("lobby","survival")){
                String world=key.equals("survival")?"world":"lobby";
                if(y.getStringList("items."+key+".left_click_commands").equals(List.of("[close]","[player] mv tp w:"+world))&&y.getStringList("items."+key+".right_click_commands").equals(y.getStringList("items."+key+".left_click_commands")))y.set("items."+key,null);
            }
            Map<String,Map<String,Object>> moved=new LinkedHashMap<>();
            for(String key:List.of("flat","homes","travel"))if(y.getConfigurationSection("items."+key)!=null){moved.put(key,new LinkedHashMap<>(y.getConfigurationSection("items."+key).getValues(false)));y.set("items."+key,null);}
            Set<Integer> used=new HashSet<>();var items=y.getConfigurationSection("items");
            if(items!=null)for(String key:new ArrayList<>(items.getKeys(false))){int slot=y.getInt("items."+key+".slot",-1);
                if(slot>=11&&slot<=15&&" ".equals(y.getString("items."+key+".display_name"))&&y.getStringList("items."+key+".left_click_commands").isEmpty()&&y.getStringList("items."+key+".right_click_commands").isEmpty())y.set("items."+key,null);else used.add(slot);
            }
            for(var entry:moved.entrySet()){
                String key=entry.getKey();int slot=source.getInt("items."+key+".slot");if(used.contains(slot)){slot=-1;for(int candidate=10;candidate<17;candidate++)if(!used.contains(candidate)){slot=candidate;break;}}
                if(slot<0)throw new IOException("No free slot; custom main menu retained");y.createSection("items."+key,entry.getValue());y.set("items."+key+".slot",slot);used.add(slot);
            }
            for(int slot:List.of(11,15))if(!used.contains(slot)&&source.getConfigurationSection("items.layout_"+slot)!=null)y.createSection("items.layout_"+slot,source.getConfigurationSection("items.layout_"+slot).getValues(true));
            Path backup=file.toPath().resolveSibling(file.getName()+".before-hierarchy-v0926");if(!Files.exists(backup))Files.copy(file.toPath(),backup);y.save(file);Files.writeString(marker.toPath(),"World navigation folded into existing exploration menu\n");
        }catch(IOException e){throw new IllegalStateException("Cannot consolidate world navigation",e);}
    }
}
