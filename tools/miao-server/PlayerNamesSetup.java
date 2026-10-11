package cn.nankexi;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

final class PlayerNamesSetup {
    static void apply(MiaoServer p) {
        File marker=new File(p.getDataFolder(),"player-names-v099.done");if(marker.exists())return;
        File file=new File(p.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus/account.yml");
        try {
            var y=new YamlConfiguration();y.load(file);boolean occupied=false;
            var items=y.getConfigurationSection("items");if(items!=null)for(String key:items.getKeys(false))if(y.getInt("items."+key+".slot",-1)==20&&!(key.equals("frame_20")&&" ".equals(y.getString("items."+key+".display_name"))))occupied=true;
            if(!occupied) {
                try(InputStream in=p.getResource("player-names-account.yml")){var source=new YamlConfiguration();source.load(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8));y.set("items.frame_20",null);y.set("items.player_names",source.getConfigurationSection("items.player_names").getValues(true));}
                File backup=new File(file.getParentFile(),file.getName()+".before-player-names-v099");if(!backup.exists())Files.copy(file.toPath(),backup.toPath());
                File tmp=new File(file.getParentFile(),file.getName()+".nlk-tmp");y.save(tmp);Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(marker.toPath(),"Added names menu entry to free slot only; account data unchanged\n");
        }catch(Exception e){throw new IllegalStateException("Cannot install player names entry",e);}
    }
}
