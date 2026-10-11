param([switch]$CheckOnly, [string]$Package, [string]$ExpectedSha256)
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$ProgressPreference = 'SilentlyContinue'
$root = Split-Path $PSScriptRoot -Parent
$settings = Get-Content (Join-Path $root 'server-settings.json') -Raw | ConvertFrom-Json
if ($settings.serverDirectory -ne 'servers/survival') { throw 'Custom serverDirectory requires manual update.' }
$server = Join-Path $root 'servers/survival'
$headers = @{'User-Agent'='MY-MC-SERVER-Updater'; 'Accept'='application/vnd.github+json'}
$work = Join-Path $root ('downloads/release-update-' + [guid]::NewGuid().ToString('N'))
$stateFile = Join-Path $root '.update-state.json'
$state = $null
if (Test-Path $stateFile) { $state = Get-Content $stateFile -Raw | ConvertFrom-Json }

function Hash([string]$Path) {
  if (Test-Path -LiteralPath $Path -PathType Leaf) {
    $stream = [IO.File]::OpenRead($Path); $sha = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-','').ToLower() } finally { $stream.Dispose(); $sha.Dispose() }
  }
  return $null
}
function TextHash([string]$Path) {
  if (!(Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
  $sha = [Security.Cryptography.SHA256]::Create()
  try { $bytes = [Text.Encoding]::UTF8.GetBytes([IO.File]::ReadAllText($Path).TrimStart([char]0xfeff).Replace("`r`n","`n")); return [BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-','').ToLower() } finally { $sha.Dispose() }
}
function SafePath([string]$Base, [string]$Relative) {
  if ($Relative -notmatch '^[A-Za-z0-9_\-\u4e00-\u9fff./]+$' -or $Relative -match '(^|/)\.\.(/|$)|//|^/') { throw "Unsafe update path: $Relative" }
  $result = [IO.Path]::GetFullPath((Join-Path $Base $Relative))
  if (!$result.StartsWith([IO.Path]::GetFullPath($Base).TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Path escapes update directory.' }
  return $result
}
function Stopped {
  $running = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.ExecutablePath -like "$root\runtime\*" -or $_.CommandLine -like "*$root*paper.jar*" }
  if ($running) { throw '请先在服务器控制台输入 stop，待服务器退出后再更新。' }
}
function Download($Url, $Target, $Sha, $Method='GET', $Body=$null) {
  if ($Url -notmatch '^https://') { throw 'Download must use HTTPS.' }
  New-Item -ItemType Directory -Force (Split-Path $Target) | Out-Null
  $arguments = @{ Uri=$Url; Headers=$headers; OutFile=$Target; UseBasicParsing=$true; TimeoutSec=300 }
  if ($Method -eq 'POST') { $arguments.Method='POST'; $arguments.Body=$Body }
  Invoke-WebRequest @arguments
  if ((Hash $Target) -ne $Sha) { throw "下载校验失败：$Target" }
}

$mutexHash = [Security.Cryptography.SHA256]::Create()
try { $mutexName = 'Local\MY-MC-SERVER-' + [BitConverter]::ToString($mutexHash.ComputeHash([Text.Encoding]::UTF8.GetBytes($root.ToLower()))).Replace('-','') } finally { $mutexHash.Dispose() }
$mutex = New-Object Threading.Mutex($false, $mutexName)
$ownsMutex = $false
try {
try { $ownsMutex = $mutex.WaitOne(0) } catch [Threading.AbandonedMutexException] { $ownsMutex = $true }
if (!$ownsMutex) { throw '已有更新程序正在运行，请等待它完成。' }
if (!$Package) {
  Write-Host '正在检查 GitHub 最新正式版本……'
  $release = Invoke-RestMethod -Uri 'https://api.github.com/repos/NLKASHEI/MY-MC-SERVER/releases/latest' -Headers $headers -TimeoutSec 60
  $asset = @($release.assets | Where-Object name -match '^MY-MC-SERVER-update-v[0-9.]+\.zip$')
  if ($asset.Count -ne 1) { throw '此 Release 没有一键更新包，请查看 Release 说明。' }
  if ($state -and ('v' + $state.version) -eq $release.tag_name) { Write-Host "已是最新版本 $($release.tag_name)。如有待合并配置，查看 $($state.pendingDirectory)"; return }
  Write-Host "发现版本 $($release.tag_name)：$($release.html_url)"
  if ($CheckOnly) { return }
  Stopped
  New-Item -ItemType Directory -Force $work | Out-Null
  $checksum = @($release.assets | Where-Object name -eq ($asset[0].name + '.sha256.txt'))
  if ($checksum.Count -ne 1) { throw '更新包缺少 SHA256 文件。' }
  $checksumFile = Join-Path $work 'checksum.txt'
  Invoke-WebRequest -Uri $checksum[0].browser_download_url -Headers $headers -OutFile $checksumFile -UseBasicParsing -TimeoutSec 60
  $ExpectedSha256 = ((Get-Content $checksumFile -Raw).Trim() -split '\s+')[0]
  if ($ExpectedSha256 -notmatch '^[a-fA-F0-9]{64}$') { throw 'Invalid checksum file.' }
  if ($asset[0].digest -and $asset[0].digest -ne ('sha256:' + $ExpectedSha256.ToLower())) { throw 'GitHub asset checksum mismatch.' }
  $Package = Join-Path $work $asset[0].name
  Download $asset[0].browser_download_url $Package $ExpectedSha256.ToLower()
} else {
  if ($CheckOnly) { throw '-CheckOnly is only available for GitHub checks.' }
  Stopped
  $Package = (Resolve-Path -LiteralPath $Package).Path
  if (!$ExpectedSha256) { throw 'Local package requires -ExpectedSha256.' }
  if ((Hash $Package) -ne $ExpectedSha256.ToLower()) { throw 'Local update package checksum mismatch.' }
  New-Item -ItemType Directory -Force $work | Out-Null
}

$stage = Join-Path $work 'payload'
New-Item -ItemType Directory $stage | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($Package)
try {
  $seen = @{}
  foreach ($entry in $zip.Entries) {
    $name = $entry.FullName.Replace('\','/')
    if ($name.EndsWith('/')) { continue }
    $destination = SafePath $stage $name
    if ($seen.ContainsKey($name.ToLower())) { throw 'Duplicate update ZIP path.' }
    $seen[$name.ToLower()] = $true
    New-Item -ItemType Directory -Force (Split-Path $destination) | Out-Null
    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destination, $false)
  }
} finally { $zip.Dispose() }
$index = Get-Content (Join-Path $stage 'update-index.json') -Raw | ConvertFrom-Json
if ($index.schema -ne 1 -or $index.minecraftVersion -ne $settings.minecraftVersion -or $index.version -notmatch '^\d+\.\d+\.\d+$') { throw '更新格式或 Minecraft 版本不匹配，需人工迁移。' }
if ($state -and [version]$index.version -lt [version]$state.version) { throw '不允许一键降级，请使用备份回退。' }
if ($release -and $release.tag_name -ne ('v' + $index.version)) { throw 'Release tag and update package version differ.' }
$files = @($index.files)
$known = @{}
foreach ($file in $files) {
  $path = [string]$file.path
  if ($known.ContainsKey($path)) { throw 'Duplicate manifest path.' }; $known[$path]=$true
  $allowed = $path -match '^(scripts|docs|skills)/.+\.(ps1|md)$' -or $path -match '^manifests/[A-Za-z0-9_-]+\.lock\.json$' -or $path -match '^[^/]+\.bat$' -or $path -in @('README.md','AGENTS.md','GLOSSARY.md','CHANGELOG.md','release-version.json') -or $path -match '^config/survival/(permissions\.yml|server\.properties|plugins/.+\.(yml|sk|txt))$'
  if (!$allowed -or $path -match '(?i)(userdata|playerdata|claimdata|messages\.db|key\.pem|variables\.csv|logs)/') { throw "Disallowed update file: $path" }
  if ($file.sha256 -notmatch '^[a-f0-9]{64}$' -or (Hash (SafePath $stage $path)) -ne $file.sha256 -or $file.contentSha256 -notmatch '^[a-f0-9]{64}$') { throw "Update file checksum mismatch: $path" }
}
foreach ($required in @('manifests/plugins.lock.json','manifests/paper.lock.json','manifests/extensions.lock.json','manifests/java.lock.json')) {
  if (!$known.ContainsKey($required)) { throw "Missing component lock: $required" }
}
$oldJava = Get-Content (Join-Path $root 'manifests/java.lock.json') -Raw | ConvertFrom-Json
$newJava = Get-Content (Join-Path $stage 'manifests/java.lock.json') -Raw | ConvertFrom-Json
if ($oldJava.binary.package.checksum -ne $newJava.binary.package.checksum) { throw '此版本更换 Java，需要按 Release 说明人工迁移。' }
$paper = Get-Content (Join-Path $stage 'manifests/paper.lock.json') -Raw | ConvertFrom-Json
$assetPaper = $paper.downloads.'server:default'
if ($paper.channel -ne 'STABLE' -or $assetPaper.name -notlike "paper-$($settings.minecraftVersion)-*.jar") { throw 'Unsupported Paper release.' }
$components = @([pscustomobject]@{ path='servers/survival/paper.jar'; url=$assetPaper.url; sha256=$assetPaper.checksums.sha256; method='GET'; body=$null })
foreach ($plugin in (Get-Content (Join-Path $stage 'manifests/plugins.lock.json') -Raw | ConvertFrom-Json)) {
  if ($plugin.name -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid plugin name.' }
  $components += [pscustomobject]@{ path="servers/survival/plugins/$($plugin.name).jar"; url=$plugin.url; sha256=$plugin.sha256; method=$plugin.method; body=$plugin.body }
}
foreach ($extension in (Get-Content (Join-Path $stage 'manifests/extensions.lock.json') -Raw | ConvertFrom-Json)) {
  if ($extension.path -ne 'plugins/floodgate/floodgate-sqlite-database.jar') { throw 'Unsupported extension path.' }
  $components += [pscustomobject]@{ path="servers/survival/$($extension.path)"; url=$extension.url; sha256=$extension.sha256; method='GET'; body=$null }
}
$componentSeen = @{}
$changes = @()
foreach ($component in $components) {
  if ($componentSeen.ContainsKey($component.path)) { throw 'Duplicate component path.' }; $componentSeen[$component.path]=$true
  if ($component.sha256 -notmatch '^[a-f0-9]{64}$') { throw 'Invalid component checksum.' }
  $target = SafePath $root $component.path
  if ((Hash $target) -eq $component.sha256) { continue }
  Write-Host "下载变化的组件：$($component.path)"
  $source = SafePath (Join-Path $work 'components') $component.path
  $body = @{}; if ($component.body) { $component.body.psobject.Properties | ForEach-Object { $body[$_.Name]=$_.Value } }
  Download $component.url $source $component.sha256 $component.method $body
  $changes += [pscustomobject]@{ path=$component.path; source=$source }
}

$pending = @()
$nextBaseline = @{}
$protected = @('servers/survival/server.properties','servers/survival/plugins/Essentials/spawn.yml','servers/survival/plugins/Multiverse-Core/worlds.yml','servers/survival/plugins/Multiverse-Inventories/groups.yml','servers/survival/plugins/WorldGuard/worlds/lobby/regions.yml','servers/survival/plugins/WorldGuard/worlds/flat/regions.yml')
foreach ($file in $files) {
  $destinations = @([string]$file.path)
  if ($file.path.StartsWith('config/survival/')) { $destinations += 'servers/survival/' + $file.path.Substring(16) }
  foreach ($path in $destinations) {
    $target = SafePath $root $path
    $current = TextHash $target
    $baseline = $file.baseSha256
    if ($state -and $state.baseline -and $state.baseline.psobject.Properties[$path]) { $baseline = $state.baseline.psobject.Properties[$path].Value }
    elseif ($path.StartsWith('servers/survival/')) { $baseline = TextHash (SafePath $root ([string]$file.path)) }
    $nextBaseline[$path] = $file.contentSha256
    if ($current -eq $file.contentSha256) { continue }
    if ($path.StartsWith('manifests/') -and $current -and $current -ne $baseline) { throw "本地组件清单已修改，需要人工核对：$path" }
    if ($current -and ($path -in $protected -or !$baseline -or $current -ne $baseline)) {
      $pending += [pscustomobject]@{ path=$path; source=(SafePath $stage ([string]$file.path)); reason='本地修改或受保护设置，原文件保留' }
    } else { $changes += [pscustomobject]@{ path=$path; source=(SafePath $stage ([string]$file.path)) } }
  }
}
Stopped
$backup = Join-Path $root ('backups/release-update-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,6))
Write-Host '正在创建完整停服备份，世界越大耗时越长……'
& (Join-Path $PSScriptRoot 'backup.ps1') -Destination $backup
Stopped
$undo = Join-Path $backup 'update-files'
$conflictRoot = Join-Path $backup 'pending'
$journal = @()
try {
  foreach ($item in $changes) {
    $target = SafePath $root $item.path
    $saved = SafePath $undo $item.path
    $exists = Test-Path -LiteralPath $target
    if ($exists) { New-Item -ItemType Directory -Force (Split-Path $saved) | Out-Null; Copy-Item -LiteralPath $target -Destination $saved }
    $journal += [pscustomobject]@{ path=$item.path; existed=$exists }
    New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
    Copy-Item -LiteralPath $item.source -Destination $target -Force
  }
  foreach ($item in $pending) {
    $incoming = SafePath $conflictRoot ($item.path + '.new')
    New-Item -ItemType Directory -Force (Split-Path $incoming) | Out-Null
    Copy-Item -LiteralPath $item.source -Destination $incoming
  }
  $pending | Select-Object path,reason | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $backup 'pending-configs.json') -Encoding UTF8
  [ordered]@{ version=$index.version; backup=$backup; pendingDirectory=$conflictRoot; baseline=$nextBaseline; changed=@($journal); updatedAt=(Get-Date).ToString('o') } | ConvertTo-Json -Depth 8 | Set-Content "$stateFile.tmp" -Encoding UTF8
  Move-Item -LiteralPath "$stateFile.tmp" -Destination $stateFile -Force
} catch {
  foreach ($item in $journal) {
    $target = SafePath $root $item.path
    if ($item.existed) { Copy-Item -LiteralPath (SafePath $undo $item.path) -Destination $target -Force }
    elseif (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Force }
  }
  throw "更新失败，已回退本次替换的文件。备份：$backup。原因：$($_.Exception.Message)"
}
Write-Host "更新完成：v$($index.version)，替换 $($changes.Count) 个文件，保留 $($pending.Count) 个本地配置。"
Write-Host "完整备份：$backup"
if ($pending.Count) { Write-Host "待合并新版配置：$conflictRoot（.new 文件）；清单：$backup\pending-configs.json" }
Write-Host '存档、玩家数据库、密钥和内存设置保留。可双击 启动服务器.bat。'
} finally {
  if ($ownsMutex) { $mutex.ReleaseMutex() }
  $mutex.Dispose()
}
