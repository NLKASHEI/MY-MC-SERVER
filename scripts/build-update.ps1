param([string]$OutputDirectory, [string]$BaseRef = 'HEAD')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (!$OutputDirectory) { $OutputDirectory = Join-Path $root 'release' }
$version = Get-Content (Join-Path $root 'release-version.json') -Raw | ConvertFrom-Json
$stage = Join-Path $OutputDirectory ('update-build-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $stage | Out-Null
$files = @()
$paths = @('README.md','AGENTS.md','GLOSSARY.md','CHANGELOG.md','release-version.json')
$paths += @(Get-ChildItem $root -Filter *.bat | ForEach-Object { $_.Name })
foreach ($folder in @('scripts','docs','skills','manifests','config')) {
  $paths += @(Get-ChildItem (Join-Path $root $folder) -File -Recurse | ForEach-Object { $_.FullName.Substring($root.Length + 1).Replace('\','/') })
}
foreach ($path in ($paths | Sort-Object -Unique)) {
  # Older permanent updaters allow YAML/text configs only. BlueMap HOCON is
  # embedded in the locked MiaoServer JAR and installed only when missing.
  if ($path -match '^config/survival/plugins/BlueMap/.+\.conf$') { continue }
  # Old permanent updaters only accept plugin config templates. The startup
  # migration merges these technical settings into existing Paper config.
  if ($path -eq 'config/survival/config/paper-global.yml') { continue }
  $source = Join-Path $root $path
  $target = Join-Path $stage $path
  New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
  Copy-Item -LiteralPath $source -Destination $target
  $baseHash = $null
  # Use Git's exact prior blob bytes, not PowerShell text redirection.
  $info = New-Object Diagnostics.ProcessStartInfo
  $info.FileName = 'git'
  $info.Arguments = "show ${BaseRef}:$path"
  $info.WorkingDirectory = $root
  $info.UseShellExecute = $false
  $info.RedirectStandardOutput = $true
  $info.RedirectStandardError = $true
  $info.CreateNoWindow = $true
  $process = New-Object Diagnostics.Process
  $process.StartInfo = $info
  $null = $process.Start()
  $memory = New-Object IO.MemoryStream
  $process.StandardOutput.BaseStream.CopyTo($memory)
  $null = $process.StandardError.ReadToEnd()
  $process.WaitForExit()
  if ($process.ExitCode -eq 0) { $sha = [Security.Cryptography.SHA256]::Create(); try { $bytes = [Text.Encoding]::UTF8.GetBytes([Text.Encoding]::UTF8.GetString($memory.ToArray()).TrimStart([char]0xfeff).Replace("`r`n","`n")); $baseHash = [BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-','').ToLower() } finally { $sha.Dispose() } }
  $memory.Dispose(); $process.Dispose()
  $sha = [Security.Cryptography.SHA256]::Create()
  try { $bytes = [Text.Encoding]::UTF8.GetBytes([IO.File]::ReadAllText($source).TrimStart([char]0xfeff).Replace("`r`n","`n")); $contentHash = [BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-','').ToLower() } finally { $sha.Dispose() }
  $files += [ordered]@{ path=$path; sha256=(Get-FileHash $source -Algorithm SHA256).Hash.ToLower(); contentSha256=$contentHash; baseSha256=$baseHash }
}
$index = [ordered]@{ schema=1; version=$version.version; minecraftVersion=$version.minecraftVersion; files=$files }
$index | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $stage 'update-index.json') -Encoding UTF8
$zip = Join-Path $OutputDirectory "MY-MC-SERVER-update-v$($version.version).zip"
if (Test-Path $zip) { throw "Update package already exists: $zip" }
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $zip -CompressionLevel Optimal
$hash = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLower()
Set-Content "$zip.sha256.txt" "$hash  $([IO.Path]::GetFileName($zip))" -Encoding ASCII
Write-Host "Update package: $zip"
