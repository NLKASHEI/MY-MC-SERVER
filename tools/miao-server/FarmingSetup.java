package cn.nankexi;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Install new crop defaults only when absent, and add the two requested egg offers. */
final class FarmingSetup {
    static void apply(MiaoServer plugin) {
        try {
            File plugins=plugin.getDataFolder().getParentFile();
            for(String name:List.of("config","locale")) {
                File file=new File(plugins,"Scythe/"+name+".yml");
                if(!file.exists()) {
                    file.getParentFile().mkdirs();
                    try(InputStream in=plugin.getResource("scythe-"+name+".yml")) {
                        if(in==null)throw new IOException("Missing crop resource");
                        Files.copy(in,file.toPath());
                    }
                }
            }
            File marker=new File(plugin.getDataFolder(),"spawn-egg-offers-v0913.done");
            if(marker.exists())return;
            File file=new File(plugins,"EconomyShopGUI/shops/nlk_technical.yml");
            var y=new YamlConfiguration();y.load(file);
            var expected=new YamlConfiguration();
            try(InputStream in=plugin.getResource("economy-shops-nlk_technical.yml")) {
                if(in==null)throw new IOException("Missing technical catalog");
                expected.load(new InputStreamReader(in,StandardCharsets.UTF_8));
            }
            Set<String> materials=new HashSet<>();
            for(String key:y.getKeys(true))if(key.endsWith(".material"))materials.add(y.getString(key));
            boolean changed=false;
            for(String key:List.of("19","20")) {
                String source="pages.page1.items."+key;
                if(materials.contains(expected.getString(source+".material")))continue;
                int id=Integer.parseInt(key);while(y.contains("pages.page1.items."+id))id++;
                y.set("pages.page1.items."+id,expected.getConfigurationSection(source).getValues(true));changed=true;
            }
            if(changed) {
                File prior=new File(plugin.getDataFolder(),"config-backups/v0913/EconomyShopGUI/nlk_technical-before-eggs.yml");
                prior.getParentFile().mkdirs();if(!prior.exists())Files.copy(file.toPath(),prior.toPath());
                File temp=new File(file.getParentFile(),file.getName()+".nlk-tmp");y.save(temp);
                Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(marker.toPath(),"Two buy-only spawn egg offers installed; existing offers preserved\n");
        } catch(Exception e){throw new IllegalStateException("Cannot install crop defaults and egg offers",e);}
    }
}
