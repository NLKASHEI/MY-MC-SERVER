package cn.nankexi;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.SkinProperty;

/** Resolve skins by the chosen original account, not the shared progress UUID. */
final class SyncAppearance {
    static Optional<SkinProperty> resolve(SkinsRestorer api,UUID origin,String name)throws Exception{
        var stored=api.getPlayerStorage().getSkinOfPlayer(origin);
        if(stored.isPresent())return stored;
        // Bedrock identities are not Mojang usernames. Keep the current texture if no saved skin exists.
        if(!name.matches("[A-Za-z0-9_]{1,16}"))return Optional.empty();
        return api.getPlayerStorage().getSkinForPlayer(origin,name,false);
    }
    static void apply(MiaoServer plugin,Player player,CrossplaySync.Binding binding,BooleanSupplier unchanged){
        if(!Bukkit.getPluginManager().isPluginEnabled("SkinsRestorer"))return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            try{
                var api=SkinsRestorerProvider.get();var skin=resolve(api,binding.identityId(),binding.identityName());
                if(!plugin.isEnabled())return;
                Bukkit.getScheduler().runTask(plugin,()->{
                    if(!player.isOnline()||!unchanged.getAsBoolean())return;
                    if(skin.isPresent())api.getSkinApplier(Player.class).applySkin(player,skin.get());
                    else player.sendMessage("§6南可熙提醒您：§f已使用所选用户名，但未找到该账号的服务端皮肤；当前皮肤保留。可用 /skin 设置皮肤。");
                });
            }catch(Exception e){
                if(plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline()&&unchanged.getAsBoolean())player.sendMessage("§6南可熙提醒您：§f皮肤服务暂不可用；名字和共享进度已生效，可稍后从账号菜单重新选择当前名字刷新皮肤。");});
            }
        });
    }
}
