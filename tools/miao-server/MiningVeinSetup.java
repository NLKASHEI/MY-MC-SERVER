package cn.nankexi;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Only update the old default fee and its known menu copy. Never rewrite chunks. */
final class MiningVeinSetup {
    static void apply(MiaoServer plugin){
        File marker=new File(plugin.getDataFolder(),"mining-veins-v0926.done");if(marker.exists())return;
        try{
            File config=new File(plugin.getDataFolder(),"config.yml");var y=new YamlConfiguration();y.load(config);boolean fee=!y.contains("resource-world.entry-fee")||y.getDouble("resource-world.entry-fee")==100;
            if(fee){y.set("resource-world.entry-fee",200.0);save(config,y);}
            for(String name:List.of("worlds","resource")){
                File file=new File(plugin.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus/"+name+".yml");var menu=new YamlConfiguration();menu.load(file);var items=menu.getConfigurationSection("items");if(items==null)continue;
                for(String key:items.getKeys(false)){
                    String root="items."+key;String label=menu.getString(root+".display_name","");if(fee)label=label.replace("入场 · 100 金币","入场 · 200 金币");menu.set(root+".display_name",label);
                    List<String> lore=new ArrayList<>(menu.getStringList(root+".lore"));for(int i=0;i<lore.size();i++){String line=lore.get(i);if(fee)line=line.replace("入场 100 金币","入场 200 金币").replace("门票 100 金币","门票 200 金币");line=line.replace("新区块石质层约 30% 矿石 · 煤铁铜为主","新区块连片矿脉 · 煤铁铜为主").replace("石质层约 30% 矿石 · 与生存共用背包","连片矿脉 · 与生存共用背包");lore.set(i,line);}menu.set(root+".lore",lore);
                }
                if(name.equals("resource")){
                    var source=new YamlConfiguration();try(InputStream in=plugin.getResource("resource-world-menu.yml")){source.load(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8));}
                    // Move only the shipped status action, and replace only blank panes at the two new entries.
                    if(menu.getInt("items.statement.slot",-1)==13&&menu.getStringList("items.statement.left_click_commands").equals(List.of("[player] miao mining status"))){
                        clearBlank(menu,14,"statement");menu.set("items.statement.slot",14);
                    }
                    for(String id:List.of("nether","end"))if(!menu.contains("items."+id)){int slot=source.getInt("items."+id+".slot");clearBlank(menu,slot,id);menu.createSection("items."+id,source.getConfigurationSection("items."+id).getValues(true));}
                    if(menu.getStringList("items.rules.lore").stream().anyMatch(line->line.contains("矿产每 3 份")||line.contains("连片矿脉")))menu.set("items.rules.lore",source.getStringList("items.rules.lore"));
                }
                save(file,menu);
            }Files.writeString(marker.toPath(),"Default mining fee 200 and vein descriptions merged; no chunks regenerated\n");
        }catch(Exception e){throw new IllegalStateException("Cannot prepare mining veins/fee",e);}
    }
    private static void save(File file,YamlConfiguration y)throws IOException{Path before=file.toPath().resolveSibling(file.getName()+".before-mining-veins-v0926");if(!Files.exists(before))Files.copy(file.toPath(),before);y.save(file);}
    private static void clearBlank(YamlConfiguration y,int slot,String own)throws IOException{
        var items=y.getConfigurationSection("items");if(items==null)return;for(String key:new ArrayList<>(items.getKeys(false)))if(!key.equals(own)&&y.getInt("items."+key+".slot",-1)==slot){if(" ".equals(y.getString("items."+key+".display_name"))&&y.getStringList("items."+key+".left_click_commands").isEmpty()&&y.getStringList("items."+key+".right_click_commands").isEmpty())y.set("items."+key,null);else throw new IOException("Custom resource menu occupies new dimension slot "+slot);}
    }
}
