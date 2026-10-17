param([Parameter(Mandatory=$true)][string]$Stage)
$ErrorActionPreference = 'Stop'
function FileHash([string]$Path) {
  $stream = [IO.File]::OpenRead($Path); $sha = [Security.Cryptography.SHA256]::Create()
  try { return [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-','').ToLower() }
  finally { $stream.Dispose(); $sha.Dispose() }
}
$root = Split-Path $PSScriptRoot -Parent
$stagePath = (Resolve-Path $Stage).Path
$items = Get-Content (Join-Path $stagePath 'candidates.json') -Raw | ConvertFrom-Json
if (!$items -or $items.Count -eq 0) { Write-Host 'No candidates to apply.'; exit }
$installedLock = Get-Content (Join-Path $root 'manifests/plugins.lock.json') -Raw | ConvertFrom-Json
foreach ($item in $items) {
  if ($item.name -notmatch '^[A-Za-z0-9_-]+$' -or $item.file -ne "$($item.name).jar") { throw 'Invalid candidate path' }
  if ((FileHash (Join-Path $stagePath $item.file)) -ne $item.sha256) { throw "Checksum mismatch: $($item.name)" }
  if ($item.name -ne 'paper' -and !($installedLock | Where-Object name -eq $item.name)) { throw "Unknown installed plugin: $($item.name)" }
  if ($item.name -eq 'paper') {
    $paperLock = Get-Content (Join-Path $stagePath 'paper.lock.json') -Raw | ConvertFrom-Json
    if ($paperLock.channel -ne 'STABLE' -or $paperLock.id.ToString() -ne $item.version -or $paperLock.downloads.'server:default'.checksums.sha256 -ne $item.sha256 -or $paperLock.downloads.'server:default'.name -notlike 'paper-26.2-*.jar') { throw 'Invalid Paper lock; only stable 26.2 updates are allowed.' }
  }
}
$running = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.ExecutablePath -like "$root\runtime\*" -or $_.CommandLine -like "*$root*paper.jar*" }
if ($running) { throw 'Stop the server before applying updates.' }
Write-Host 'Applying update without a full-server backup; scheduled backups are managed by Backuper.'
$server = Join-Path $root 'servers/survival'
$lock = $installedLock
foreach ($item in $items) {
  if ($item.name -eq 'paper') {
    Copy-Item (Join-Path $stagePath $item.file) (Join-Path $server 'paper.jar') -Force
    Copy-Item (Join-Path $stagePath 'paper.lock.json') (Join-Path $root 'manifests/paper.lock.json') -Force
  } else {
    $existing = @($lock | Where-Object name -eq $item.name)[0]
    if (!$existing) { throw "Unknown installed plugin: $($item.name)" }
    Copy-Item (Join-Path $stagePath $item.file) (Join-Path $server "plugins/$($item.name).jar") -Force
    $existing.version=$item.version; $existing.url=$item.url; $existing.sha256=$item.sha256
  }
}
$lock | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $root 'manifests/plugins.lock.json') -Encoding UTF8
Write-Host 'Updated binaries only. Run startup and client acceptance checks before reopening the server.'
