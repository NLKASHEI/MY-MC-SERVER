# 南可熙的神奇妙妙服

Windows 云服务器使用的 Paper 26.2 原版生存辅助服务端。Java 离线账号和基岩版互联账号使用注册、密码登录。未安装反作弊。

已组装主城地图、生存世界和创造超平坦。中文指南针菜单切换世界；主城与生存共用背包，创造区单独隔离。支持坐台阶、躺下、爬行、骑头开关和保守的定时掉落物清理。

指南针「丰饶矿界」（0.9.20）：新区块石质层矿石覆盖约 30%，煤铁铜为主，跨世界入场 100 金币，天然矿产预留三分之一资源费；与生存共享背包。每三天北京时间凌晨四点更换随机种子并重置，禁止设置家园和床重生点。创造超平坦按服主授权一次性清空，改为无结构、128 格深地层。详见 [资源世界与超平坦](docs/operations/resource-world.md)。

按 [mattpocock/skills](https://github.com/mattpocock/skills) 的分类技能、镜像文档、根目录规范组织维护流程；这是服务端项目，组件运行目录单独放在 `servers/survival`。

新增中文侧边栏、伙伴组队和自动同步的领地共享，指南针统一入口。自动备份每两小时一次，只保留最近三份；Java 列表使用温馨 MC 小屋图标。PE 指南针使用原生表单，侧边栏可选择成长数据并保存开关；主城保护不放行 OP。详见 [伙伴与侧边栏指南](docs/operations/community.md)。

0.9.21：同 IP 多账号可自由注册，账号菜单支持自愿选择进度同步（PC↔PE、同 IP Java↔Java、PE↔PE）；验证密码后才绑定，也可保持各自进度并关闭提醒。详见 [账号同步](docs/operations/crossplay-sync.md)。

## 快速使用

金币来自初始赠送、在线活跃奖励、天然矿石开采、亲手打怪和系统收购。指南针交易中心整合系统商店、出售窗口与玩家箱子交易，详见 [金币与交易](docs/operations/economy.md)。

完整部署包：从 [GitHub Release](https://github.com/NLKASHEI/MY-MC-SERVER/releases/latest) 下载 ZIP 和 SHA256 校验文件。GitHub 自动生成的 Source code 仅是源码，不包含 Java 和插件。

以后更新：服务器控制台输入 `stop`，退出后双击 **一键更新.bat**，程序自动获取我们发布的正式更新包、核验 SHA256、完整备份并替换变化的文件。存档、玩家数据、密钥、内存设置保留；本地改过的配置保留，冲突新版另存为 `.new`，详见 [更新指南](docs/operations/update-guide.md)。无需安装 Git、Python 或登录 GitHub。

下载完整部署包并解压到 Windows 服务器。阅读 Minecraft EULA，将 `servers/survival/eula.txt` 改为 `eula=true`，双击 `启动服务器.bat`。内存在 `server-settings.json` 中修改。云端放行 Java TCP 25565 和基岩 UDP 8585。

按现有映射填写：Java `s9-4.yxjsmc.cn:20005` → TCP 25565；基岩 `s9-4.yxjsmc.cn:20013` → UDP 8585。用户已向云商确认映射支持 TCP+UDP，公网实际入服仍需部署后验收。3389 的远程桌面映射保持原用途。

新增 FakePlayer-CE 生电假人、AuraSkills 中文技能成长与 Floodgate 本地 SQLite 账号绑定。绑定后两端使用 Java 主账号的存档、金币、圈地和技能进度；先转移旧 PE 账号物品，再绑定，不能自动合并两份历史记录。详见 [双端与生电指南](docs/operations/crossplay-technical.md)。

指南针有 16 个中文界面，整合世界探索、矿界入场、家园、好友传送、圈地、奖励、假人和账号操作。主面板三排五列，生电与领地面板两排五列，手机表单显示完整矿界规则；AuraSkills 钓鱼扩展为 54 项奖励。布局与奖励说明见 [菜单与钓鱼指南](docs/operations/menu-fishing.md)。已有服务器更新前先备份，按更新指南选择性合并配置，不能用整包覆盖现有世界或玩家数据库。

从源码安装：双击 `安装服务端.bat` 获取锁定组件，再按上面的步骤接受 EULA 并启动。安装需要联网，初次启动部分插件也会获取运行依赖。

完整使用说明见 [服务端指南](docs/operations/server-guide.md)，更新流程见 [更新指南](docs/operations/update-guide.md)，测试结论见 [验收记录](docs/operations/validation.md)。

## 目录

| 路径 | 用途 |
|---|---|
| `skills/engineering` | 配置和升级维护技能 |
| `skills/productivity` | 汉化技能 |
| `docs` | 对应技能文档和运维指南 |
| `.agents/adr` | 架构决策 |
| `config/survival` | 配置模板 |
| `manifests` | 版本与校验清单 |
| `scripts` | Windows 管理工具 |
| `servers/survival` | 生成的实际服务端，不进入 Git |
| `runtime` | 随包 Java 25，不进入 Git |
| `assets/worlds` | 署名主城静态地图模板，无玩家数据 |
| `tools/map-import` | 组装阶段使用的 WorldEdit 导入工具源码 |

## Model-invoked skills

- [configure-minecraft](skills/engineering/configure-minecraft/SKILL.md)：配置插件、权限和互联。
- [update-minecraft](skills/engineering/update-minecraft/SKILL.md)：候选更新、备份和回退。
- [localize-minecraft](skills/productivity/localize-minecraft/SKILL.md)：玩家可见文本汉化。

JAR 和 Java 保留各自上游许可证。仓库保存项目配置、脚本、文档和静态主城地图资产；不上传玩家运行数据或认证密钥。

主城地图署名、来源及模板使用规则见 [地图说明](assets/worlds/README.md)。已有玩家的云服禁止用初始地图模板覆盖存档。

新增 Backuper 定时备份与保留清理、QuickShop-Hikari 玩家箱子交易，以及 Java 遥远地平线服务端支持。创世神 WorldEdit 已安装，仅供管理员使用。详见 [备份、集市与远景指南](docs/operations/backup-market-dh.md)。

连锁挖矿使用 VeinMiner 2.4.1：指南针 → 生电工具箱 → 连锁挖矿，默认蹲下触发，可选择自动触发；只连矿石、最多 32 块，正常消耗耐久与饥饿。服务端视距 20、模拟距离 15。已有 ViaVersion / ViaBackwards 5.12.1 支持 Java 协议版本兼容，详见 [挖矿与版本指南](docs/operations/vein-version.md)。

新领地长、宽、高分别最多 25 格，金铲选两个立体角点、私人粒子预览，超限需 OP 审批；旧领地保留。队员可通过在线玩家头像邀请伙伴，PE 使用原生列表。生存世界 51% 入睡即可跳过夜晚。详见 [立体领地](docs/operations/claims-3d.md)。

0.9.9 新增 BlueMap 5.28 五维度在线地图，中文地图名称、指南针网页入口，公网 14514 映射内网 8100；见 [在线地图指南](docs/operations/online-map.md)。

0.9.9 同时新增公开昵称与私人好友备注，PC 头像菜单、PE 原生表单，实际账号和 UUID 不变；见 [昵称与备注](docs/operations/player-names.md)。

双端绑定支持同 IP 候选提示、对方账号密码验证，以及选择 PE/PC 进度来源；见 [双端进度同步](docs/operations/crossplay-sync.md)。新部署包启动内存 4–12GB，旧默认 2–6GB 首次启动有备份地合并升级，自行调整的内存保留。

新增 Scythe 右键收获与消耗种子的自动补种，指南针 → 生电工具箱可开关；成熟作物按原版掉落，领地、主城和出生点保护有效。生电器材商店提供 18 种基础零件，另售村民蛋 5,000、掠夺者蛋 10,000 金币，均不回收。
