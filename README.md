# MY-MC-SERVER

Windows 云服务器使用的 Paper 26.2 原版生存辅助服务端。Java 离线账号和基岩版互联账号使用注册、密码登录。未安装反作弊。

按 [mattpocock/skills](https://github.com/mattpocock/skills) 的分类技能、镜像文档、根目录规范组织维护流程；这是服务端项目，组件运行目录单独放在 `servers/survival`。

## 快速使用

下载完整部署包并解压到 Windows 服务器。阅读 Minecraft EULA，将 `servers/survival/eula.txt` 改为 `eula=true`，双击 `启动服务器.bat`。内存在 `server-settings.json` 中修改。云端放行 Java TCP 25565 和基岩 UDP 19132。

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

## Model-invoked skills

- [configure-minecraft](skills/engineering/configure-minecraft/SKILL.md)：配置插件、权限和互联。
- [update-minecraft](skills/engineering/update-minecraft/SKILL.md)：候选更新、备份和回退。
- [localize-minecraft](skills/productivity/localize-minecraft/SKILL.md)：玩家可见文本汉化。

JAR 和 Java 保留各自上游许可证。仓库只保存项目配置、脚本和文档；不上传运行数据或认证密钥。
