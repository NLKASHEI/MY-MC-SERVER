package cn.nankexi;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** User-requested IP registration allowance and bounded account-menu upgrade. */
final class AccountSyncSetup {
    static void apply(MiaoServer plugin){
        File marker=new File(plugin.getDataFolder(),"account-sync-v0921.done");if(marker.exists())return;
        try{
            File plugins=plugin.getDataFolder().getParentFile();
            File file=new File(plugins,"AuthMe/config.yml");var y=new YamlConfiguration();y.load(file);
            y.set("settings.restrictions.maxRegPerIp",0);write(file,y);
            file=new File(plugins,"DeluxeMenus/gui_menus/account.yml");y=new YamlConfiguration();y.load(file);
            try(InputStream in=plugin.getResource("player-names-account.yml")){
                var source=YamlConfiguration.loadConfiguration(new InputStreamReader(java.util.Objects.requireNonNull(in),StandardCharsets.UTF_8));
                for(String key:java.util.List.of("menu_title","items.heading.display_name","items.link.display_name","items.link.lore","items.help.lore"))y.set(key,source.get(key));
                if(!y.contains("items.sync_prompts")){
                    int slot=24;boolean available=true;var items=y.getConfigurationSection("items");
                    if(items!=null)for(String item:items.getKeys(false))if(y.getInt("items."+item+".slot",-1)==slot){if(" ".equals(y.getString("items."+item+".display_name")))y.set("items."+item,null);else available=false;}
                    if(available)y.createSection("items.sync_prompts",source.getConfigurationSection("items.sync_prompts").getValues(true));
                }
                write(file,y);
            }
            Files.createDirectories(marker.toPath().getParent());Files.writeString(marker.toPath(),"Unlimited registrations per IP; optional account sync menu; identities untouched\n");
        }catch(Exception e){throw new IllegalStateException("Cannot update optional account sync configuration",e);}
    }
    private static void write(File file,YamlConfiguration y)throws IOException{
        Path backup=file.toPath().resolveSibling(file.getName()+".before-account-sync-v0921");if(!Files.exists(backup))Files.copy(file.toPath(),backup);y.save(file);
    }
}
