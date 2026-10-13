package cn.nankexi;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Explicit, one-time, key-level fixes; never copies a player database or a world. */
final class RuntimeMigration {
    static void apply(MiaoServer plugin) {
        branding(plugin);
        menuLayout(plugin);
        WalletIconRepair.apply(plugin);
        veinAndDistance(plugin);
        simulationDistance(plugin);
        respawnRules(plugin);
        ShopCatalogRepair.prepare(plugin);
        EconomySetup.apply(plugin);
        ClaimsSetup.apply(plugin);
        TeamInviteSetup.apply(plugin);
        ShopBackSetup.apply(plugin);
        ShopCatalogRepair.apply(plugin);
        TechnicalShopSetup.apply(plugin);
        FarmingSetup.apply(plugin);
        BlueMapSetup.apply(plugin);
        PlayerNamesSetup.apply(plugin);
        CrossplaySyncSetup.apply(plugin);
        File marker = new File(plugin.getDataFolder(), "ui-fixes-v080.done");
        if (marker.exists()) return;
        try {
            patch(plugin, "Essentials/config.yml", Map.of("newbies.spawnpoint", "default", "newbies.announce-format", "&6欢迎 {DISPLAYNAME} &6加入南可熙的神奇妙妙服！"));
            patch(plugin, "AuthMe/config.yml", Map.of("settings.useWelcomeMessage", false, "Protection.geoIpDatabase.enabled", false));
            patch(plugin, "WorldEdit/config.yml", Map.of("navigation-wand.item", "minecraft:recovery_compass"));
            patch(plugin, "WorldGuard/config.yml", Map.of("op-permissions", false, "regions.disable-bypass-by-default", true));
            patch(plugin, "Multiverse-Core/config.yml", Map.of("spawn.first-spawn-location", "w:lobby"));
            patch(plugin, "Multiverse-Core/worlds.yml", Map.of("minecraft:lobby.gamemode", "adventure"));
            patch(plugin, "EconomyShopGUI/config.yml", Map.of("debug", false));
            patch(plugin, "SkinsRestorer/config.yml", Map.of("login.offlineModeWarning.enabled", false));
            // Replace only a known broken action in existing menus; preserve other customizations.
            File account = new File(plugin.getDataFolder().getParentFile(), "DeluxeMenus/gui_menus/account.yml");
            if (account.exists()) {
                YamlConfiguration y = new YamlConfiguration(); y.load(account);
                for (String path : List.of("items.link.left_click_commands", "items.link.right_click_commands")) {
                    List<String> commands = y.getStringList(path);
                    commands.replaceAll(s -> s.equals("[player] linkaccount") ? "[player] accountlink" : s);
                    y.set(path, commands);
                }
                write(account, y);
            }
            File main = new File(plugin.getDataFolder().getParentFile(), "DeluxeMenus/gui_menus/survival.yml");
            if (main.exists()) {
                YamlConfiguration y = new YamlConfiguration(); y.load(main);
                if (!y.contains("items.sidebar") && " ".equals(y.getString("items.frame_46.display_name"))) {
                    y.set("items.frame_46", null);
                    String item = "items.sidebar";
                    y.set(item + ".material", "CLOCK"); y.set(item + ".slot", 46);
                    y.set(item + ".display_name", "&d我的成长 · 侧边栏");
                    y.set(item + ".lore", List.of("&7开关侧边栏，选择喜欢的数据", "&7总时长、本次时长、金币与建造成就"));
                    y.set(item + ".left_click_commands", List.of("[close]", "[player] sidebar settings"));
                    y.set(item + ".right_click_commands", List.of("[close]", "[player] sidebar settings"));
                    write(main, y);
                }
            }
            File spawn = new File(plugin.getDataFolder().getParentFile(), "Essentials/spawn.yml");
            if (spawn.exists()) {
                YamlConfiguration y = new YamlConfiguration(); y.load(spawn);
                if (!y.contains("spawns.newbies") && y.contains("spawns.default")) {
                    y.set("spawns.newbies", y.getConfigurationSection("spawns.default").getValues(true)); write(spawn, y);
                }
            }
            plugin.getDataFolder().mkdirs(); Files.writeString(marker.toPath(), "Applied bounded UI/spawn/protection fixes\n");
            plugin.getLogger().info("已修正中文提示、首次主城出生、绑定按钮和指南针冲突；玩家数据未替换。");
        } catch (Exception e) { throw new IllegalStateException("Cannot apply v0.8 UI fixes", e); }
    }
    private static void branding(MiaoServer plugin) {
        File marker=new File(plugin.getDataFolder(),"branding-v092-final.done");
        if(marker.exists())return;
        try {
            for(String name:List.of("MiaoServer/config.yml","Geyser-Spigot/config.yml","DeluxeMenus/gui_menus/survival.yml","Essentials/config.yml","Essentials/motd.txt","Skript/scripts/survival.sk")) {
                File file=new File(plugin.getDataFolder().getParentFile(),name);
                if(!file.isFile())continue;
                String old=Files.readString(file.toPath());
                String updated=old.replace("南可熙的神奇服","南可熙的神奇妙妙服").replace("[妙妙服]","南可熙提醒您：").replace("[服]","南可熙提醒您：");
                if(updated.equals(old))continue;
                File prior=new File(file.getParentFile(),file.getName()+".before-v092");
                if(!prior.exists())Files.copy(file.toPath(),prior.toPath());
                File temp=new File(file.getParentFile(),file.getName()+".nlk-tmp");
                Files.writeString(temp.toPath(),updated);
                Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
            }
            File sidebar=new File(plugin.getDataFolder(),"config.yml");
            if(sidebar.isFile()) {
                YamlConfiguration y=new YamlConfiguration();y.load(sidebar);
                String title=y.getString("sidebar.title");
                if(title==null||title.equals("&6&l南可熙 &8· &bNLK")) {
                    y.set("sidebar.title","&6&l欢迎游玩");
                    File prior=new File(sidebar.getParentFile(),"config.yml.before-title-v092");
                    if(!prior.exists())Files.copy(sidebar.toPath(),prior.toPath());
                    File temp=new File(sidebar.getParentFile(),"config.yml.nlk-tmp");y.save(temp);
                    Files.move(temp.toPath(),sidebar.toPath(),StandardCopyOption.REPLACE_EXISTING);
                }
            }
            plugin.getDataFolder().mkdirs();Files.writeString(marker.toPath(),"Default branding migrated; local settings and player data preserved\n");
            plugin.getLogger().info("提示与侧边栏已更新；完整服名保持南可熙的神奇妙妙服。");
        }catch(Exception e){throw new IllegalStateException("Cannot migrate NLK branding",e);}
    }
    private static void menuLayout(MiaoServer plugin) {
        File marker=new File(plugin.getDataFolder(),"menu-layout-v0913.done");
        File file=new File(plugin.getDataFolder().getParentFile(),"DeluxeMenus/gui_menus/survival.yml");
        if(marker.exists()||!file.isFile())return;
        try(InputStream in=plugin.getResource("menu-layout.yml")) {
            if(in==null)throw new IOException("Missing menu layout resource");
            YamlConfiguration layout=YamlConfiguration.loadConfiguration(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8));
            YamlConfiguration y=new YamlConfiguration();y.load(file);
            var expected=layout.getConfigurationSection("items");
            var current=y.getConfigurationSection("items");
            if(expected==null||current==null)throw new IOException("Menu items are missing");
            // Unknown custom buttons may occupy the new slots. Leave them for a reviewed merge.
            for(String key:current.getKeys(false)) {
                if(!expected.contains(key)&&!key.startsWith("frame_")&& !key.startsWith("layout_")) {
                    plugin.getLogger().warning("指南针含自定义按钮 "+key+"，保留现有布局，请按新版模板合并。");
                    return;
                }
            }
            for(String key:new ArrayList<>(current.getKeys(false))) {
                if(key.startsWith("frame_")||key.startsWith("layout_")) {
                    if(!y.getStringList("items."+key+".left_click_commands").isEmpty()||!y.getStringList("items."+key+".right_click_commands").isEmpty()) {
                        plugin.getLogger().warning("指南针装饰格含自定义动作，保留现有布局。");return;
                    }
                }
            }
            for(String key:new ArrayList<>(current.getKeys(false)))if(key.startsWith("frame_")||key.startsWith("layout_"))y.set("items."+key,null);
            y.set("size",45);
            for(String key:expected.getKeys(false)) {
                String path="items."+key;
                if(key.startsWith("layout_")||!y.contains(path))y.set(path,expected.getConfigurationSection(key).getValues(true));
                else { y.set(path+".slot",expected.getInt(key+".slot")); y.set(path+".material",expected.getString(key+".material")); }
            }
            // Replace only the old default heading/duplicate clock icon, retaining custom labels and actions.
            if("&b&l生存指南 · 玩家中心".equals(y.getString("items.heading.display_name"))) {
                y.set("items.heading.display_name",expected.getString("heading.display_name"));
                y.set("items.heading.lore",expected.getStringList("heading.lore"));
            }
            if("CLOCK".equals(y.getString("items.sidebar.material")))y.set("items.sidebar.material","SPYGLASS");
            File prior=new File(file.getParentFile(),file.getName()+".before-layout-v0913");
            if(!prior.exists())Files.copy(file.toPath(),prior.toPath());
            File temp=new File(file.getParentFile(),file.getName()+".layout-tmp");y.save(temp);
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
            plugin.getDataFolder().mkdirs();Files.writeString(marker.toPath(),"Compact 5x3 layout; existing button actions preserved\n");
            plugin.getLogger().info("指南针已重排为五列三排，保留原有按钮功能与本地文案。");
        }catch(Exception e){throw new IllegalStateException("Cannot migrate compass menu layout",e);}
    }
    private static void veinAndDistance(MiaoServer plugin) {
        File marker=new File(plugin.getDataFolder(),"vein-distance-v095-v2.done");
        if(marker.exists())return;
        try {
            File plugins=plugin.getDataFolder().getParentFile();
            File messages=new File(plugins,"VeinMiner/messages.json");
            if(!messages.exists()) {
                messages.getParentFile().mkdirs();
                try(InputStream in=plugin.getResource("vein-messages.json")){if(in==null)throw new IOException("Missing vein messages");Files.copy(in,messages.toPath());}
            }
            File menu=new File(plugins,"DeluxeMenus/gui_menus/vein.yml");
            if(!menu.exists())try(InputStream in=plugin.getResource("vein-menu.yml")){if(in==null)throw new IOException("Missing vein menu");Files.copy(in,menu.toPath());}
            File config=new File(plugins,"DeluxeMenus/config.yml");
            YamlConfiguration y=YamlConfiguration.loadConfiguration(config);
            if(!y.contains("gui_menus.vein")){y.set("gui_menus.vein.file","vein.yml");write095(config,y);}
            File technical=new File(plugins,"DeluxeMenus/gui_menus/technical.yml");
            y=YamlConfiguration.loadConfiguration(technical);
            if(!y.contains("items.vein")) {
                boolean occupied=false;
                var items=y.getConfigurationSection("items");
                if(items!=null)for(String key:items.getKeys(false))if(y.getInt("items."+key+".slot",-1)==25)occupied=true;
                if(!occupied)try(InputStream in=plugin.getResource("technical-menu.yml")) {
                    var template=YamlConfiguration.loadConfiguration(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8));
                    y.set("items.vein",template.getConfigurationSection("items.vein").getValues(true));
                    Map<String,Integer> oldSlots=Map.of("stop",20,"kick",22,"help",24);
                    boolean standard=oldSlots.entrySet().stream().allMatch(e->ySlot(technical,e.getKey(),e.getValue()));
                    if(standard)for(String key:oldSlots.keySet())y.set("items."+key+".slot",template.getInt("items."+key+".slot"));
                    write095(technical,y);
                }
            }
            File permissions=new File(plugins.getParentFile(),"permissions.yml");
            y=new YamlConfiguration();y.options().pathSeparator('|');if(permissions.exists())y.load(permissions);boolean changed=false;
            for(String node:List.of("veinminer.command.toggle","veinminer.command.mode","veinminer.veinmine.pickaxe")) {
                String path="survival.player|children|"+node;
                if(!y.contains(path)){y.set(path,true);changed=true;}
            }
            for(String node:List.of("veinminer.pattern.tunnel","veinminer.pattern.staircase_up","veinminer.pattern.staircase_down")) {
                String path="survival.player|children|"+node;
                if(!y.contains(path)){y.set(path,false);changed=true;}
            }
            if(!y.contains("survival.dummy|children|veinminer.veinmine.*")){y.set("survival.dummy|children|veinminer.veinmine.*",false);changed=true;}
            if(changed)write095(permissions,y);
            File properties=new File(plugins.getParentFile(),"server.properties");
            String text=Files.readString(properties.toPath());
            String updated=text.replaceAll("(?m)^view-distance=[^\\r\\n]*","view-distance=20");
            if(updated.equals(text)&&!text.matches("(?s).*\\bview-distance=.*"))updated=text+"\nview-distance=20\n";
            if(!updated.equals(text)) {
                File backup=new File(properties.getParentFile(),"server.properties.before-v095");
                if(!backup.exists())Files.copy(properties.toPath(),backup.toPath());
                Files.writeString(properties.toPath(),updated);
            }
            plugin.getDataFolder().mkdirs();Files.writeString(marker.toPath(),"Vein menu installed; view-distance=20; local settings preserved\n");
            plugin.getLogger().info("连锁挖矿已接入工具箱；服务端视距设为 20，模拟距离保持原值。");
        }catch(Exception e){throw new IllegalStateException("Cannot apply vein mining and view distance settings",e);}
    }
    private static void simulationDistance(MiaoServer plugin) {
        File marker=new File(plugin.getDataFolder(),"simulation-distance-v096.done");
        if(marker.exists())return;
        try {
            File file=new File(plugin.getDataFolder().getParentFile().getParentFile(),"server.properties");
            String text=Files.readString(file.toPath());
            String updated=text.replaceAll("(?m)^simulation-distance=[^\\r\\n]*","simulation-distance=15");
            if(updated.equals(text)&&!text.matches("(?s).*\\bsimulation-distance=.*"))updated=text+"\nsimulation-distance=15\n";
            if(!updated.equals(text)) {
                File backup=new File(file.getParentFile(),"server.properties.before-v096");
                if(!backup.exists())Files.copy(file.toPath(),backup.toPath());
                Files.writeString(file.toPath(),updated);
            }
            plugin.getDataFolder().mkdirs();Files.writeString(marker.toPath(),"Requested simulation-distance=15\n");
            plugin.getLogger().info("模拟距离已设为 15；其他服务端参数保持本地设置。");
        }catch(Exception e){throw new IllegalStateException("Cannot apply requested simulation distance",e);}
    }
    private static void respawnRules(MiaoServer plugin) {
        File marker=new File(plugin.getDataFolder(),"respawn-world-v097.done");
        if(marker.exists())return;
        try {
            File plugins=plugin.getDataFolder().getParentFile();
            File essentials=new File(plugins,"Essentials/config.yml");
            var y=YamlConfiguration.loadConfiguration(essentials);
            y.set("respawn-listener-priority","none");writeVersion(essentials,y,"v097");
            File core=new File(plugins,"Multiverse-Core/config.yml");
            y=YamlConfiguration.loadConfiguration(core);
            y.set("spawn.default-respawn-in-overworld",false);
            y.set("spawn.default-respawn-within-same-world",true);
            y.set("spawn.enforce-respawn-at-world-spawn",false);writeVersion(core,y,"v097");
            plugin.getDataFolder().mkdirs();Files.writeString(marker.toPath(),"Death respawns stay in their world; same-world bed and anchor retained\n");
            plugin.getLogger().info("死亡重生已按所在世界处理，保留同世界床和重生锚；首次入服仍进主城。");
        }catch(Exception e){throw new IllegalStateException("Cannot migrate death respawn settings",e);}
    }
    private static boolean ySlot(File file,String key,int expected) {
        return YamlConfiguration.loadConfiguration(file).getInt("items."+key+".slot",-1)==expected;
    }
    private static void write095(File file,YamlConfiguration y)throws IOException {
        writeVersion(file,y,"v095");
    }
    private static void writeVersion(File file,YamlConfiguration y,String version)throws IOException {
        File backup=new File(file.getParentFile(),file.getName()+".before-"+version);
        if(file.exists()&&!backup.exists())Files.copy(file.toPath(),backup.toPath());
        File tmp=new File(file.getParentFile(),file.getName()+".nlk-tmp");y.save(tmp);
        Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
    private static void patch(MiaoServer p, String name, Map<String, Object> values) throws Exception {
        File file = new File(p.getDataFolder().getParentFile(), name);
        if (!file.exists()) file.getParentFile().mkdirs();
        YamlConfiguration y = new YamlConfiguration(); if (file.exists()) y.load(file);
        values.forEach(y::set); write(file, y);
    }
    private static void write(File file, YamlConfiguration y) throws IOException {
        File tmp = new File(file.getParentFile(), file.getName() + ".miao-tmp"); y.save(tmp);
        // Keep the prior config for reviewing this explicit migration.
        File prior = new File(file.getParentFile(), file.getName() + ".before-v080");
        if (file.exists() && !prior.exists()) Files.copy(file.toPath(), prior.toPath());
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
