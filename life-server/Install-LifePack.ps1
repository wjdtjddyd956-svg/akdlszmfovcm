param(
    [Parameter(Mandatory = $true)][string]$ServerDirectory,
    [string]$DependencyCache = (Join-Path $PSScriptRoot 'download-cache')
)
. (Join-Path $PSScriptRoot 'LifeCommon.ps1')
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$root = (Resolve-Path -LiteralPath $ServerDirectory).Path
$plugins = Join-Path $root 'plugins'
if (!(Test-Path -LiteralPath $plugins -PathType Container)) { throw '기존 Paper 서버 폴더를 지정하세요.' }
$package = $PSScriptRoot
foreach ($row in Get-Content -LiteralPath (Join-Path $package 'SHA256SUMS.txt')) {
    if ($row -notmatch '^([0-9a-fA-F]{64})  ([^:]+)$') { throw '설치 ZIP 체크섬 목록이 올바르지 않습니다.' }
    $expected = $Matches[1]
    $relative = $Matches[2]
    if ($relative -match '(^[/\\]|(^|[/\\])\.\.?([/\\]|$))') { throw "잘못된 설치 경로: $relative" }
    if ((Get-FileHash -LiteralPath (Join-Path $package $relative) -Algorithm SHA256).Hash -ne $expected) { throw "설치 파일 체크섬 불일치: $relative" }
}
Assert-LifeOffline $root

function Plugin-Name([string]$Path) {
    $jar = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $file = $jar.GetEntry('plugin.yml')
        if (!$file) { $file = $jar.GetEntry('paper-plugin.yml') }
        if (!$file) { return $null }
        $reader = New-Object IO.StreamReader($file.Open())
        try { $match = [regex]::Match($reader.ReadToEnd(), '(?m)^name:[ \t]*([^\r\n#]+)') } finally { $reader.Dispose() }
        if ($match.Success) { return $match.Groups[1].Value.Trim().Trim([char[]]@(34,39)) }
    } finally { $jar.Dispose() }
}

$existing = @{}
foreach ($file in (Get-ChildItem -LiteralPath $plugins -File -Filter '*.jar')) {
    $name = Plugin-Name $file.FullName
    if (!$name) { continue }
    $existing[$name] = @($existing[$name] | Where-Object { $_ }) + @($file)
}
foreach ($base in @('Vault','WorldEdit','WorldGuard','QuickShop-Hikari')) {
    if (@($existing[$base] | Where-Object { $_ }).Count -ne 1) { throw "기존 $base 플러그인 JAR 하나가 필요합니다." }
}

$plan = @{}
$replaced = @()
$known = @{
    'GriefPrevention.jar' = 'GriefPrevention'; 'prism-paper-v4.5.jar' = 'prism';
    'item-nbt-api-plugin-2.16.1.jar' = 'NBTAPI';
    'EssentialsX-2.22.1-dev+28-f8724e2.jar' = 'Essentials'; 'EssentialsXSpawn-2.22.1-dev+28-f8724e2.jar' = 'EssentialsSpawn'
}
New-Item -ItemType Directory -Path $DependencyCache -Force | Out-Null
foreach ($dependency in (Get-Content -Raw -LiteralPath (Join-Path $package 'dependencies.json') | ConvertFrom-Json)) {
    $id = $known[$dependency.file]
    if (!$id -or $dependency.url -notmatch '^https://(cdn\.modrinth\.com|ci\.ender\.zone)/' -or $dependency.algorithm -notin @('SHA256','SHA512')) {
        throw '의존 플러그인 목록이 올바르지 않습니다.'
    }
    $already = @($existing[$id] | Where-Object { $_ })
    if ($already.Count -eq 1 -and (Get-FileHash -LiteralPath $already[0].FullName -Algorithm $dependency.algorithm).Hash -eq $dependency.hash) {
        Write-Host "검증된 기존 플러그인 유지: $id $($dependency.version)"
        continue
    }
    $cache = Join-Path $DependencyCache $dependency.file
    if (!(Test-Path -LiteralPath $cache) -or (Get-FileHash -LiteralPath $cache -Algorithm $dependency.algorithm).Hash -ne $dependency.hash) {
        $partial = $cache + '.partial'
        Invoke-WebRequest -UseBasicParsing -Uri $dependency.url -OutFile $partial -TimeoutSec 60
        if ((Get-FileHash -LiteralPath $partial -Algorithm $dependency.algorithm).Hash -ne $dependency.hash) {
            throw "공식 JAR 체크섬 불일치: $($dependency.file)"
        }
        Copy-Item -LiteralPath $partial -Destination $cache -Force
        [IO.File]::Delete($partial)
    }
    if ((Plugin-Name $cache) -ne $id) { throw "플러그인 식별자 불일치: $cache" }
    $replaced += $already
    $plan["plugins/$($dependency.file)"] = $cache
}
foreach ($file in (Get-ChildItem -LiteralPath (Join-Path $package 'plugins') -File -Filter '*.jar')) {
    $id = Plugin-Name $file.FullName
    if ($id -in @('KoreanCommands','KoreanPackSetup') -and $existing[$id]) { continue }
    if ($id -notin @('KoreanEconomy','KoreanMenu','KoreanCommands','KoreanPackSetup')) { throw "예상하지 않은 설치 JAR: $($file.Name)" }
    $replaced += @($existing[$id] | Where-Object { $_ })
    $plan["plugins/$($file.Name)"] = $file.FullName
}

