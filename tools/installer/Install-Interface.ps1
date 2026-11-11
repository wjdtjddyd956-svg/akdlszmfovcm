param([string]$ServerDirectory)
$ErrorActionPreference = 'Stop'
if (!$ServerDirectory) { $ServerDirectory = (Read-Host 'server.properties가 있는 서버 폴더 경로').Trim('"') }
$ServerDirectory = (Resolve-Path -LiteralPath $ServerDirectory).Path
if (!(Test-Path -LiteralPath (Join-Path $ServerDirectory 'server.properties'))) { throw '실제 서버 폴더를 선택하세요.' }
$Java = (Get-Command java -ErrorAction Stop).Source
Write-Host '서버와 자동 백업 관리자를 정상 종료하세요. 전체 서버를 백업·검증한 뒤 메뉴·도감·도시팩·농사·경제 JAR 5개를 교체합니다. 기존 데이터 폴더는 유지합니다.' -ForegroundColor Yellow
if ((Read-Host 'ServerStop.cmd 또는 콘솔 stop으로 모두 종료했다면 종료 입력') -ne '종료') { throw '종료 확인을 받지 못해 설치를 중단했습니다.' }
& $Java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -jar (Join-Path $PSScriptRoot 'InterfaceInstaller.jar') 메뉴설치 $ServerDirectory (Join-Path $PSScriptRoot 'plugins') --서버종료확인
if ($LASTEXITCODE -ne 0) { throw '설치를 완료하지 못했습니다. 위 오류와 기존 백업을 확인하세요.' }
Write-Host '설치 완료. 기존 ServerStart.cmd로 시작하고 접속 시 통합팩을 허용하세요. 쉬프트+F로 시작합니다.' -ForegroundColor Green
