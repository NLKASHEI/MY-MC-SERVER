param([string]$SettingsFile)
$ErrorActionPreference = 'Stop'
if (!$SettingsFile) { $SettingsFile = Join-Path (Split-Path $PSScriptRoot -Parent) 'server-settings.json' }
$SettingsFile = (Resolve-Path -LiteralPath $SettingsFile).Path
$prior = $SettingsFile + '.before-memory-v099'
if (Test-Path -LiteralPath $prior) { return }
$settings = Get-Content -LiteralPath $SettingsFile -Raw -Encoding UTF8 | ConvertFrom-Json
# Only migrate the old shipped default; preserve a cloud operator's own tuning.
if ($settings.memoryMin -ne '2G' -or $settings.memoryMax -ne '6G') { return }
Copy-Item -LiteralPath $SettingsFile -Destination $prior
$settings.memoryMin = '4G'
$settings.memoryMax = '12G'
$temp = $SettingsFile + '.memory-tmp'
$settings | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $temp -Encoding UTF8
Move-Item -LiteralPath $temp -Destination $SettingsFile -Force
# Keep this source ASCII: Windows PowerShell 5.1 reads BOM-less scripts as ANSI.
Write-Host 'Default memory updated to 4-12GB. Backup: server-settings.json.before-memory-v099'
