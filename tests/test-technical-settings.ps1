$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$stage = Join-Path $root ('downloads/technical-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force (Join-Path $stage 'config') | Out-Null
$file = Join-Path $stage 'config/paper-global.yml'
@"
_version: 31
proxies:
  velocity:
    secret: 'fixture-only'
unsupported-settings:
  allow-piston-duplication: false
  perform-username-validation: true
update-checker:
  enabled: false
"@ | Set-Content $file -Encoding ascii
& (Join-Path $root 'scripts/update-technical-settings.ps1') -ServerDirectory $stage
$text = [IO.File]::ReadAllText($file)
foreach ($key in @('allow-piston-duplication','allow-headless-pistons','allow-permanent-block-break-exploits','allow-unsafe-end-portal-teleportation','skip-tripwire-hook-placement-validation')) {
  if ($text -notmatch ('(?m)^  '+$key+': true\r?$')) { throw ('Missing ' + $key) }
}
if ($text -notmatch 'fixture-only' -or $text -notmatch 'perform-username-validation: true' -or $text -notmatch 'enabled: false') { throw 'Unrelated configuration changed' }
if (!(Test-Path ($file + '.before-technical-v0911'))) { throw 'Backup missing' }
[IO.File]::WriteAllText($file,$text.Replace('allow-piston-duplication: true','allow-piston-duplication: false'))
& (Join-Path $root 'scripts/update-technical-settings.ps1') -ServerDirectory $stage
if ([IO.File]::ReadAllText($file) -notmatch 'allow-piston-duplication: false') { throw 'Operator retuning overwritten' }
Write-Host ('TECHNICAL_MIGRATION_PASS ' + $PSVersionTable.PSVersion)
Write-Host ('Fixture: ' + $stage)
