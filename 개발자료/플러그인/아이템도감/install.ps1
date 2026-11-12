param([string]$ServerDirectory)
$ErrorActionPreference = 'Stop'
if (!$ServerDirectory) { $ServerDirectory = (Read-Host 'server.properties가 있는 서버 폴더').Trim('"') }
$Root = (Resolve-Path -LiteralPath $ServerDirectory).Path
if (!(Test-Path -LiteralPath (Join-Path $Root 'server.properties'))) { throw '실제 서버 폴더가 아닙니다.' }
if ($PSScriptRoot.StartsWith($Root + [IO.Path]::DirectorySeparatorChar)) { throw '설치 ZIP은 서버 폴더 밖에 풀어 실행하세요.' }
$Payload = Join-Path $PSScriptRoot 'plugins\KoreanCodex-1.0.1.jar'
$Manifest = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'plugins\SHA256.properties'))
$Match = [regex]::Match($Manifest,'(?m)^KoreanCodex-1\.0\.1\.jar=([a-f0-9]{64})\s*$')
if (!$Match.Success -or (Get-FileHash -LiteralPath $Payload -Algorithm SHA256).Hash -ne $Match.Groups[1].Value) { throw '도감 JAR 검증 실패. 서버를 변경하지 않았습니다.' }
$Tool = Join-Path $PSScriptRoot '전체백업도구.jar'
$ToolManifest = [IO.File]::ReadAllText((Join-Path $PSScriptRoot '전체백업도구.sha256')).Trim()
if ((Get-FileHash -LiteralPath $Tool -Algorithm SHA256).Hash -ne $ToolManifest) { throw '전체 백업 도구 검증 실패' }
$Java = (Get-Command java -ErrorAction Stop).Source
$Stop = Join-Path $Root 'ServerStop.cmd'
if (!(Test-Path -LiteralPath $Stop)) { throw '기존 ServerStop.cmd를 찾지 못했습니다. 이 설치기는 기존 Windows 생활서버용입니다.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Plugin-Name([string]$Path) {
    $Zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $Entry = $Zip.GetEntry('plugin.yml'); if (!$Entry) { return '' }
        $Reader = New-Object IO.StreamReader($Entry.Open(), [Text.Encoding]::UTF8)
        try { $Text = $Reader.ReadToEnd() } finally { $Reader.Dispose() }
        return [regex]::Match($Text,'(?m)^name:[ \t]*([^\r\n]+)').Groups[1].Value.Trim().Trim([char[]]@(34,39))
    } finally { $Zip.Dispose() }
}
if ((Plugin-Name $Payload) -ne 'KoreanCodex') { throw '도감 플러그인이 아닌 파일입니다.' }
$Plugins = Join-Path $Root 'plugins'
$Existing = @(Get-ChildItem -LiteralPath $Plugins -Filter '*.jar' -File | Where-Object { (Plugin-Name $_.FullName) -eq 'KoreanCodex' })
Write-Host 'ServerStop.cmd로 서버와 백업 관리자를 종료하고 전체 백업을 만듭니다.' -ForegroundColor Yellow
& $Stop
if ($LASTEXITCODE -ne 0) { throw '정상 종료를 확인하지 못해 설치를 중단했습니다.' }
& $Java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -jar $Tool 백업 $Root --서버종료확인
if ($LASTEXITCODE -ne 0) { throw '전체 백업 또는 해시 검증 실패. 도감 JAR를 설치하지 않았습니다.' }
$Retired = Join-Path $Root ('tycoon-server-backups\도감-교체전-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $Retired | Out-Null
$Moved = @(); $Copied = $false; $Destination = Join-Path $Plugins 'KoreanCodex-1.0.1.jar'
try {
    foreach ($Old in $Existing) { Move-Item -LiteralPath $Old.FullName -Destination (Join-Path $Retired $Old.Name); $Moved += $Old }
    Copy-Item -LiteralPath $Payload -Destination $Destination -ErrorAction Stop; $Copied = $true
    if ((Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash -ne $Match.Groups[1].Value) { throw '복사 후 도감 JAR 검증 실패' }
} catch {
    if ($Copied) { Remove-Item -LiteralPath $Destination -Force }
    foreach ($Old in $Moved) { Move-Item -LiteralPath (Join-Path $Retired $Old.Name) -Destination $Old.FullName }
    throw
}
Write-Host '도감 JAR 한 개 설치 완료. 기존 ServerStart.cmd로 시작하세요.' -ForegroundColor Green
Write-Host '/도감 · /도감 등록 · /업적 · /도감효과. 기존 데이터 폴더와 다른 플러그인 JAR는 유지했습니다.'
