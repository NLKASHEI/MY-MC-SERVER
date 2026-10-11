param([Parameter(Mandatory=$true)][string]$Backup)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$running = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.ExecutablePath -like "$root\runtime\*" }
if ($running) { throw 'Stop the server before restoring.' }
$backupPath = (Resolve-Path $Backup).Path
foreach ($path in @('survival/paper.jar','manifests/plugins.lock.json','server-settings.json')) {
  if (!(Test-Path (Join-Path $backupPath $path))) { throw "Backup missing: $path" }
}
$server = [IO.Path]::GetFullPath((Join-Path $root 'servers/survival'))
$archive = [IO.Path]::GetFullPath((Join-Path $root ('backups/pre-restore-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))))
if (!$server.StartsWith([IO.Path]::GetFullPath((Join-Path $root 'servers')) + '\') -or !$archive.StartsWith([IO.Path]::GetFullPath((Join-Path $root 'backups')) + '\')) { throw 'Invalid workspace target' }
if ($backupPath.StartsWith($server + '\') -or $backupPath -eq $server) { throw 'Backup must be outside the active server' }
New-Item -ItemType Directory -Force (Split-Path $archive) | Out-Null
Move-Item -LiteralPath $server -Destination $archive
Copy-Item (Join-Path $backupPath 'survival') (Join-Path $root 'servers') -Recurse
Copy-Item (Join-Path $backupPath 'manifests/*') (Join-Path $root 'manifests') -Force
Copy-Item (Join-Path $backupPath 'server-settings.json') $root -Force
Write-Host "Restored backup. Previous state preserved at $archive"
