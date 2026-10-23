package cn.nankexi;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

final class CommunitySetup {
    static void apply(MiaoServer plugin){
        try{
            File config=new File(plugin.getDataFolder(),"community.yml");if(!config.exists())try(InputStream in=plugin.getResource("community.yml")){Files.copy(Objects.requireNonNull(in),config.toPath());}
            File marker=new File(plugin.getDataFolder(),"community-menu-v0926.done");if(marker.exists())return;
            File file=new File(plugin.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus/survival.yml");var y=new YamlConfiguration();y.load(file);var source=YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(plugin.getResource("menu-layout.yml")),StandardCharsets.UTF_8));
            for(String id:List.of("community","report"))if(!y.contains("items."+id)){
                int slot=source.getInt("items."+id+".slot");boolean occupied=false;var items=y.getConfigurationSection("items");
                if(items!=null)for(String key:new ArrayList<>(items.getKeys(false)))if(y.getInt("items."+key+".slot",-1)==slot){
                    if(" ".equals(y.getString("items."+key+".display_name"))&&y.getStringList("items."+key+".left_click_commands").isEmpty()&&y.getStringList("items."+key+".right_click_commands").isEmpty())y.set("items."+key,null);else occupied=true;
                }
                if(occupied)throw new IOException("Community menu slot occupied; merge custom menu before retrying");
                y.createSection("items."+id,source.getConfigurationSection("items."+id).getValues(true));
            }
            Path copy=file.toPath().resolveSibling(file.getName()+".before-community-v0926");if(!Files.exists(copy))Files.copy(file.toPath(),copy);y.save(file);Files.writeString(marker.toPath(),"Community entry and personal report merged; player data untouched\n");
        }catch(Exception e){throw new IllegalStateException("Cannot prepare community features",e);}
    }
}
