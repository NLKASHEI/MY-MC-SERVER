# 初始主城地图模板

`bootstrap-26.2.zip` 是组装后的静态地图资产，仅用于创建新服。包含主城地图区块和 Paper 26.2 的五个维度的生成、出生点等初始元数据；不包含生存区块、玩家数据、实体区块、账户、插件数据库或密钥。

主城原作：[Minecraft Lobby/Spawn Map + Schematic [Free]](https://www.planetminecraft.com/project/minecraft-lobby-spawn-map-schematic-free/)，作者 **Kahcerus**。作者在原页公开免费提供并邀请使用。请保留署名和来源；免费使用不等于获得任意转售或重新授权的许可。本项目不声称拥有该地图版权。

原始蓝图下载 SHA256：`455187e15040ef42d9b8df01312e5e9a4c2ed1b10819354945b4483d51638534`。选择范围 405 × 223 × 502，约 159 万个非空气方块。主城出生点 `0,100,0`，世界边界宽 512。主城为虚空地图，脚本在玩家落到 Y < -20 时送回出生点。

模板 ZIP 的校验值见 `manifests/world-template.lock.json`。用 `scripts/export-world-template.ps1` 从停服且没有玩家数据的组装服生成；该脚本只导出允许的元数据与主城区块。

已有玩家的云服必须保留当前 `servers/survival/world`，不能重新解压模板覆盖。以后更换主城也应只迁移主城维度并备份、验收，不能替换整个 world。Paper 26.2 的多维度均位于 `world/dimensions/minecraft/` 下。
