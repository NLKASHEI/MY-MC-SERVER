# 首次组装工具，仅用于没有玩家数据的新服。
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$server = Join-Path $root 'servers/survival'
if (Test-Path (Join-Path $server 'world/playerdata')) {
  if (Get-ChildItem (Join-Path $server 'world/playerdata') -Filter *.dat) { throw 'This helper is only for fresh servers without player data.' }
}
$template = Join-Path $root 'config/survival'
$utf8 = New-Object System.Text.UTF8Encoding($false)
function Edit-Config($relative, $edits) {
  $source = Join-Path $server $relative
  $text = [IO.File]::ReadAllText($source)
  if ($edits[0] -is [string]) { $edits = ,$edits }
  foreach ($edit in $edits) { $text = [regex]::Replace($text,[string]$edit[0],[string]$edit[1]) }
  $target = Join-Path $template $relative
  New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
  [IO.File]::WriteAllText($target,$text,$utf8)
}
Edit-Config 'plugins/AuthMe/config.yml' @(
 @('messagesLanguage: en','messagesLanguage: zhcn'),
 @('perPlayerLocale: true','perPlayerLocale: false'),
 @('maxNicknameLength: 16','maxNicknameLength: 32'),
 @('allowedNicknameCharacters: .*',"allowedNicknameCharacters: '[a-zA-Z0-9_.]*'"),
 @('serverName: Your Minecraft Server','serverName: 生存服务器'),
 @('enableAntiBot: true','enableAntiBot: false')
)
Edit-Config 'plugins/Geyser-Spigot/config.yml' @(
 @('auth-type: online','auth-type: floodgate'),
 @('default-locale: system','default-locale: zh_cn')
)
Edit-Config 'plugins/floodgate/config.yml' @(
 @('enable-global-linking: true','enable-global-linking: false'),
 @('(?m)^  enabled: true','  enabled: false'),
 @('(?m)^  uuid: .*','  uuid: 00000000-0000-0000-0000-000000000000'),
 @('invalid-key: .*','invalid-key: 请通过服务器提供的基岩版入口连接'),
 @('invalid-arguments-length: .*','invalid-arguments-length: 互联数据异常，请联系管理员')
)
Edit-Config 'plugins/Essentials/config.yml' @(
 @('(?m)^#locale: en','locale: zh'),
 @('(?m)^teleport-delay: 0','teleport-delay: 3'),
 @('(?m)^teleport-cooldown: 0','teleport-cooldown: 5'),
 @('(?m)^starting-balance: 0','starting-balance: 100'),
 @('(?m)^  kit: tools',"  kit: ''")
)
Edit-Config 'plugins/CoreProtect/config.yml' @(@('(?m)^language: en','language: zh-CN'))
Edit-Config 'plugins/GriefPreventionData/config.yml' @(
 @('InitialBlocks: 100','InitialBlocks: 500'),
 @('(?m)^    Enabled: true','    Enabled: false'),
 @('ChestClaimDays: 7','ChestClaimDays: -1'),
 @('UnusedClaimDays: 14','UnusedClaimDays: -1'),
 @('DaysInactive: 60','DaysInactive: -1'),
 @('BlockSurfaceCreeperExplosions: true','BlockSurfaceCreeperExplosions: false'),
 @('BlockSurfaceOtherExplosions: true','BlockSurfaceOtherExplosions: false'),
 @('PistonMovement: CLAIMS_ONLY','PistonMovement: EVERYWHERE'),
 @('(?m)^  FireSpreads: false','  FireSpreads: true'),
 @('(?m)^  FireDestroys: false','  FireDestroys: true')
)
Edit-Config 'plugins/EconomyShopGUI/config.yml' @(
 @('language-file: lang-en.yml','language-file: lang-zh_cn.yml'),
 @('locale: en-US','locale: zh-CN')
)
# 禁用预置分类，包括子分类，确保默认表不会参与当前经济。
Get-ChildItem (Join-Path $server 'plugins/EconomyShopGUI/sections') -Recurse -Filter *.yml | ForEach-Object {
  $relative = $_.FullName.Substring($server.Length+1)
  Edit-Config $relative @(@('(?m)^enable: true','enable: false'))
}
Copy-Item (Join-Path $server 'server.properties') (Join-Path $template 'server.properties')
Copy-Item (Join-Path $template '*') $server -Recurse -Force
