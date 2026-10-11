param([switch]$Download)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$headers = @{'User-Agent'='SurvivalServerSetup/1.0 (https://github.com/NLKASHEI/MY-MC-SERVER)'}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$stage = Join-Path $root "downloads/updates-$stamp"
New-Item -ItemType Directory -Force $stage | Out-Null
$candidates = [System.Collections.Generic.List[object]]::new()
$report = [System.Collections.Generic.List[object]]::new()
$paper = Get-Content (Join-Path $root 'manifests/paper.lock.json') -Raw | ConvertFrom-Json
$current = Get-Content (Join-Path $root 'manifests/plugins.lock.json') -Raw | ConvertFrom-Json
function Candidate($name,$old,$new,$url,$hash,$note,$sha512) {
  $report.Add([pscustomobject]@{name=$name;installed=$old;candidate=$new;note=$note})
  if ($old -eq $new -or !$Download) { return }
  $path = Join-Path $stage "$name.jar"
  Invoke-WebRequest -UseBasicParsing $url -Headers $headers -OutFile $path
  if ($hash -and (Get-FileHash $path -Algorithm SHA256).Hash.ToLower() -ne $hash) { throw "Hash mismatch: $name" }
  if ($sha512 -and (Get-FileHash $path -Algorithm SHA512).Hash.ToLower() -ne $sha512) { throw "Hash mismatch: $name" }
  $candidates.Add([pscustomobject]@{name=$name;version=$new;url=$url;sha256=(Get-FileHash $path -Algorithm SHA256).Hash.ToLower();file="$name.jar";note=$note})
}
$builds = Invoke-RestMethod 'https://fill.papermc.io/v3/projects/paper/versions/26.2/builds' -Headers $headers
$b = @($builds | Where-Object channel -eq 'STABLE')[0]
$f = $b.downloads.'server:default'
Candidate 'paper' ([string]$paper.id) ([string]$b.id) $f.url $f.checksums.sha256 '26.2 stable; do not change Minecraft version' $null
$b | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $stage 'paper.lock.json') -Encoding UTF8
foreach ($plugin in $current) {
 try {
  if ($plugin.url -match '^https://cdn.modrinth.com/data/([^/]+)/') {
    $versions = Invoke-RestMethod "https://api.modrinth.com/v2/project/$($Matches[1])/version" -Headers $headers
    $v = @($versions | Where-Object {$_.version_type -eq 'release' -and $_.game_versions -contains '26.2' -and @($_.loaders | Where-Object {$_ -in @('paper','spigot','bukkit')}).Count -gt 0})[0]
    if (!$v) { throw 'No compatible release' }
    $f = @($v.files | Where-Object primary)[0]
    Candidate $plugin.name $plugin.version $v.version_number $f.url $null 'Modrinth declares Paper/Bukkit 26.2 compatibility; still test' $f.hashes.sha512
  } elseif ($plugin.url -match '^https://download.geysermc.org/v2/projects/([^/]+)/') {
    $project = $Matches[1]
    $v = Invoke-RestMethod "https://download.geysermc.org/v2/projects/$project/versions/latest/builds/latest" -Headers $headers
    $url = "https://download.geysermc.org/v2/projects/$project/versions/$($v.version)/builds/$($v.build)/downloads/spigot"
    Candidate $plugin.name $plugin.version "$($v.version)-$($v.build)" $url $v.downloads.spigot.sha256 'Check Geyser supported Java/Bedrock versions before applying' $null
  } elseif ($plugin.url -match '^https://github.com/([^/]+/[^/]+)/releases/') {
    $repo = $Matches[1]
    $v = Invoke-RestMethod "https://api.github.com/repos/$repo/releases/latest" -Headers $headers
    $oldFile = [IO.Path]::GetFileName($plugin.url)
    $pattern = if ($plugin.name -eq 'AuthMe') {'-Paper.jar$'} else {'^'+[regex]::Escape($plugin.name)+'([-.].*)?\.jar$'}
    $f = @($v.assets | Where-Object name -Match $pattern)[0]
    if (!$f) { throw 'No matching release asset' }
    $hash = if ($f.digest -like 'sha256:*') {$f.digest.Substring(7)} else {$null}
    Candidate $plugin.name $plugin.version $v.tag_name $f.browser_download_url $hash 'Upstream latest release; manual 26.2 dependency review required' $null
  } else {
    $report.Add([pscustomobject]@{name=$plugin.name;installed=$plugin.version;candidate='manual';note='Pinned special source; review official site and update manifest after testing'})
  }
 } catch { $report.Add([pscustomobject]@{name=$plugin.name;installed=$plugin.version;candidate='unavailable';note=$_.Exception.Message}) }
}
$report | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $stage 'report.json') -Encoding UTF8
ConvertTo-Json -InputObject @($candidates.ToArray()) -Depth 5 | Set-Content (Join-Path $stage 'candidates.json') -Encoding UTF8
$report | Format-Table -AutoSize
Write-Host "Candidates saved to $stage. Nothing has been applied."
