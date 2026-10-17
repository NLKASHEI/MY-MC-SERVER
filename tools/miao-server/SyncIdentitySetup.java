package cn.nankexi;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Update only the account-button help; no identities, skins or player data migration. */
final class SyncIdentitySetup {
    static void apply(MiaoServer plugin){
        File marker=new File(plugin.getDataFolder(),"sync-identity-v0924.done");if(marker.exists())return;
        try{
            File file=new File(plugin.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus/account.yml");var y=YamlConfiguration.loadConfiguration(file);
            try(InputStream in=plugin.getResource("player-names-account.yml")){
                var source=YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8));
                for(String key:List.of("items.link.display_name","items.link.lore","items.help.lore"))y.set(key,source.get(key));
            }
            Path backup=file.toPath().resolveSibling(file.getName()+".before-sync-identity-v0924");if(Files.exists(file.toPath())&&!Files.exists(backup))Files.copy(file.toPath(),backup);y.save(file);
            Path guide=new File(plugin.getDataFolder().getParentFile(),"Skript/scripts/survival.sk").toPath();
            if(Files.exists(guide)){
                String before=Files.readString(guide,StandardCharsets.UTF_8),after=before.replace("先选择保留谁的进度，再按提示验证对方服内密码；取消则各自进度不变。","分别选择进度、实际用户名与皮肤来源，再验证密码；取消则保持原样。\"\n            send \"&7重连使用所选用户名的服内密码；已绑定也能从 /accountlink 修改名字，进度不变。");
                if(!before.equals(after)){backup=guide.resolveSibling(guide.getFileName()+".before-sync-identity-v0924");if(!Files.exists(backup))Files.copy(guide,backup);Files.writeString(guide,after,StandardCharsets.UTF_8);}
            }
            Files.writeString(marker.toPath(),"Updated account menu and guide only; existing bindings keep their identity until selected\n");
        }catch(IOException e){throw new IllegalStateException("Cannot update identity selection help",e);}
    }
}
