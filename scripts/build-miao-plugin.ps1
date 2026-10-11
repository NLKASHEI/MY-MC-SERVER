param([switch]$Smoke)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
  $jdk = Split-Path (@(Get-ChildItem runtime -Recurse -Filter javac.exe)[0].FullName)
  if (!$jdk) { throw 'Install the locked JDK first.' }
  $version = (Get-Content release-version.json -Raw | ConvertFrom-Json).version
  $folder = 'downloads/miao-build-' + [guid]::NewGuid().ToString('N')
  New-Item -ItemType Directory -Force $folder | Out-Null
  $jars = @(Get-ChildItem servers/survival/libraries -Recurse -Filter '*.jar') + @(Get-ChildItem servers/survival/plugins -File -Filter '*.jar')
  $cp = @($jars | ForEach-Object { $_.FullName.Substring($root.Length + 1).Replace('\','/') }) -join ';'
  $source = (@(Get-ChildItem tools/miao-server -Filter '*.java') | ForEach-Object { 'tools/miao-server/' + $_.Name }) -join ' '
  if ($Smoke) { $source = 'tests/miao-smoke/MiaoSmoke.java'; $cp = 'servers/survival/plugins/MiaoServer.jar;' + $cp }
  $argFile = $folder + '/javac.args'
  $line = '-encoding UTF-8 -classpath "' + $cp + '" -d ' + $folder + ' ' + $source
  [IO.File]::WriteAllText((Join-Path $root $argFile), $line, (New-Object Text.UTF8Encoding($false)))
  & (Join-Path $jdk 'javac.exe') "@$argFile"
  if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
  $output = "downloads/MiaoServer-$version.jar"
  if ($Smoke) {
    Copy-Item tests/miao-smoke/plugin.yml "$folder/plugin.yml"
    $output = 'downloads/MiaoSmoke.jar'
  } else {
    Copy-Item tools/miao-server/plugin.yml "$folder/plugin.yml"
    Copy-Item config/survival/plugins/MiaoServer/config.yml "$folder/config.yml"
    Copy-Item config/survival/plugins/DeluxeMenus/gui_menus/survival.yml "$folder/menu-layout.yml"
    Copy-Item assets/branding/server-icon.png "$folder/server-icon.png"
  }
  & (Join-Path $jdk 'jar.exe') --create --file $output -C $folder .
  if ($LASTEXITCODE -ne 0) { throw 'JAR build failed' }
  Write-Host "Built: $output"
  if ($Smoke) { Write-Host 'Test plugin only. Never include it in a release.' }
} finally { Pop-Location }
