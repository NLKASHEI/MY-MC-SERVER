package cn.nankexi;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Versioned player-facing release notes, checked against the plugin build. */
final class ReleaseNews {
    private final String version,summary;private final List<String> lines;
    ReleaseNews(MiaoServer plugin){
        try(InputStream in=Objects.requireNonNull(plugin.getResource("release-notes.yml"))){
            var source=YamlConfiguration.loadConfiguration(new InputStreamReader(in,StandardCharsets.UTF_8));version=source.getString("version","");summary=source.getString("summary","");lines=List.copyOf(source.getStringList("lines"));
            if(!version.equals(plugin.getDescription().getVersion())||lines.isEmpty())throw new IllegalStateException("Player release notes must match plugin version");
        }catch(IOException e){throw new IllegalStateException("Cannot load player release notes",e);}
    }
    void show(Player player){player.sendMessage("§6南可熙提醒您：§f服务器更新至 §a"+version+"§f · "+summary);for(String line:lines)player.sendMessage(ChatColor.translateAlternateColorCodes('&',line));}
}
