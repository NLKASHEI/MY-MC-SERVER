$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Add-Type -AssemblyName System.IO.Compression.FileSystem
$running = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.ExecutablePath -like "$root\runtime\*" }
if ($running) { throw 'Stop the local server before packaging.' }
$version = Get-Content (Join-Path $root 'release-version.json') -Raw | ConvertFrom-Json
$updateZip = Join-Path $root "release/MY-MC-SERVER-update-v$($version.version).zip"
if (!(Test-Path $updateZip)) { & (Join-Path $PSScriptRoot 'build-update.ps1') }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$release = Join-Path $root "release/MY-MC-SERVER-26.2-$stamp"
New-Item -ItemType Directory -Force $release | Out-Null
foreach ($name in @('scripts','runtime','manifests','config','docs','skills','assets','server-settings.json','release-version.json','SCOPE.md','README.md','AGENTS.md','GLOSSARY.md','CHANGELOG.md')) {
  Copy-Item (Join-Path $root $name) $release -Recurse
}
Get-ChildItem $root -Filter *.bat | Copy-Item -Destination $release
$server = Join-Path $release 'servers/survival'
New-Item -ItemType Directory -Force (Join-Path $server 'plugins') | Out-Null
Copy-Item (Join-Path $root 'servers/survival/paper.jar') $server
# Only locked production jars; assembly/testing plugins never enter a release.
foreach ($plugin in (Get-Content (Join-Path $root 'manifests/plugins.lock.json') -Raw | ConvertFrom-Json)) {
  $path = Join-Path $root "servers/survival/plugins/$($plugin.name).jar"
  if ((Get-FileHash $path -Algorithm SHA256).Hash.ToLower() -ne $plugin.sha256) { throw "Checksum mismatch: $($plugin.name)" }
  Copy-Item $path (Join-Path $server 'plugins')
}
foreach ($name in @('libraries','cache','versions')) {
  $path = Join-Path $root "servers/survival/$name"
  if (Test-Path $path) { Copy-Item $path $server -Recurse }
}
Copy-Item (Join-Path $root 'config/survival/*') $server -Recurse -Force
foreach ($extension in (Get-Content (Join-Path $root 'manifests/extensions.lock.json') -Raw | ConvertFrom-Json)) {
  $source = Join-Path $root "servers/survival/$($extension.path)"
  if ((Get-FileHash $source -Algorithm SHA256).Hash.ToLower() -ne $extension.sha256) { throw 'Extension checksum mismatch' }
  $target = Join-Path $server $extension.path
  New-Item -ItemType Directory -Force (Split-Path $target -Parent) | Out-Null
  Copy-Item $source $target
}
$worldTemplate = Get-Content (Join-Path $root 'manifests/world-template.lock.json') -Raw | ConvertFrom-Json
$worldZip = Join-Path $root $worldTemplate.file
if ((Get-FileHash $worldZip -Algorithm SHA256).Hash.ToLower() -ne $worldTemplate.sha256) { throw 'World template checksum mismatch' }
Expand-Archive $worldZip $server -Force
# Public item/texture mappings only; never copy account or skull caches.
$icons = Join-Path $root 'servers/survival/plugins/EconomyShopGUI/cache'
$iconTarget = Join-Path $server 'plugins/EconomyShopGUI/cache'
New-Item -ItemType Directory -Force $iconTarget | Out-Null
foreach ($name in @('blocks.json','geyser-items.json','item_texture.json','terrain_texture.json')) {
  $path = Join-Path $icons $name
  if (Test-Path $path) { Copy-Item $path $iconTarget }
}
Set-Content (Join-Path $server 'eula.txt') 'eula=false' -Encoding ASCII
$updateArchive = [IO.Compression.ZipFile]::OpenRead($updateZip)
try {
  $reader = New-Object IO.StreamReader($updateArchive.GetEntry('update-index.json').Open())
  try { $updateIndex = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
} finally { $updateArchive.Dispose() }
$baseline = @{}
foreach ($file in $updateIndex.files) {
  $baseline[$file.path] = $file.contentSha256
  if ($file.path.StartsWith('config/survival/')) { $baseline['servers/survival/' + $file.path.Substring(16)] = $file.contentSha256 }
}
@{ version=$version.version; baseline=$baseline; pendingDirectory=$null } | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $release '.update-state.json') -Encoding UTF8
$zip = "$release.zip"
Compress-Archive -Path (Join-Path $release '*') -DestinationPath $zip -CompressionLevel Optimal
$hash = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLower()
Set-Content "$zip.sha256.txt" "$hash  $([IO.Path]::GetFileName($zip))" -Encoding ASCII
Write-Host "Release: $zip"