function Set-Scalar([string]$Text, [string]$Name, [string]$Separator, [string]$Value) {
    $rule = '(?m)^' + [regex]::Escape($Name + $Separator) + '[^\r\n]*'
    $line = $Name + $Separator + $Value
    if ([regex]::IsMatch($Text,$rule)) {
        return [regex]::Replace($Text,$rule,[Text.RegularExpressions.MatchEvaluator]{ param($m) $line })
    }
    return $Text.TrimEnd() + [Environment]::NewLine + $line + [Environment]::NewLine
}

$id = [Guid]::NewGuid().ToString('N')
$backupRoot = Get-LifeBackupRoot $root ''
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
$stage = Join-Path $backupRoot "install-$id"
New-Item -ItemType Directory -Path $stage | Out-Null
$properties = [IO.File]::ReadAllText((Join-Path $root 'server.properties'))
foreach ($setting in @(@('max-players','10'),@('view-distance','8'),@('simulation-distance','5'))) {
    $properties = Set-Scalar $properties $setting[0] '=' $setting[1]
}
$propertiesFile = Join-Path $stage 'server.properties'
Write-LifeUtf8 $propertiesFile $properties
$plan['server.properties'] = $propertiesFile

$essentialsPath = Join-Path $plugins 'Essentials/config.yml'
if (Test-Path -LiteralPath $essentialsPath) {
    $essentials = [IO.File]::ReadAllText($essentialsPath)
    foreach ($setting in @(@('starting-balance',' 1000'),@('currency-symbol'," '원'"),@('currency-symbol-suffix',' true'),
            @('locale',' ko'),@('per-player-locale',' false'))) {
        $essentials = Set-Scalar $essentials $setting[0] ':' $setting[1]
    }
    $essentialsFile = Join-Path $stage 'Essentials.yml'
    Write-LifeUtf8 $essentialsFile $essentials
    $plan['plugins/Essentials/config.yml'] = $essentialsFile
}
foreach ($name in @('config','messages')) {
    $relative = "plugins/GriefPreventionData/$name.yml"
    if (!(Test-Path -LiteralPath (Join-Path $root $relative))) {
        $plan[$relative] = Join-Path $package "GriefPrevention-$name.yml"
    }
}
foreach ($name in @('Run-LifeServer.ps1','Stop-LifeServer.ps1','Restore-LifeBackup.ps1','LifeCommon.ps1','backup.json')) {
    $relative = "life-tools/$name"
    if ($name -eq 'backup.json' -and (Test-Path -LiteralPath (Join-Path $root $relative))) { continue }
    $plan[$relative] = Join-Path $package $name
}
foreach ($name in @('ServerStart.cmd','ServerStop.cmd')) { $plan[$name] = Join-Path $package $name }

$control = Join-Path $root 'life-control'
New-Item -ItemType Directory -Path $control -Force | Out-Null
$lock = [IO.File]::Open((Join-Path $control 'manager.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
$archive = Join-Path $backupRoot ('pre-life-install-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + $id.Substring(0,6) + '.zip')
$saved = Join-Path $stage 'snapshot'
$originals = Join-Path $stage 'original-files'
$priorJars = Join-Path $stage 'original-jars'
$changes = New-Object 'Collections.Generic.List[string]'
$moved = New-Object 'Collections.Generic.List[string]'
try {
    New-LifeSnapshot $root $saved
    [IO.Compression.ZipFile]::CreateFromDirectory($saved,$archive,[IO.Compression.CompressionLevel]::Fastest,$false)
    $check = [IO.Compression.ZipFile]::OpenRead($archive)
    try { if (!$check.GetEntry('life-snapshot.json')) { throw '설치 전 백업 검증 실패' } } finally { $check.Dispose() }
    Write-LifeUtf8 ($archive + '.sha256') (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
    # Keep originals of changed files until every replacement is copied successfully.
    New-Item -ItemType Directory -Path $priorJars,$originals | Out-Null
    foreach ($file in $replaced) {
        Move-Item -LiteralPath $file.FullName -Destination $priorJars
        $moved.Add($file.Name)
    }
    foreach ($relative in $plan.Keys) {
        $dest = Join-Path $root $relative
        $before = Join-Path $originals $relative
        if (Test-Path -LiteralPath $dest) {
            if ($relative -like 'plugins/*.jar') { throw "다른 플러그인이 이미 이 이름을 사용합니다: $relative" }
            New-Item -ItemType Directory -Path (Split-Path -Parent $before) -Force | Out-Null
            Copy-Item -LiteralPath $dest -Destination $before
        }
        New-Item -ItemType Directory -Path (Split-Path -Parent $dest) -Force | Out-Null
        $changes.Add($relative)
        Copy-Item -LiteralPath $plan[$relative] -Destination $dest -Force
        if ((Get-FileHash -LiteralPath $dest).Hash -ne (Get-FileHash -LiteralPath $plan[$relative]).Hash) { throw "설치 파일 검증 실패: $relative" }
    }
} catch {
    foreach ($relative in $changes) {
        $dest = Join-Path $root $relative
        $before = Join-Path $originals $relative
        if (Test-Path -LiteralPath $before) { Copy-Item -LiteralPath $before -Destination $dest -Force }
        elseif (Test-Path -LiteralPath $dest) { [IO.File]::Delete($dest) }
    }
    foreach ($name in $moved) { Move-Item -LiteralPath (Join-Path $priorJars $name) -Destination $plugins }
    throw
} finally { $lock.Dispose() }

if ((Resolve-Path -LiteralPath $saved).Path -eq (Join-Path $stage 'snapshot')) { [IO.Directory]::Delete($saved,$true) }
Write-Host "설치 완료. 설치 전 백업: $archive"
Write-Host "서버 시작: $(Join-Path $root 'ServerStart.cmd')"
