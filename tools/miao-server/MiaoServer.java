package cn.nankexi;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;
import org.bukkit.util.CachedServerIcon;
import me.ryanhamshire.GriefPrevention.*;

/** Small, local-only survival helpers. All claim mutations run on the main thread. */
public final class MiaoServer extends JavaPlugin implements Listener {
    private YamlConfiguration data;
    private File store;
    private final Map<UUID, Invitation> invites = new HashMap<>();
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Set<UUID> hidden = new HashSet<>();
    private final Map<UUID, World> deathWorlds = new HashMap<>();
    private final Map<UUID, Location> endPortalSpawns = new HashMap<>();
    private CachedServerIcon icon;
    private String motd;
    private volatile int online;
    private MiaoInterface ui;
    private LobbyGuard lobbyGuard;
    private CuboidClaims cuboids;
    boolean authenticated(Player p) { return ui != null && ui.authenticated(p); }
    boolean land(Player p, String[] args) { return cuboids.command(p,args); }
    boolean inCuboid(Player p) { return cuboids != null && cuboids.at(p.getLocation()) != null; }
    boolean canInvite(Player p) { String t=team(p.getUniqueId());return authenticated(p)&&p.hasPermission("survival.team")&&t!=null&&members(t).size()<getConfig().getInt("teams.max-members",8); }
    boolean inviteCandidate(Player p,Player q) { return !p.getUniqueId().equals(q.getUniqueId())&&authenticated(q)&&!dummy(q)&&team(q.getUniqueId())==null; }
    private record Invitation(String team, long expires) {}
    private DataStore claims() { return GriefPrevention.instance.dataStore; }

