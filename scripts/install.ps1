param([switch]$AcceptEula)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$server = Join-Path $root 'servers/survival'
if (Test-Path (Join-Path $server 'world/level.dat')) { throw 'Existing world detected. Use the update procedure, not installation.' }
New-Item -ItemType Directory -Force -Path $server,(Join-Path $server 'plugins'),(Join-Path $root 'downloads'),(Join-Path $root 'runtime') | Out-Null
$headers = @{'User-Agent'='SurvivalServerSetup/1.0 (https://github.com/NLKASHEI/MY-MC-SERVER)'}
function Fetch($url,$target,$sha) {
  if (!(Test-Path $target) -or (Get-FileHash $target -Algorithm SHA256).Hash.ToLower() -ne $sha) {
    Invoke-WebRequest -UseBasicParsing $url -Headers $headers -OutFile $target
  }
  if ((Get-FileHash $target -Algorithm SHA256).Hash.ToLower() -ne $sha) { throw "Checksum mismatch: $target" }
}
$paper = Get-Content (Join-Path $root 'manifests/paper.lock.json') -Raw | ConvertFrom-Json
$asset = $paper.downloads.'server:default'
Fetch $asset.url (Join-Path $server 'paper.jar') $asset.checksums.sha256
$jdk = Get-Content (Join-Path $root 'manifests/java.lock.json') -Raw | ConvertFrom-Json
$zip = Join-Path $root 'downloads/java25.zip'
if (!(Get-ChildItem (Join-Path $root 'runtime') -Recurse -Filter java.exe)) {
  Fetch $jdk.binary.package.link $zip $jdk.binary.package.checksum
  Expand-Archive $zip (Join-Path $root 'runtime') -Force
}
foreach ($plugin in (Get-Content (Join-Path $root 'manifests/plugins.lock.json') -Raw | ConvertFrom-Json)) {
  $target = Join-Path $server "plugins/$($plugin.name).jar"
  if ($plugin.method -eq 'POST') {
    if (!(Test-Path $target)) {
      $body = @{}; $plugin.body.psobject.Properties | ForEach-Object { $body[$_.Name] = $_.Value }
      Invoke-WebRequest -UseBasicParsing $plugin.url -Method Post -Body $body -OutFile $target
    }
    if ((Get-FileHash $target -Algorithm SHA256).Hash.ToLower() -ne $plugin.sha256) { throw "Checksum mismatch: $($plugin.name)" }
  } else { Fetch $plugin.url $target $plugin.sha256 }
}
Copy-Item (Join-Path $root 'config/survival/*') $server -Recurse -Force
$worldTemplate = Get-Content (Join-Path $root 'manifests/world-template.lock.json') -Raw | ConvertFrom-Json
$worldZip = Join-Path $root $worldTemplate.file
if ((Get-FileHash $worldZip -Algorithm SHA256).Hash.ToLower() -ne $worldTemplate.sha256) { throw 'World template checksum mismatch' }
Expand-Archive $worldZip $server -Force
$eula = if ($AcceptEula) { 'eula=true' } else { 'eula=false' }
Set-Content (Join-Path $server 'eula.txt') $eula -Encoding ASCII
Write-Host 'Installed pinned components. Review https://aka.ms/MinecraftEULA before accepting the EULA.'
