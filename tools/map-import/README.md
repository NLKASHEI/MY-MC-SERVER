# 主城组装工具

这是本机组装阶段使用的控制台插件，调用 WorldEdit 官方 API 导入 Kahcerus 的免费主城蓝图。正式发布包不带这个插件。

蓝图原页：https://www.planetminecraft.com/project/minecraft-lobby-spawn-map-schematic-free/

作者：Kahcerus。蓝图下载的 SHA256：`455187e15040ef42d9b8df01312e5e9a4c2ed1b10819354945b4483d51638534`。

原始蓝图选择范围为 405 × 223 × 502，约 159 万个非空气方块。只导入方块，不导入实体；生成虚空底图，在 `0,100,0` 设置出生点。已导入标记存在时拒绝再次覆盖。插件不能由玩家调用。

编译依赖来自锁定 Paper 的运行库与 WorldEdit.jar，使用随包 JDK 25。正式安装通过主城种子包复现，已有云服存档不得用种子覆盖。
