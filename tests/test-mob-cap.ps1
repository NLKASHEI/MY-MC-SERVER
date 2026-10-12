$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$stage = Join-Path $root ('downloads/mob-cap-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $stage | Out-Null
$file = Join-Path $stage 'bukkit.yml'
@"
settings:
  custom: retained
spawn-limits:
  monsters: 70
  animals: 10
  ambient: 15
ticks-per:
  monster-spawns: 1
"@ | Set-Content $file -Encoding ascii
$script = Join-Path $root 'scripts/update-mob-cap.ps1'
& $script -ServerDirectory $stage
$text = [IO.File]::ReadAllText($file)
if ($text -notmatch 'monsters: 100' -or $text -notmatch 'animals: 10' -or $text -notmatch 'monster-spawns: 1' -or $text -notmatch 'custom: retained') { throw 'Merge failed' }
if (!(Test-Path ($file + '.before-mob-cap-v0912'))) { throw 'Backup missing' }
[IO.File]::WriteAllText($file,$text.Replace('monsters: 100','monsters: 80'))
& $script -ServerDirectory $stage
if ([IO.File]::ReadAllText($file) -notmatch 'monsters: 80') { throw 'Operator setting overwritten' }
Write-Host ('MOB_CAP_MIGRATION_PASS ' + $PSVersionTable.PSVersion)
