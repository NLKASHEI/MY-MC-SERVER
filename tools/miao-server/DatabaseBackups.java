package cn.nankexi;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;

/** Uses each plugin's existing connection, never a second H2 engine or raw locked file. */
final class DatabaseBackups implements Listener {
    private final MiaoServer plugin;
    private final AtomicBoolean preparing = new AtomicBoolean();
    DatabaseBackups(MiaoServer plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> backup(Bukkit.getConsoleSender()), 120L*60*20, 120L*60*20);
    }
    static void migrate(MiaoServer plugin) {
        File marker = new File(plugin.getDataFolder(), "database-backups-v090.done");
        if (marker.exists()) return;
        File file = new File(plugin.getDataFolder().getParentFile(), "Backuper/config.yml");
        try {
            YamlConfiguration y = new YamlConfiguration(); y.load(file);
            Files.copy(file.toPath(), file.toPath().resolveSibling("config.yml.before-v090"), StandardCopyOption.REPLACE_EXISTING);
            // Miao owns the timer so a fresh database snapshot always precedes ZIP preparation.
            y.set("backup.autoBackup", false);
            y.set("backup.skipDuplicateBackup", false);
            List<String> excluded = new ArrayList<>(y.getStringList("backup.excludeDirectoryFromBackup"));
            for (String path : List.of("plugins/LuckPerms/luckperms-h2-v2.mv.db", "plugins/QuickShop-Hikari/shops.mv.db"))
                if (!excluded.contains(path)) excluded.add(path);
            y.set("backup.excludeDirectoryFromBackup", excluded);
            List<String> included = new ArrayList<>(y.getStringList("backup.addDirectoryToBackup"));
            if (!included.contains("plugins")) included.add("plugins");
            y.set("backup.addDirectoryToBackup", included);
            File tmp = new File(file.getParentFile(), "config.yml.miao-tmp"); y.save(tmp);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getDataFolder().mkdirs(); Files.writeString(marker.toPath(), "H2 online snapshots before each local backup; timer 120 minutes\n");
        } catch (Exception e) { throw new IllegalStateException("Cannot enable safe database backups", e); }
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void command(PlayerCommandPreprocessEvent e) {
        if (matches(e.getMessage().substring(1))) {
            e.setCancelled(true);
            if (!e.getPlayer().hasPermission("backuper.local.backup")) { e.getPlayer().sendMessage("§c你没有备份权限。"); return; }
            requested(e.getPlayer(), e.getMessage().substring(1));
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void console(ServerCommandEvent e) {
        if (matches(e.getCommand())) { e.setCancelled(true); requested(e.getSender(), e.getCommand()); }
    }
    private void requested(CommandSender sender, String command) {
        String[] parts = command.trim().split("\\s+");
        if (parts.length != 3 || !parts[2].equalsIgnoreCase("local")) {
            sender.sendMessage("§e安全在线备份请使用 /backuper backup local；重启和停服另行执行。"); return;
        }
        backup(sender);
    }
    private boolean matches(String cmd) {
        String[] parts = cmd.toLowerCase(Locale.ROOT).trim().split("\\s+");
        return parts.length >= 2 && Set.of("backuper", "backuper:backuper").contains(parts[0]) && parts[1].equals("backup");
    }
    private static Object call(Object o, String method) throws Exception {
        Method m = o.getClass().getMethod(method); m.setAccessible(true); return m.invoke(o);
    }
    private static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
    private static Connection luckPerms() throws Exception {
        Object bootstrap = field(Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("LuckPerms")), "plugin");
        Object core = field(bootstrap, "plugin");
        Object storage = call(core, "getStorage");
        Object sql = call(storage, "getImplementation");
        return (Connection) call(call(sql, "getConnectionFactory"), "getConnection");
    }
    private static Connection quickShop() throws Exception {
        Object qs = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("QuickShop-Hikari"));
        qs = call(qs, "getQuickShop");
        return (Connection) call(call(call(qs, "getDatabaseHelper"), "getManager"), "getConnection");
    }
    private void snapshot(Connection connection, String name, String expected) throws Exception {
        Path dir = plugin.getDataFolder().toPath().resolve("database-snapshots"); Files.createDirectories(dir);
        Path temp = dir.resolve(name + ".tmp.zip"); Path target = dir.resolve(name + ".zip");
        try (connection; Statement statement = connection.createStatement()) {
            if (!connection.getMetaData().getDatabaseProductName().equals("H2"))
                throw new IllegalStateException(name + " is no longer H2; refusing an incomplete backup");
            statement.execute("BACKUP TO '" + temp.toAbsolutePath().toString().replace("'", "''") + "'");
        }
        try (ZipFile zip = new ZipFile(temp.toFile())) {
            ZipEntry database = zip.getEntry(expected);
            if (database == null || database.getSize() == 0) throw new IOException("Empty or unexpected H2 snapshot: " + name);
            try (InputStream in = zip.getInputStream(database)) {
                CRC32 crc = new CRC32(); byte[] b = new byte[65536]; int n;
                while ((n = in.read(b)) != -1) crc.update(b, 0, n);
                if (crc.getValue() != database.getCrc()) throw new IOException("Snapshot CRC mismatch: " + name);
            }
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }
    void backup(CommandSender sender) {
        Plugin backuper = Bukkit.getPluginManager().getPlugin("Backuper");
        try {
            if (backuper == null || !backuper.isEnabled()) throw new IllegalStateException("Backuper unavailable");
            Object manager = call(backuper, "getTaskManager");
            if ((boolean)call(manager, "isLocked") || !preparing.compareAndSet(false, true)) {
                sender.sendMessage("§e备份已在进行中，请稍候。"); return;
            }
        } catch (Exception e) { sender.sendMessage("§c无法开始备份：" + e.getMessage()); return; }
        sender.sendMessage("§6[备份] 正在生成权限和商店数据库在线快照…");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all flush");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                snapshot(luckPerms(), "luckperms", "luckperms-h2-v2.mv.db");
                snapshot(quickShop(), "quickshop", "shops.mv.db");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        Object storage = call(backuper, "getStorageManager").getClass().getMethod("getStorage", String.class).invoke(call(backuper, "getStorageManager"), "local");
                        Class<?> taskClass = Class.forName("ru.dvdishka.backuper.backend.task.BackupTask", true, backuper.getClass().getClassLoader());
                        Object task = taskClass.getConstructor(List.class, String.class, boolean.class).newInstance(List.of(storage), "NOTHING", false);
                        Object manager = call(backuper, "getTaskManager");
                        Class<?> base = Class.forName("ru.dvdishka.backuper.backend.task.Task", true, backuper.getClass().getClassLoader());
                        Object result = manager.getClass().getMethod("startTaskAsync", base, CommandSender.class, List.class).invoke(manager, task, sender, List.of("backuper.local.backup"));
                        plugin.getLogger().info("数据库在线快照校验通过，已提交完整本地备份：" + result);
                    } catch (Exception e) { fail(sender, e); }
                    finally { preparing.set(false); }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("数据库快照失败，取消本次备份，保留现有恢复点：" + e);
                Bukkit.getScheduler().runTask(plugin, () -> { fail(sender, e); preparing.set(false); });
            }
        });
    }
    private void fail(CommandSender sender, Exception e) {
        plugin.getLogger().severe("安全备份未完成：" + e);
        sender.sendMessage("§c备份失败，现有备份保留；请检查日志。没有创建缺少数据库的新备份。");
    }
}
