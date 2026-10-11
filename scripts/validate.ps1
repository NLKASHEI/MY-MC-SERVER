$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$errors = @()
Get-ChildItem $PSScriptRoot -Filter *.ps1 | ForEach-Object {
  $tokens=$null; $parseErrors=$null
  $null=[Management.Automation.Language.Parser]::ParseFile($_.FullName,[ref]$tokens,[ref]$parseErrors)
  if ($parseErrors) { $errors += $parseErrors | ForEach-Object { "$($_.Extent.File): $($_.Message)" } }
}
foreach ($file in (Get-ChildItem (Join-Path $root 'manifests') -Filter *.json)) { $null=Get-Content $file.FullName -Raw | ConvertFrom-Json }
$lock = Get-Content (Join-Path $root 'manifests/plugins.lock.json') -Raw | ConvertFrom-Json
if (@($lock | Group-Object name | Where-Object Count -gt 1).Count) { throw 'Duplicate plugin names in manifest' }
foreach ($plugin in $lock) {
  if ($plugin.name -notmatch '^[A-Za-z0-9_-]+$' -or $plugin.sha256 -notmatch '^[a-f0-9]{64}$' -or $plugin.url -notmatch '^https://') { throw 'Invalid plugin manifest entry' }
  $jar=Join-Path $root "servers/survival/plugins/$($plugin.name).jar"
  if (Test-Path $jar) {
    if ((Get-FileHash $jar -Algorithm SHA256).Hash.ToLower() -ne $plugin.sha256) { throw "JAR checksum mismatch: $($plugin.name)" }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive=[IO.Compression.ZipFile]::OpenRead($jar)
    try { if (!$archive.GetEntry('plugin.yml') -and !$archive.GetEntry('paper-plugin.yml')) { throw "Wrong platform JAR: $($plugin.name)" } } finally { $archive.Dispose() }
  }
}
foreach ($extension in (Get-Content (Join-Path $root 'manifests/extensions.lock.json') -Raw | ConvertFrom-Json)) {
  if ($extension.path -ne 'plugins/floodgate/floodgate-sqlite-database.jar' -or $extension.sha256 -notmatch '^[a-f0-9]{64}$' -or $extension.url -notmatch '^https://download.geysermc.org/') { throw 'Invalid Floodgate extension manifest' }
  $jar = Join-Path $root "servers/survival/$($extension.path)"
  if ((Test-Path $jar) -and (Get-FileHash $jar -Algorithm SHA256).Hash.ToLower() -ne $extension.sha256) { throw 'Extension checksum mismatch' }
}
if ($errors.Count) { throw ($errors -join "`n") }
Write-Host "PowerShell syntax and $($lock.Count) plugin manifest entries validated."
