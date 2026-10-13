package cn.nankexi;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Add technical shop entry and literal permissions, without changing existing prices. */
final class TechnicalShopSetup {
    static void apply(MiaoServer plugin) {
        File marker=new File(plugin.getDataFolder(),"technical-shop-farming-v0913.done");if(marker.exists())return;
        try {
            File plugins=plugin.getDataFolder().getParentFile();
            File permissions=new File(plugins.getParentFile(),"permissions.yml");
            var y=new YamlConfiguration();y.options().pathSeparator('|');y.load(permissions);
            y.set("survival.player|children|economyshopgui.shop.nlk_technical",true);save(plugin,permissions,y,"permissions.yml");
            mergeMenu(plugin,new File(plugins,"DeluxeMenus/gui_menus/systemshop.yml"),"systemshop-menu.yml",List.of("supplies","resources","farming","drops","technical"));
            mergeMenu(plugin,new File(plugins,"DeluxeMenus/gui_menus/technical.yml"),"technical-shop-menu.yml",List.of("shop","harvest"));
            plugin.getDataFolder().mkdirs();Files.writeString(marker.toPath(),"Technical catalog menu and permission installed; prices preserved\n");
        }catch(Exception e){throw new IllegalStateException("Cannot install technical shop menus",e);}
    }
    private static void mergeMenu(MiaoServer plugin,File file,String resource,List<String> names)throws Exception {
        var y=new YamlConfiguration();y.load(file);var expected=new YamlConfiguration();
        try(InputStream in=plugin.getResource(resource)){if(in==null)throw new IOException(resource);expected.load(new InputStreamReader(in,StandardCharsets.UTF_8));}
        var items=y.getConfigurationSection("items");if(items==null)throw new IOException("Missing menu items: "+file);
        Set<Integer> slots=new HashSet<>();for(String name:names)slots.add(expected.getInt("items."+name+".slot"));
        for(String key:items.getKeys(false)) {
            if(names.contains(key)||!slots.contains(y.getInt("items."+key+".slot",-1)))continue;
            if(!key.startsWith("frame_")||!y.getStringList("items."+key+".left_click_commands").isEmpty()||!y.getStringList("items."+key+".right_click_commands").isEmpty()) {
                plugin.getLogger().warning("生电商店入口格位与自定义按钮冲突，保留菜单："+file.getName());return;
            }
        }
        for(String key:new ArrayList<>(items.getKeys(false)))if(key.startsWith("frame_")&&slots.contains(y.getInt("items."+key+".slot",-1)))y.set("items."+key,null);
        for(String name:names) {
            String path="items."+name;
            if(!y.contains(path))y.set(path,expected.getConfigurationSection(path).getValues(true));
            else y.set(path+".slot",expected.getInt(path+".slot"));
        }
        save(plugin,file,y,"DeluxeMenus/"+file.getName());
    }
    private static void save(MiaoServer plugin,File file,YamlConfiguration y,String name)throws IOException {
        File prior=new File(plugin.getDataFolder(),"config-backups/v0913/"+name);prior.getParentFile().mkdirs();
        if(file.exists()&&!prior.exists())Files.copy(file.toPath(),prior.toPath());
        File temp=new File(file.getParentFile(),file.getName()+".nlk-tmp");y.save(temp);Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
}

