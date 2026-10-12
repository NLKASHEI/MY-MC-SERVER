param([string]$ServerDirectory)
$ErrorActionPreference = 'Stop'
if (!$ServerDirectory) {
  $root = Split-Path $PSScriptRoot -Parent
  $settings = Get-Content (Join-Path $root 'server-settings.json') -Raw -Encoding UTF8 | ConvertFrom-Json
  $ServerDirectory = Join-Path $root $settings.serverDirectory
}
$file = Join-Path $ServerDirectory 'config/paper-global.yml'
$marker = $file + '.technical-v0911-applied'
if (Test-Path -LiteralPath $marker) { return }
$keys = @('allow-piston-duplication','allow-headless-pistons','allow-permanent-block-break-exploits','allow-unsafe-end-portal-teleportation','skip-tripwire-hook-placement-validation')
$text = if (Test-Path -LiteralPath $file) { [IO.File]::ReadAllText($file) } else { "_version: 31`n" }
$section = [regex]::Match($text, '(?m)^unsupported-settings:[^\r\n]*(?:\r?\n(?:[ \t]+[^\r\n]*|[ \t]*))*')
$body = if ($section.Success) { $section.Value.TrimEnd("`r","`n") } else { 'unsupported-settings:' }
foreach ($key in $keys) {
  $pattern = '(?m)^  ' + [regex]::Escape($key) + ':.*$'
  if ([regex]::IsMatch($body,$pattern)) { $body = [regex]::Replace($body,$pattern,('  ' + $key + ': true')) }
  else { $body += "`n  ${key}: true" }
}
if ($section.Success) { $text = $text.Substring(0,$section.Index) + $body + "`n" + $text.Substring($section.Index + $section.Length) }
else { $text = $text.TrimEnd("`r","`n") + "`n" + $body + "`n" }
New-Item -ItemType Directory -Force (Split-Path $file) | Out-Null
if (Test-Path -LiteralPath $file) {
  $backup = $file + '.before-technical-v0911'
  if (!(Test-Path -LiteralPath $backup)) { Copy-Item -LiteralPath $file -Destination $backup }
}
$temp = $file + '.technical-tmp'
[IO.File]::WriteAllText($temp,$text,(New-Object Text.UTF8Encoding($false)))
Move-Item -LiteralPath $temp -Destination $file -Force
[IO.File]::WriteAllText($marker,'Applied technical settings v0.9.11')
Write-Host 'Technical survival settings applied. Existing Paper settings preserved.'
