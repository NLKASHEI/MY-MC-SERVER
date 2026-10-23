# 更新与回退

## 一键获取我们发布的更新

0.9.26：首次安装称号目录；玩家记录单独保存到 `plugins/MiaoServer/titles-data.yml`，不随更新覆盖。指南针只合并默认重复入口和独立称号按钮，保留自定义功能；迁移仅暂存所改菜单文件，不执行整服备份。详见 [称号殿堂](titles.md)。后续每次版本更新必须同步内嵌 `release-notes.yml`，由配置验证检查版本一致。

同版安装社区生活与奇珍阁，社区玩家状态单独存到 `community-data.yml`。矿界新增下界/末地，背包仍加入原生存组；旧100金币门票升级为200，保留其他自定价。连片矿脉仅影响新生成区块，已生成区块等三天重置后更新。三维度一起换种重置，不清空生存、主城或建筑世界。出售窗口和已知旧回收价按范围合并。

0.9.25 首次启动创建固定种子的 `architect` 创造世界并建立独立 `architecture` 背包组，再开放指南针入口。已有同名世界种子不同或分组冲突时拒绝初始化，不清空或覆盖。权限、AuraSkills 排除项和世界按钮按范围合并；保留原生存、矿界、超平坦及玩家存档。无需手动覆盖 worlds.yml 或 groups.yml。

0.9.24 增加进度与实际用户名的独立选择。已有绑定升级后保持原样；需由玩家打开原账号菜单、选择目标名字并验证其密码后才变更。启动只合并账号按钮说明和指南，不改既有绑定或玩家存档。继续不执行整服更新备份。

0.9.23 起，按服主要求，两个更新脚本均不再调用整服备份。Backuper 仍每两小时备份、保留最近三份。更新器只暂存本次变更文件供复制失败撤回，成功后删除；不会扫描复制世界和数据库。旧云服第一次获取本版时仍运行旧引擎，可能执行最后一次旧备份；完成后未来更新自动采用新行为。若希望这一次也跳过，可先安装本版 `MY-MC-SERVER-updater.zip` 再更新。历史整服备份不自动删除。

0.9.22 修复同步菜单跳转和离线头像查询，刷新历史账号 IP，并在首次启动替换旧绑定说明。只备份和替换说明书的已知文本（`.before-sync-menu-v0922`），不需要手动替换配置，不修改账号数据库、既有绑定或玩家存档。

0.9.21 停服更新再启动，会将 AuthMe `settings.restrictions.maxRegPerIp` 合并为 `0`，并更新账号面板和提示开关；配置备份后缀 `.before-account-sync-v0921`。无需手动改云服配置，密码、账号和进度不因更新自动绑定。玩家自愿选择后才启用同平台/跨平台进度共享。

0.9.20 停服更新并启动后自动合并矿石覆盖率和已知菜单说明，保留费用、账本和旧区块。新增区块为约 30% 石质层矿石覆盖，等计划换种重置后整个新矿界使用新生成规则。门票关联矿界 UUID，旧家园入口关闭；不需要手动替换整个配置目录。首次启动设置生存三维度困难且死亡保留物品/经验、矿界和平且死亡保留物品/经验、主城冒险；之后三维度规则跟随主世界，矿界死亡返回主世界并显示已预留结算，不再次扣背包。原 Multiverse 世界表备份为 `worlds.yml.before-world-policy-v0920`。

0.9.18 是服主明确授权的世界变更：首次启动只清空旧 `minecraft:flat`，不额外备份该维度，重新生成无结构、128 层的创造超平坦；完成标记 `plugins/MiaoServer/flat-reset-v0918.done` 以后不得删除，否则会再次触发清空。生存、主城和玩家数据不重置。新增矿界每三天北京时间凌晨四点换种重置；背包分组通过原生 API 仅添加 `mining`，不要求手动覆盖 `groups.yml` 或 `worlds.yml`。标准更新程序仍保留既有停服备份流程。相关配置和四个世界按钮由有范围的迁移合并，资源菜单注册到 DeluxeMenus；首次安装也执行相同初始化。

0.9.13 修复系统商店分类：交易分类的 `display-item` 必须为 `false`，`true` 表示展示物品并排除正常分类。首次启动合并四个分类的启用与模式，保留现有商品页与价格；之后只补缺失的配置。历史迁移备份从商店扫描目录移至 `plugins/MiaoServer/config-backups/v0913/EconomyShopGUI`，保留文件内容，避免被误加载。指南针重新合并默认图标与格位、清除旧装饰格，备份为 `survival.yml.before-layout-v0913`；自定义按钮冲突仍保留并提示人工合并。

