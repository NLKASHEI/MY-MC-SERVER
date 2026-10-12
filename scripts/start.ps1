$ErrorActionPreference = 'Stop'
[Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$root = Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'update-memory-settings.ps1')
$settings = Get-Content (Join-Path $root 'server-settings.json') -Raw | ConvertFrom-Json
$java = @(Get-ChildItem (Join-Path $root 'runtime') -Filter java.exe -Recurse)[0].FullName
if (!$java) { throw 'Bundled Java not found' }
$server = Join-Path $root $settings.serverDirectory
Set-Location $server
& $java "-Xms$($settings.memoryMin)" "-Xmx$($settings.memoryMax)" '-Dfile.encoding=UTF-8' '-Duser.timezone=Asia/Shanghai' '-Dorg.bukkit.plugin.java.LibraryLoader.centralURL=https://maven.aliyun.com/repository/central' -jar paper.jar --nogui
exit $LASTEXITCODE
