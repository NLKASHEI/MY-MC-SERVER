package cn.nankexi;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.mvplugins.multiverse.core.MultiverseCoreApi;
import org.mvplugins.multiverse.core.world.AllowedPortalType;
import org.mvplugins.multiverse.core.world.options.*;
import org.mvplugins.multiverse.inventories.MultiverseInventoriesApi;

/** One disposable resource family. A reset can only delete these three verified dimension paths. */
final class MiningDimensions {
    static final List<String> NAMES=List.of("mining","mining_nether","mining_the_end");
    static void prepare(MiaoServer plugin){
        File marker=new File(plugin.getDataFolder(),"mining-family-v0926.done");if(marker.exists())return;
        try{
            File plugins=plugin.getDataFolder().getParentFile(),file=new File(plugin.getDataFolder(),"config.yml");var y=load(file);
            List<String> protectedWorlds=new ArrayList<>(y.getStringList("spawn-protection.worlds"));for(String name:NAMES)if(!protectedWorlds.contains(name))protectedWorlds.add(name);y.set("spawn-protection.worlds",protectedWorlds);write(file,y);
            file=new File(plugins,"GriefPreventionData/config.yml");y=load(file);for(String name:NAMES)y.set("GriefPrevention.Claims.Worlds."+name,"Disabled");write(file,y);
            file=new File(plugins.getParentFile(),"permissions.yml");y=new YamlConfiguration();y.options().pathSeparator('|');y.load(file);for(String name:NAMES)for(String prefix:List.of("multiverse.access.","multiverse.teleport.self.w."))for(String form:List.of(name,"minecraft:"+name))y.set("survival.player|children|"+prefix+form,true);write(file,y);
            Files.writeString(marker.toPath(),"Mining family permissions and spawn protection merged; no existing worlds reset\n");
        }catch(Exception e){throw new IllegalStateException("Cannot prepare resource dimensions",e);}
    }
    static void start(MiaoServer plugin,long seed){
        var manager=MultiverseCoreApi.get().getWorldManager();var groups=MultiverseInventoriesApi.get().getWorldGroupManager();var survival=groups.getGroup("survival");if(survival==null)throw new IllegalStateException("Missing survival inventory group");
        for(String name:NAMES){if(groups.getGroupsForWorld(name).stream().anyMatch(group->!group.equals(survival)))throw new IllegalStateException("Conflicting mining inventory group: "+name);survival.addWorld(name);}groups.updateGroup(survival);
        for(String name:NAMES){
            var managed=manager.getWorld("minecraft:"+name).getOrNull();World.Environment environment=name.endsWith("_nether")?World.Environment.NETHER:name.endsWith("_the_end")?World.Environment.THE_END:World.Environment.NORMAL;
            if(managed==null)managed=manager.createWorld(CreateWorldOptions.worldKey(NamespacedKey.minecraft(name)).environment(environment).seed(seed).worldPropertyString("gamemode","survival").worldPropertyString("difficulty","peaceful").worldPropertyString("portal-form","none").worldPropertyString("auto-load","false").worldPropertyString("pvp","false")).getOrThrow(f->new IllegalStateException("Cannot create resource dimension: "+f.getFailureReason()));
            else if(Bukkit.getWorld(NamespacedKey.minecraft(name))==null)manager.loadWorld(managed).getOrThrow(f->new IllegalStateException("Cannot load resource dimension: "+f.getFailureReason()));
            World world=Objects.requireNonNull(Bukkit.getWorld(NamespacedKey.minecraft(name)));if(world.getEnvironment()!=environment)throw new IllegalStateException("Resource dimension environment mismatch: "+name);
            managed.setAutoLoad(false).get();managed.setGameMode(GameMode.SURVIVAL).get();managed.setDifficulty(Difficulty.PEACEFUL).get();managed.setPvp(false).get();managed.setPortalForm(AllowedPortalType.ALL).get();managed.setRespawnWorld("world").get();managed.setAlias(name.equals("mining")?"丰饶矿界":name.endsWith("_nether")?"矿界下界":"矿界末地").get();
            world.setDifficulty(Difficulty.PEACEFUL);world.setGameRule(GameRule.KEEP_INVENTORY,true);
            if(!world.getPersistentDataContainer().has(new NamespacedKey(plugin,"mining-family-spawn"))){
                if(environment!=World.Environment.NORMAL){int x=environment==World.Environment.THE_END?100:0,z=0,y=environment==World.Environment.THE_END?50:64;for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){world.getBlockAt(x+dx,y-1,z+dz).setType(Material.OBSIDIAN,false);for(int dy=0;dy<4;dy++)world.getBlockAt(x+dx,y+dy,z+dz).setType(Material.AIR,false);}Location spawn=new Location(world,x+0.5,y,z+0.5);world.setSpawnLocation(spawn);managed.setSpawnLocation(spawn).get();}
                world.getWorldBorder().setCenter(world.getSpawnLocation());world.getWorldBorder().setSize(plugin.getConfig().getDouble("resource-world.border-size",6000));world.getPersistentDataContainer().set(new NamespacedKey(plugin,"mining-family-spawn"),org.bukkit.persistence.PersistentDataType.BYTE,(byte)1);
            }
        }manager.saveWorldsConfig();
    }
    static void reset(MiaoServer plugin){
        try{
            File planFile=new File(plugin.getDataFolder(),"mining-reset-plan.yml");var plan=load(planFile);
            if(!plan.contains("seed")){World old=Bukkit.getWorld(NamespacedKey.minecraft("mining"));long seed;do{seed=java.util.concurrent.ThreadLocalRandom.current().nextLong();}while(old!=null&&seed==old.getSeed());plan.set("seed",seed);plan.save(planFile);}
            World main=Objects.requireNonNull(Bukkit.getWorld("world"));Path dimensions=main.getWorldFolder().toPath().toRealPath().getParent().getParent();if(!dimensions.getFileName().toString().equals("dimensions"))throw new IllegalStateException("Unexpected dimension root");
            var manager=MultiverseCoreApi.get().getWorldManager();
            // Validate every destructive target before deleting any member. Never follow a world-path alias.
            for(String name:NAMES){World world=Bukkit.getWorld(NamespacedKey.minecraft(name));var managed=manager.getWorld("minecraft:"+name).getOrNull();if(world==null&&managed!=null){manager.loadWorld(managed).getOrThrow(f->new IllegalStateException("Cannot load reset target"));world=Bukkit.getWorld(NamespacedKey.minecraft(name));}if(world!=null&&(!world.getPlayers().isEmpty()||!world.getWorldFolder().toPath().toRealPath().equals(dimensions.resolve("minecraft/"+name).normalize())))throw new IllegalStateException("Unsafe mining reset target: "+name);}
            if(!new File(plugin.getDataFolder(),"flat-reset-v0918.done").isFile())throw new IllegalStateException("World setup incomplete");
            for(String name:NAMES){var managed=manager.getWorld("minecraft:"+name).getOrNull();if(managed!=null)manager.deleteWorld(DeleteWorldOptions.world(managed)).getOrThrow(f->new IllegalStateException("Cannot delete mining dimension: "+f.getFailureReason()));}
            WorldExpansion.start(plugin,plan.getLong("seed"));Files.deleteIfExists(planFile.toPath());
        }catch(Exception e){throw new IllegalStateException("Cannot reset resource dimension family",e);}
    }
    private static YamlConfiguration load(File file)throws Exception{var y=new YamlConfiguration();if(file.exists())y.load(file);return y;}
    private static void write(File file,YamlConfiguration y)throws IOException{Path before=file.toPath().resolveSibling(file.getName()+".before-mining-family-v0926");if(file.exists()&&!Files.exists(before))Files.copy(file.toPath(),before);y.save(file);}
}
