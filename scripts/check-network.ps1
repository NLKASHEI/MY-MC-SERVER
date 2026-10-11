param([string]$JavaHost = "127.0.0.1", [int]$JavaPort = 25565, [string]$BedrockHost = "127.0.0.1", [int]$BedrockPort = 8585)
$ErrorActionPreference = 'Stop'
function VarInt([int]$value) {
  $bytes = New-Object 'System.Collections.Generic.List[byte]'
  do {
    $part = $value -band 127
    $value = $value -shr 7
    if ($value -ne 0) { $part = $part -bor 128 }
    $bytes.Add([byte]$part)
  } while ($value -ne 0)
  return $bytes.ToArray()
}
function ReadVarInt($stream) {
  $value=0; $shift=0
  do {
    $b=$stream.ReadByte()
    if ($b -lt 0 -or $shift -gt 28) { throw 'Invalid VarInt' }
    $value=$value -bor (($b -band 127) -shl $shift)
    $shift+=7
  } while (($b -band 128) -ne 0)
  return $value
}
$client=New-Object Net.Sockets.TcpClient
try {
  $connect = $client.ConnectAsync($JavaHost,$JavaPort)
  if (!$connect.Wait(5000)) { throw 'Java TCP connection timed out' }
  $stream=$client.GetStream(); $stream.ReadTimeout=5000; $stream.WriteTimeout=5000
  $address=[Text.Encoding]::UTF8.GetBytes($JavaHost)
  [byte[]]$handshake=@(0,0)+@(VarInt $address.Length)+$address+@((($JavaPort -shr 8) -band 255),($JavaPort -band 255),1)
  [byte[]]$packet=@(VarInt $handshake.Length)+$handshake+@(1,0)
  $stream.Write($packet,0,$packet.Length)
  $null=ReadVarInt $stream
  if ((ReadVarInt $stream) -ne 0) { throw 'Invalid status response packet' }
  $length=ReadVarInt $stream
  $buffer=New-Object byte[] $length; $offset=0
  while ($offset -lt $length) { $n=$stream.Read($buffer,$offset,$length-$offset); if ($n -le 0) {throw 'Truncated status'}; $offset+=$n }
  $status=[Text.Encoding]::UTF8.GetString($buffer) | ConvertFrom-Json
  if ($status.version.name -notmatch '26\.2') { throw 'Unexpected Minecraft version' }
  Write-Host "Java status OK: $($status.version.name), $JavaHost TCP $JavaPort"
} finally { $client.Dispose() }
$udp=New-Object Net.Sockets.UdpClient
try {
  $udp.Client.ReceiveTimeout=5000
  [byte[]]$magic=@(0,255,255,0,254,254,254,254,253,253,253,253,18,52,86,120)
  [byte[]]$ping=@(1)+@(0,0,0,0,0,0,0,1)+$magic+@(0,0,0,0,0,0,0,2)
  $udp.Connect($BedrockHost,$BedrockPort)
  $null=$udp.Send($ping,$ping.Length)
  $remote=New-Object Net.IPEndPoint ([Net.IPAddress]::Any),0
  $pong=$udp.Receive([ref]$remote)
  if ($pong.Length -lt 35 -or $pong[0] -ne 28) { throw 'Invalid Geyser RakNet pong' }
  $name=[Text.Encoding]::UTF8.GetString($pong,35,$pong.Length-35)
  if ($name -notmatch '^MCPE;') { throw 'Invalid Bedrock advertisement' }
  Write-Host "Bedrock status OK: $BedrockHost UDP $BedrockPort, $name"
} finally { $udp.Dispose() }
