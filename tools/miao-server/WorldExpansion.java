package cn.nankexi;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.mvplugins.multiverse.core.MultiverseCoreApi;
import org.mvplugins.multiverse.core.world.*;
import org.mvplugins.multiverse.core.world.options.*;
import org.mvplugins.multiverse.inventories.MultiverseInventoriesApi;

/** This migration may reset only the explicitly authorized minecraft:flat dimension. */
final class WorldExpansion {
    static final String FLAT_SETTINGS="{\"structure_overrides\":[],\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},{\"block\":\"minecraft:stone\",\"height\":124},{\"block\":\"minecraft:dirt\",\"height\":2},{\"block\":\"minecraft:grass_block\",\"height\":1}],\"biome\":\"minecraft:plains\",\"lakes\":false,\"features\":false}";
    static void prepare(MiaoServer plugin){
        File marker=new File(plugin.getDataFolder(),"world-expansion-config-v0918.done");if(marker.exists())return;
        try {
            File plugins=plugin.getDataFolder().getParentFile();
            File file=new File(plugin.getDataFolder(),"config.yml");var y=load(file);
            Map<String,Object> defaults=Map.of("resource-world.entry-fee",100.0,"resource-world.tax-divisor",3,"resource-world.ore-coverage-percent",30,"resource-world.border-size",6000,"resource-world.reset-period-days",3,"resource-world.reset-hour",4);
            for(var entry:defaults.entrySet())if(!y.contains(entry.getKey()))y.set(entry.getKey(),entry.getValue());
            var worlds=new ArrayList<>(y.getStringList("spawn-protection.worlds"));if(!worlds.contains("mining"))worlds.add("mining");y.set("spawn-protection.worlds",worlds);save(file,y);
            file=new File(plugins,"GriefPreventionData/config.yml");y=load(file);y.set("GriefPrevention.Claims.Mode.mining","Disabled");save(file,y);
            file=new File(plugins.getParentFile(),"permissions.yml");y=new YamlConfiguration();y.options().pathSeparator('|');y.load(file);
            for(String key:List.of("multiverse.access.mining","multiverse.access.minecraft:mining","multiverse.teleport.self.w.mining","multiverse.teleport.self.w.minecraft:mining"))y.set("survival.player|children|"+key,true);save(file,y);
            file=new File(plugins,"DeluxeMenus/gui_menus/worlds.yml");y=load(file);
            try(InputStream in=plugin.getResource("expanded-worlds-menu.yml")){
                var source=new YamlConfiguration();source.load(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8));
                // Change only the four world buttons, retaining unrelated local menu items.
                for(String item:List.of("lobby","survival","flat","mining"))y.set("items."+item,null);
                Set<Integer> used=new HashSet<>();var items=y.getConfigurationSection("items");if(items!=null)for(String key:items.getKeys(false))used.add(y.getInt("items."+key+".slot",-1));
                for(String item:List.of("lobby","survival","flat","mining")){
                    int slot=source.getInt("items."+item+".slot");
                    if(used.contains(slot)){slot=-1;for(int i=9;i<18;i++)if(!used.contains(i)){slot=i;break;}}
                    if(slot<0)throw new IOException("No room for four world buttons; inspect custom world menu");
                    y.createSection("items."+item,source.getConfigurationSection("items."+item).getValues(true));y.set("items."+item+".slot",slot);used.add(slot);
                }
                save(file,y);
            }
            file=new File(plugins,"DeluxeMenus/gui_menus/resource.yml");if(!file.exists()){
                try(InputStream in=plugin.getResource("resource-world-menu.yml")){Files.copy(Objects.requireNonNull(in),file.toPath());}
            }
            file=new File(plugins,"DeluxeMenus/config.yml");y=load(file);y.set("gui_menus.resource.file","resource.yml");save(file,y);
            plugin.getDataFolder().mkdirs();Files.writeString(marker.toPath(),"Resource-world defaults and four world buttons merged; no player data replaced\n");
        }catch(Exception e){throw new IllegalStateException("Cannot prepare resource world configuration",e);}
    }
    static void start(MiaoServer plugin){start(plugin,null);}
    private static void start(MiaoServer plugin,Long seed){
        var manager=MultiverseCoreApi.get().getWorldManager();
        File marker=new File(plugin.getDataFolder(),"flat-reset-v0918.done");
        try {
            var inventories=MultiverseInventoriesApi.get().getWorldGroupManager();
            var survival=inventories.getGroup("survival");
            if(survival==null)throw new IllegalStateException("Missing survival inventory group; refusing to expose mining world");
            if(inventories.getGroupsForWorld("mining").stream().anyMatch(g->!g.equals(survival)))throw new IllegalStateException("Mining world already belongs to another inventory group");
            survival.addWorld("mining");inventories.updateGroup(survival);
            if(!marker.exists()){
                World flat=Bukkit.getWorld(NamespacedKey.minecraft("flat"));
                var existing=manager.getWorld("minecraft:flat").getOrNull();
                if(flat!=null){
                    if(!flat.getPlayers().isEmpty())throw new IllegalStateException("Flat reset requires zero players");
                    World main=Objects.requireNonNull(Bukkit.getWorld("world"));
                    Path dimensions=main.getWorldFolder().toPath().toRealPath().getParent().getParent();
                    Path target=flat.getWorldFolder().toPath().toRealPath();
                    if(!dimensions.getFileName().toString().equals("dimensions")||!target.equals(dimensions.resolve("minecraft/flat").normalize())||!flat.getKey().equals(NamespacedKey.minecraft("flat")))throw new IllegalStateException("Unexpected flat dimension path; refusing reset: "+target);
                    if(existing==null)throw new IllegalStateException("Flat is not managed by Multiverse");
                    plugin.getLogger().warning("按服主明确要求清空无人使用的创造超平坦，仅重建 minecraft:flat，不额外备份该维度。");
                    manager.deleteWorld(DeleteWorldOptions.world(existing)).getOrThrow(f->new IllegalStateException("Flat deletion failed: "+f.getFailureReason()));
                }else if(existing!=null){
                    throw new IllegalStateException("Flat exists in Multiverse but is unloaded; inspect before reset");
                }
                var created=manager.createWorld(CreateWorldOptions.worldKey(NamespacedKey.minecraft("flat")).environment(World.Environment.NORMAL).worldType(WorldType.FLAT).generateStructures(false).generatorSettings(FLAT_SETTINGS).worldPropertyString("gamemode","creative").worldPropertyString("difficulty","peaceful").worldPropertyString("portal-form","none"))
                    .getOrThrow(f->new IllegalStateException("Deep flat creation failed: "+f.getFailureReason()));
                flat=created.getBukkitWorld().get();flat.setSpawnLocation(0,64,0);
                created.setSpawnLocation(flat.getSpawnLocation()).get();created.setPvp(false).get();created.setAllowWeather(false).get();created.setHunger(false).get();created.setAllowFlight(true).get();
                flat.setGameRule(GameRule.DO_MOB_SPAWNING,false);flat.setGameRule(GameRule.DO_WEATHER_CYCLE,false);
                manager.saveWorldsConfig();Files.writeString(marker.toPath(),"Authorized one-time flat reset: no structures; 128 layers; surface y=63; no separate world backup\n");
            }
            var mining=manager.getWorld("minecraft:mining").getOrNull();
            if(mining==null)mining=manager.createWorld(CreateWorldOptions.worldKey(NamespacedKey.minecraft("mining")).environment(World.Environment.NORMAL).seed(seed==null?java.util.concurrent.ThreadLocalRandom.current().nextLong():seed).worldPropertyString("gamemode","survival").worldPropertyString("difficulty","peaceful").worldPropertyString("pvp","false").worldPropertyString("auto-load","false").worldPropertyString("portal-form","none"))
                .getOrThrow(f->new IllegalStateException("Mining world creation failed: "+f.getFailureReason()));
            else if(Bukkit.getWorld(NamespacedKey.minecraft("mining"))==null)manager.loadWorld(mining).getOrThrow(f->new IllegalStateException("Mining world load failed: "+f.getFailureReason()));
            World world=Objects.requireNonNull(Bukkit.getWorld(NamespacedKey.minecraft("mining")));
            mining.setAutoLoad(false).get();mining.setRespawnWorld("world").get();mining.setGameMode(GameMode.SURVIVAL).get();mining.setPortalForm(AllowedPortalType.NONE).get();mining.setPvp(false).get();mining.setAlias("丰饶矿界").get();
            if(!world.getPersistentDataContainer().has(new NamespacedKey(plugin,"mining-initialized"))){
                world.setGameRule(GameRule.KEEP_INVENTORY,true);
                world.getWorldBorder().setCenter(world.getSpawnLocation());world.getWorldBorder().setSize(plugin.getConfig().getDouble("resource-world.border-size",6000));
                int x=world.getSpawnLocation().getBlockX(),z=world.getSpawnLocation().getBlockZ();
                int feet=Math.min(world.getMaxHeight()-4,Math.max(world.getSeaLevel()+2,world.getHighestBlockYAt(x,z,HeightMap.MOTION_BLOCKING_NO_LEAVES)+1));
                for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){
                    world.getBlockAt(x+dx,feet-1,z+dz).setType(Material.STONE_BRICKS,false);
                    for(int dy=0;dy<3;dy++)world.getBlockAt(x+dx,feet+dy,z+dz).setType(Material.AIR,false);
                }
                Location safe=new Location(world,x+0.5,feet,z+0.5);world.setSpawnLocation(safe);mining.setSpawnLocation(safe).get();
                world.getPersistentDataContainer().set(new NamespacedKey(plugin,"mining-initialized"),org.bukkit.persistence.PersistentDataType.BYTE,(byte)1);
            }
            ArchitectWorld.start(plugin);
            manager.saveWorldsConfig();
            plugin.getLogger().info("深层无结构超平坦和丰饶矿界就绪；矿界加入生存背包组，创造背包继续隔离。");
        }catch(Exception e){throw new IllegalStateException("Cannot initialize managed worlds",e);}
    }
    static void resetMining(MiaoServer plugin){
        try{
            World world=Objects.requireNonNull(Bukkit.getWorld(NamespacedKey.minecraft("mining")));
            if(!world.getPlayers().isEmpty())throw new IllegalStateException("Mining reset requires all players evacuated");
            Path dimensions=Objects.requireNonNull(Bukkit.getWorld("world")).getWorldFolder().toPath().toRealPath().getParent().getParent();
            Path target=world.getWorldFolder().toPath().toRealPath();
            if(!dimensions.getFileName().toString().equals("dimensions")||!target.equals(dimensions.resolve("minecraft/mining").normalize()))throw new IllegalStateException("Unexpected mining dimension path; refusing reset: "+target);
            if(!new File(plugin.getDataFolder(),"flat-reset-v0918.done").isFile())throw new IllegalStateException("Initial world migration incomplete");
            long seed;do{seed=java.util.concurrent.ThreadLocalRandom.current().nextLong();}while(seed==world.getSeed());
            var manager=MultiverseCoreApi.get().getWorldManager();
            manager.deleteWorld(DeleteWorldOptions.world(manager.getWorld(world).get())).getOrThrow(f->new IllegalStateException("Mining deletion failed: "+f.getFailureReason()));
            start(plugin,seed);
        }catch(Exception e){throw new IllegalStateException("Cannot reset mining dimension",e);}
    }
    private static YamlConfiguration load(File file)throws Exception{var y=new YamlConfiguration();if(file.exists())y.load(file);return y;}
    private static void save(File file,YamlConfiguration y)throws IOException{
        file=file.getAbsoluteFile();
        file.getParentFile().mkdirs();File before=new File(file.getParentFile(),file.getName()+".before-worlds-v0918");if(file.exists()&&!before.exists())Files.copy(file.toPath(),before.toPath());
        File tmp=new File(file.getParentFile(),file.getName()+".nlk-tmp");y.save(tmp);Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
}
