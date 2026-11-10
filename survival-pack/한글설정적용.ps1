param([Parameter(Mandatory=$true)][string]$ServerDirectory)
$ErrorActionPreference = 'Stop'
$ServerDirectory = (Resolve-Path -LiteralPath $ServerDirectory).Path
$Plugins = Join-Path $ServerDirectory 'plugins'
if (-not (Test-Path -LiteralPath $Plugins -PathType Container)) { throw '서버의 plugins 폴더를 찾을 수 없습니다.' }
$Backup = Join-Path $ServerDirectory ('korean-settings-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$Encoding = New-Object System.Text.UTF8Encoding($false)
New-Item -ItemType Directory -Path $Backup | Out-Null

function Backup-File([string]$Path, [string]$Relative) {
    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        $Target = Join-Path $Backup $Relative
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Target) | Out-Null
        if (-not (Test-Path -LiteralPath $Target)) { Copy-Item -LiteralPath $Path -Destination $Target }
    }
}

function Set-Language([string]$Relative, [string]$Key, [string]$Value) {
    $Path = Join-Path $Plugins $Relative
    if (-not (Test-Path -LiteralPath $Path)) {
        Write-Warning "$Relative 파일이 없습니다. 서버를 한 번 시작하고 종료한 뒤 다시 실행하세요."
        return
    }
    Backup-File $Path $Relative
    $Content = [IO.File]::ReadAllText($Path)
    $Pattern = '(?m)^#?[ \t]*' + [regex]::Escape($Key) + ':[^\r\n]*'
    if ([regex]::IsMatch($Content, $Pattern)) { $Content = [regex]::Replace($Content, $Pattern, ($Key + ': ' + $Value)) }
    else { $Content += "`r`n${Key}: $Value`r`n" }
    [IO.File]::WriteAllText($Path, $Content, $Encoding)
}

Write-Host '서버가 꺼진 상태에서 적용하세요. 이 스크립트는 서버를 자동 종료하지 않습니다.'
# Change language only. Preserve prices, balances, homes and other existing settings.
Set-Language 'Essentials/config.yml' 'locale' 'ko'
Set-Language 'Essentials/config.yml' 'per-player-locale' 'false'
Set-Language 'Essentials/config.yml' 'currency-symbol' "'원'"
Set-Language 'Essentials/config.yml' 'currency-symbol-suffix' 'true'
Set-Language 'QuickShop-Hikari/config.yml' 'game-language' 'ko_kr'
Set-Language 'QuickShop-Hikari/config.yml' 'use-crowdin-ota' 'false'
# Force all clients to Korean while preserving the surrounding YAML configuration.
$QuickShopPath = Join-Path $Plugins 'QuickShop-Hikari/config.yml'
if (Test-Path -LiteralPath $QuickShopPath) {
    $Content = [IO.File]::ReadAllText($QuickShopPath)
    $Pattern = '(?m)^enabled-languages:[^\r\n]*(?:\r?\n(?![A-Za-z0-9_-]+:)[^\r\n]*)*'
    if ([regex]::IsMatch($Content, $Pattern)) {
        $Content = [regex]::Replace($Content, $Pattern, "enabled-languages:`r`n  - ko_kr")
        [IO.File]::WriteAllText($QuickShopPath, $Content, $Encoding)
    } else { Write-Warning '언어 목록을 찾지 못했습니다. QuickShop 설정을 직접 확인하세요.' }
}

$Overrides = Join-Path $PSScriptRoot '한글설정'
foreach ($Source in Get-ChildItem -LiteralPath $Overrides -File -Recurse) {
    $Relative = $Source.FullName.Substring($Overrides.Length + 1)
    $Destination = Join-Path $Plugins $Relative
    Backup-File $Destination $Relative
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Destination) | Out-Null
    Copy-Item -LiteralPath $Source.FullName -Destination $Destination
}
Write-Host "한글 설정을 적용했습니다. 원본 백업: $Backup"
Write-Host '서버를 다시 시작하세요. JAR·유저 잔액·땅 보호 데이터는 변경하지 않았습니다.'
