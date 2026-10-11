# 更新与回退

## 一键获取我们发布的更新

首次部署选最新完整 ZIP，以后在控制台输入 `stop`，待退出后双击根目录 **一键更新.bat**。无需 Git、Python 或 GitHub 账号。它使用公开 GitHub Release API 获取最新正式版（[官方接口说明](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)）。需要云服务器能访问 GitHub 和发生变化的插件下载源，网络失败不会开始替换。

程序下载小型 `MY-MC-SERVER-update-v版本.zip`，包含维护脚本、说明、锁定清单和配置，不包含 Java、大型地图或运行数据；Paper 和插件只在 SHA256 不同时下载。压缩包及每个文件核验后创建完整停服备份，再按单文件清单替换。启动依赖由原启动流程管理。

以下文件保留：世界区块、`level.dat`、玩家存档/背包、经济/登录/绑定/圈地数据库、权限数据、密钥、EULA、白名单、封禁列表、`server-settings.json` 及云端自行添加的插件。不会删除旧文件或解压初始主城。端口/MOTD 的 `server.properties`、出生点、Multiverse 世界表/背包分组及 WorldGuard 现有区域，即使没有改过也不自动覆盖；如本版调整这些内容，会提供新版供人工迁移。

普通菜单、Skript、奖励表等配置与上次官方基线一致时自动更新；如果本地改过（包括插件启动时自行改写），保留本地文件，新版写到备份目录 `pending/原路径.new`，清单为 `pending-configs.json`。根目录 `.update-state.json` 记录版本和官方基线，更新后不要删除。遇到冲突可以先使用当前配置，按 `.new` 差异选择性合并，再启动。程序保留旧目录和新增的本地文件，不执行盲目同步或文件删除。

发生复制错误时自动回退本次替换，完整备份仍保留。每次成功会显示备份位置；玩家重新进入后若插件迁移异常，停服再使用下面的完整还原流程。检查新版而不应用：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/update-release.ps1 -CheckOnly`。禁止自动降级或跨 Minecraft 版本，Java 更换需人工迁移。程序不会自动停服、重启或处理更换内存设置。

旧版 0.4.0 首次接入：从 0.5.0 Release 下载 `一键更新.bat` 与 `update-release.ps1`，前者放旧服根目录，后者放 `scripts`，停服双击即可。旧完整包里的配置模板作为首次基线；不知道来源或已修改的配置宁可保留。

维护者发布：更新 `release-version.json` 和 CHANGELOG，运行 `scripts/build-update.ps1 -BaseRef 上次发布的标签` 生成 ZIP/校验文件，运行 `scripts/package.ps1` 生成首次部署整包，将两组资产和首次接入入口发布到同一个 Release。跳过版本的客户端仍按自己的基线比较文件。构建目录与运行数据不进 Git。

## 单独检查上游插件（维护者）

1. 双击 `检查更新.bat` 获取版本报告。仅查询，不覆盖正式组件；特殊来源的 EssentialsX 开发构建和商店需要人工查上游。
2. 在项目根目录运行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-updates.ps1 -Download` 下载候选更新到 `downloads/updates-时间`。
3. 查看 `report.json` 与上游发布说明，确认所有候选支持 Paper 26.2、Java 25 和当前依赖。GitHub 最新版不等于兼容版本。删除不准备应用的 `candidates.json` 条目即可缩小更新范围。
4. 云服控制台输入 `stop`，确认退出。运行 `scripts/apply-update.ps1 -Stage 候选目录`。脚本核验哈希、做完整停服备份，再只替换 JAR 和锁定清单，不覆盖配置。
5. 启动，检查日志，完成客户端验收后恢复开放。若未通过，停服后运行 `scripts/restore-backup.ps1 -Backup backups/备份时间`，还原整个服务端及版本清单。旧状态另存，脚本不删除它。

备份包含世界、账号、插件变量、权限和密钥，存放在自己的私有存储，定期复制到云主机之外。备份容量和保留周期由服主根据实际人数安排。

当前脚本针对同一 Minecraft 版本的组件更新。大版本升级应先复制整个服务端做迁移测试，不能在升级后的世界上尝试降级。

更新成功后同步 Git 中的 `manifests`、经过清理的配置模板、CHANGELOG 和验收记录。部署包可用 `scripts/package.ps1` 重新生成，只带固定的初始主城地图模板，不包含正在使用的生存区块、玩家、账户、日志或认证密钥。

更新 JAR 不解压 `assets/worlds/bootstrap-26.2.zip`，也不重建背包分组。更改 Multiverse-Inventories 分组需停服备份，并按官方迁移命令处理已有背包；直接把有玩家的世界加入新分组可能丢失当前背包。更新后核查 Applicable Worlds、模式隔离和两个客户端的创造往返、末影箱、重启保留测试。

FakePlayer-CE 与 CommandAPI 成对检查版本和权限，CommandAPI 必须取 Paper JAR。Floodgate SQLite 扩展由 `manifests/extensions.lock.json` 管理，目前更新检查会提示人工审核；停服备份后只替换该清单指向的 `plugins/floodgate/floodgate-sqlite-database.jar`，保留绑定数据库和密钥。切勿把扩展移到 plugins 根目录。AuraSkills 更新不得覆盖 userdata、备份或当前配置，更新后复查禁用世界、假人经验权限、中文版菜单与技能收益。
