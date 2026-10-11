param([string]$Destination)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$java = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.ExecutablePath -like "$root\runtime\*" }
if ($java) { throw 'Stop the server with the stop console command before creating a backup.' }
if (!$Destination) { $Destination = Join-Path $root ('backups/' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
if (Test-Path $Destination) { throw 'Backup destination already exists.' }
New-Item -ItemType Directory $Destination | Out-Null
Copy-Item (Join-Path $root 'servers/survival') $Destination -Recurse
Copy-Item (Join-Path $root 'manifests') $Destination -Recurse
Copy-Item (Join-Path $root 'server-settings.json') $Destination
Write-Host "Backup created: $Destination"
