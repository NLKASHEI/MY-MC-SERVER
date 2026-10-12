package cn.nankexi;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Update only the shipped link-button wording, preserving other menu customization. */
final class CrossplaySyncSetup {
    static void apply(MiaoServer p) {
        File marker=new File(p.getDataFolder(),"crossplay-sync-v099.done");if(marker.exists())return;
        File file=new File(p.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus/account.yml");
        try {
            var y=new YamlConfiguration();y.load(file);
            if(y.getStringList("items.link.left_click_commands").contains("[player] accountlink")&&y.getStringList("items.link.lore").stream().anyMatch(s->s.contains("采用 Java 身份"))) {
                try(InputStream in=p.getResource("player-names-account.yml")){var source=new YamlConfiguration();source.load(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8));y.set("items.link.lore",source.getStringList("items.link.lore"));}
                File backup=new File(file.getParentFile(),file.getName()+".before-crossplay-sync-v099");if(!backup.exists())Files.copy(file.toPath(),backup.toPath());
                File tmp=new File(file.getParentFile(),file.getName()+".nlk-tmp");y.save(tmp);Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
            }
            File guide=new File(p.getDataFolder().getParentFile(),"Skript/scripts/survival.sk");
            if(guide.exists()) {
                String text=Files.readString(guide.toPath(),StandardCharsets.UTF_8),updated=text.replace("绑定后共用 Java 进度，不自动合并旧 PE 背包；绑定前先转移物品。","可选择保留 PE 或 PC 进度，验证对方的服内密码；旧数据保留，不叠加背包金币。");
                if(!text.equals(updated)){File backup=new File(guide.getParentFile(),guide.getName()+".before-crossplay-sync-v099");if(!backup.exists())Files.copy(guide.toPath(),backup.toPath());Files.writeString(guide.toPath(),updated,StandardCharsets.UTF_8);}
            }
            Files.writeString(marker.toPath(),"Updated standard account button and linking guide only; no player data migration\n");
        }catch(Exception e){throw new IllegalStateException("Cannot update sync menu wording",e);}
    }
}
