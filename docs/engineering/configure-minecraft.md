# 配置生存服

## What it does

维护 `config/survival` 中的插件配置和普通玩家权限，保留存档和账户数据。

## When to reach for it

添加插件、修改菜单、调整圈地或商店、排查 Java 和 PE 配置时。

## Common questions

模板是否可以覆盖云服务器？新服可以，已有玩家的服要合并变更，不能覆盖数据库。小游戏如何加？参见架构决策，优先独立子服。

## It's working if

插件启动正常，普通玩家权限符合预期，两端均能完成相关操作，存档和账户保持一致。

多世界分组必须检查 Multiverse-Inventories 的 Applicable Worlds：当前它使用旧世界名，不能拿 Multiverse-Core worlds.yml 的 `minecraft:...` 键直接替代。运行 `python tests/validate-config.py` 检查分组、菜单、主城保护和地图模板；实际进服还要检查创造往返、末影箱、装备、副手、经验与重启保存。

`tests/cleanup.sk` 是临时控制台测试脚本：放到测试服的 Skript/scripts 后执行 `/verifycleanup`，验证旧普通掉落物删除，新、命名、贵重和受保护物品保留。无玩家的测试服须先强制加载测试出生点附近区块，测试后解除加载并移除脚本；生产包不包含它。

新增 Backuper、QuickShop-Hikari 和 DHSupport 的配置见 [备份、集市与远景指南](../operations/backup-market-dh.md)。维护时保留玩家商店数据库、备份存储和 LOD 数据，不给普通玩家备份/创世神管理权限。
