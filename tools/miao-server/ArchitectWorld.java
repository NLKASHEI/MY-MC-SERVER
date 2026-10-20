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
import org.mvplugins.multiverse.inventories.share.Sharables;

/** Permanent creative landscape, never part of mining reset or survival inventories. */
final class ArchitectWorld {
    static final long SEED=8176667507634209290L;
    static void prepare(MiaoServer p){
        File marker=new File(p.getDataFolder(),"architect-config-v0925.done");if(marker.exists())return;
        try{
            File plugins=p.getDataFolder().getParentFile();
            File file=new File(plugins.getParentFile(),"permissions.yml");var y=new YamlConfiguration();y.options().pathSeparator('|');y.load(file);
            for(String node:List.of("multiverse.access.architect","multiverse.access.minecraft:architect","multiverse.teleport.self.w.architect","multiverse.teleport.self.w.minecraft:architect"))y.set("survival.player|children|"+node,true);y.save(file);
            file=new File(plugins,"AuraSkills/config.yml");y=YamlConfiguration.loadConfiguration(file);for(String key:List.of("blocked_worlds","disabled_worlds")){var list=new ArrayList<>(y.getStringList(key));if(!list.contains("architect"))list.add("architect");y.set(key,list);}y.save(file);
            file=new File(plugins,"GriefPreventionData/config.yml");y=YamlConfiguration.loadConfiguration(file);y.set("GriefPrevention.Claims.Mode.architect","Disabled");y.save(file);
            Path script=new File(plugins,"Skript/scripts/survival.sk").toPath();if(Files.exists(script)){
                String text=Files.readString(script,StandardCharsets.UTF_8);if(!text.contains("name of world of loop-player is \"flat\" or \"architect\""))text=text.replace("        if gamemode of loop-player is creative:","        if name of world of loop-player is \"flat\" or \"architect\":\n            continue\n        if gamemode of loop-player is creative:").replace("        if gamemode of player is creative:","        if name of world of player is \"flat\" or \"architect\":\n            send \"{@prefix} &c请先回生存或主城领取奖励。\"\n            stop\n        if gamemode of player is creative:");Files.writeString(script,text,StandardCharsets.UTF_8);
            }
            file=new File(plugins,"DeluxeMenus/gui_menus/worlds.yml");y=YamlConfiguration.loadConfiguration(file);
            try(InputStream in=p.getResource("expanded-worlds-menu.yml")){
                var source=YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8));
                // Reposition shipped world buttons only. Retain custom functional buttons.
                var items=y.getConfigurationSection("items");if(items!=null)for(String key:new ArrayList<>(items.getKeys(false)))if(key.matches("layout_\\d+")&&y.getStringList("items."+key+".left_click_commands").isEmpty()&&y.getStringList("items."+key+".right_click_commands").isEmpty())y.set("items."+key,null);
                Set<Integer> used=new HashSet<>();if(items!=null)for(String key:items.getKeys(false))if(!List.of("lobby","survival","flat","architect","mining").contains(key))used.add(y.getInt("items."+key+".slot",-1));
                for(String key:List.of("lobby","survival","flat","architect","mining")){
                    int slot=source.getInt("items."+key+".slot");if(used.contains(slot)){slot=-1;for(int n=9;n<18;n++)if(!used.contains(n)){slot=n;break;}}if(slot<0)throw new IOException("No room for architect world button");
                    if(!y.contains("items."+key))y.createSection("items."+key,source.getConfigurationSection("items."+key).getValues(true));y.set("items."+key+".slot",slot);if(key.equals("flat")||key.equals("architect"))y.set("items."+key+".lore",source.getStringList("items."+key+".lore"));used.add(slot);
                }
                y.set("items.heading.lore",source.getStringList("items.heading.lore"));y.save(file);
            }
            Files.writeString(marker.toPath(),"Added permanent creative landscape menu and permissions only\n");
        }catch(Exception e){throw new IllegalStateException("Cannot configure architect world",e);}
    }
    static void start(MiaoServer p){
        var groups=MultiverseInventoriesApi.get().getWorldGroupManager();
        if(groups.getGroupsForWorld("architect").stream().anyMatch(g->!g.getName().equals("architecture")))throw new IllegalStateException("Architect inventory conflict; refusing world exposure");
        var group=groups.getGroup("architecture");if(group==null)group=groups.newEmptyGroup("architecture");group.addWorld("architect");group.getShares().setSharing(Sharables.allOf(),true);groups.updateGroup(group);
        var manager=MultiverseCoreApi.get().getWorldManager();var entry=manager.getWorld("minecraft:architect").getOrNull();
        World flat=Bukkit.getWorld("flat");if(flat!=null){flat.setDifficulty(Difficulty.PEACEFUL);manager.getWorld(flat).get().setDifficulty(Difficulty.PEACEFUL).get();}
        if(entry==null)entry=manager.createWorld(CreateWorldOptions.worldKey(NamespacedKey.minecraft("architect")).environment(World.Environment.NORMAL).worldType(WorldType.NORMAL).seed(SEED).generateStructures(true).worldPropertyString("gamemode","creative").worldPropertyString("difficulty","peaceful").worldPropertyString("portal-form","none"))
            .getOrThrow(f->new IllegalStateException("Architect creation failed: "+f.getFailureReason()));
        else if(Bukkit.getWorld(NamespacedKey.minecraft("architect"))==null)manager.loadWorld(entry).getOrThrow(f->new IllegalStateException("Architect load failed: "+f.getFailureReason()));
        World world=Objects.requireNonNull(Bukkit.getWorld(NamespacedKey.minecraft("architect")));
        if(world.getSeed()!=SEED)throw new IllegalStateException("Architect seed differs; existing world retained, no reset performed");
        entry.setAlias("云峦筑境").get();entry.setGameMode(GameMode.CREATIVE).get();entry.setDifficulty(Difficulty.PEACEFUL).get();entry.setPvp(false).get();entry.setAllowFlight(true).get();entry.setPortalForm(AllowedPortalType.NONE).get();entry.setRespawnWorld("architect").get();
        var key=new NamespacedKey(p,"architect-initialized");if(!world.getPersistentDataContainer().has(key)){
            world.setGameRule(GameRule.KEEP_INVENTORY,true);world.setGameRule(GameRule.DO_MOB_SPAWNING,false);world.setGameRule(GameRule.DO_FIRE_TICK,false);
            Location spawn=world.getSpawnLocation();spawn.setY(world.getHighestBlockYAt(spawn.getBlockX(),spawn.getBlockZ(),HeightMap.MOTION_BLOCKING_NO_LEAVES)+1);world.setSpawnLocation(spawn);entry.setSpawnLocation(spawn).get();world.getPersistentDataContainer().set(key,org.bukkit.persistence.PersistentDataType.BYTE,(byte)1);
        }
        manager.saveWorldsConfig();p.getLogger().info("云峦筑境就绪：固定种子 "+SEED+"，创造模式，独立背包组，不参与矿界重置。");
    }
}
