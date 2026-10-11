# 双端账号、生电假人与技能成长

## 云端入口

| 客户端 | 玩家填写 | 云机监听 | 映射协议 |
|---|---|---|---|
| Java | s9-4.yxjsmc.cn:20005 | 25565 | TCP |
| PE / 基岩 | 地址 s9-4.yxjsmc.cn，端口 20013 | 8585 | UDP |

用户已向云商确认现有映射支持 TCP+UDP。服务端按上述目标监听 UDP 8585、公告公网 20013；部署时在 Windows 防火墙和云安全组放行 UDP 8585。公网实际连接未在本机验证。截图的 20004 → 3389 是远程桌面，不用于游戏。以后更改映射时对应修改 Geyser `bedrock.port`、`advanced.bedrock.broadcast-port` 和网络检测脚本。

云商已确认协议，此次不执行公网探测。如上云后需要诊断，可以在另一台外部 Windows 电脑的部署包目录运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-public-network.ps1
```

出现 `Bedrock status OK` 并显示 UDP 20013，表示外部收到 Geyser 响应，证明这条 UDP 游戏入口可达。超时可能由尚未启动、映射错误、安全组/防火墙拦截或网络造成，不能据此证明只支持 TCP。若 Java 检测已失败，应先修复 Java TCP 入口再重试。该工具只探测网络，不修改映射或防火墙。

目前采用 RakNet，当前 Geyser 控制台报告支持 PE 26.30–26.52。未来切换 NetherNet 时另核查 TCP 信令、UDP 数据通道及公网公告，不能只沿用一条 TCP 映射。本机端口测试不能证明云端映射可达。

## 一次绑定，共用身份

1. Java 使用以后长期使用的固定名字注册并 `/login`，这是主账号。离线名字的大小写也应保持一致。
2. PE 使用 Xbox 身份经 Floodgate 入服，先按 AuthMe 提示注册、登录。未绑定时名字带 `.`，拥有独立数据。
3. 在已经登录的 Java 端输入 `/linkaccount`，按插件提示输入目标 PE 名称并取得验证码；在已经登录的 PE 端按提示输入确认命令。命令参数以当前插件提示为准，验证码五分钟有效。
4. 绑定后重连 PE，身份切换为 Java 主账号，使用 Java 主账号的服内密码登录。两端依次登录，共用同一份 UUID 数据；同一账号不能同时双端在线。
5. 需要解除时使用 `/unlinkaccount`。解除后 PE 恢复其原本独立身份和数据。

共用内容包括原版背包/末影箱/经验/位置/进度，以及 Essentials 金币与家、GriefPrevention 圈地、LuckPerms 权限、在线奖励记录、AuraSkills 等级。创造超平坦的背包仍与生存隔离；账号绑定不取消世界隔离。各插件升级或改动存储键后需重新验收。

**两份已有进度不会自动合并。** 绑定采用 Java 进度，旧 PE 数据保留但绑定期间不可访问。先把 PE 的物品、金币转到 Java；圈地授权/转让由管理员核实后操作，技能如需迁移要先备份再使用插件专用转移命令。不要直接覆盖整个 playerdata 或数据库。

当前使用本服 SQLite 本地绑定，关闭全局绑定，避免全局正版 UUID 与离线 Java UUID 不一致。SQLite 扩展位于 `plugins/floodgate/floodgate-sqlite-database.jar`，由独立哈希清单管理，不能放到 plugins 根目录当作 Bukkit 插件。绑定库、Floodgate 密钥、AuthMe 数据库都是运行数据，必须随整服备份，不能上传 Git 或混入新服部署包。

参考：[Floodgate 本地绑定](https://geysermc.org/wiki/floodgate/linking/)、[离线服务器绑定问题](https://geysermc.org/wiki/floodgate/issues/)。

## 生电假人

FakePlayer-CE `fp.build13` 搭配 CommandAPI `12.1.0`，支持 Paper 26.2，模拟实际玩家实体。默认每人一个、全服最多二十个。关闭按 IP 共享额度，避免 Geyser 转接导致多人被误计为同一个 IP。玩家离线十五秒后移除假人，保存其背包；重启不自动恢复。

- `/fp spawn`：在玩家当前位置创建，普通玩家不能指定别人的名字。
- `/fp list`、`/fp status`：查看状态。
- `/fp attack continuous`、`/fp use continuous`：持续攻击或使用；`/fp stop` 停止。
- `/fp invsee`：操作自己的假人背包，放入工具或补给。
- `/fp kick`：移除，保存背包；下次生成读取原数据。

主城和创造区禁止假人指令，普通玩家没有假人任意传送、配置无敌、强制占用真实玩家身份、代执行命令、HTTP 管理等权限。假人拥有独立 UUID，插件通过 AuthMe API 处理其登录；真实玩家没有名字前缀免密通道。假人生成后通过 UUID 分配 `survival.dummy`，禁止在线奖励、所有技能经验并排除技能排行榜；Skript 还通过插件实体元数据跳过在线奖励统计。首次出生和登录插件处理后恢复假人生成位置，避免把它送回主城。假人动作对圈地、WorldGuard、红石刷怪和服务器性能的实际影响仍需普通玩家客户端与机器验收。

Paper 并不保证所有 Carpet/Fabric 生电特性；本插件不改变 Paper 的刷怪半径、模拟距离和红石实现。不要依赖假人无限离线加载，也不要未经测量提高总额度。

参考：[FakePlayer-CE 中文说明](https://github.com/EndlessPixel-Studio/FakePlayer-CE/blob/fp.build13/README_zh.md)。

## 技能成长

使用免费 AuraSkills `2.4.0`，不是 mcMMO 本体。`/skills` 中文图形界面、`/stats` 查看属性、`/skilltop` 排行榜。挖矿、伐木、钓鱼、农业、战斗等行为升级并逐步解锁能力；技能会改变部分原版数值，属于用户请求的玩法增强。

主城、创造区禁用技能效果与经验，创造模式不获取经验；技能职业收入关闭，不通过技能生成金币。保留放置方块检查，避免反复放置/挖掘刷等级。默认上游能力平衡先保留，真实玩家体验后再调整等级速度和产出；如决定完全原版数值，可另行禁用奖励属性和能力。

完整包附带首次成功获取的运行依赖。源码安装初次启动仍需联网；启动使用 Maven Central 的阿里云镜像，避免默认 Google 镜像连接失败。参考：[AuraSkills](https://wiki.aurelium.dev/auraskills/)、[配置说明](https://wiki.aurelium.dev/auraskills/main-config/)。
