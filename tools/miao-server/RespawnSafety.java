package cn.nankexi;

import org.bukkit.*;
import org.bukkit.block.Block;
import java.util.Set;

/** Never use the Nether/End metadata spawn as a death fallback. */
public final class RespawnSafety {
    private static final Set<Material> HAZARDS=Set.of(Material.LAVA,Material.FIRE,Material.SOUL_FIRE,Material.MAGMA_BLOCK,Material.CACTUS,Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.POWDER_SNOW,Material.SWEET_BERRY_BUSH,Material.WITHER_ROSE,Material.POINTED_DRIPSTONE);
    static World overworld(World death) {
        String name=death.getName();
        for(String suffix:new String[]{"_nether","_the_end"})if(name.endsWith(suffix)) {
            World paired=Bukkit.getWorld(name.substring(0,name.length()-suffix.length()));
            if(paired!=null&&paired.getEnvironment()==World.Environment.NORMAL)return paired;
        }
        World world=Bukkit.getWorld("world");
        if(world==null||world.getEnvironment()!=World.Environment.NORMAL)throw new IllegalStateException("Main survival overworld is missing");
        return world;
    }
    public static boolean safe(Location at) {
        World world=at.getWorld();int y=at.getBlockY();
        if(y<=world.getMinHeight()||y+1>=world.getMaxHeight()||!world.getWorldBorder().isInside(at))return false;
        Block feet=at.getBlock(),head=feet.getRelative(0,1,0),ground=feet.getRelative(0,-1,0);
        return feet.isPassable()&&head.isPassable()&&!feet.isLiquid()&&!head.isLiquid()&&ground.getType().isSolid()&&!HAZARDS.contains(feet.getType())&&!HAZARDS.contains(head.getType())&&!HAZARDS.contains(ground.getType());
    }
    static Location spawn(World world) {
        Location origin=world.getSpawnLocation().clone();
        origin.setX(origin.getBlockX()+0.5);origin.setZ(origin.getBlockZ()+0.5);
        if(safe(origin))return origin;
        for(int radius=0;radius<=16;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            int x=origin.getBlockX()+dx,z=origin.getBlockZ()+dz;
            int y=world.getHighestBlockYAt(x,z,HeightMap.MOTION_BLOCKING_NO_LEAVES)+1;
            Location candidate=new Location(world,x+0.5,y,z+0.5,origin.getYaw(),origin.getPitch());
            if(safe(candidate))return candidate;
        }
        // A normal ocean spawn can be survivable even when no dry landing is nearby.
        // Keep this exceptional fallback in the overworld and expose it to operators.
        Bukkit.getLogger().warning("[NLK] 主世界出生点附近没有安全干燥落脚点，请检查世界出生坐标。");
        return origin;
    }
}
