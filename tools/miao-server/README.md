# MiaoServer

项目自研的轻量中文辅助插件，包含伙伴队伍、GriefPrevention 共享信任、侧边栏和服务器列表样式。无外部数据库或网络服务，玩家数据单独保存在插件目录。

源码使用 Java 25 和本项目锁定的 Paper 26.2/GriefPrevention 16.18.7 API。构建执行 `scripts/build-miao-plugin.ps1`；输出在 `downloads`，确认后更新插件锁定 SHA256，将对应 JAR 上传同版本 GitHub Release。不要将编译后的 JAR 提交 Git。

服务器列表图标来自本次 imagegen 生成的温馨 MC 小屋图；原图位于 `assets/branding/cozy-server.png`，机械导出为 64×64 PNG 后嵌入插件。完整包包含原图；增量更新通过 JAR 分发图标。

`tests/miao-smoke` 是临时启动检查插件，仅用于本地组装验证，不发布。其测试使用真实 GP 数据存储和临时领地，结束清理领地并恢复原队伍数据。
