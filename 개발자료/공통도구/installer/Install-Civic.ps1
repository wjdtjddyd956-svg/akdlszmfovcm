param([string]$ServerDirectory)
$ErrorActionPreference = 'Stop'
if (!$ServerDirectory) { $ServerDirectory = (Read-Host 'server.properties가 있는 서버 폴더 경로').Trim('"') }
$ServerDirectory = (Resolve-Path -LiteralPath $ServerDirectory).Path
if (!(Test-Path -LiteralPath (Join-Path $ServerDirectory 'server.properties'))) { throw '실제 서버 폴더를 선택하세요.' }
$Java = (Get-Command java -ErrorAction Stop).Source
$Installer = Join-Path $PSScriptRoot 'CivicInstaller.jar'
$Expected = (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'CivicInstaller.sha256') -Raw).Trim()
if ((Get-FileHash -LiteralPath $Installer -Algorithm SHA256).Hash -ine $Expected) { throw '설치 도구 SHA-256이 다릅니다. ZIP을 다시 받으세요.' }
foreach ($Line in Get-Content -LiteralPath (Join-Path $PSScriptRoot 'plugins\SHA256.properties')) {
    if (!$Line.Trim()) { continue }
    $Parts = $Line.Split('=', 2)
    if ($Parts.Count -ne 2 -or $Parts[0] -notmatch '^[A-Za-z0-9.-]+\.jar$') { throw '플러그인 목록이 잘못되었습니다.' }
    if ((Get-FileHash -LiteralPath (Join-Path $PSScriptRoot ('plugins\' + $Parts[0])) -Algorithm SHA256).Hash -ine $Parts[1]) { throw ('플러그인 SHA-256이 다릅니다: ' + $Parts[0]) }
}
$StopScript = Join-Path $ServerDirectory 'ServerStop.cmd'
if (Test-Path -LiteralPath $StopScript) {
    Write-Host '서버와 백업 관리자를 정상 종료합니다.' -ForegroundColor Yellow
    & $StopScript
    if ($LASTEXITCODE -ne 0) { throw '정상 종료에 실패해 설치를 중단했습니다.' }
} else {
    Write-Host '서버 콘솔에 stop을 입력하고 Java 서버 프로세스와 백업 관리자를 완전히 종료하세요.' -ForegroundColor Yellow
    if ((Read-Host '종료가 끝났으면 종료확인 입력') -cne '종료확인') { throw '설치를 취소했습니다.' }
}
& $Java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -jar $Installer 마을설치 $ServerDirectory (Join-Path $PSScriptRoot 'plugins') --서버종료확인
if ($LASTEXITCODE -ne 0) { throw '설치를 완료하지 못했습니다. 위 오류와 기존 백업을 확인하세요.' }
Write-Host '마을·개인상점 설치 완료. 기존 실행기로 서버를 시작하세요. 쉬프트+F → 마을·땅 거래 / 개인상점.' -ForegroundColor Green
