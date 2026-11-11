param([string]$ServerDirectory)
$ErrorActionPreference = 'Stop'
if (!$ServerDirectory) { $ServerDirectory = (Read-Host '1.2 또는 2.0 서버 폴더 경로').Trim('"') }
$ServerDirectory = (Resolve-Path -LiteralPath $ServerDirectory).Path
if (!(Test-Path -LiteralPath (Join-Path $ServerDirectory 'server.properties'))) { throw 'server.properties가 있는 서버 폴더를 선택하세요.' }
$Java = (Get-Command java -ErrorAction Stop).Source
Add-Type -AssemblyName System.IO.Compression.FileSystem
# Verify the complete custom payload before requesting a shutdown.
$Expected = @{}
foreach ($Line in Get-Content -LiteralPath (Join-Path $PSScriptRoot 'plugins\SHA256.properties')) {
    if ($Line -match '^([^#=]+)=([a-fA-F0-9]{64})$') {
        $File = Join-Path $PSScriptRoot ('plugins\' + $Matches[1])
        $Digest = $Matches[2]
        if ((Get-FileHash -LiteralPath $File -Algorithm SHA256).Hash -ne $Digest) { throw ('배포 파일 손상: ' + $File) }
        $Expected[$Matches[1]] = $Digest
    }
}
if ($Expected.Count -ne 4) { throw '자체 플러그인 4개의 검증 목록이 필요합니다.' }
$Existing = @{}
foreach ($Jar in Get-ChildItem -LiteralPath (Join-Path $ServerDirectory 'plugins') -File -Filter '*.jar') {
    $Zip = [IO.Compression.ZipFile]::OpenRead($Jar.FullName)
    try {
        $Entry = $Zip.GetEntry('plugin.yml'); if (!$Entry) { continue }
        $Reader = New-Object IO.StreamReader($Entry.Open(), [Text.Encoding]::UTF8)
        try { $Description = $Reader.ReadToEnd() } finally { $Reader.Dispose() }
        $Name = [regex]::Match($Description, '(?m)^name:[ \t]*([^\r\n]+)').Groups[1].Value.Trim().Trim([char[]]@(34,39))
        if (@('Vault','QuickShop-Hikari','Essentials','EssentialsSpawn','GriefPrevention','prism') -contains $Name) {
            if ($Existing.ContainsKey($Name)) { throw ('중복된 기존 플러그인: ' + $Name) }
            $Existing[$Name] = $Jar.FullName
        }
    } finally { $Zip.Dispose() }
}
foreach ($Name in @('Vault','QuickShop-Hikari','Essentials','EssentialsSpawn','GriefPrevention','prism')) {
    if (!$Existing.ContainsKey($Name)) { throw ('기존 생활서버용 업그레이드입니다. 먼저 확인할 플러그인: ' + $Name) }
}
$Stage = Join-Path $PSScriptRoot ('설치준비-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $Stage | Out-Null
try {
    foreach ($Name in $Expected.Keys) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot ('plugins\' + $Name)) -Destination $Stage }
    # Reuse exact installed files, including the compatible matched Essentials pair.
    foreach ($Name in @('Vault','QuickShop-Hikari','Essentials','EssentialsSpawn')) { Copy-Item -LiteralPath $Existing[$Name] -Destination $Stage }
    $Lines = @(Get-ChildItem -LiteralPath $Stage -File -Filter '*.jar' | ForEach-Object { $_.Name + '=' + (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() })
    [IO.File]::WriteAllLines((Join-Path $Stage 'SHA256.properties'), $Lines, [Text.Encoding]::ASCII)
    $Stop = Join-Path $ServerDirectory 'life-tools\Stop-LifeServer.ps1'
    if (Test-Path -LiteralPath $Stop) {
        Write-Host '서버와 기존 백업 관리자를 정상 종료합니다. 전체 백업 후 JAR를 교체합니다.' -ForegroundColor Yellow
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $Stop -ServerDirectory $ServerDirectory
        if ($LASTEXITCODE -ne 0) { throw '정상 종료를 확인하지 못해 설치를 중단했습니다.' }
    } else { throw '기존 Windows 백업 관리자를 찾지 못했습니다. 이 파일은 생활서버 1.2/2.0 업그레이드 전용입니다.' }
    & $Java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -jar (Join-Path $PSScriptRoot '설치도구.jar') 설치 $ServerDirectory $Stage --서버종료확인
    if ($LASTEXITCODE -ne 0) { throw '설치를 완료하지 못했습니다. 기존 버전과 표시된 백업을 확인하세요.' }
    # Only patch the exact original notice; keep local manager changes and all options.
    $Manager = Join-Path $ServerDirectory 'life-tools\Run-LifeServer.ps1'
    $Text = [IO.File]::ReadAllText($Manager, [Text.Encoding]::UTF8)
    $OldNotice = 'Send-Paper "say $($options.warningSeconds)초 후 월드 백업을 위해 잠시 서버를 재시작합니다."'
    $NewNotice = @'
$notice = "$($options.warningSeconds)초 후 월드 백업을 위해 잠시 서버를 재시작합니다."
                $escaped = -join ($notice.ToCharArray() | ForEach-Object { '\u{0:x4}' -f [int]$_ })
                Send-Paper ('tellraw @a {"text":"' + $escaped + '","color":"yellow"}')
'@
    if ($Text.Contains($OldNotice)) {
        [IO.File]::WriteAllText($Manager, $Text.Replace($OldNotice, $NewNotice), (New-Object Text.UTF8Encoding($true)))
        Write-Host '기존 백업 관리자의 한글 예고 인코딩을 보정했습니다. 주기·보관·경로·메모리 설정은 유지했습니다.'
    }
    Write-Host '설치 완료. 기존 ServerStart.cmd로 시작하고 설치·운영안내.md를 확인하세요.' -ForegroundColor Green
} finally {
    $ResolvedStage = [IO.Path]::GetFullPath($Stage)
    if (!$ResolvedStage.StartsWith([IO.Path]::GetFullPath($PSScriptRoot) + [IO.Path]::DirectorySeparatorChar)) { throw '임시 폴더 경로 검증 실패' }
    if (Test-Path -LiteralPath $ResolvedStage) { Remove-Item -LiteralPath $ResolvedStage -Recurse -Force }
}
