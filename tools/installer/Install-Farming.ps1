param([string]$ServerDirectory)
$ErrorActionPreference = 'Stop'
if (!$ServerDirectory) { $ServerDirectory = (Read-Host 'server.properties가 있는 서버 폴더 경로').Trim('"') }
$ServerDirectory = (Resolve-Path -LiteralPath $ServerDirectory).Path
if (!(Test-Path -LiteralPath (Join-Path $ServerDirectory 'server.properties'))) { throw '실제 서버 폴더를 선택하세요.' }
$Java = (Get-Command java -ErrorAction Stop).Source
$Installer = Join-Path $PSScriptRoot 'FarmInstaller.jar'
$Expected = (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'FarmInstaller.sha256') -Raw).Trim()
if ((Get-FileHash -LiteralPath $Installer -Algorithm SHA256).Hash -ine $Expected) { throw '설치 도구 SHA-256이 다릅니다. ZIP을 다시 받으세요.' }
foreach ($Line in Get-Content -LiteralPath (Join-Path $PSScriptRoot 'plugins\SHA256.properties')) {
    if (!$Line.Trim()) { continue }
    $Parts = $Line.Split('=', 2)
    if ($Parts.Count -ne 2 -or $Parts[0] -notmatch '^[A-Za-z0-9.-]+\.jar$') { throw '플러그인 목록이 잘못되었습니다.' }
    if ((Get-FileHash -LiteralPath (Join-Path $PSScriptRoot ('plugins\' + $Parts[0])) -Algorithm SHA256).Hash -ine $Parts[1]) { throw ('플러그인 SHA-256이 다릅니다: ' + $Parts[0]) }
}
$StopScript = Join-Path $ServerDirectory 'ServerStop.cmd'
if (!(Test-Path -LiteralPath $StopScript)) { throw 'ServerStop.cmd가 없습니다. 기존 Windows 백업 관리자의 정상 종료 파일이 있는 서버 폴더를 선택하세요.' }
Write-Host 'ServerStop.cmd로 서버와 백업 관리자를 종료합니다. 전체 백업의 SHA-256 검증 후 경제·농사·도감 JAR 3개만 교체합니다.' -ForegroundColor Yellow
& $StopScript
if ($LASTEXITCODE -ne 0) { throw '정상 종료에 실패해 설치를 중단했습니다.' }
& $Java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -jar (Join-Path $PSScriptRoot 'FarmInstaller.jar') 농장설치 $ServerDirectory (Join-Path $PSScriptRoot 'plugins') --서버종료확인
if ($LASTEXITCODE -ne 0) { throw '설치를 완료하지 못했습니다. 위 오류와 기존 백업을 확인하세요.' }
Write-Host '설치 완료. 기존 ServerStart.cmd로 시작하고 농장 리소스팩을 허용하세요. 농산물·잡화 NPC 우클릭 → 씨앗 구매 → 젖은 경작지에 심기 → 우클릭 수확. 쉬프트+F에서 농장·요리·도감을 이용하세요.' -ForegroundColor Green
