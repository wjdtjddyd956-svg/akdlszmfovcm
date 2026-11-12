param([string]$ServerDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# 한글 배포 파일은 그대로 두고, 검증된 설치기가 읽을 임시 폴더만 준비합니다.
$BundleRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$Manifest = Get-Content -LiteralPath (Join-Path $BundleRoot '설치목록.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($Manifest.형식 -ne 1) { throw '지원하지 않는 설치 목록입니다.' }
$KnownFiles = @{
    KoreanEconomy = 'KoreanEconomy-2.0.3.jar'
    KoreanMenu = 'KoreanMenu-2.2.0.jar'
    CityTextures = 'CityTextures-1.2.0.jar'
    KoreanCivic = 'KoreanCivic-1.0.0.jar'
    KoreanFarming = 'KoreanFarming-1.0.2.jar'
    KoreanCodex = 'KoreanCodex-1.2.1.jar'
}
if (@($Manifest.플러그인).Count -ne $KnownFiles.Count) { throw '필수 플러그인 6개가 필요합니다.' }
$Seen = @{}
foreach ($Entry in $Manifest.플러그인) {
    if (!$KnownFiles.ContainsKey($Entry.식별자) -or $Seen.ContainsKey($Entry.식별자)) { throw '알 수 없거나 중복된 플러그인입니다.' }
    $Seen[$Entry.식별자] = $true
    if ($Entry.설치파일명 -cne $KnownFiles[$Entry.식별자] -or $Entry.파일 -notmatch '^플러그인/[\p{L}\p{N}._-]+\.jar$') { throw '설치 파일 이름이 올바르지 않습니다.' }
    if ($Entry.SHA256 -notmatch '^[a-fA-F0-9]{64}$') { throw '파일 검증값이 올바르지 않습니다.' }
    if ((Get-FileHash -LiteralPath (Join-Path $BundleRoot $Entry.파일) -Algorithm SHA256).Hash -ine $Entry.SHA256) { throw ('파일이 손상됐습니다: ' + $Entry.파일) }
}
$Installer = Join-Path $BundleRoot '설치도구.jar'
if ($Manifest.설치도구.파일 -cne '설치도구.jar' -or $Manifest.설치도구.SHA256 -notmatch '^[a-fA-F0-9]{64}$') { throw '설치 도구 목록이 올바르지 않습니다.' }
if ((Get-FileHash -LiteralPath $Installer -Algorithm SHA256).Hash -ine $Manifest.설치도구.SHA256) { throw '설치 도구가 손상됐습니다.' }
$Java = (Get-Command java -ErrorAction Stop).Source
if (!$ServerDirectory) { $ServerDirectory = (Read-Host 'server.properties가 있는 서버 폴더 경로').Trim('"') }
$ServerDirectory = (Resolve-Path -LiteralPath $ServerDirectory).Path
if (!(Test-Path -LiteralPath (Join-Path $ServerDirectory 'server.properties'))) { throw '실제 서버 폴더를 선택하세요.' }
$ServerRoot = [IO.Path]::GetFullPath($ServerDirectory).TrimEnd([char[]]@([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar))
if ($BundleRoot.Equals($ServerRoot,[StringComparison]::OrdinalIgnoreCase) -or $BundleRoot.StartsWith($ServerRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw '압축은 서버 폴더 밖에 풀어 실행하세요.' }
$StopScript = Join-Path $ServerDirectory 'ServerStop.cmd'
if (Test-Path -LiteralPath $StopScript) {
    Write-Host '서버와 백업 관리자를 정상 종료합니다.' -ForegroundColor Yellow
    & $StopScript
    if ($LASTEXITCODE -ne 0) { throw '정상 종료에 실패해 설치를 중단했습니다.' }
} else {
    Write-Host '서버 콘솔에 stop을 입력하고 서버 Java 프로세스와 백업 관리자를 완전히 종료하세요.' -ForegroundColor Yellow
    if ((Read-Host '완전히 종료했으면 종료확인 입력') -cne '종료확인') { throw '설치를 취소했습니다.' }
}
$Stage = Join-Path ([IO.Path]::GetTempPath()) ('도시생활-설치-' + [Guid]::NewGuid().ToString('N'))

$StageCreated = $false
try {
    New-Item -ItemType Directory -Path $Stage -ErrorAction Stop | Out-Null
    $StageCreated = $true
    $Hashes = @()
    foreach ($Entry in $Manifest.플러그인) {
        Copy-Item -LiteralPath (Join-Path $BundleRoot $Entry.파일) -Destination (Join-Path $Stage $Entry.설치파일명)
        $Hashes += $Entry.설치파일명 + '=' + $Entry.SHA256
    }
    [IO.File]::WriteAllLines((Join-Path $Stage 'SHA256.properties'), [string[]]$Hashes, [Text.Encoding]::ASCII)
    & $Java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -jar $Installer 마을설치 $ServerDirectory $Stage --서버종료확인
    if ($LASTEXITCODE -ne 0) { throw '설치에 실패했습니다. 위 오류와 전체 서버 백업을 확인하세요.' }
    Write-Host '전체 백업과 마을·개인상점 설치가 끝났습니다. 기존 실행기로 서버를 시작하세요.' -ForegroundColor Green
    Write-Host '쉬프트+F → 마을·땅 거래 / 개인상점' -ForegroundColor Green
} finally {
    if ($StageCreated -and (Test-Path -LiteralPath $Stage)) { Remove-Item -LiteralPath $Stage -Recurse -Force }
}
