param([string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (!$OutputDirectory) { $OutputDirectory = Join-Path $root 'release' }
$stage = Join-Path $OutputDirectory ('permanent-updater-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force (Join-Path $stage 'scripts') | Out-Null
foreach ($name in @('update-fast.bat','一键更新.bat','scripts/update-release.ps1')) {
  Copy-Item -LiteralPath (Join-Path $root $name) -Destination (Join-Path $stage $name)
}
$readme = @'
永久一键更新入口：安装一次，以后不需要重复下载入口。

先在服务器控制台输入 stop，等退出后，把全部文件解压合并到现有服务端根目录。
以后双击一键更新.bat 或 update-fast.bat，自动查找最新正式 Release。
默认走 https://gh-proxy.org/ 加速，失败再尝试直连；不锁定旧版本。
只下载发生变化的组件，做停服备份，保留存档、数据库、密钥和本地修改的配置。
更新器本身也由后续正式更新包维护。已是最新版本时不重复备份或替换。
旧 update-release-fixed.ps1 可以保留；永久入口不再使用它。
'@
[IO.File]::WriteAllText((Join-Path $stage 'README-UPDATER.txt'), $readme, (New-Object Text.UTF8Encoding($true)))
$zip = Join-Path $OutputDirectory 'MY-MC-SERVER-updater.zip'
if (Test-Path -LiteralPath $zip) { throw "Updater ZIP already exists: $zip" }
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $zip -CompressionLevel Optimal
$sha = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLower()
Set-Content -LiteralPath "$zip.sha256.txt" -Value "$sha  MY-MC-SERVER-updater.zip" -Encoding ASCII
Write-Host "Permanent updater: $zip"
