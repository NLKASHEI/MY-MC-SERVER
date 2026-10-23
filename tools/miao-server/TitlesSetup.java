package cn.nankexi;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Install catalog only if missing and add a single independent, centered title button. */
final class TitlesSetup {
    static void apply(MiaoServer p){
        File catalog=new File(p.getDataFolder(),"titles.yml");
        try{
            if(!catalog.exists())try(InputStream in=p.getResource("title-catalog.yml")){Files.copy(Objects.requireNonNull(in),catalog.toPath());}
            File marker=new File(p.getDataFolder(),"titles-menu-v0926.done");if(marker.exists())return;
            File file=new File(p.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus/survival.yml");var current=YamlConfiguration.loadConfiguration(file);
            var source=YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(p.getResource("menu-layout.yml")),StandardCharsets.UTF_8));
            if(!current.contains("items.titles")){
                var items=current.getConfigurationSection("items");int slot=-1;
                for(int candidate:List.of(4,11,15)){
                    boolean free=true;if(items!=null)for(String key:new ArrayList<>(items.getKeys(false)))if(current.getInt("items."+key+".slot",-1)==candidate){
                        if((key.equals("heading")||" ".equals(current.getString("items."+key+".display_name")))&&current.getStringList("items."+key+".left_click_commands").isEmpty()&&current.getStringList("items."+key+".right_click_commands").isEmpty())current.set("items."+key,null);else free=false;
                    }if(free){slot=candidate;break;}
                }
                if(slot<0)throw new IOException("No safe slot for title button; retain custom menu and inspect conflicts");
                current.createSection("items.titles",source.getConfigurationSection("items.titles").getValues(true));current.set("items.titles.slot",slot);
                Path backup=file.toPath().resolveSibling(file.getName()+".before-titles-v0926");if(!Files.exists(backup))Files.copy(file.toPath(),backup);current.save(file);
            }
            Files.writeString(marker.toPath(),"Installed optional cosmetic title menu and catalog; player ownership untouched\n");
        }catch(IOException e){throw new IllegalStateException("Cannot prepare title catalog/menu",e);}
    }
}
