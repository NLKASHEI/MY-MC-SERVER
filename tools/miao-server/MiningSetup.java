package cn.nankexi;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Merge only the new coverage key and known old ore descriptions. */
final class MiningSetup {
    static void apply(MiaoServer plugin){
        File marker=new File(plugin.getDataFolder(),"mining-coverage-v0920.done");if(marker.exists())return;
        try{
            File file=new File(plugin.getDataFolder(),"config.yml");var y=new YamlConfiguration();y.load(file);
            if(!y.contains("resource-world.ore-coverage-percent")){y.set("resource-world.ore-coverage-percent",30);write(file,y);}
            for(String menu:List.of("worlds","resource")){
                file=new File(plugin.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus/"+menu+".yml");if(!file.exists())continue;
                y=new YamlConfiguration();y.load(file);String key=menu.equals("worlds")?"items.mining.lore":"items.rules.lore";
                var lore=new ArrayList<>(y.getStringList(key));boolean changed=false;
                for(int i=0;i<lore.size();i++){
                    String old=lore.get(i),updated=old.replace("原版地形 · 新区块矿藏约增加 50%","新区块石质层约 30% 矿石 · 煤铁铜为主").replace("矿藏约 +50% · 与生存共用背包","石质层约 30% 矿石 · 与生存共用背包");
                    if(!old.equals(updated)){lore.set(i,updated);changed=true;}
                }
                if(changed){y.set(key,lore);write(file,y);}
            }
            Files.writeString(marker.toPath(),"Coverage key and known ore descriptions merged; no existing chunks replaced\n");
        }catch(Exception e){throw new IllegalStateException("Cannot merge mining coverage settings",e);}
    }
    private static void write(File file,YamlConfiguration y)throws IOException{
        Path backup=file.toPath().resolveSibling(file.getName()+".before-mining-v0920");if(!Files.exists(backup))Files.copy(file.toPath(),backup);y.save(file);
    }
}
