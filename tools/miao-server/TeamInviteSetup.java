package cn.nankexi;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.bukkit.configuration.file.YamlConfiguration;

final class TeamInviteSetup {
    static void apply(MiaoServer plugin) {
        File marker=new File(plugin.getDataFolder(),"team-invite-v098.done");if(marker.exists())return;
        File file=new File(plugin.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus/teams.yml");
        try(InputStream in=plugin.getResource("team-invite-menu.yml")) {
            if(in==null)throw new IOException("Missing team invite menu");
            var source=new YamlConfiguration();source.load(new InputStreamReader(in,StandardCharsets.UTF_8));
            var y=new YamlConfiguration();y.load(file);
            for(String key:java.util.List.of("display_name","lore","left_click_commands","right_click_commands"))y.set("items.invite."+key,source.get("items.invite."+key));
            File backup=new File(file.getParentFile(),"teams.yml.before-invite-v098");if(!backup.exists())Files.copy(file.toPath(),backup.toPath());
            File tmp=new File(file.getParentFile(),"teams.yml.nlk-tmp");y.save(tmp);Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(marker.toPath(),"Online player heads and Bedrock invite list; all members can invite\n");
        }catch(Exception e){throw new IllegalStateException("Cannot migrate team invite panel",e);}
    }
}
