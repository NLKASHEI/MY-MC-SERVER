package cn.nankexi;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.permissions.PermissionDefault;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import simplexity.scythe.events.HarvestEvent;
import simplexity.scythe.events.ReplantEvent;

/** Crop events cannot bypass authentication, immutable spawns or either claim system. */
public final class FarmingGuard implements Listener {
    private final MiaoServer plugin;
    private final java.util.Set<ReplantEvent> delayedEvents=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    FarmingGuard(MiaoServer plugin) {
        this.plugin=plugin;
        if(!Bukkit.getPluginManager().isPluginEnabled("Scythe"))throw new IllegalStateException("Scythe is unavailable");
        var permission=Bukkit.getPluginManager().getPermission("scythe.reload");
        if(permission!=null)permission.setDefault(PermissionDefault.OP);
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    boolean immutable(Block block) {
        if(LobbyGuard.lobby(block.getWorld()))return true;
        int radius=plugin.getConfig().getInt("spawn-protection.radius",8);
        if(radius<0||!plugin.getConfig().getStringList("spawn-protection.worlds").contains(block.getWorld().getName()))return false;
        Location spawn=block.getWorld().getSpawnLocation();
        return Math.abs((long)block.getX()-spawn.getBlockX())<=radius&&Math.abs((long)block.getZ()-spawn.getBlockZ())<=radius;
    }
    boolean allowed(Player player,Block block,boolean planting) {
        if(!plugin.authenticated(player)||(immutable(block)&&!SpawnGuard.mayEdit(player,block.getWorld())))return false;
        var query=WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        if(!query.testBuild(BukkitAdapter.adapt(block.getLocation()),WorldGuardPluginAccess.player(player),planting?Flags.BLOCK_PLACE:Flags.BLOCK_BREAK))return false;
        return GriefPrevention.instance.allowBuild(player,block.getLocation(),block.getType())==null;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void harvest(HarvestEvent event){if(!allowed(event.getPlayer(),event.getBlock(),false))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void replant(ReplantEvent event) {
        if(!allowed(event.getPlayer(),event.getBlock(),true)){event.setCancelled(true);return;}
        if(delayedEvents.contains(event))return;
        // Scythe schedules at NORMAL before other break handlers can cancel. Recheck
        // the empty target and permissions at planting time; never overwrite a block.
        event.setCancelled(true);
        Player player=event.getPlayer();Block block=event.getBlock();var data=event.getBlockData().clone();
        long delay=Math.max(1,Bukkit.getPluginManager().getPlugin("Scythe").getConfig().getInt("auto-replant.delay-ticks",1));
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(!player.isOnline()||!block.getType().isAir()||!allowed(player,block,true))return;
            var checked=new ReplantEvent(player,block,data);delayedEvents.add(checked);
            try {
                Bukkit.getPluginManager().callEvent(checked);
                if(!checked.isCancelled()&&checked.getBlock().getType().isAir()&&allowed(checked.getPlayer(),checked.getBlock(),true))
                    simplexity.scythe.handling.ReplantManager.getInstance().replantCrop(checked.getPlayer(),checked.getBlock(),checked.getBlockData());
            } finally {delayedEvents.remove(checked);}
        },delay);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void eggs(PlayerInteractEvent event) {
        if(!event.getAction().isRightClick()||event.getClickedBlock()==null||event.getItem()==null)return;
        Material item=event.getItem().getType();
        if(item!=Material.VILLAGER_SPAWN_EGG&&item!=Material.PILLAGER_SPAWN_EGG)return;
        Block clicked=event.getClickedBlock(),target=clicked.getRelative(event.getBlockFace());
        if(clicked.getType()==Material.SPAWNER||clicked.getType()==Material.TRIAL_SPAWNER) {
            event.setCancelled(true);event.getPlayer().sendMessage("§6南可熙提醒您： §f补给刷怪蛋不能改造刷怪笼，请在允许建造的地方直接使用。");return;
        }
        if(!allowed(event.getPlayer(),clicked,true)||!allowed(event.getPlayer(),target,true))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void eggOnMob(PlayerInteractEntityEvent event) {
        Material item=event.getPlayer().getInventory().getItem(event.getHand()).getType();
        if(item!=Material.VILLAGER_SPAWN_EGG&&item!=Material.PILLAGER_SPAWN_EGG)return;
        if(!allowed(event.getPlayer(),event.getRightClicked().getLocation().getBlock(),true))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void eggSpawn(CreatureSpawnEvent event) {
        if(event.getSpawnReason()!=CreatureSpawnEvent.SpawnReason.SPAWNER_EGG)return;
        if(event.getEntityType()!=org.bukkit.entity.EntityType.VILLAGER&&event.getEntityType()!=org.bukkit.entity.EntityType.PILLAGER)return;
        if(immutable(event.getLocation().getBlock()))event.setCancelled(true);
    }
    private static final class WorldGuardPluginAccess {
        static com.sk89q.worldguard.LocalPlayer player(Player player){return com.sk89q.worldguard.bukkit.WorldGuardPlugin.inst().wrapPlayer(player);}
    }
}

