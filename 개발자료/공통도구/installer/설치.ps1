param([string]$ServerDirectory)
$ErrorActionPreference = 'Stop'
$Root = $PSScriptRoot
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Paper 서버를 실행하는 Java 25를 먼저 설치하고 이 창을 다시 여세요.' }
if (-not $ServerDirectory) {
    Write-Host 'server.properties와 plugins가 들어 있는 서버 폴더 경로를 입력하세요.'
    $ServerDirectory = (Read-Host '서버 폴더').Trim('"')
}
if (-not (Test-Path -LiteralPath (Join-Path $ServerDirectory 'server.properties'))) { throw '실제 서버 폴더가 아닙니다.' }
Write-Host '1.2의 ServerStop.cmd로 서버와 백업 관리자를 종료하세요. 관리자가 없다면 콘솔 stop 후 완전히 종료될 때까지 기다리세요.' -ForegroundColor Yellow
if ((Read-Host '종료했다면 STOP 입력') -cne 'STOP') { throw '서버 종료 확인을 취소했습니다. 아무것도 설치하지 않았습니다.' }
$ManagerLock = Join-Path $ServerDirectory 'life-control\manager.lock'
if (Test-Path -LiteralPath $ManagerLock) {
    try { $Handle = [IO.File]::Open($ManagerLock, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None); $Handle.Dispose() }
    catch { throw '1.2의 ServerStop.cmd로 서버와 백업 관리자를 함께 종료한 뒤 실행하세요.' }
}
$Stage = Join-Path $Root ('설치준비-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $Stage | Out-Null
try {
    Copy-Item -LiteralPath (Join-Path $Root 'plugins\KoreanEconomy-2.0.0.jar') -Destination $Stage
    Copy-Item -LiteralPath (Join-Path $Root 'plugins\KoreanMenu-2.0.0.jar') -Destination $Stage
    Copy-Item -LiteralPath (Join-Path $Root 'plugins\KoreanCommands-2.0.0.jar') -Destination $Stage
    Copy-Item -LiteralPath (Join-Path $Root 'plugins\KoreanPackSetup-2.0.0.jar') -Destination $Stage
    Copy-Item -LiteralPath (Join-Path $Root 'plugins\Vault.jar') -Destination $Stage
    Copy-Item -LiteralPath (Join-Path $Root 'plugins\QuickShop-Hikari-6.3.0.4.jar') -Destination $Stage
    # Reuse the already validated 26.3 core/Spawn in an existing 1.2 installation.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $Core = $null; $Spawn = $null; $CoreVersion = ''; $SpawnVersion = ''
    foreach ($Jar in @(Get-ChildItem -LiteralPath (Join-Path $ServerDirectory 'plugins') -File -Filter '*.jar')) {
        $Zip = [IO.Compression.ZipFile]::OpenRead($Jar.FullName)
        try {
            $Entry = $Zip.GetEntry('plugin.yml'); if (-not $Entry) { continue }
            $Reader = New-Object IO.StreamReader($Entry.Open(),[Text.Encoding]::UTF8)
            try { $Description = $Reader.ReadToEnd() } finally { $Reader.Dispose() }
            $NameMatch = [regex]::Match($Description,'(?m)^name:[ \t]*([^\r\n]+)')
            $VersionMatch = [regex]::Match($Description,'(?m)^version:[ \t]*([^\r\n]+)')
            $Name = $NameMatch.Groups[1].Value.Trim().Trim([char[]]@(34,39))
            $Version = $VersionMatch.Groups[1].Value.Trim().Trim([char[]]@(34,39))
            if ($Name -eq 'Essentials') {
                $Class = $Zip.GetEntry('com/earth2me/essentials/utils/VersionUtil.class')
                if ($Class) {
                    $Memory = New-Object IO.MemoryStream; $Input = $Class.Open()
                    try { $Input.CopyTo($Memory); $Constants = [Text.Encoding]::GetEncoding('ISO-8859-1').GetString($Memory.ToArray()) } finally { $Input.Dispose(); $Memory.Dispose() }
                    if ($Constants.Contains('v26_3_R01')) { if ($Core) { throw 'Essentials 본체 JAR가 중복되어 있습니다.' }; $Core = $Jar.FullName; $CoreVersion = $Version }
                }
            } elseif ($Name -eq 'EssentialsSpawn') { if ($Spawn) { throw 'EssentialsSpawn JAR가 중복되어 있습니다.' }; $Spawn = $Jar.FullName; $SpawnVersion = $Version }
        } finally { $Zip.Dispose() }
    }
    if ($Core -and $Spawn -and $CoreVersion -eq $SpawnVersion) {
        Copy-Item -LiteralPath $Core -Destination (Join-Path $Stage 'EssentialsX-26.3.jar')
        Copy-Item -LiteralPath $Spawn -Destination (Join-Path $Stage 'EssentialsXSpawn-26.3.jar')
        Write-Host ('기존 26.3 지원 EssentialsX 유지: ' + $CoreVersion)
    } else {
        $Downloads = Join-Path $Stage '공식다운로드'
        & (Join-Path $Root 'EssentialsX-개발빌드받기.ps1') -OutputDirectory $Downloads
        $Build = @(Get-ChildItem -LiteralPath $Downloads -Directory -Filter 'build-*')
        if ($Build.Count -ne 1) { throw '공식 개발 빌드 폴더를 확인하지 못했습니다.' }
        Get-ChildItem -LiteralPath $Build[0].FullName -Filter '*.jar' | Copy-Item -Destination $Stage
    }
    $Lines = @()
    foreach ($Jar in @(Get-ChildItem -LiteralPath $Stage -File -Filter '*.jar' | Sort-Object Name)) {
        $Lines += $Jar.Name + '=' + (Get-FileHash -LiteralPath $Jar.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    # Verify the six packaged JARs against the release manifest, independently of downloaded JARs.
    foreach ($Line in Get-Content -LiteralPath (Join-Path $Root 'plugins\SHA256.properties')) {
        if ($Line -match '^([^#=]+)=([a-fA-F0-9]{64})$') {
            $File = Join-Path $Stage $Matches[1]
            if ((Get-FileHash -LiteralPath $File -Algorithm SHA256).Hash -ne $Matches[2]) { throw ('배포 파일 검증 실패: ' + $Matches[1]) }
        }
    }
    [IO.File]::WriteAllLines((Join-Path $Stage 'SHA256.properties'), $Lines, [Text.Encoding]::ASCII)
    & java '-Dfile.encoding=UTF-8' -jar (Join-Path $Root '설치도구.jar') 설치 $ServerDirectory $Stage --서버종료확인
    if ($LASTEXITCODE -ne 0) { throw '설치 도구가 오류를 보고했습니다. 표시된 기록을 확인하세요.' }
    Write-Host '설치 완료. 서버를 시작하고 동봉한 시작안내.md를 확인하세요.' -ForegroundColor Green
} finally {
    if (Test-Path -LiteralPath $Stage) { Remove-Item -LiteralPath $Stage -Recurse -Force }
}
