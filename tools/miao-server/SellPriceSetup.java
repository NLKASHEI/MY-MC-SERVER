package cn.nankexi;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Apply only the previous shipped sell values. Custom cloud prices remain authoritative. */
final class SellPriceSetup {
    static void apply(MiaoServer plugin){
        File marker=new File(plugin.getDataFolder(),"sell-prices-v0926.done");if(marker.exists())return;
        try{
            var policy=new YamlConfiguration();try(InputStream in=plugin.getResource("sell-prices.yml")){policy.load(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8));}
            for(String name:policy.getConfigurationSection("shops").getKeys(false)){
                File file=new File(plugin.getDataFolder().getParentFile(),"EconomyShopGUI/shops/"+name+".yml");var y=new YamlConfiguration();y.load(file);var pages=y.getConfigurationSection("pages");if(pages==null)continue;boolean changed=false;
                for(String page:pages.getKeys(false)){var items=y.getConfigurationSection("pages."+page+".items");if(items==null)continue;for(String item:items.getKeys(false)){
                    String k="pages."+page+".items."+item,rule="shops."+name+"."+y.getString(k+".material","");
                    if(policy.contains(rule+".old")&&Math.abs(y.getDouble(k+".sell",-1)-policy.getDouble(rule+".old"))<0.000000001){double value=policy.getDouble(rule+".new"),buy=y.getDouble(k+".buy",-1);if(value<0||!Double.isFinite(value)||(buy>=0&&value>=buy))throw new IOException("Invalid sell price "+rule);y.set(k+".sell",value);changed=true;}
                }}
                if(changed){Path before=file.toPath().resolveSibling(file.getName()+".before-sell-prices-v0926");if(!Files.exists(before))Files.copy(file.toPath(),before);y.save(file);}
            }Files.writeString(marker.toPath(),"Known default crop/fish/mineral sell prices merged; custom prices retained\n");
        }catch(Exception e){throw new IllegalStateException("Cannot merge sell prices",e);}
    }
}
