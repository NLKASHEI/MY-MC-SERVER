$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Write-Host 'Run this from an external computer AFTER the cloud server is started.'
try {
  & (Join-Path $PSScriptRoot 'check-network.ps1') -JavaHost 's9-4.yxjsmc.cn' -JavaPort 20005 -BedrockHost 's9-4.yxjsmc.cn' -BedrockPort 20013
  Write-Host 'Both public game endpoints replied. The Bedrock UDP mapping is reachable from this computer.'
} catch {
  Write-Host 'No complete response. Check the cloud server, UDP mapping, Windows firewall and cloud security group.'
  Write-Host 'A timeout alone does not prove the mapping is TCP-only.'
  throw
}
