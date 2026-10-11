# 配置生存服

## What it does

维护 `config/survival` 中的插件配置和普通玩家权限，保留存档和账户数据。

## When to reach for it

添加插件、修改菜单、调整圈地或商店、排查 Java 和 PE 配置时。

## Common questions

模板是否可以覆盖云服务器？新服可以，已有玩家的服要合并变更，不能覆盖数据库。小游戏如何加？参见架构决策，优先独立子服。

## It's working if

插件启动正常，普通玩家权限符合预期，两端均能完成相关操作，存档和账户保持一致。
