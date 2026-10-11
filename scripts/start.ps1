$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$settings = Get-Content (Join-Path $root 'server-settings.json') -Raw | ConvertFrom-Json
$java = @(Get-ChildItem (Join-Path $root 'runtime') -Filter java.exe -Recurse)[0].FullName
if (!$java) { throw 'Bundled Java not found' }
$server = Join-Path $root $settings.serverDirectory
Set-Location $server
& $java "-Xms$($settings.memoryMin)" "-Xmx$($settings.memoryMax)" '-Dfile.encoding=UTF-8' '-Duser.timezone=Asia/Shanghai' -jar paper.jar --nogui
exit $LASTEXITCODE
