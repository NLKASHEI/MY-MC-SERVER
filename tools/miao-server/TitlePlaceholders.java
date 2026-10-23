package cn.nankexi;
import org.bukkit.OfflinePlayer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/** Read only immutable caches: no file, economy, advancement or skin lookups from chat placeholders. */
final class TitlePlaceholders extends PlaceholderExpansion {
    private final MiaoServer plugin;private final PlayerTitles titles;
    TitlePlaceholders(MiaoServer plugin,PlayerTitles titles){this.plugin=plugin;this.titles=titles;}
    @Override public String getIdentifier(){return "nlktitles";}
    @Override public String getAuthor(){return "NLKASHEI";}
    @Override public String getVersion(){return plugin.getDescription().getVersion();}
    @Override public boolean persist(){return true;}
    @Override public String onRequest(OfflinePlayer player,String params){return titles.placeholder(player==null?null:player.getUniqueId(),params);}
}
