package cn.nankexi;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.StructureGrowEvent;

/** Protects each world's public spawn; beds and personal anchors are excluded. */
final class SpawnGuard implements Listener {
    private final MiaoServer plugin;
    SpawnGuard(MiaoServer plugin){this.plugin=plugin;Bukkit.getPluginManager().registerEvents(this,plugin);}
    boolean protectedBlock(Block block) {
        int radius=plugin.getConfig().getInt("spawn-protection.radius",8);
        if(radius<0||!plugin.getConfig().getStringList("spawn-protection.worlds").contains(block.getWorld().getName()))return false;
        Location spawn=block.getWorld().getSpawnLocation();
        return Math.abs((long)block.getX()-spawn.getBlockX())<=radius&&Math.abs((long)block.getZ()-spawn.getBlockZ())<=radius;
    }
    private void notice(org.bukkit.entity.Player p){p.sendMessage("§6南可熙提醒您： §f这里是公共出生点保护区，请到外围建设。");}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breaking(BlockBreakEvent e){if(protectedBlock(e.getBlock())){e.setCancelled(true);notice(e.getPlayer());}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void placing(BlockPlaceEvent e){if(protectedBlock(e.getBlock())){e.setCancelled(true);notice(e.getPlayer());}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void multi(BlockMultiPlaceEvent e){if(e.getReplacedBlockStates().stream().anyMatch(b->protectedBlock(b.getBlock()))){e.setCancelled(true);notice(e.getPlayer());}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explosion(EntityExplodeEvent e){e.blockList().removeIf(this::protectedBlock);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explosion(BlockExplodeEvent e){e.blockList().removeIf(this::protectedBlock);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void extend(BlockPistonExtendEvent e){if(protectedBlock(e.getBlock())||protectedBlock(e.getBlock().getRelative(e.getDirection()))||e.getBlocks().stream().anyMatch(b->protectedBlock(b)||protectedBlock(b.getRelative(e.getDirection()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void retract(BlockPistonRetractEvent e){if(protectedBlock(e.getBlock())||e.getBlocks().stream().anyMatch(b->protectedBlock(b)||protectedBlock(b.getRelative(e.getDirection()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void entity(EntityChangeBlockEvent e){if(protectedBlock(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void flow(BlockFromToEvent e){if(protectedBlock(e.getToBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void burn(BlockBurnEvent e){if(protectedBlock(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void grow(StructureGrowEvent e){if(e.getBlocks().stream().anyMatch(b->protectedBlock(b.getBlock())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void bucket(org.bukkit.event.player.PlayerBucketEmptyEvent e){if(protectedBlock(e.getBlock())){e.setCancelled(true);notice(e.getPlayer());}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void bucket(org.bukkit.event.player.PlayerBucketFillEvent e){if(protectedBlock(e.getBlock())){e.setCancelled(true);notice(e.getPlayer());}}
}
