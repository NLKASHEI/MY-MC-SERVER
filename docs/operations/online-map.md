# BlueMap 在线地图

待发布版本使用 BlueMap 5.28 Paper 版，官方支持 Paper 26.1.1–26.3 / Java 25，已锁定下载来源与 SHA256。不需要玩家安装客户端 MOD；PC 和 PE 都用浏览器查看同一份地图。

## 地址和网络

云平台新增映射：公网 TCP **14514** → 内网 TCP **8100**。Windows 云服防火墙及安全组须允许 TCP 8100；此项目未修改本机防火墙。

沿用现有进服 IP，地图入口为 `http://103.37.44.174:14514/`。域名可正常解析后也可使用 `http://s9-4.yxjsmc.cn:14514/`。指南针的“在线地图”在聊天发送可点击链接；PE 同时显示原生说明表单，客户端不能点击链接时复制到手机浏览器。链接由 `plugins/MiaoServer/config.yml` 的 `online-map.url` 管理。

## 地图与性能

中文地图：温馨主城、自由生存、下界冒险、末地探索、创造工坊。Minecraft 26.2 使用共享 `world` 保存根目录，通过 `minecraft:overworld`、`minecraft:the_nether`、`minecraft:the_end`、`minecraft:lobby`、`minecraft:flat` 区分维度；不能沿用旧版本每个维度独立根目录的配置。

两个低优先级渲染线程，更新冷却 60 秒；十二人或以上在线时暂停地图渲染，玩家动态标记仍可更新。建筑变化不是逐帧实时，保存区块和渲染队列会造成延迟。地图仅渲染已有世界区块，不预生成新地形。下界隐藏 90–127 层顶部，便于查看内部建筑；主世界默认不公开深层洞穴。

官网默认需要下载 Mojang 客户端纹理。此配置开启首次素材下载，下载的客户端文件只留在本地 `bluemap/data`，不提交 Git、不进入我们的发行包，不重新分发 Mojang 资源。首次启动下载较慢时，地图可能暂时尚未就绪，检查 BlueMap 的加载日志。

## 数据与更新

地图配置在 `plugins/BlueMap`；渲染缓存与网页在服务端根目录 `bluemap`，是可重新生成的地图数据，不包含在源码或部署包中。保持既有世界和玩家存档不变。首次升级仅补充缺失的 BlueMap 配置和默认地图入口，不覆盖已有 BlueMap 配置及地图存储；以后管理员自定义的配置按现有更新冲突规则保留。

`/bluemap` 查看状态，管理员 `/bluemap reload` 重载配置。地图访问不要求游戏账号登录；隐身、隐形效果和旁观玩家默认不显示标记。在线标记可能展示生电假人；不要把网页玩家列表当作经过登录校验的在线人数。

官方：[兼容版本与下载](https://github.com/BlueMap-Minecraft/BlueMap/releases/tag/v5.28)、[安装与资源下载](https://bluemap.bluecolored.de/wiki/getting-started/Installation.html)。云端公网访问和真实双端菜单需正式部署后验收；目前按服主要求不发布。
