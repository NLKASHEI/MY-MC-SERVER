package cn.nankexi;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Install missing map configs only, never replace existing map storage or world data. */
final class BlueMapSetup {
    static void apply(MiaoServer plugin) {
        permanentAddress(plugin);
        File marker=new File(plugin.getDataFolder(),"bluemap-v099.done");if(marker.exists())return;
        File plugins=plugin.getDataFolder().getParentFile();
        try {
            for(String name:List.of("core.conf","plugin.conf","webserver.conf","webapp.conf","storages/file.conf","maps/nlk_lobby.conf","maps/nlk_survival.conf","maps/nlk_nether.conf","maps/nlk_end.conf","maps/nlk_flat.conf")) {
                File file=new File(plugins,"BlueMap/"+name);if(file.exists())continue;
                file.getParentFile().mkdirs();try(InputStream in=plugin.getResource("bluemap/"+name)){if(in==null)throw new IOException("Missing BlueMap resource "+name);Files.copy(in,file.toPath());}
            }
            File file=new File(plugin.getDataFolder(),"config.yml");var y=new YamlConfiguration();y.load(file);
            if(!y.contains("online-map.url")){y.set("online-map.url","http://s9-4.yxsjmc.cn:14514/");save(file,y);}
            file=new File(plugins,"DeluxeMenus/gui_menus/survival.yml");y=new YamlConfiguration();y.load(file);
            boolean occupied=false;for(String key:y.getConfigurationSection("items").getKeys(false))if(!key.equals("online_map")&&y.getInt("items."+key+".slot",-1)==10&&!((key.equals("frame_10")||key.equals("layout_10"))&&" ".equals(y.getString("items."+key+".display_name"))))occupied=true;
            if(!occupied&&!y.contains("items.online_map")) {
                try(InputStream in=plugin.getResource("menu-layout.yml")){var source=new YamlConfiguration();source.load(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8));y.set("items.frame_10",null);y.set("items.layout_10",null);y.set("items.online_map",source.getConfigurationSection("items.online_map").getValues(true));save(file,y);}
            }
            Files.writeString(marker.toPath(),"Missing BlueMap configs installed; existing config/storage preserved; web port 8100\n");
        }catch(Exception e){throw new IllegalStateException("Cannot install BlueMap configs",e);}
    }
    private static void permanentAddress(MiaoServer plugin) {
        File marker=new File(plugin.getDataFolder(),"map-domain-v0927.done");if(marker.exists())return;
        try {
            plugin.getDataFolder().mkdirs();File file=new File(plugin.getDataFolder(),"config.yml");
            var y=new YamlConfiguration();if(file.exists())y.load(file);
            y.set("online-map.url","http://s9-4.yxsjmc.cn:14514/");
            File tmp=new File(file.getParentFile(),"config.yml.map-tmp");y.save(tmp);Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(marker.toPath(),"Public BlueMap URL uses the owner's permanent domain\n");
        }catch(Exception e){throw new IllegalStateException("Cannot update public map address",e);}
    }
    private static void save(File file,YamlConfiguration y)throws IOException {
        File backup=new File(file.getParentFile(),file.getName()+".before-bluemap-v099");if(!backup.exists())Files.copy(file.toPath(),backup.toPath());
        File tmp=new File(file.getParentFile(),file.getName()+".nlk-tmp");y.save(tmp);Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
}