首次部署选最新完整 ZIP，以后在控制台输入 `stop`，待退出后双击根目录 **一键更新.bat** 或 **update-fast.bat**。两个入口均默认加速，自动查找最新正式版本，不锁定版本。无需 Git、Python 或 GitHub 账号。它使用公开 GitHub Release API 获取最新正式版（[官方接口说明](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)）。需要云服务器能访问 GitHub 和发生变化的插件下载源，网络失败不会开始替换。

### 永久入口（0.9.3 起）

已有云服仅需安装一次 `MY-MC-SERVER-updater.zip`：停服，解压合并到原服务端根目录；只放入两个更新按钮和 `scripts/update-release.ps1`，不覆盖世界、数据库或插件配置。以后始终使用同一个按钮，不必再次下载入口或手动转移 Release。默认通过 `https://gh-proxy.org/` 加速公开 GitHub API 和资产，失败尝试直连。引擎与按钮随未来更新包维护；已是最新版本时直接提示退出。原本锁定旧版本的 `update-fast.bat` 会被这个永久入口替换。

维护者用 `scripts/build-permanent-updater.ps1` 构建固定资产名 `MY-MC-SERVER-updater.zip`，新正式 Release 一并发布；客户端日常更新只需要轻量更新包，不重新下载永久入口 ZIP。

### 0.9.0 旧入口的临时修复（历史）

如果出现 `Disallowed update file` 且文件名乱码，原因是旧脚本在 PowerShell 5.1 下按系统编码读取 UTF-8 更新清单。不是下载没有完成，也不能通过放宽路径检查处理。修复后的脚本显式按 UTF-8 读取所有 JSON；回归测试清单保留实际中文字符，覆盖中文 `.bat` 文件名。

