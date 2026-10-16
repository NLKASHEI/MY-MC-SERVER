# MiaoServer

`WorldExpansion` 与 `MiningWorld` 管理无结构深层超平坦和资源世界。只有 `minecraft:flat` 的一次性授权清空和 `minecraft:mining` 的定期换种可删除维度；检查真实路径、空玩家列表，再调用 Multiverse 原生删除/创建 API。天然矿石预扣来自实际掉落，不扫描或扣除带入背包；放置来源保存在区块 PDC，分矿物余数跨访问保留。跨世界传送事件逐人收费，取消后退费，TPA 同样检查。北京时间三日凌晨四点重置，离线票据关闭但保留背包和资源余数。详见运维文档和本地临时启动测试。

项目自研的轻量中文辅助插件，包含伙伴队伍、GriefPrevention 共享信任、侧边栏和服务器列表样式。无外部数据库或网络服务，玩家数据单独保存在插件目录。

源码使用 Java 25 和本项目锁定的 Paper 26.2/GriefPrevention 16.18.7 API。构建执行 `scripts/build-miao-plugin.ps1`；输出在 `downloads`，确认后更新插件锁定 SHA256，将对应 JAR 上传同版本 GitHub Release。不要将编译后的 JAR 提交 Git。

`DatabaseBackups` 针对锁定的 LuckPerms 5.5.71、QuickShop-Hikari 6.3.0.3 和 Backuper 4.1.2 连接/任务接口接管安全备份。使用各插件现有连接执行 H2 在线备份，校验嵌套 ZIP 的数据库文件与 CRC 后再提交打包任务；接口或数据库类型变化时失败并取消，不回退到复制被锁定文件。升级这三个上游插件时必须验证在线备份与数据库恢复。

服务器列表图标来自本次 imagegen 生成的温馨 MC 小屋图；原图位于 `assets/branding/cozy-server.png`，机械导出为 64×64 PNG 后嵌入插件。完整包包含原图；增量更新通过 JAR 分发图标。

`tests/miao-smoke` 是临时启动检查插件，仅用于本地组装验证，不发布。其测试使用真实 GP 数据存储和临时领地，结束清理领地并恢复原队伍数据。

指南针 Java 入口通过 DeluxeMenus 自身加载器调用 `Menu.getMenuByName(...).openMenu(player)`，PE 继续原生表单。设置界面在点击事件结束后打开。传送只从 Paper `PlayerArmSwingEvent` 发起，并排除最近右键与丢弃动作，不从 `LEFT_CLICK_AIR` 发起；该交互会在 Q 丢弃时被合成。回归覆盖临时 AuthMe 内存会话、伪左键、丢弃关联挥手、真实挥手目标查询、延后打开侧边栏设置和原生菜单解析，不创建玩家账号数据库记录。实际客户端点击仍需上云确认。
