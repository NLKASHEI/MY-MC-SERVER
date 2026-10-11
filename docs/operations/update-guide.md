# 更新与回退

1. 双击 `检查更新.bat` 获取版本报告。仅查询，不覆盖正式组件；特殊来源的 EssentialsX 开发构建和商店需要人工查上游。
2. 在项目根目录运行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-updates.ps1 -Download` 下载候选更新到 `downloads/updates-时间`。
3. 查看 `report.json` 与上游发布说明，确认所有候选支持 Paper 26.2、Java 25 和当前依赖。GitHub 最新版不等于兼容版本。删除不准备应用的 `candidates.json` 条目即可缩小更新范围。
4. 云服控制台输入 `stop`，确认退出。运行 `scripts/apply-update.ps1 -Stage 候选目录`。脚本核验哈希、做完整停服备份，再只替换 JAR 和锁定清单，不覆盖配置。
5. 启动，检查日志，完成客户端验收后恢复开放。若未通过，停服后运行 `scripts/restore-backup.ps1 -Backup backups/备份时间`，还原整个服务端及版本清单。旧状态另存，脚本不删除它。

备份包含世界、账号、插件变量、权限和密钥，存放在自己的私有存储，定期复制到云主机之外。备份容量和保留周期由服主根据实际人数安排。

当前脚本针对同一 Minecraft 版本的组件更新。大版本升级应先复制整个服务端做迁移测试，不能在升级后的世界上尝试降级。

更新成功后同步 Git 中的 `manifests`、经过清理的配置模板、CHANGELOG 和验收记录。部署包可用 `scripts/package.ps1` 重新生成，不包含世界、账户、日志或认证密钥。