从 0.9.0 Release 下载 `MY-MC-SERVER-fast-update-v0.9.0.zip`，停服后把全部内容解压到**现有服务端根目录**，合并 `scripts`，双击 `update-fast.bat`。该入口包含修复后的独立引擎，固定到 0.9.0 和已发布更新包的 SHA256；优先使用 [GH-Proxy 官方说明](https://gh-proxy.com/docs/github-accelerator) 的 `https://gh-proxy.org/` 转发公开 API 和 Release 下载，失败或组件校验不一致尝试直连。其他插件官方源保持直连，不上传任何存档或凭据。

成功后恢复普通入口的兼容性：将修复引擎复制为 `scripts/update-release.ps1`，旧脚本另存 `.before-fast-update`。本临时入口不会被旧更新包替换，后续版本继续使用普通中文“一键更新.bat”。新版引擎也支持 `-GitHubProxy https://gh-proxy.org/`，方便指定加速；0.9.0–0.9.2 普通入口默认直连；从 0.9.3 起两个永久入口默认加速。停服、完整备份、世界/数据库保护、配置冲突、校验及失败回退均保留。

维护者用 `scripts/build-update-rescue.ps1 -ReleaseTag v0.9.0` 从当前修复引擎构建救援 ZIP；固定 SHA 必须取已经发布的原更新包，不能重打包并替换旧 Release 资产。只需上传新救援 ZIP 与校验文件，不重新上传大型完整服务端。

程序下载小型 `MY-MC-SERVER-update-v版本.zip`，包含维护脚本、说明、锁定清单和配置，不包含 Java、大型地图或运行数据；Paper 和插件只在 SHA256 不同时下载。压缩包及每个文件核验并确认停服后，按单文件清单替换，不创建整服备份。启动依赖由原启动流程管理。

以下文件保留：世界区块、`level.dat`、玩家存档/背包、经济/登录/绑定/圈地数据库、权限数据、密钥、EULA、白名单、封禁列表、`server-settings.json` 及云端自行添加的插件。不会删除旧文件或解压初始主城。端口/MOTD 的 `server.properties`、出生点、Multiverse 世界表/背包分组及 WorldGuard 现有区域，即使没有改过也不自动覆盖；如本版调整这些内容，会提供新版供人工迁移。

普通菜单、Skript、奖励表等配置与上次官方基线一致时自动更新；如果本地改过（包括插件启动时自行改写），保留本地文件，新版写到 `updates/release-版本-时间-标识/pending/原路径.new`，旁边清单为 `pending-configs.json`。该目录仅保存更新记录和冲突配置，不是整服备份。根目录 `.update-state.json` 记录版本和官方基线，更新后不要删除。遇到冲突可以先使用当前配置，按 `.new` 差异选择性合并，再启动。程序保留旧目录和新增的本地文件，不执行盲目同步或文件删除。

发生复制错误时自动回退本次替换；成功后清理临时旧文件，不提供更新时点的整服还原点。启动后若迁移异常，停服并选用已有的定期备份或手动备份；Backuper 备份按其格式恢复，下面的 restore-backup 脚本仅适用于原手动脚本格式。检查新版而不应用：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/update-release.ps1 -CheckOnly`。禁止自动降级或跨 Minecraft 版本，Java 更换需人工迁移。程序不会自动停服、重启或处理更换内存设置。

旧版 0.4.0 首次接入：从 0.5.0 Release 下载 `一键更新.bat` 与 `update-release.ps1`，前者放旧服根目录，后者放 `scripts`，停服双击即可。旧完整包里的配置模板作为首次基线；不知道来源或已修改的配置宁可保留。

维护者发布：更新 `release-version.json` 和 CHANGELOG，运行 `scripts/build-update.ps1 -BaseRef 上次发布的标签` 生成 ZIP/校验文件，运行 `scripts/package.ps1` 生成首次部署整包，将两组资产和首次接入入口发布到同一个 Release。跳过版本的客户端仍按自己的基线比较文件。构建目录与运行数据不进 Git。

## 单独检查上游插件（维护者）

1. 双击 `检查更新.bat` 获取版本报告。仅查询，不覆盖正式组件；特殊来源的 EssentialsX 开发构建和商店需要人工查上游。
2. 在项目根目录运行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-updates.ps1 -Download` 下载候选更新到 `downloads/updates-时间`。
3. 查看 `report.json` 与上游发布说明，确认所有候选支持 Paper 26.2、Java 25 和当前依赖。GitHub 最新版不等于兼容版本。删除不准备应用的 `candidates.json` 条目即可缩小更新范围。
4. 云服控制台输入 `stop`，确认退出。运行 `scripts/apply-update.ps1 -Stage 候选目录`。脚本核验哈希并检查停服，只替换 JAR 和锁定清单，不覆盖配置、不自动备份。
5. 启动，检查日志，完成客户端验收后恢复开放。若未通过，停服后运行 `scripts/restore-backup.ps1 -Backup backups/备份时间`，还原整个服务端及版本清单。旧状态另存，脚本不删除它。

备份包含世界、账号、插件变量、权限和密钥，存放在自己的私有存储，定期复制到云主机之外。备份容量和保留周期由服主根据实际人数安排。

当前脚本针对同一 Minecraft 版本的组件更新。大版本升级应先复制整个服务端做迁移测试，不能在升级后的世界上尝试降级。

更新成功后同步 Git 中的 `manifests`、经过清理的配置模板、CHANGELOG 和验收记录。部署包可用 `scripts/package.ps1` 重新生成，只带固定的初始主城地图模板，不包含正在使用的生存区块、玩家、账户、日志或认证密钥。

更新 JAR 不解压 `assets/worlds/bootstrap-26.2.zip`，也不重建背包分组。更改 Multiverse-Inventories 分组需停服备份，并按官方迁移命令处理已有背包；直接把有玩家的世界加入新分组可能丢失当前背包。更新后核查 Applicable Worlds、模式隔离和两个客户端的创造往返、末影箱、重启保留测试。

FakePlayer-CE 与 CommandAPI 成对检查版本和权限，CommandAPI 必须取 Paper JAR。Floodgate SQLite 扩展由 `manifests/extensions.lock.json` 管理，目前更新检查会提示人工审核；停服备份后只替换该清单指向的 `plugins/floodgate/floodgate-sqlite-database.jar`，保留绑定数据库和密钥。切勿把扩展移到 plugins 根目录。AuraSkills 更新不得覆盖 userdata、备份或当前配置，更新后复查禁用世界、假人经验权限、中文版菜单与技能收益。

0.9.13 新增 Scythe 右键收获插件，首次启动仅创建缺失的 Scythe 中文配置，并追加生电器材分类、收获开关和两种高价刷怪蛋；现有余额、商品价格、种子/物品和领地数据保留。补种消耗种子并在执行时重查权限与空地，已有方块不覆盖。全部变更通过永久更新入口下载，无需再次上传更新脚本。

0.9.14 独立修复指南针的钱包图标，覆盖钱包按钮的显示材质和数量，清除残留模型与动态数量，不依赖整页重排标记。保留点击动作、文案、可见权限条件和实际金币余额；自定义功能按钮占位时寻找空格，备份位于 MiaoServer/config-backups/v0914。

0.9.15 修复下界/末地默认死亡重生：无有效床锚时返回对应主世界，保留有效个人床锚及末地出口；主城、创造区仍独立。停服使用永久更新按钮后启动即可，首次启动只合并相关重生键，生成 `.before-v0915` 备份，不重置任何个人重生点或世界数据。

0.9.16 新增单人发包豁免命令及 NLKASHEI 的一次性登录后预置。永久更新后启动即可；账号成功登录后自动生效，无需手动改 ViaVersion 配置。运行名单文件 `plugins/MiaoServer/packet-exemptions.yml` 保留，不会每次启动重置。
