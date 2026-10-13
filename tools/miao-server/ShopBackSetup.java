package cn.nankexi;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Restore native back navigation without touching prices or wallets. */
final class ShopBackSetup {
    static void apply(MiaoServer plugin) {
        File marker=new File(plugin.getDataFolder(),"shop-back-v099.done");if(marker.exists())return;
        File plugins=plugin.getDataFolder().getParentFile();
        try {
            for(String section:List.of("supplies","nlk_resources","nlk_farming","nlk_drops")) {
                File file=new File(plugins,"EconomyShopGUI/sections/"+section+".yml");var y=load(file);y.set("display-item",false);save(file,y);
            }
            File file=new File(plugins,"EconomyShopGUI/config.yml");var y=load(file);
            y.set("disable-back.direct-shop-command",false);y.set("disable-back.direct-shop-command-console",false);y.set("disable-back.sub-sections",false);save(file,y);
            file=new File(plugins,"DeluxeMenus/gui_menus/systemshop.yml");y=load(file);
            for(String click:List.of("left_click_commands","right_click_commands"))y.set("items.back."+click,List.of("[close]","[player] miao menu survival"));save(file,y);
            Files.writeString(marker.toPath(),"Native shop BACK enabled; four categories visible; guide return uses explicit API route\n");
        }catch(Exception e){throw new IllegalStateException("Cannot fix shop back navigation",e);}
    }
    private static YamlConfiguration load(File file)throws Exception{var y=new YamlConfiguration();y.load(file);return y;}
    private static void save(File file,YamlConfiguration y)throws IOException {
        File backup=new File(file.getParentFile(),file.getName()+".before-back-v099");if(!backup.exists())Files.copy(file.toPath(),backup.toPath());
        File temp=new File(file.getParentFile(),file.getName()+".nlk-tmp");y.save(temp);Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
}
