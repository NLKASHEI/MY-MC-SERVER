$ErrorActionPreference = 'Stop'
$script = Join-Path (Split-Path $PSScriptRoot -Parent) 'scripts/update-memory-settings.ps1'
$file = Join-Path $PSScriptRoot 'memory-ps51-fixture.json'
try {
  '{"memoryMin":"2G","memoryMax":"6G","custom":"preserved"}' | Set-Content $file -Encoding UTF8
  & $script -SettingsFile $file
  $settings = Get-Content $file -Raw -Encoding UTF8 | ConvertFrom-Json
  if ($settings.memoryMin -ne '4G' -or $settings.memoryMax -ne '12G' -or $settings.custom -ne 'preserved') { throw 'Migration failed' }
  if (!(Test-Path ($file + '.before-memory-v099'))) { throw 'Backup missing' }
  '{"memoryMin":"3G","memoryMax":"8G"}' | Set-Content $file -Encoding UTF8
  & $script -SettingsFile $file
  if ((Get-Content $file -Raw -Encoding UTF8 | ConvertFrom-Json).memoryMax -ne '8G') { throw 'Custom setting overwritten' }
  Remove-Item ($file + '.before-memory-v099')
  & $script -SettingsFile $file
  if (Test-Path ($file + '.before-memory-v099')) { throw 'Custom setting migrated' }
  Write-Host ('PS51_MEMORY_PASS ' + $PSVersionTable.PSVersion)
} finally {
  Remove-Item $file,($file + '.before-memory-v099') -ErrorAction SilentlyContinue
}
