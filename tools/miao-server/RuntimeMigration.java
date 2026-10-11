package cn.nankexi;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Explicit, one-time, key-level fixes; never copies a player database or a world. */
final class RuntimeMigration {
    static void apply(MiaoServer plugin) {
        branding(plugin);
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
