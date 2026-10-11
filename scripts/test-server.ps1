param([string]$CommandsFile)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$server = Join-Path $root 'servers/survival'
New-Item -ItemType Directory -Force (Join-Path $root 'downloads') | Out-Null
$java = @(Get-ChildItem (Join-Path $root 'runtime') -Filter java.exe -Recurse)[0].FullName
$info = New-Object System.Diagnostics.ProcessStartInfo
$info.FileName = $java
$info.Arguments = '-Xms1G -Xmx3G -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai -jar paper.jar --nogui'
$info.WorkingDirectory = $server
$info.UseShellExecute = $false
$info.CreateNoWindow = $true
$info.RedirectStandardInput = $true
$process = New-Object System.Diagnostics.Process
$process.StartInfo = $info
$null = $process.Start()
$ready = $false
$deadline = (Get-Date).AddMinutes(12)
try {
  while (!$process.HasExited -and (Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    $log = Get-Content (Join-Path $server 'logs/latest.log') -Raw -ErrorAction SilentlyContinue
    if ($log -match 'Done \([0-9.]+s\)!') { $ready = $true; break }
  }
  if (!$ready) { throw 'Server failed to reach ready state' }
  Start-Sleep -Seconds 15
  & (Join-Path $PSScriptRoot 'check-network.ps1')
  if ($CommandsFile) {
    foreach ($command in Get-Content $CommandsFile -Encoding UTF8) {
      if ($command.Trim()) { $process.StandardInput.WriteLine($command); $process.StandardInput.Flush(); Start-Sleep -Seconds 1 }
    }
  }
  $process.StandardInput.WriteLine('plugins')
  $process.StandardInput.WriteLine('skript reload all')
  $process.StandardInput.WriteLine('save-all flush')
  $process.StandardInput.Flush()
  Start-Sleep -Seconds 10
} finally {
  if (!$process.HasExited) {
    $process.StandardInput.WriteLine('stop')
    $process.StandardInput.Flush()
    if (!$process.WaitForExit(90000)) { $process.Kill(); throw 'Server did not stop gracefully' }
  }
  $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
  Copy-Item (Join-Path $server 'logs/latest.log') (Join-Path $root "downloads/test-$stamp.log")
}
if ($process.ExitCode -ne 0) { throw "Server exit code: $($process.ExitCode)" }
