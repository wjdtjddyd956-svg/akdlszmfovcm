param(
    [string]$ServerPath,
    [string]$JavaPath = 'C:\Program Files\Java\jdk-27.0.1.0.3\bin\java.exe',
    [switch]$DownloadOnly,
    [switch]$CheckOnly
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$bundleRoot = $PSScriptRoot
$manifest = Get-Content -LiteralPath (Join-Path $bundleRoot 'files.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$vendorRoot = Join-Path $bundleRoot 'vendor-downloads'
New-Item -ItemType Directory -Force -Path $vendorRoot | Out-Null
function Get-Sha256([string]$File) {
    $stream = [IO.File]::OpenRead($File)
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($hasher.ComputeHash($stream))).Replace('-', '').ToLowerInvariant() }
    finally { $hasher.Dispose(); $stream.Dispose() }
}
function Assert-Hash([string]$File, [string]$Expected) {
    if (!(Test-Path -LiteralPath $File -PathType Leaf)) { throw "파일 누락: $File" }
    if ((Get-Sha256 $File) -ne $Expected) { throw "SHA-256 불일치: $File" }
}
function Get-Identity([string]$File) {
    $zip = [IO.Compression.ZipFile]::OpenRead($File)
    try {
        $entry = $zip.GetEntry('plugin.yml')
        if ($null -eq $entry) { $entry = $zip.GetEntry('paper-plugin.yml') }
        if ($null -eq $entry) { throw "플러그인 식별 정보가 없습니다: $File" }
        $reader = New-Object IO.StreamReader($entry.Open())
        try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $nameMatch = [regex]::Match($text, '(?m)^name:\s*[''"]?([^\r\n''"]+)')
        $versionMatch = [regex]::Match($text, '(?m)^version:\s*[''"]?([^\r\n''"]+)')
        if (!$nameMatch.Success -or !$versionMatch.Success) { throw "식별 정보 구문 오류: $File" }
        return @{ Name = $nameMatch.Groups[1].Value.Trim(); Version = $versionMatch.Groups[1].Value.Trim() }
    } finally { $zip.Dispose() }
}
foreach ($file in $manifest.bundled) { Assert-Hash (Join-Path $bundleRoot $file.path) $file.sha256 }
foreach ($file in $manifest.vendor) {
    $targetFile = Join-Path $vendorRoot $file.file
    if (!(Test-Path -LiteralPath $targetFile)) {
        $partialFile = $targetFile + '.partial'
        Write-Host "공식 배포판 다운로드: $($file.name) $($file.version)"
        try {
            Invoke-WebRequest -Uri $file.url -OutFile $partialFile -UseBasicParsing
            Assert-Hash $partialFile $file.sha256
            Move-Item -LiteralPath $partialFile -Destination $targetFile
        } finally { if (Test-Path -LiteralPath $partialFile) { Remove-Item -LiteralPath $partialFile } }
    }
    Assert-Hash $targetFile $file.sha256
    $identity = Get-Identity $targetFile
    if ($identity.Name -cne $file.name -or $identity.Version -cne $file.version) { throw "배포판 이름·버전 불일치: $targetFile" }
}
if ($DownloadOnly) { Write-Host '공식 배포판 5개 다운로드·이름·버전·SHA-256 확인 완료. 서버는 변경하지 않았습니다.'; exit 0 }
if (!$ServerPath) { $ServerPath = Read-Host 'server.properties가 있는 서버 폴더 전체 경로' }
$serverRoot = [IO.Path]::GetFullPath($ServerPath).TrimEnd('\')
if (!(Test-Path -LiteralPath (Join-Path $serverRoot 'server.properties'))) { throw '서버 폴더가 아닙니다.' }
if ($bundleRoot.StartsWith($serverRoot + '\', [StringComparison]::OrdinalIgnoreCase) -or $bundleRoot -eq $serverRoot) { throw '설치 묶음을 서버 폴더 밖에 풀어주세요.' }
foreach ($requiredFile in @('ServerStop.cmd','ServerStart.cmd','life-tools\Run-LifeServer.ps1')) {
    if (!(Test-Path -LiteralPath (Join-Path $serverRoot $requiredFile))) { throw "기존 정상 종료·백업 관리자 구성 누락: $requiredFile" }
}
$pluginRoot = Join-Path $serverRoot 'plugins'
$installed = @{}
foreach ($jarFile in Get-ChildItem -LiteralPath $pluginRoot -Filter '*.jar' -File) {
    $identity = Get-Identity $jarFile.FullName
    if ($installed.ContainsKey($identity.Name)) { throw "동일 플러그인 JAR 중복: $($identity.Name)" }
    $installed[$identity.Name] = @{ File = $jarFile.FullName; Version = $identity.Version }
}
foreach ($requiredPlugin in @('Vault','Essentials','EssentialsSpawn','KoreanEconomy','KoreanCodex','KoreanMenu','KoreanFarming','CityTextures','GriefPrevention','prism')) {
    if (!$installed.ContainsKey($requiredPlugin)) { throw "기존 필수 플러그인 누락: $requiredPlugin" }
}
if ($installed['KoreanEconomy'].Version -ne '2.0.2' -or $installed['KoreanCodex'].Version -ne '1.2.1' -or $installed['KoreanMenu'].Version -ne '2.1.0' -or $installed['KoreanFarming'].Version -ne '1.0.2' -or $installed['CityTextures'].Version -ne '1.1.1') {
    throw '검증한 조합(Economy 2.0.2 / Codex 1.2.1 / Menu 2.1.0 / Farming 1.0.2 / CityTextures 1.1.1)이 아닙니다. 기존 플러그인을 교체하지 않고 중단합니다.'
}
if ($installed['Essentials'].Version -ne $installed['EssentialsSpawn'].Version -or $installed['Essentials'].Version -match '^2\.22\.0($|[+-])') {
    throw 'EssentialsX·Spawn의 동일 호환 빌드를 먼저 확인해야 합니다. 기존 파일을 교체하거나 2.22.0으로 내리지 않습니다.'
}
$payload = @($manifest.vendor | ForEach-Object { @{ Name=$_.name; Version=$_.version; Source=(Join-Path $vendorRoot $_.file); Hash=$_.sha256 } })
$payload += @{ Name='KoreanServerBridge'; Version='1.0.0'; Source=(Join-Path $bundleRoot 'plugins\KoreanServerBridge-1.0.0.jar'); Hash=$manifest.bridgeSha256 }
foreach ($item in $payload) {
    Assert-Hash $item.Source $item.Hash
    if ($installed.ContainsKey($item.Name)) {
        # Different existing builds require review; never silently downgrade.
        Assert-Hash $installed[$item.Name].File $item.Hash
    }
}
foreach ($file in $manifest.configs) {
    $destinationFile = Join-Path $pluginRoot $file.path
    if (Test-Path -LiteralPath $destinationFile) {
        $currentHash = Get-Sha256 $destinationFile
        if ($currentHash -ne $file.sha256 -and $currentHash -ne $file.originalSha256) {
            throw "기존 설정이 달라 자동 덮어쓰기를 중단합니다: $($file.path). 원본을 유지하고 수동 병합하세요."
        }
    }
}
if ($CheckOnly) { Write-Host '배포 파일·설정 충돌·중복 JAR 사전 검사 통과. 서버는 변경하지 않았습니다.'; exit 0 }
if (!(Test-Path -LiteralPath $JavaPath)) { throw 'JavaPath에 기존 서버용 Java 25 이상 실행 파일을 지정하세요.' }
Write-Host '서버와 Windows 백업 관리자를 정상 종료합니다.'
& (Join-Path $serverRoot 'ServerStop.cmd')
if ($LASTEXITCODE -ne 0) { throw '정상 종료 확인 실패. 설치하지 않았습니다.' }
# Existing tool locks all worlds, makes a cold full-server ZIP and verifies every file.
& $JavaPath '-Dstdout.encoding=UTF-8' -jar (Join-Path $bundleRoot 'InterfaceInstaller.jar') '백업' $serverRoot '--서버종료확인'
if ($LASTEXITCODE -ne 0) { throw '전체 백업·검증 실패. 설치하지 않았습니다.' }
$addedFiles = New-Object 'System.Collections.Generic.List[string]'
$changedFiles = New-Object 'System.Collections.Generic.List[hashtable]'
$configBackupRoot = Join-Path $serverRoot ('plugin-integration-config-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
try {
    foreach ($item in $payload) {
        if ($installed.ContainsKey($item.Name)) { continue }
        $destinationFile = Join-Path $pluginRoot ([IO.Path]::GetFileName($item.Source))
        if (Test-Path -LiteralPath $destinationFile) { throw "대상 파일 충돌: $destinationFile" }
        $addedFiles.Add($destinationFile)
        Copy-Item -LiteralPath $item.Source -Destination $destinationFile
        Assert-Hash $destinationFile $item.Hash
    }
    foreach ($file in $manifest.configs) {
        $destinationFile = Join-Path $pluginRoot $file.path
        if (Test-Path -LiteralPath $destinationFile) {
            if ((Get-Sha256 $destinationFile) -eq $file.sha256) { continue }
            Assert-Hash $destinationFile $file.originalSha256
            $backupFile = Join-Path $configBackupRoot $file.path
            New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($backupFile)) | Out-Null
            Copy-Item -LiteralPath $destinationFile -Destination $backupFile
            $changedFiles.Add(@{ File=$destinationFile; Backup=$backupFile })
            Copy-Item -LiteralPath (Join-Path $bundleRoot ('config\' + $file.path)) -Destination $destinationFile -Force
            Assert-Hash $destinationFile $file.sha256
            continue
        }
        New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($destinationFile)) | Out-Null
        $addedFiles.Add($destinationFile)
        Copy-Item -LiteralPath (Join-Path $bundleRoot ('config\' + $file.path)) -Destination $destinationFile
        Assert-Hash $destinationFile $file.sha256
    }
} catch {
    # Roll back only files added by this invocation, before first boot. Existing data was never overwritten.
    foreach ($addedFile in $addedFiles) { if (Test-Path -LiteralPath $addedFile) { Remove-Item -LiteralPath $addedFile } }
    foreach ($changedFile in $changedFiles) { Copy-Item -LiteralPath $changedFile.Backup -Destination $changedFile.File -Force }
    throw
}
Write-Host '추가 플러그인 5종·연결 모듈·한국어 설정 설치 완료. 기존 JAR와 데이터는 유지했습니다.'
Write-Host '서버는 종료 상태입니다. ServerStart.cmd로 시작한 뒤 로그·일반 이용자 동작을 확인하세요.'
Write-Host 'BetterRTP·야생 이동은 검증 대기입니다. 설치하거나 활성화하지 않았습니다.'
