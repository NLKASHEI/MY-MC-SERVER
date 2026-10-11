package cn.nankexi;

import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BlockStateHolder;

/** Immutable lobby for players, including OP and WorldEdit actors. */
final class LobbyGuard implements Listener {
    static boolean lobby(World w) { return w != null && (w.getName().equals("lobby") || w.getName().equals("minecraft:lobby")); }
    LobbyGuard(MiaoServer p) {
        // Also update the live configuration if WorldEdit already parsed its file during onLoad.
        WorldEdit.getInstance().getConfiguration().navigationWand = "minecraft:recovery_compass";
        Bukkit.getPluginManager().registerEvents(this, p); WorldEdit.getInstance().getEventBus().register(this);
    }
    void stop() { WorldEdit.getInstance().getEventBus().unregister(this); }
    @EventHandler(priority=EventPriority.HIGHEST) public void breakBlock(BlockBreakEvent e) { if (lobby(e.getBlock().getWorld())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void place(BlockPlaceEvent e) { if (lobby(e.getBlock().getWorld())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void bucketFill(PlayerBucketFillEvent e) { if (lobby(e.getPlayer().getWorld())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void bucketEmpty(PlayerBucketEmptyEvent e) { if (lobby(e.getPlayer().getWorld())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void explosion(EntityExplodeEvent e) { if (lobby(e.getLocation().getWorld())) { e.blockList().clear(); e.setCancelled(true); } }
    @EventHandler(priority=EventPriority.HIGHEST) public void explosion(BlockExplodeEvent e) { if (lobby(e.getBlock().getWorld())) { e.blockList().clear(); e.setCancelled(true); } }
    @EventHandler(priority=EventPriority.HIGHEST) public void ignite(BlockIgniteEvent e) { if (lobby(e.getBlock().getWorld())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void burn(BlockBurnEvent e) { if (lobby(e.getBlock().getWorld())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void liquid(BlockFromToEvent e) { if (lobby(e.getBlock().getWorld()) || lobby(e.getToBlock().getWorld())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void piston(BlockPistonExtendEvent e) { if (lobby(e.getBlock().getWorld())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void piston(BlockPistonRetractEvent e) { if (lobby(e.getBlock().getWorld())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void change(EntityChangeBlockEvent e) { if (lobby(e.getBlock().getWorld())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true) public void command(PlayerCommandPreprocessEvent e) {
        String cmd = e.getMessage().toLowerCase(java.util.Locale.ROOT);
        String root = cmd.split(" ", 2)[0].replace("/minecraft:", "/");
        boolean editing = java.util.Set.of("/setblock", "/fill", "/clone", "/place").contains(root);
        if ((lobby(e.getPlayer().getWorld()) && (editing || root.equals("/execute"))) || (root.equals("/execute") && cmd.contains("lobby"))) {
            e.setCancelled(true); e.getPlayer().sendMessage("§6[妙妙服] §c主城为只读地图，OP 也不能修改。请停服维护地图。");
        }
    }
    @Subscribe public void editing(EditSessionEvent e) {
        if (e.getWorld() == null || !java.util.Set.of("lobby", "minecraft:lobby").contains(e.getWorld().getName()) || e.getStage() != EditSession.Stage.BEFORE_CHANGE) return;
        e.setExtent(new AbstractDelegateExtent(e.getExtent()) {
            @Override public <B extends BlockStateHolder<B>> boolean setBlock(BlockVector3 p, B b) throws WorldEditException { return false; }
            @Override public boolean setBiome(BlockVector3 p, com.sk89q.worldedit.world.biome.BiomeType b) { return false; }
            @Override public com.sk89q.worldedit.entity.Entity createEntity(com.sk89q.worldedit.util.Location l, com.sk89q.worldedit.entity.BaseEntity b) { return null; }
        });
    }
}
