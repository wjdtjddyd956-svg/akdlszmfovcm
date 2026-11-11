param(
    [Parameter(Mandatory = $true)][string]$Archive,
    [string]$ServerDirectory
)
if (!$ServerDirectory) { $ServerDirectory = Split-Path -Parent $PSScriptRoot }
. (Join-Path $PSScriptRoot 'LifeCommon.ps1')
$root = (Resolve-Path -LiteralPath $ServerDirectory).Path
$zip = (Resolve-Path -LiteralPath $Archive).Path
$expected = (Get-Content -Raw -LiteralPath ($zip + '.sha256')).Trim()
if ($expected -notmatch '^[a-fA-F0-9]{64}$' -or (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne $expected) {
    throw '백업 체크섬이 일치하지 않습니다.'
}
$control = Join-Path $root 'life-control'
New-Item -ItemType Directory -Path $control -Force | Out-Null
$lock = [IO.File]::Open((Join-Path $control 'manager.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
$id = [Guid]::NewGuid().ToString('N')
$parent = Split-Path -Parent $root
$stage = Join-Path $parent "life-restore-stage-$id"
$previous = Join-Path $parent "life-before-restore-$id"
$moved = New-Object 'Collections.Generic.List[string]'
$placed = New-Object 'Collections.Generic.List[string]'
try {
    Assert-LifeOffline $root
    $inputZip = [IO.Compression.ZipFile]::OpenRead($zip)
    try {
        $manifest = $inputZip.GetEntry('life-snapshot.json')
        if (!$manifest) { throw '이 설치판의 백업 형식이 아닙니다.' }
        $reader = New-Object IO.StreamReader($manifest.Open())
        try { $meta = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        if ($meta.version -ne 1 -or !$meta.worlds.Count -or !$meta.roots.Count) { throw '백업 목록이 손상되었습니다.' }
        $names = @($meta.roots)
        if (($names | Select-Object -Unique).Count -ne $names.Count -or 'plugins' -notin $names -or 'server.properties' -notin $names) {
            throw '백업 목록의 필수 경로가 올바르지 않습니다.'
        }
        $allowed = @('plugins','config','life-tools','server.properties','bukkit.yml','spigot.yml','commands.yml',
            'permissions.yml','wepif.yml','ops.json','whitelist.json','banned-players.json','banned-ips.json',
            'usercache.json','eula.txt','ServerStart.cmd','ServerStop.cmd') + @($meta.worlds)
        foreach ($name in $names) {
            if ($name -notmatch '^[\p{L}\p{N}_ .-]+$' -or $name -match '(^\.|\.$)' -or $name -notin $allowed) { throw "잘못된 백업 경로: $name" }
        }
        foreach ($world in $meta.worlds) {
            if ($world -notin $names -or !($inputZip.Entries | Where-Object { $_.FullName.Replace('\','/') -eq "$world/level.dat" } | Select-Object -First 1)) {
                throw "월드 파일 누락: $world"
            }
        }
        foreach ($entry in $inputZip.Entries) {
            $relative = $entry.FullName.Replace('\','/')
            if ($relative -ne 'life-snapshot.json' -and (($relative -split '/')[0] -notin $names -or
                $relative -match '(^/|:|(^|/)\.\.?(/|$))')) { throw "잘못된 ZIP 경로: $relative" }
        }
    } finally { $inputZip.Dispose() }
    [IO.Compression.ZipFile]::ExtractToDirectory($zip,$stage)
    if ((Resolve-Path -LiteralPath $stage).Path -ne $stage) { throw '복원 준비 경로가 바뀌었습니다.' }
    New-Item -ItemType Directory -Path $previous | Out-Null
    Assert-LifeOffline $root
    foreach ($name in $names) {
        $live = Join-Path $root $name
        $from = Join-Path $stage $name
        Assert-LifeLocalTree $from
        if (Test-Path -LiteralPath $live) {
            Assert-LifeLocalTree $live
            if ((Resolve-Path -LiteralPath $live).Path -ne $live) { throw '원본 경로가 바뀌었습니다.' }
            Move-Item -LiteralPath $live -Destination $previous
            $moved.Add($name)
        }
        Move-Item -LiteralPath $from -Destination $live
        $placed.Add($name)
    }
    Write-Host "복원 완료. 복원 직전 원본은 $previous 에 있습니다."
} catch {
    foreach ($name in $placed) { Move-Item -LiteralPath (Join-Path $root $name) -Destination $stage }
    foreach ($name in $moved) { Move-Item -LiteralPath (Join-Path $previous $name) -Destination $root }
    throw
} finally { $lock.Dispose() }
