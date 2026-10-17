param([string]$ReleaseTag)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (!$ReleaseTag) { $ReleaseTag = 'v' + (Get-Content (Join-Path $root 'release-version.json') -Raw -Encoding UTF8 | ConvertFrom-Json).version }
if ($ReleaseTag -notmatch '^v[0-9]+\.[0-9]+\.[0-9]+$') { throw 'Invalid release tag' }
$package = Join-Path $root "release/MY-MC-SERVER-update-$ReleaseTag.zip"
if (!(Test-Path -LiteralPath $package)) { throw 'Build or download the exact published update package first.' }
$sha = (Get-FileHash -LiteralPath $package -Algorithm SHA256).Hash.ToLower()
$stage = Join-Path $root ('release/rescue-build-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force (Join-Path $stage 'scripts') | Out-Null
Copy-Item -LiteralPath (Join-Path $root 'scripts/update-release.ps1') -Destination (Join-Path $stage 'scripts/update-release-fixed.ps1')
$batch = @"
@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist "server-settings.json" (
  echo Please extract these files into your existing server root folder.
  pause
  exit /b 1
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\update-release-fixed.ps1" -GitHubProxy "https://gh-proxy.org/" -ReleaseTag "$ReleaseTag" -PinnedPackageSha256 "$sha"
set "update_result=%ERRORLEVEL%"
if not "%update_result%"=="0" echo Update failed. Read the error above; do not delete your worlds.
pause
exit /b %update_result%
"@
[IO.File]::WriteAllText((Join-Path $stage 'update-fast.bat'), $batch.Replace("`r`n","`n").Replace("`n","`r`n"), [Text.Encoding]::ASCII)
$readme = @"
南可熙的神奇妙妙服：临时加速更新入口（固定 $ReleaseTag）

1. 在服务器控制台输入 stop，等服务器完全退出。
2. 把本 ZIP 的全部内容解压到现有服务端根目录。update-fast.bat 必须和 server-settings.json 在同一层，scripts 子目录合并进去。
3. 双击 update-fast.bat。先走 https://gh-proxy.org/ GitHub 加速，失败或校验不一致再尝试直连。

修复 Windows PowerShell 5.1 默认编码把 UTF-8 中文文件名读成乱码的问题。
固定版本更新包 SHA256：$sha
更新包和变化的插件仍执行 SHA256 校验；下载验证通过后停服替换，不创建整服更新备份。保留世界、玩家、密码/绑定/经济/商店数据库、密钥及本地配置保护，复制失败仍回退本次改动。
成功后自动修复 scripts/update-release.ps1，并将其 prior copy 保存在 update-release.ps1.before-fast-update。以后用原来的中文“一键更新.bat”获取后续版本；本临时入口固定更新到 $ReleaseTag。
加速只转发公开 GitHub 下载，不上传存档，不需要账号、令牌或梯子。插件其他官方源沿用原地址。
"@
[IO.File]::WriteAllText((Join-Path $stage 'README-FAST-UPDATE.txt'), $readme.Replace('prior copy', '旧副本'), (New-Object Text.UTF8Encoding($true)))
$zip = Join-Path $root "release/MY-MC-SERVER-fast-update-$ReleaseTag.zip"
if (Test-Path -LiteralPath $zip) { throw "Rescue ZIP already exists: $zip" }
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $zip -CompressionLevel Optimal
$zipSha = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLower()
Set-Content -LiteralPath "$zip.sha256.txt" -Value "$zipSha  $([IO.Path]::GetFileName($zip))" -Encoding ASCII
Write-Host "Rescue ZIP: $zip"
