$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$running = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.ExecutablePath -like "$root\runtime\*" }
if ($running) { throw 'Stop the local server before packaging.' }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$release = Join-Path $root "release/MY-MC-SERVER-26.2-$stamp"
New-Item -ItemType Directory -Force $release | Out-Null
foreach ($name in @('scripts','runtime','manifests','config','docs','skills','server-settings.json','README.md','AGENTS.md','GLOSSARY.md')) {
  Copy-Item (Join-Path $root $name) $release -Recurse
}
Get-ChildItem $root -Filter *.bat | Copy-Item -Destination $release
$server = Join-Path $release 'servers/survival'
New-Item -ItemType Directory -Force (Join-Path $server 'plugins') | Out-Null
Copy-Item (Join-Path $root 'servers/survival/paper.jar') $server
Get-ChildItem (Join-Path $root 'servers/survival/plugins') -Filter *.jar | Copy-Item -Destination (Join-Path $server 'plugins')
foreach ($name in @('libraries','cache','versions')) {
  $path = Join-Path $root "servers/survival/$name"
  if (Test-Path $path) { Copy-Item $path $server -Recurse }
}
Copy-Item (Join-Path $root 'config/survival/*') $server -Recurse -Force
# Public item/texture mappings only; never copy account or skull caches.
$icons = Join-Path $root 'servers/survival/plugins/EconomyShopGUI/cache'
$iconTarget = Join-Path $server 'plugins/EconomyShopGUI/cache'
New-Item -ItemType Directory -Force $iconTarget | Out-Null
foreach ($name in @('blocks.json','geyser-items.json','item_texture.json','terrain_texture.json')) {
  $path = Join-Path $icons $name
  if (Test-Path $path) { Copy-Item $path $iconTarget }
}
Set-Content (Join-Path $server 'eula.txt') 'eula=false' -Encoding ASCII
$zip = "$release.zip"
Compress-Archive -Path (Join-Path $release '*') -DestinationPath $zip -CompressionLevel Optimal
$hash = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLower()
Set-Content "$zip.sha256.txt" "$hash  $([IO.Path]::GetFileName($zip))" -Encoding ASCII
Write-Host "Release: $zip"
