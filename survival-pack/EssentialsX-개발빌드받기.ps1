param([string]$OutputDirectory = (Join-Path $PSScriptRoot 'EssentialsX-26.3-개발빌드'))
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Add-Type -AssemblyName System.IO.Compression.FileSystem
$Headers = @{ 'User-Agent' = 'Korean-Economy-Pack-26.3-Fix/1.0' }
$Job = 'https://ci.ender.zone/job/EssentialsX'
$Metadata = Invoke-RestMethod -Uri ($Job + '/lastSuccessfulBuild/api/json?tree=number,result,building,artifacts[fileName,relativePath]') -Headers $Headers
if ($Metadata.result -ne 'SUCCESS' -or $Metadata.building) { throw '성공한 공식 빌드 정보를 확인하지 못했습니다.' }
$Build = [int]$Metadata.number
if ($Build -lt 1) { throw '빌드 번호가 올바르지 않습니다.' }
$Core = @($Metadata.artifacts | Where-Object { $_.fileName -match '^EssentialsX-[^/\\]+\.jar$' -and $_.fileName -notmatch '(sources|javadoc|original)' })
$Spawn = @($Metadata.artifacts | Where-Object { $_.fileName -match '^EssentialsXSpawn-[^/\\]+\.jar$' -and $_.fileName -notmatch '(sources|javadoc|original)' })
if ($Core.Count -ne 1 -or $Spawn.Count -ne 1) { throw '같은 빌드의 본체와 스폰 JAR를 하나씩 찾지 못했습니다. 공식 다운로드 페이지를 확인하세요.' }
$Destination = Join-Path $OutputDirectory ('build-' + $Build)
if (Test-Path -LiteralPath $Destination) { throw "이미 받은 폴더입니다: $Destination" }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$Stage = Join-Path $OutputDirectory ([IO.Path]::GetRandomFileName() + '.temporary')
New-Item -ItemType Directory -Path $Stage | Out-Null
$Records = @()

function Read-Entry([IO.Compression.ZipArchiveEntry]$Entry) {
    if (-not $Entry) { throw 'JAR 안에서 필요한 파일을 찾지 못했습니다.' }
    $Reader = New-Object IO.StreamReader($Entry.Open(), [Text.Encoding]::UTF8)
    try { return $Reader.ReadToEnd() } finally { $Reader.Dispose() }
}
function Read-Field([string]$Text, [string]$Name) {
    $Match = [regex]::Match($Text, ('(?m)^' + [regex]::Escape($Name) + ':[ \t]*([^\r\n]+)'))
    if (-not $Match.Success) { throw "플러그인 메타데이터에 $Name 항목이 없습니다." }
    return $Match.Groups[1].Value.Trim().Trim([char[]]@(34,39))
}

try {
    foreach ($Artifact in @($Core[0], $Spawn[0])) {
        $Name = [string]$Artifact.fileName
        $Relative = [string]$Artifact.relativePath
        if ([IO.Path]::GetFileName($Name) -ne $Name -or $Relative -match '(^/|\\|(^|/)\.\.(/|$))') { throw '잘못된 파일 경로를 받았습니다.' }
        $Encoded = (($Relative -split '/') | ForEach-Object { [Uri]::EscapeDataString($_) }) -join '/'
        $Url = $Job + '/' + $Build + '/artifact/' + $Encoded
        $File = Join-Path $Stage $Name
        Write-Host "공식 개발 빌드 $Build 다운로드: $Name"
        Invoke-WebRequest -UseBasicParsing -Uri $Url -Headers $Headers -OutFile $File
        $Zip = [IO.Compression.ZipFile]::OpenRead($File)
        try {
            $Plugin = Read-Entry ($Zip.GetEntry('plugin.yml'))
            $PluginName = Read-Field $Plugin 'name'
            $Version = Read-Field $Plugin 'version'
            $Expected = if ($Artifact -eq $Core[0]) { 'Essentials' } else { 'EssentialsSpawn' }
            if ($PluginName -ne $Expected) { throw "예상한 플러그인이 아닙니다: $PluginName" }
            if ($Expected -eq 'Essentials') {
                $VersionClass = $Zip.GetEntry('com/earth2me/essentials/utils/VersionUtil.class')
                if (-not $VersionClass) { throw '버전 확인 클래스가 없습니다. 공식 배포 구조가 달라졌는지 확인하세요.' }
                $Stream = $VersionClass.Open()
                $Memory = New-Object IO.MemoryStream
                try { $Stream.CopyTo($Memory); $Bytes = $Memory.ToArray() } finally { $Stream.Dispose(); $Memory.Dispose() }
                $Constants = [Text.Encoding]::GetEncoding('ISO-8859-1').GetString($Bytes)
                if (-not $Constants.Contains('v26_3_R01') -or -not $Constants.Contains('26.3-R0.1-SNAPSHOT')) {
                    throw '이 본체 JAR에서 확인한 공식 26.3 지원 식별자가 없습니다. 기존 JAR는 교체하지 마세요.'
                }
            }
        } finally { $Zip.Dispose() }
        $Records += [PSCustomObject]@{ file=$Name; plugin=$PluginName; version=$Version; build=$Build; url=$Url; sha256=(Get-FileHash -LiteralPath $File -Algorithm SHA256).Hash }
    }
    if ($Records[0].version -ne $Records[1].version) { throw '본체와 스폰의 버전이 서로 다릅니다.' }
    $Records | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $Stage 'download-manifest.json') -Encoding UTF8
    Move-Item -LiteralPath $Stage -Destination $Destination
    Write-Host "검사한 JAR 2개를 받았습니다: $Destination" -ForegroundColor Green
    Write-Host '서버를 stop으로 종료하고 기존 EssentialsX·EssentialsXSpawn JAR만 백업한 뒤 이 두 파일로 교체하세요.'
    Write-Host 'plugins/Essentials 데이터 폴더는 지우지 마세요. 스크립트는 서버 폴더를 직접 변경하지 않습니다.'
    Write-Host '이는 공식 배포 출처·메타데이터·26.3 식별자 검사이며 실제 서버 동작 검증을 대신하지 않습니다.'
} catch {
    Write-Error $_
    throw
} finally {
    if (Test-Path -LiteralPath $Stage) { Remove-Item -LiteralPath $Stage -Recurse -Force }
}
