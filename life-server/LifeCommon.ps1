$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Write-LifeUtf8([string]$Path, [string]$Content) {
    [IO.File]::WriteAllText($Path, $Content, (New-Object Text.UTF8Encoding($false)))
}

function Read-LifeProperty([string]$Root, [string]$Key, [string]$Default) {
    $match = Select-String -LiteralPath (Join-Path $Root 'server.properties') -Pattern ('^' + [regex]::Escape($Key) + '=(.*)$') |
        Select-Object -Last 1
    if ($match) { return $match.Matches[0].Groups[1].Value.Trim() }
    return $Default
}

function Assert-LifeOffline([string]$Root) {
    $port = [int](Read-LifeProperty $Root 'server-port' '25565')
    $listen = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
    if ($listen.Count) { throw "서버 포트 $port 가 사용 중입니다. stop으로 먼저 종료하세요." }
    $hostName = Read-LifeProperty $Root 'server-ip' ''
    if (!$hostName) { $hostName = '127.0.0.1' }
    $client = New-Object Net.Sockets.TcpClient
    try {
        $connection = $client.ConnectAsync($hostName, $port)
        try { $null = $connection.Wait(600) } catch {}
        if ($client.Connected) { throw "서버 포트 $port 가 사용 중입니다. stop으로 먼저 종료하세요." }
    } finally { $client.Dispose() }
    foreach ($world in (Get-LifeWorlds $Root)) {
        $lock = Join-Path $world.FullName 'session.lock'
        if (Test-Path -LiteralPath $lock) {
            try {
                $stream = [IO.File]::Open($lock, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
                try { $stream.Lock(0, 1); $stream.Unlock(0, 1) } finally { $stream.Dispose() }
            } catch { throw "월드가 열려 있습니다: $($world.Name)" }
        }
    }
}

function Get-LifeWorlds([string]$Root) {
    Get-ChildItem -LiteralPath $Root -Directory -Force | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'level.dat') -PathType Leaf }
}

function Assert-LifeLocalTree([string]$Path) {
    $item = Get-Item -LiteralPath $Path -Force
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "백업 경로에 링크가 있습니다: $Path" }
    if ($item.PSIsContainer) {
        $link = Get-ChildItem -LiteralPath $Path -Force -Recurse -Attributes ReparsePoint | Select-Object -First 1
        if ($link) { throw "백업 경로에 링크가 있습니다: $($link.FullName)" }
    }
}

function New-LifeSnapshot([string]$Root, [string]$Stage) {
    Assert-LifeOffline $Root
    $worlds = @(Get-LifeWorlds $Root)
    if ($worlds.Count -eq 0) { throw 'level.dat가 있는 월드가 없습니다.' }
    $directories = @($worlds.Name) + @('plugins','config','life-tools')
    $files = @('server.properties','bukkit.yml','spigot.yml','commands.yml','permissions.yml',
        'wepif.yml','ops.json','whitelist.json','banned-players.json','banned-ips.json',
        'usercache.json','eula.txt','ServerStart.cmd','ServerStop.cmd')
    $saved = @()
    New-Item -ItemType Directory -Path $Stage | Out-Null
    foreach ($name in (($directories + $files) | Select-Object -Unique)) {
        $source = Join-Path $Root $name
        if (!(Test-Path -LiteralPath $source)) { continue }
        Assert-LifeLocalTree $source
        Copy-Item -LiteralPath $source -Destination $Stage -Recurse -Force
        $saved += $name
    }
    if ('plugins' -notin $saved -or 'server.properties' -notin $saved) { throw '서버 필수 파일이 빠졌습니다.' }
    $manifest = [ordered]@{ version = 1; createdUtc = [DateTime]::UtcNow.ToString('o'); worlds = @($worlds.Name); roots = @($saved) }
    Write-LifeUtf8 (Join-Path $Stage 'life-snapshot.json') ($manifest | ConvertTo-Json -Depth 4)
}

function Get-LifeBackupRoot([string]$Root, [string]$Configured) {
    $default = $Root + '-backups'
    $destination = if (!$Configured) { $default } elseif ([IO.Path]::IsPathRooted($Configured)) { $Configured } else { Join-Path (Split-Path -Parent $Root) $Configured }
    $destination = [IO.Path]::GetFullPath($destination)
    if ($destination.TrimEnd('\','/') -eq $Root.TrimEnd('\','/') -or
        $destination.StartsWith($Root.TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw '백업 저장소는 서버 폴더 밖에 지정하세요.'
    }
    return $destination
}

function Get-LifeId([string]$Root) {
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($algorithm.ComputeHash([Text.Encoding]::UTF8.GetBytes($Root.ToLowerInvariant())))).Replace('-','').Substring(0,12) }
    finally { $algorithm.Dispose() }
}
