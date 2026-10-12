param([string]$ServerDirectory)
$ErrorActionPreference = 'Stop'
if (!$ServerDirectory) {
  $root = Split-Path $PSScriptRoot -Parent
  $settings = Get-Content (Join-Path $root 'server-settings.json') -Raw -Encoding UTF8 | ConvertFrom-Json
  $ServerDirectory = Join-Path $root $settings.serverDirectory
}
$file = Join-Path $ServerDirectory 'bukkit.yml'
$marker = $file + '.mob-cap-v0912-applied'
if (Test-Path -LiteralPath $marker) { return }
$text = if (Test-Path -LiteralPath $file) { [IO.File]::ReadAllText($file) } else { '' }
$section = [regex]::Match($text, '(?m)^spawn-limits:[^\r\n]*(?:\r?\n(?:[ \t]+[^\r\n]*|[ \t]*))*')
$body = if ($section.Success) { $section.Value.TrimEnd("`r","`n") } else { 'spawn-limits:' }
$pattern = '(?m)^  monsters:.*$'
if ([regex]::IsMatch($body,$pattern)) { $body = [regex]::Replace($body,$pattern,'  monsters: 100') }
else { $body += "`n  monsters: 100" }
if ($section.Success) { $text = $text.Substring(0,$section.Index) + $body + "`n" + $text.Substring($section.Index + $section.Length) }
else { $text = $text.TrimEnd("`r","`n") + "`n" + $body + "`n" }
New-Item -ItemType Directory -Force $ServerDirectory | Out-Null
if (Test-Path -LiteralPath $file) {
  $backup = $file + '.before-mob-cap-v0912'
  if (!(Test-Path -LiteralPath $backup)) { Copy-Item -LiteralPath $file -Destination $backup }
}
$temp = $file + '.mob-cap-tmp'
[IO.File]::WriteAllText($temp,$text,(New-Object Text.UTF8Encoding($false)))
Move-Item -LiteralPath $temp -Destination $file -Force
[IO.File]::WriteAllText($marker,'Applied monster baseline 100 v0.9.12')
Write-Host 'Natural monster cap baseline updated to 100. Other spawn limits preserved.'
