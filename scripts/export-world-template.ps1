$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$world = Join-Path $root 'servers/survival/world'
$running = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.ExecutablePath -like "$root\runtime\*" }
if ($running) { throw 'Stop the assembly server before exporting the map template.' }
# Export is for an empty assembly server only. Never export a live player world.
foreach ($dir in @('players','playerdata','advancements','stats')) {
  $path = Join-Path $world $dir
  if (Test-Path $path) {
    if (Get-ChildItem $path -Recurse -File) { throw "Player data detected: $path" }
  }
}
$region = Join-Path $world 'dimensions/minecraft/lobby/region'
if (!(Test-Path $region)) { throw 'Imported lobby region is missing.' }
$assetDir = Join-Path $root 'assets/worlds'
New-Item -ItemType Directory -Force $assetDir | Out-Null
$zip = Join-Path $assetDir 'bootstrap-26.2.zip'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$stream = [IO.File]::Open($zip, [IO.FileMode]::Create)
$archive = New-Object IO.Compression.ZipArchive($stream, [IO.Compression.ZipArchiveMode]::Create)
try {
  $files = @((Get-Item (Join-Path $world 'level.dat')))
  foreach ($dimension in @('overworld','the_nether','the_end','lobby','flat')) {
    $base = Join-Path $world "dimensions/minecraft/$dimension"
    # Explicit public world metadata allowlist; no entities, tickets, raids, schedules or plugin data.
    foreach ($file in @('paper-world.yml','data/minecraft/world_gen_settings.dat','data/minecraft/game_rules.dat','data/minecraft/weather.dat','data/minecraft/world_border.dat','data/minecraft/world_clocks.dat','data/paper/level_overrides.dat','data/paper/metadata.dat')) {
      $path = Join-Path $base $file
      if (Test-Path $path) { $files += Get-Item $path }
    }
  }
  $files += Get-ChildItem $region -Filter *.mca
  foreach ($file in $files) {
    $relative = $file.FullName.Substring($world.Length+1).Replace('\','/')
    $null = [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,"world/$relative",[IO.Compression.CompressionLevel]::Optimal)
  }
} finally { $archive.Dispose(); $stream.Dispose() }
$hash = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLower()
@{file='assets/worlds/bootstrap-26.2.zip';sha256=$hash;source='https://www.planetminecraft.com/project/minecraft-lobby-spawn-map-schematic-free/';author='Kahcerus';minecraftVersion='26.2';description='Clean map template: lobby blocks and five dimension generation metadata; no player data or survival chunks'} | ConvertTo-Json | Set-Content (Join-Path $root 'manifests/world-template.lock.json') -Encoding UTF8
Write-Host "World template exported: $zip ($hash)"
