package cn.nankexi;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** A bounded repair independent of the full guide layout and its existing marker. */
final class WalletIconRepair {
    static void apply(MiaoServer plugin) {
        File marker=new File(plugin.getDataFolder(),"wallet-icon-v0914.done");
        File file=new File(plugin.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus/survival.yml");
        if(marker.exists()||!file.isFile())return;
        try {
            var y=new YamlConfiguration();y.load(file);
            var items=y.getConfigurationSection("items");if(items==null)throw new IOException("Missing guide items");
            int size=y.getInt("size",45),slot=23;
            if(!available(y,items,slot)) {
                slot=y.getInt("items.balance.slot",-1);
                if(slot<0||slot>=size||!available(y,items,slot)) {
                    slot=-1;for(int candidate=0;candidate<size;candidate++)if(available(y,items,candidate)){slot=candidate;break;}
                }
            }
            if(slot<0||slot>=size){plugin.getLogger().warning("钱包图标修复：菜单没有可用格位，保留自定义按钮。");return;}
            File prior=new File(plugin.getDataFolder(),"config-backups/v0914/survival-before-wallet.yml");
            prior.getParentFile().mkdirs();if(!prior.exists())Files.copy(file.toPath(),prior.toPath());
            for(String key:new ArrayList<>(items.getKeys(false)))if(!key.equals("balance")&&occupies(y,key,slot))y.set("items."+key,null);
            if(!y.isConfigurationSection("items.balance")) {
                y.set("items.balance.display_name","&e我的钱包");
                y.set("items.balance.lore",List.of("&7查看余额与转账说明"));
                for(String click:List.of("left_click_commands","right_click_commands"))y.set("items.balance."+click,List.of("[close]","[player] guide wallet"));
            }
            y.set("items.balance.material","GOLD_NUGGET");y.set("items.balance.amount",1);
            y.set("items.balance.slot",slot);y.set("items.balance.slots",null);y.set("items.balance.priority",0);
            for(String key:List.of("dynamic_amount","model_data","model_data_component","item_model","nbt","nbt_string","nbt_strings","nbt_int","nbt_ints","nbt_byte","nbt_bytes","nbt_short","nbt_shorts"))y.set("items.balance."+key,null);
            File temp=new File(file.getParentFile(),file.getName()+".wallet-tmp");y.save(temp);
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(marker.toPath(),"Wallet vanilla coin icon restored; commands and balances preserved\n");
            plugin.getLogger().info("钱包已独立修复：原版金粒图标，固定数量 1，保留按钮功能与金币数据。");
        }catch(Exception e){throw new IllegalStateException("Cannot repair wallet guide icon",e);}
    }
    private static boolean occupies(YamlConfiguration y,String key,int slot) {
        return y.getInt("items."+key+".slot",-1)==slot||y.getIntegerList("items."+key+".slots").contains(slot);
    }
    private static boolean available(YamlConfiguration y,org.bukkit.configuration.ConfigurationSection items,int slot) {
        for(String key:items.getKeys(false))if(!key.equals("balance")&&occupies(y,key,slot)) {
            if(!(key.startsWith("frame_")||key.startsWith("layout_"))||!y.getStringList("items."+key+".left_click_commands").isEmpty()||!y.getStringList("items."+key+".right_click_commands").isEmpty())return false;
        }
        return true;
    }
}
