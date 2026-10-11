package cn.nankexi;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Bounded migration: legacy claims/worlds remain intact, only creation routes change. */
final class ClaimsSetup {
    static void apply(MiaoServer plugin) {
        File marker=new File(plugin.getDataFolder(),"claims-3d-v098.done");
        if(marker.exists())return;
        try {
            File plugins=plugin.getDataFolder().getParentFile();
            File file=new File(plugins,"GriefPreventionData/config.yml");var y=load(file);
            y.set("GriefPrevention.Claims.ModificationTool","IRON_HOE");
            y.set("GriefPrevention.Claims.AutomaticNewPlayerClaimsRadius",-1);save(file,y);
            file=new File(plugins.getParentFile(),"permissions.yml");y=new YamlConfiguration();y.options().pathSeparator('|');y.load(file);
            y.set("survival.player|children|griefprevention.createclaims",false);
            y.set("survival.player|children|survival.claims",true);save(file,y);
            file=new File(plugin.getDataFolder(),"config.yml");y=load(file);
            if(!y.contains("claims-3d.max-side"))y.set("claims-3d.max-side",25);
            if(!y.contains("claims-3d.worlds"))y.set("claims-3d.worlds",List.of("world","world_nether","world_the_end"));
            if(!y.contains("sleep.worlds"))y.set("sleep.worlds",List.of("world"));save(file,y);
            for(String menu:List.of("claims","claim_confirm")) {
                file=new File(plugins,"DeluxeMenus/gui_menus/"+menu+".yml");y=load(file);
                try(InputStream in=plugin.getResource("claims-3d-"+menu+".yml")) {
                    if(in==null)throw new IOException("Missing claims menu resource");
                    var template=new YamlConfiguration();template.load(new InputStreamReader(in,StandardCharsets.UTF_8));
                    List<String> items=menu.equals("claims")?List.of("tutorial","quick","list","trust","access","trustlist","untrust"):List.of("confirm");
                    for(String item:items)for(String key:List.of("display_name","lore","left_click_commands","right_click_commands"))
                        y.set("items."+item+"."+key,template.get("items."+item+"."+key));
                    save(file,y);
                }
            }
            plugin.getDataFolder().mkdirs();Files.writeString(marker.toPath(),"Strict 25x25x25 WorldGuard claims; legacy claims preserved; majority sleeping\n");
        }catch(Exception e){throw new IllegalStateException("Cannot migrate strict cuboid claims",e);}
    }
    private static YamlConfiguration load(File file)throws Exception{var y=new YamlConfiguration();if(file.exists())y.load(file);return y;}
    private static void save(File file,YamlConfiguration y)throws IOException {
        file=file.getAbsoluteFile();
        file.getParentFile().mkdirs();File backup=new File(file.getParentFile(),file.getName()+".before-claims-v098");
        if(file.exists()&&!backup.exists())Files.copy(file.toPath(),backup.toPath());
        File temp=new File(file.getParentFile(),file.getName()+".nlk-tmp");y.save(temp);Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
}