    @Override public void onLoad() {
        RuntimeMigration.apply(this);
        DatabaseBackups.migrate(this);
        // One migration for the explicitly requested schedule. Leave other backup settings intact.
        File marker = new File(getDataFolder(), "backup-schedule-v070.done");
        File backup = new File(getDataFolder().getParentFile(), "Backuper/config.yml");
        if (!marker.exists() && backup.isFile()) {
            try {
                YamlConfiguration y = new YamlConfiguration(); y.load(backup);
                y.set("backup.autoBackupPeriod", 120);
                y.set("storages.local.maxBackupsNumber", 3);
                File tmp = new File(backup.getParentFile(), "config.yml.miao-tmp"); y.save(tmp);
                Files.move(tmp.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                getDataFolder().mkdirs(); Files.writeString(marker.toPath(), "120 minutes; 3 local snapshots\n");
                getLogger().info("自动备份已调整：每两小时一次，保留最近三份。");
            } catch (Exception e) { throw new IllegalStateException("Cannot migrate backup schedule", e); }
        }
    }
    @Override public void onEnable() {
        saveDefaultConfig(); store = new File(getDataFolder(), "teams.yml");
        File simulationMarker=new File(getDataFolder(),"simulation-distance-runtime-v096.done");
        if(!simulationMarker.exists()) {
            Bukkit.getWorlds().forEach(w->w.setSimulationDistance(15));
            try { Files.writeString(simulationMarker.toPath(),"First startup applied simulation-distance=15\n"); }
            catch(IOException e){throw new IllegalStateException("Cannot record simulation distance migration",e);}
        }
        File distanceMarker=new File(getDataFolder(),"view-distance-runtime-v095.done");
        if(!distanceMarker.exists()) {
            Bukkit.getWorlds().forEach(w->w.setViewDistance(20));
            try { Files.writeString(distanceMarker.toPath(),"First startup applied view-distance=20\n"); }
            catch(IOException e){throw new IllegalStateException("Cannot record view distance migration",e);}
        }
        data = new YamlConfiguration();
        try { if (store.exists()) data.load(store); }
        catch (Exception e) { throw new IllegalStateException("Refusing to replace unreadable team data", e); }
        motd = color(getConfig().getString("motd", "南可熙的神奇妙妙服").replace("\\n", "\n"));
        try (InputStream in = getResource("server-icon.png")) {
            if (in != null) icon = Bukkit.loadServerIcon(javax.imageio.ImageIO.read(in));
        } catch (Exception e) { getLogger().warning("服务器图标读取失败：" + e.getMessage()); }
        Bukkit.getPluginManager().registerEvents(this, this);
        ui = new MiaoInterface(this);
        lobbyGuard = new LobbyGuard(this);
        new DatabaseBackups(this);
        new EconomyAccess(this);
        new SpawnGuard(this);
        cuboids = new CuboidClaims(this);
        new MajoritySleep(this);
        reconcile();
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            online = (int) Bukkit.getOnlinePlayers().stream().filter(p -> !dummy(p)).count();
            for (Player p : Bukkit.getOnlinePlayers()) if (!dummy(p) && ui.authenticated(p)) render(p);
        }, 20L, Math.max(40L, getConfig().getLong("sidebar.refresh-ticks", 40)));
        Bukkit.getScheduler().runTaskTimer(this, this::reconcile, 100L, 100L);
        getLogger().info("中文侧边栏、组队共享领地、中文服务器列表已启用。");
    }
    private static boolean dummy(Player p) { return p.hasMetadata("fakeplayer:spawned_at"); }
    @EventHandler(priority=EventPriority.MONITOR)
    public void rememberDeathWorld(PlayerDeathEvent event) {
        deathWorlds.put(event.getEntity().getUniqueId(),event.getEntity().getWorld());
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void respawnInDeathWorld(PlayerRespawnEvent event) {
        if(event.getRespawnReason()==PlayerRespawnEvent.RespawnReason.END_PORTAL) {
            Location original=endPortalSpawns.remove(event.getPlayer().getUniqueId());
            if(original!=null)event.setRespawnLocation(original);
            return;
        }
        if(event.getRespawnReason()!=PlayerRespawnEvent.RespawnReason.DEATH)return;
        World world=deathWorlds.remove(event.getPlayer().getUniqueId());
        if(world==null)world=event.getPlayer().getWorld();
        // Valid beds/anchors or a custom spawn in this world remain intact.
        if(!world.equals(event.getRespawnLocation().getWorld()))event.setRespawnLocation(world.getSpawnLocation());
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void rememberEndPortalSpawn(PlayerRespawnEvent event) {
        if(event.getRespawnReason()==PlayerRespawnEvent.RespawnReason.END_PORTAL)
            endPortalSpawns.put(event.getPlayer().getUniqueId(),event.getRespawnLocation().clone());
    }
    private static String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private void say(CommandSender p, String s) { p.sendMessage(color("&6南可熙提醒您： &f" + s)); }
    private void save() {
        try {
            File temp = new File(getDataFolder(), "teams.yml.tmp"); data.save(temp);
            try { Files.move(temp.toPath(), store.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp.toPath(), store.toPath(), StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { throw new IllegalStateException("Cannot persist team data", e); }
    }
    private Set<String> keys(String path) {
        ConfigurationSection s = data.getConfigurationSection(path);
        return s == null ? Set.of() : new HashSet<>(s.getKeys(false));
    }
    String team(UUID player) {
        for (String t : keys("teams")) if (members(t).contains(player.toString())) return t;
        return null;
    }
    List<String> members(String t) { return data.getStringList("teams." + t + ".members"); }
    private boolean leader(Player p, String t) { return p.getUniqueId().toString().equals(data.getString("teams." + t + ".owner")); }
    private String title(String t) { return t == null ? "未加入" : data.getString("teams." + t + ".name", "队伍"); }
    private void notifyTeam(String t, String text) {
        for (String id : members(t)) { Player p = Bukkit.getPlayer(UUID.fromString(id)); if (p != null) say(p, text); }
    }
    private Claim ownedClaim(Player p) {
        Claim c = claims().getClaimAt(p.getLocation(), true, null);
        if (c == null || c.parent != null || c.isAdminClaim() || !p.getUniqueId().equals(c.getOwnerID())) return null;
        return c;
    }
    // Journal the previous explicit trust before granting. Only undo unchanged grants made here.
    private boolean restore(Claim c, String root, String id) {
        if (c.getPermission(id) == ClaimPermission.Build) {
            String prior = data.getString(root + ".grants." + id, "NONE");
            if (prior.equals("NONE")) c.dropPermission(id); else c.setPermission(id, ClaimPermission.valueOf(prior));
            claims().saveClaim(c);
        }
        data.set(root + ".grants." + id, null); return true;
    }
    private void reconcile() {
        if(cuboids!=null)cuboids.syncTeams();
        boolean changed = false;
        for (String key : keys("claims")) {
            String root = "claims." + key;
            Claim c = claims().getClaim(Long.parseLong(key));
            String t = data.getString(root + ".team"), owner = data.getString(root + ".owner");
            boolean valid = c != null && !c.isAdminClaim() && c.parent == null
                && Objects.equals(owner, c.getOwnerID().toString()) && keys("teams").contains(t) && members(t).contains(owner);
            Set<String> wanted = valid ? new HashSet<>(members(t)) : new HashSet<>(); wanted.remove(owner);
            for (String id : keys(root + ".grants")) if (!wanted.contains(id)) {
                // Ownership changes end this journal's automatic grants as well.
                if (c != null) restore(c, root, id);
                else data.set(root + ".grants." + id, null);
                changed = true;
            }
            if (!valid) { data.set(root, null); changed = true; continue; }
            for (String id : wanted) {
                // Do not overwrite an independent manual trust already as strong as Build.
                ClaimPermission before = c.getPermission(id);
                if (data.contains(root + ".grants." + id)) {
                    String prior = data.getString(root + ".grants." + id);
                    if ((before == null && "NONE".equals(prior)) || (before != null && before.name().equals(prior))) {
                        c.setPermission(id, ClaimPermission.Build); claims().saveClaim(c);
                    }
                    continue;
                }
                if (before != null && ClaimPermission.Build.isGrantedBy(before)) continue;
                data.set(root + ".grants." + id, before == null ? "NONE" : before.name());
                save(); // Write-ahead record survives a crash before or after the GP save.
                c.setPermission(id, ClaimPermission.Build); claims().saveClaim(c); changed = true;
            }
        }
        if (changed) save();
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        if (!(sender instanceof Player p)) { say(sender, "MiaoServer 0.8.0：中文组队、侧边栏和服务器图标已加载。玩家使用 /team。"); return true; }
        if (dummy(p)) { say(p, "假人不能使用组队系统。"); return true; }
        if (command.getName().equals("sidebar")) return ui.sidebar(p, a);
        if (command.getName().equals("miao")) return ui.command(p, a);
        if (command.getName().equals("accountlink")) return ui.command(p, new String[]{"link"});
        if (command.getName().equals("land")) return land(p,a);
        if(!ui.authenticated(p)){say(p,"请先完成登录。");return true;}
        if(!p.hasPermission("survival.team")){say(p,"你没有使用组队系统的权限。");return true;}
        String t = team(p.getUniqueId()), sub = a.length == 0 ? "info" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (t != null || a.length != 2 || !a[1].matches("[\\p{L}\\p{N}_-]{1,16}")) { say(p, "先退出现有队伍，再用 /team create 队名（1～16个中文、字母或数字）。"); break; }
                if (keys("teams").stream().anyMatch(k -> title(k).equalsIgnoreCase(a[1]))) { say(p, "这个队名已经有人使用。"); break; }
                t = UUID.randomUUID().toString(); String r = "teams." + t;
                data.set(r + ".name", a[1]); data.set(r + ".owner", p.getUniqueId().toString());
                data.set(r + ".members", List.of(p.getUniqueId().toString())); save(); say(p, "队伍创建成功！用 /team invite 玩家 邀请伙伴。");
            }
            case "invite" -> {
                Player q = a.length == 2 ? Bukkit.getPlayerExact(a[1]) : null;
                if (!canInvite(p) || q == null || !inviteCandidate(p,q)) { say(p, "队员均可邀请在线且未组队的玩家；请确认队伍未满。"); break; }
                invites.put(q.getUniqueId(), new Invitation(t, System.currentTimeMillis() + getConfig().getLong("teams.invite-seconds", 300) * 1000));
                say(q, p.getName() + " 邀请你加入「" + title(t) + "」，五分钟内输入 /team join 接受。"); say(p, "邀请已发送。");
            }
            case "join" -> {
                Invitation i = invites.get(p.getUniqueId());
                if (t != null || i == null || i.expires < System.currentTimeMillis() || !keys("teams").contains(i.team)) { say(p, "没有有效邀请，或你已经在队伍中。"); break; }
                List<String> m = members(i.team);
                if (m.size() >= getConfig().getInt("teams.max-members", 8)) { say(p, "队伍已满，默认最多八人。"); break; }
                m.add(p.getUniqueId().toString()); data.set("teams." + i.team + ".members", m); save(); invites.remove(p.getUniqueId());
                reconcile(); notifyTeam(i.team, p.getName() + " 加入了队伍，已同步共享领地权限。");
            }
            case "leave", "kick" -> {
                if (t == null) { say(p, "你还没有队伍。"); break; }
                String target = p.getUniqueId().toString();
                if (sub.equals("kick")) {
                    if (!leader(p, t) || a.length != 2) { say(p, "队长使用 /team kick 玩家。"); break; }
                    target = null;
                    for (String id : members(t)) if (a[1].equalsIgnoreCase(Bukkit.getOfflinePlayer(UUID.fromString(id)).getName())) target = id;
                }
                if (target == null || target.equals(data.getString("teams." + t + ".owner"))) { say(p, "不能移除队长；队长解散请用 /team disband confirm。"); break; }
                List<String> m = members(t); m.remove(target); data.set("teams." + t + ".members", m); save(); reconcile();
                Player removed = Bukkit.getPlayer(UUID.fromString(target)); if (removed != null) say(removed, "已退出队伍，队伍授予的共享权限已撤销。");
                say(p, "操作完成，原有手动信任不会删除。");
            }
            case "disband" -> {
                if (t == null || !leader(p, t) || a.length != 2 || !a[1].equals("confirm")) { say(p, "队长确认解散：/team disband confirm。只取消共享，不删除领地或建筑。"); break; }
                notifyTeam(t, "队伍已解散，共享权限已撤销，领地和建筑保留。"); data.set("teams." + t, null); save(); reconcile();
            }
            case "unshare" -> {
                if(cuboids.sharing(p,t,true))break;
                Claim c=claims().getClaimAt(p.getLocation(),true,null);
                if(c==null||c.parent!=null||c.isAdminClaim()) {say(p,"请站在要取消共享的生存主领地内；子领地单独管理。");break;}
                if(!p.getUniqueId().equals(c.getOwnerID())&&!p.isOp()){say(p,"只有地主或 OP 可以取消这块领地的共享。");break;}
                String r="claims."+c.getID();
                if(!data.contains(r)){say(p,"这块领地没有启用队伍共享。");break;}
                data.set(r+".team","");save();reconcile();say(p,"已取消队伍共享，领地归属与独立手动信任保留。");
            }
            case "share" -> {
                if(cuboids.sharing(p,t,false))break;
                // Clean a transfer/leave journal before a new owner explicitly shares again.
                reconcile();
                Claim c = ownedClaim(p);
                if (c == null || t == null || Set.of("flat", "lobby").contains(p.getWorld().getName())) { say(p, "请在生存区自己拥有的主领地内操作，并先加入队伍；子领地单独管理。"); break; }
                String r = "claims." + c.getID();
                    if(data.contains(r)) {
                        if(!t.equals(data.getString(r+".team"))){say(p,"这块领地已绑定其他队伍，请先取消共享。");break;}
                        reconcile();
                        for(String id:members(t))if(!id.equals(p.getUniqueId().toString())) {
                            ClaimPermission current=c.getPermission(id);
                            if(current==null||!ClaimPermission.Build.isGrantedBy(current))c.setPermission(id,ClaimPermission.Build);
                        }
                        claims().saveClaim(c);say(p,"已重新确认队伍共享，修复队友的建造与箱子权限。");break;
                    }
                    data.set(r + ".team", t); data.set(r + ".owner", p.getUniqueId().toString()); save(); reconcile();
                    say(p, "领地已共享给「" + title(t) + "」；成员可建造和使用箱子，不能转授权。退队自动撤销。");
            }
            case "chat" -> {
                if (t == null || a.length < 2) { say(p, "队内聊天：/team chat 消息。"); break; }
                String message=ChatColor.stripColor(String.join(" ", Arrays.copyOfRange(a,1,a.length))).replace('&','＆');
                for(String id:members(t)){Player viewer=Bukkit.getPlayer(UUID.fromString(id));if(viewer!=null&&authenticated(viewer))say(viewer,"&b[队内] &f"+ui.names.label(viewer,p)+"："+message);}
            }
            default -> {
                say(p, "&b队伍：&f" + title(t));
                if (t != null) {
                    List<String> names = new ArrayList<>(); for (String id : members(t)) names.add(Objects.toString(Bukkit.getOfflinePlayer(UUID.fromString(id)).getName(), id));
                    say(p, "成员：" + String.join("、", names));
                }
                say(p, "/team create 队名 | invite 玩家 | join | leave | kick 玩家");
                say(p, "/team share 共享脚下领地 | unshare | chat 消息");
                say(p, "/team disband confirm 解散 | /sidebar 开关侧边栏");
            }
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 1) return List.of("info", "create", "invite", "join", "leave", "kick", "share", "unshare", "chat", "disband").stream().filter(v -> v.startsWith(a[0])).toList();
        if (a.length == 2 && Set.of("invite", "kick").contains(a[0])) return Bukkit.getOnlinePlayers().stream().filter(p -> !dummy(p)).map(Player::getName).filter(n -> n.toLowerCase().startsWith(a[1].toLowerCase())).toList();
        return List.of();
    }
    private void render(Player p) {
        if (!ui.enabled(p)) { detach(p); return; }
        Scoreboard b = boards.get(p.getUniqueId());
        if (b == null) {
            // Respect another plugin's sidebar instead of repeatedly taking it over.
            if (p.getScoreboard().getObjective(DisplaySlot.SIDEBAR) != null) return;
            b = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective o = b.registerNewObjective("miao", Criteria.DUMMY, color(getConfig().getString("sidebar.title", "&6&l欢迎游玩"))); o.setDisplaySlot(DisplaySlot.SIDEBAR);
            o.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
            for (int i = 0; i < 15; i++) {
                String entry = ChatColor.values()[i].toString(); Team row = b.registerNewTeam("row" + i); row.addEntry(entry); o.getScore(entry).setScore(15 - i);
            }
            boards.put(p.getUniqueId(), b); p.setScoreboard(b);
        }
        if (p.getScoreboard() != b) return;
        List<String> lines = ui.lines(p, online, title(team(p.getUniqueId())));
        Objective objective = b.getObjective("miao");
        for (int i = 0; i < 15; i++) {
            String entry = ChatColor.values()[i].toString();
            if (i < lines.size()) { b.getTeam("row" + i).setPrefix(lines.get(i)); objective.getScore(entry).setScore(15 - i); }
            else b.resetScores(entry);
        }
    }
    void detach(Player p) {
        Scoreboard b = boards.remove(p.getUniqueId());
        if (b != null && p.getScoreboard() == b) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
    @EventHandler public void quit(PlayerQuitEvent e) { boards.remove(e.getPlayer().getUniqueId()); hidden.remove(e.getPlayer().getUniqueId()); invites.remove(e.getPlayer().getUniqueId()); deathWorlds.remove(e.getPlayer().getUniqueId()); endPortalSpawns.remove(e.getPlayer().getUniqueId()); }
    @EventHandler public void ping(ServerListPingEvent e) {
        e.setMotd(motd);
        if (e instanceof com.destroystokyo.paper.event.server.PaperServerListPingEvent paper) paper.setNumPlayers(online);
        if (icon != null) e.setServerIcon(icon);
    }
    @Override public void onDisable() { if (ui != null) ui.stop(); if (lobbyGuard != null) lobbyGuard.stop(); for (Player p : Bukkit.getOnlinePlayers()) detach(p); }
}
