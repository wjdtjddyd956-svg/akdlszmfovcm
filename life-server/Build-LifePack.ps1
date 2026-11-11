param(
    [Parameter(Mandatory = $true)][string]$PaperServer,
    [Parameter(Mandatory = $true)][string]$JavaHome
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$server = (Resolve-Path -LiteralPath $PaperServer).Path
$javaHomePath = (Resolve-Path -LiteralPath $JavaHome).Path
$javac = Join-Path $javaHomePath 'bin/javac'
$java = Join-Path $javaHomePath 'bin/java'
$jar = Join-Path $javaHomePath 'bin/jar'
$out = Join-Path $repo '.build/life-package'
$classRoot = Join-Path $repo ('.build/classes-' + [Guid]::NewGuid().ToString('N'))
$vendor = Join-Path $repo '.build/life-vendor'
New-Item -ItemType Directory -Path $vendor,(Join-Path $out 'plugins'),$classRoot -Force | Out-Null

$gpRecord = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'dependencies.json') |
    ConvertFrom-Json | Where-Object { $_.file -eq 'GriefPrevention.jar' }
$gp = Join-Path $vendor $gpRecord.file
if (!(Test-Path -LiteralPath $gp)) { Invoke-WebRequest -UseBasicParsing -Uri $gpRecord.url -OutFile $gp }
if ((Get-FileHash -LiteralPath $gp -Algorithm $gpRecord.algorithm).Hash -ne $gpRecord.hash) { throw 'GriefPrevention SHA-512 mismatch' }
$vault = Join-Path $server 'plugins/Vault.jar'
$paper = Join-Path $server 'libraries/io/papermc/paper/paper-api/26.3.build.238-stable/paper-api-26.3.build.238-stable.jar'
foreach ($file in @($vault,$paper)) { if (!(Test-Path -LiteralPath $file)) { throw "Missing real build dependency: $file" } }
$libraries = Get-ChildItem -LiteralPath (Join-Path $server 'libraries') -File -Recurse -Filter '*.jar' |
    Where-Object { $_.Name -notlike 'paper-api-*' } | Select-Object -ExpandProperty FullName
$classpath = (@($paper,$vault,$gp) + @($libraries)) -join [IO.Path]::PathSeparator

function Compile-Java($Name, $Sources, $Destination, $Classpath) {
    $arguments = Join-Path $classRoot ($Name + '.args')
    $entries = @('--release','25','-encoding','UTF-8','-classpath',$Classpath,'-d',$Destination) + @($Sources)
    [IO.File]::WriteAllLines($arguments, [string[]]($entries | ForEach-Object { '"' + $_.Replace('\','/') + '"' }),
        (New-Object Text.UTF8Encoding($false)))
    & $javac "@$arguments"
    if ($LASTEXITCODE -ne 0) { throw "Compilation failed: $Name" }
}

foreach ($module in @(@('korean-economy','KoreanEconomy'),@('korean-menu','KoreanMenu'))) {
    $legacySource = Join-Path $repo ('archive/life-1.2/' + $module[0])
    $source = if (Test-Path -LiteralPath $legacySource) { $legacySource } else { Join-Path $repo $module[0] }
    $classes = Join-Path $classRoot $module[0]
    New-Item -ItemType Directory -Path $classes | Out-Null
    $sources = Get-ChildItem -LiteralPath (Join-Path $source 'src/main/java') -File -Recurse -Filter '*.java'
    Compile-Java $module[0] $sources.FullName $classes $classpath
    $resources = Join-Path $source 'src/main/resources'
    $version = (Select-String -LiteralPath (Join-Path $resources 'plugin.yml') -Pattern '^version: (\S+)$').Matches[0].Groups[1].Value
    $dest = Join-Path $out ('plugins/' + $module[1] + '-' + $version + '.jar')
    & $jar --create --file $dest -C $classes . -C $resources .
    if ($LASTEXITCODE -ne 0) { throw "Cannot create $dest" }
    "Built $dest against Paper 26.3 build 238, real Vault and GriefPrevention APIs"
}

$testSource = Join-Path $repo 'archive/life-1.2/korean-economy/src/test/java/kr/minecraft/survival/economy/LifeLedgerTest.java'
if (!(Test-Path -LiteralPath $testSource)) { $testSource = Join-Path $repo 'korean-economy/src/test/java/kr/minecraft/survival/economy/LifeLedgerTest.java' }
if (Test-Path -LiteralPath $testSource) {
    $testClasses = Join-Path $classRoot 'test'
    New-Item -ItemType Directory -Path $testClasses | Out-Null
    $economyClasses = Join-Path $classRoot 'korean-economy'
    Compile-Java 'ledger-test' @($testSource) $testClasses $economyClasses
    & $java -cp ($economyClasses + [IO.Path]::PathSeparator + $testClasses) kr.minecraft.survival.economy.LifeLedgerTest
    if ($LASTEXITCODE -ne 0) { throw 'Ledger regression test failed' }
}
