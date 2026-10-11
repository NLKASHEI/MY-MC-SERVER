# 备份、玩家集市与超远景

## 自动备份

采用 [Backuper 4.1.2](https://github.com/DVDishka/Backuper)，由 MiaoServer 每 120 分钟触发，保留最近 3 份；连续运行时最旧一份通常约四小时前。只开启本地存储，ZIP 压缩级别 1，关闭调试与外部存储。每次先保存世界，再通过 LuckPerms 和 QuickShop 已有 JDBC 连接执行 H2 `BACKUP TO`，异步生成并校验完整数据库快照，然后调用 Backuper 打包。快照失败时取消本次备份，保留现有恢复点。低压缩会多占磁盘，实际负载取决于存档和云盘性能。

目录为项目根目录 `backups/automatic`，清理只针对这个自动存储，不清理更新前的完整备份。包含所有已加载世界、插件数据与配置、Paper 配置、锁定清单和内存设置；排除可重新下载的插件 JAR、依赖缓存及可重建的 DH LOD 数据库。调度与保留机制见 [上游说明](https://github.com/DVDishka/Backuper#basic-configuration)。

管理员可 `/backuper backup local` 手动备份、`/backuper list local` 列出。普通玩家没有备份、删除或恢复权限。恢复前停服，解压到单独目录，检查结构再替换文件；插件 ZIP 和 `scripts/restore-backup.ps1` 的完整停服备份格式不同，不能混用还原入口。

在线备份不是跨所有插件数据库的原子快照；交易或账户数据故障时优先使用完整停服备份。备份包含密码数据库和密钥，必须私有保存。这里是同一云主机的本地备份，应定期复制到另一台机器或私有存储。

### 权限与商店数据库的恢复

Windows 上在线读取 `.mv.db` 会遭遇文件锁。0.9.0 起自动 ZIP 排除这两个正在使用的文件，改为包含 `plugins/MiaoServer/database-snapshots/luckperms.zip` 与 `quickshop.zip`。这是 H2 引擎生成的数据库备份，不是功能缩水的店铺清单导出。恢复时必须停服：解压外层备份；解压 `luckperms.zip` 内的 `luckperms-h2-v2.mv.db` 到 `plugins/LuckPerms/`，解压 `quickshop.zip` 内的 `shops.mv.db` 到 `plugins/QuickShop-Hikari/`，再启动。替换前另存当前文件，使用同一备份里的插件配置和版本。

不要重新开启 Backuper 的 `backup.autoBackup`，它的原生计时器无法保证先完成数据库快照。MiaoServer 接管两小时计时和 `/backuper backup local`；其他列出、状态、取消、清理命令仍由 Backuper 执行。安全入口只支持本地备份，不接受附加停服/重启参数。关闭按世界变化跳过，防止只改权限或商店时遗漏备份。云服更新会一次性迁移这些明确的键，并保留 `config.yml.before-v090`，不会覆盖玩家数据库。

## 玩家箱子商店

采用 [QuickShop-Hikari 6.3.0.3](https://modrinth.com/plugin/quickshop-hikari)，沿用 Vault + Essentials 金币，使用内置 `zh-CN`。指南针交易中心或 `/market` 提供补给、玩家店铺浏览和开店教程。

1. 在生存世界放箱子或木桶，建议放在自己的领地内。
2. 手持商品左键箱子，按提示输入价格，再补库存。
3. 他人按箱子提示输入数量，扣买方金币，支付卖方；没有实际库存不能出售。
4. 看着自己的商店，`/qs buy` 改为收购，`/qs sell` 改为出售，`/qs price 金额` 改价，`/qs remove` 移除，`/qs browse` 浏览。

每人最多 20 家，创建/改价免费，无税，无无限库存或他人管理权限。关闭展示实体和浏览传送；只允许 `world/world_nether/world_the_end`，主城/创造区不开放，另有插件级创造模式交易禁止。保留箱子锁及领地保护检查；普通用户有必要的单项权限。真人双端成交和两人领地冲突需上云验收。

## 遥远地平线

官方 [DHSupport 0.14.0](https://hangar.papermc.io/Jckf/distant-horizons-support/versions/0.14.0) 明确支持 DH 3.1/3.2。Java 26.2 使用 [Distant Horizons 3.2.0-b-26.2](https://modrinth.com/mod/distanthorizons/versions?g=26.2) 和对应 Fabric/NeoForge 加载器。这个客户端版本标记为 beta；目前 3.3.x 不作为本包互通承诺，等待官方协议支持后再升级。

Java 客户端安装 MOD 后可获取远景 LOD；普通客户端和 PE 正常入服，PE 无这个 Java MOD 的效果。LOD 是简化远景，不增加生物活动、红石运行或模拟距离。沿用 Java 游戏连接，无需额外映射端口。

上限 128 区块、一个工作线程、每客户端同时两个 LOD 请求；实时更新半径 32、刷新间隔 60 秒，不为 LOD 生成新地形，不大规模预生成。远景数据库只在运行目录中，一键更新不会覆盖。资源充足后再逐步调高。

## 创世神

WorldEdit 7.4.5 原来已安装，保持管理员限定。`//wand` 取选区工具，`//copy`、`//paste` 复制粘贴，`//undo` 撤销，`/guide worldedit` 看中文说明。大范围操作前备份、分批处理，不给普通玩家 `worldedit.*`。
