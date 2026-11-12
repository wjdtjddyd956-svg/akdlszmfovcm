param([string]$OutputDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$Catalog = Get-Content -LiteralPath (Join-Path $PSScriptRoot '모드목록.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($Catalog.대상버전 -cne '26.1.2') { throw '대상 버전이 다른 목록입니다.' }
if (!$OutputDirectory) { $OutputDirectory = Join-Path $PSScriptRoot '받은파일' }
$OutRoot = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $OutRoot -Force | Out-Null
$ModDirectory = Join-Path $OutRoot 'mods'
New-Item -ItemType Directory -Path $ModDirectory -Force | Out-Null
$Rows = @($Catalog.모드 | Where-Object { $_.상태 -ceq '기본구성' })
if ($Rows.Count -ne 6) { throw '검증한 기본 모드 6개의 목록이 필요합니다.' }
$KnownIds = @{ 'fabric-api' = $false; 'shulkerboxtooltip' = $false; 'appleskin' = $false; 'entityculling' = $false; 'notenoughanimations' = $false; 'modmenu' = $false }
foreach ($Entry in $Rows) {
    if (!$KnownIds.ContainsKey($Entry.식별자) -or $KnownIds[$Entry.식별자]) { throw '기본 모드 식별자가 알 수 없거나 중복됐습니다.' }
    $KnownIds[$Entry.식별자] = $true
}
$ExpectedNames = @($Rows | ForEach-Object { $_.한글파일 })
$ForeignFiles = @(Get-ChildItem -LiteralPath $ModDirectory -File -Filter '*.jar' | Where-Object { $_.Name -cnotin $ExpectedNames })
if ($ForeignFiles.Count) { throw '다른 모드 JAR가 있는 폴더입니다. 버전이 섞이지 않도록 새로운 출력 폴더를 선택하세요.' }
foreach ($Entry in $Rows) {
    if ($Entry.한글파일 -notmatch '^[\p{L}\p{N}._+-]+\.jar$' -or $Entry.SHA256 -notmatch '^[a-fA-F0-9]{64}$') { throw '파일 목록이 올바르지 않습니다.' }
    $Uri = [Uri]$Entry.다운로드주소
    if ($Uri.Scheme -cne 'https' -or $Uri.Host -ine 'github.com') { throw '공식 GitHub 배포 주소만 사용합니다.' }
    $File = Join-Path $ModDirectory $Entry.한글파일
    if (Test-Path -LiteralPath $File) {
        if ((Get-FileHash -LiteralPath $File -Algorithm SHA256).Hash -ieq $Entry.SHA256) {
            Write-Host ('확인됨: ' + $Entry.한글파일)
            continue
        }
        throw ('기존 파일 내용이 다릅니다. 자동으로 덮어쓰지 않습니다: ' + $File)
    }
    $Partial = $File + '.' + [Guid]::NewGuid().ToString('N') + '.part'
    try {
        Write-Host ('받는 중: ' + $Entry.한글이름)
        Invoke-WebRequest -Uri $Entry.다운로드주소 -OutFile $Partial -UseBasicParsing -Headers @{ 'User-Agent' = 'Korean-Minecraft-Mod-Pack/1.0 (github.com/wjdtjddyd956-svg/akdlszmfovcm)' }
        if ((Get-FileHash -LiteralPath $Partial -Algorithm SHA256).Hash -ine $Entry.SHA256) { throw ('다운로드 해시가 다릅니다: ' + $Entry.한글파일) }
        $Zip = [IO.Compression.ZipFile]::OpenRead($Partial)
        try {
            $MetaEntry = $Zip.GetEntry('fabric.mod.json')
            if (!$MetaEntry) { throw 'Fabric 모드가 아닙니다.' }
            $Reader = [IO.StreamReader]::new($MetaEntry.Open(), [Text.Encoding]::UTF8)
            try { $Meta = $Reader.ReadToEnd() | ConvertFrom-Json } finally { $Reader.Dispose() }
            if ($Meta.id -cne $Entry.식별자 -or $Meta.version -cne $Entry.확보버전) { throw '모드 식별자·버전이 목록과 다릅니다.' }
        } finally { $Zip.Dispose() }
        Move-Item -LiteralPath $Partial -Destination $File
    } finally {
        if (Test-Path -LiteralPath $Partial) { Remove-Item -LiteralPath $Partial -Force }
    }
}
$Pack = Join-Path $PSScriptRoot '모드한글팩-26.1.2-1.0.0.zip'
$PackDirectory = Join-Path $OutRoot 'resourcepacks'
New-Item -ItemType Directory -Path $PackDirectory -Force | Out-Null
$PackDestination = Join-Path $PackDirectory '모드한글팩-26.1.2-1.0.0.zip'
if (Test-Path -LiteralPath $PackDestination) {
    if ((Get-FileHash -LiteralPath $PackDestination -Algorithm SHA256).Hash -ine (Get-FileHash -LiteralPath $Pack -Algorithm SHA256).Hash) { throw '기존 한국어 팩과 내용이 달라 자동으로 덮어쓰지 않습니다.' }
} else { Copy-Item -LiteralPath $Pack -Destination $PackDestination }
Write-Host ('공식 파일 6개와 한국어 팩을 준비했습니다: ' + $OutRoot) -ForegroundColor Green
Write-Host '26.1.2 Fabric 프로필에서 사용하세요. Java 25 이상이 필요합니다.'
Write-Host '기존 게임·서버 폴더는 변경하지 않았습니다. 설치안내.md와 확인대기목록.md를 읽어주세요.'
