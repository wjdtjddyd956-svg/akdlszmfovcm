param([string]$ServerDirectory)
if (!$ServerDirectory) { $ServerDirectory = Split-Path -Parent $PSScriptRoot }
. (Join-Path $PSScriptRoot 'LifeCommon.ps1')
$root = (Resolve-Path -LiteralPath $ServerDirectory).Path
$control = Join-Path $root 'life-control'
if (!(Test-Path -LiteralPath (Join-Path $control 'active.txt'))) {
    Assert-LifeOffline $root
    Write-Host '이미 서버가 종료되었습니다.'
    exit 0
}
Write-LifeUtf8 (Join-Path $control 'stop.request') ([DateTime]::UtcNow.ToString('o'))
$until = [DateTime]::UtcNow.AddMinutes(5)
while ((Test-Path -LiteralPath (Join-Path $control 'active.txt')) -and [DateTime]::UtcNow -lt $until) {
    Start-Sleep -Milliseconds 500
}
if (Test-Path -LiteralPath (Join-Path $control 'active.txt')) { throw '종료 대기 시간이 지났습니다. life-control/backup.log를 확인하세요.' }
Assert-LifeOffline $root
Write-Host '서버가 종료되었습니다.'
