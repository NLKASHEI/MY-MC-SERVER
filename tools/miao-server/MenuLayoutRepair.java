package cn.nankexi;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Layout-only upgrade for known buttons; retain custom actions, prices and world state. */
final class MenuLayoutRepair {
    static void apply(MiaoServer plugin){
        File marker=new File(plugin.getDataFolder(),"menu-layout-v0919.done");if(marker.exists())return;
        Map<String,String> resources=Map.of("survival","menu-layout.yml","worlds","expanded-worlds-menu.yml","resource","resource-world-menu.yml","technical","technical-menu.yml","claims","claims-3d-claims.yml");
        try{
            for(var entry:resources.entrySet()){
                String name=entry.getKey();File file=new File(plugin.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus/"+name+".yml");
                var current=new YamlConfiguration();if(file.exists())current.load(file);
                var source=new YamlConfiguration();try(InputStream in=plugin.getResource(entry.getValue())){source.load(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8));}
                var original=current.getConfigurationSection("items");var expected=Objects.requireNonNull(source.getConfigurationSection("items"));
                boolean customized=false;
                if(original!=null)for(String key:original.getKeys(false)){
                    boolean decoration=key.matches("(?:frame|layout|border)_\\d+");
                    boolean action=!current.getStringList("items."+key+".left_click_commands").isEmpty()||!current.getStringList("items."+key+".right_click_commands").isEmpty();
                    if((decoration&&action)||(!decoration&&!expected.contains(key))){customized=true;break;}
                }
                if(customized){plugin.getLogger().warning(name+" 菜单存在额外自定义按钮，保留布局，请按新版模板合并。");continue;}
                Map<String,Map<String,Object>> old=new HashMap<>();
                if(original!=null)for(String key:original.getKeys(false)){var section=original.getConfigurationSection(key);if(section!=null)old.put(key,new LinkedHashMap<>(section.getValues(false)));}
                current.set("items",null);current.set("size",source.getInt("size"));
                for(String key:expected.getKeys(false)){
                    var definition=Objects.requireNonNull(expected.getConfigurationSection(key));
                    boolean action=!definition.getStringList("left_click_commands").isEmpty();
                    boolean changedRoute=name.equals("survival")&&key.equals("flat");
                    Map<String,Object> values=action&&!changedRoute&&old.containsKey(key)?old.get(key):new LinkedHashMap<>(definition.getValues(false));
                    values.put("slot",definition.getInt("slot"));
                    // Old multi-slot state cannot retain locations outside the new layout.
                    values.remove("slots");current.createSection("items."+key,values);
                }
                File before=new File(file.getParentFile(),file.getName()+".before-ui-v0919");if(file.exists()&&!before.exists())Files.copy(file.toPath(),before.toPath());
                File tmp=new File(file.getParentFile(),file.getName()+".nlk-tmp");current.save(tmp);Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
            }
            plugin.getDataFolder().mkdirs();Files.writeString(marker.toPath(),"Aligned five known menus; world selector reachable; unrelated custom buttons retained\n");
        }catch(Exception e){throw new IllegalStateException("Cannot repair menu layouts",e);}
    }
}
