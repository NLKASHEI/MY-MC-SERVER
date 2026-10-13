package cn.nankexi;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Repair missing catalog fields even when an earlier migration marker exists. */
final class ShopCatalogRepair {
    static void prepare(MiaoServer plugin) { apply(plugin,false); }
    static void apply(MiaoServer plugin) { apply(plugin,true); }
    private static void apply(MiaoServer plugin, boolean finish) {
        isolateBackups(plugin);
        File marker=new File(plugin.getDataFolder(),"shop-catalog-v0913.done");
        boolean first=finish&&!marker.exists();
        try {
            for(String category:List.of("supplies","nlk_resources","nlk_farming","nlk_drops","nlk_technical")) {
                for(String folder:List.of("sections","shops")) {
                    File file=new File(plugin.getDataFolder().getParentFile(),"EconomyShopGUI/"+folder+"/"+category+".yml");
                    var current=new YamlConfiguration();if(file.exists())current.load(file);
                    var expected=new YamlConfiguration();
                    try(InputStream in=plugin.getResource("economy-"+folder+"-"+category+".yml")) {
                        if(in==null)throw new IOException("Missing catalog resource: "+category);
                        expected.load(new InputStreamReader(in,StandardCharsets.UTF_8));
                    }
                    boolean changed=false;
                    // Sections need display fields. Existing shop pages/prices are authoritative;
                    // only restore complete pages when the catalog is absent or empty.
                    if(folder.equals("sections")||!current.isConfigurationSection("pages")||current.getConfigurationSection("pages").getKeys(false).isEmpty()) {
                        for(String key:expected.getKeys(true))if(!expected.isConfigurationSection(key)&&!current.contains(key)) {
                            current.set(key,expected.get(key));changed=true;
                        }
                    }
                    if(first&&folder.equals("sections"))for(String key:List.of("enable","display-item")) {
                        boolean desired=key.equals("enable");
                        if(current.getBoolean(key)!=desired){current.set(key,desired);changed=true;}
                    }
                    if(changed) {
                        file.getParentFile().mkdirs();
                        File prior=new File(plugin.getDataFolder(),"config-backups/v0913/EconomyShopGUI/"+folder+"/"+file.getName()+".before-catalog-v0913");
                        prior.getParentFile().mkdirs();
                        if(file.exists()&&!prior.exists())Files.copy(file.toPath(),prior.toPath());
                        File temp=new File(file.getParentFile(),file.getName()+".nlk-tmp");current.save(temp);
                        Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
                        plugin.getLogger().info("已补齐系统商店配置："+folder+"/"+category+"；现有价格与数据保留。");
                    }
                }
            }
            if(first){plugin.getDataFolder().mkdirs();Files.writeString(marker.toPath(),"Four catalogs repaired; existing prices preserved\n");}
        }catch(Exception e){throw new IllegalStateException("Cannot repair system shop catalogs",e);}
    }
    static void isolateBackups(MiaoServer plugin) {
        try {
            for(String folder:List.of("sections","shops")) {
                File dir=new File(plugin.getDataFolder().getParentFile(),"EconomyShopGUI/"+folder);
                File[] files=dir.listFiles(f->f.isFile()&&f.getName().matches(".+\\.yml\\.before-(back-v099|economy-v098|v098|catalog-v0913)"));
                if(files==null)continue;
                for(File file:files) {
                    File target=new File(plugin.getDataFolder(),"config-backups/v0913/EconomyShopGUI/"+folder+"/"+file.getName());
                    target.getParentFile().mkdirs();
                    if(target.exists())target=new File(target.getParentFile(),target.getName()+"-"+UUID.randomUUID());
                    Files.move(file.toPath(),target.toPath());
                    plugin.getLogger().info("已隔离商店旧备份，避免被当作正式分类加载："+file.getName());
                }
            }
        }catch(IOException e){throw new IllegalStateException("Cannot isolate shop migration backups",e);}
    }
}
