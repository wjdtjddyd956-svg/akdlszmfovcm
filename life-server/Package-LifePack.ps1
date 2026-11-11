$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$stage = Join-Path $repo '.build/life-package'
$plugins = Join-Path $stage 'plugins'
$release = Join-Path $repo 'releases/survival-pack'
foreach ($required in @('KoreanEconomy-1.2.0.jar','KoreanMenu-1.2.0.jar')) {
    if (!(Test-Path -LiteralPath (Join-Path $plugins $required))) { throw 'Build-LifePack.ps1부터 실행하세요.' }
}
foreach ($name in @('KoreanCommands-1.1.0.jar','KoreanPackSetup-1.0.0.jar')) {
    Copy-Item -LiteralPath (Join-Path $release $name) -Destination $plugins -Force
}
foreach ($name in @('Install-LifePack.ps1','Run-LifeServer.ps1','Stop-LifeServer.ps1','Restore-LifeBackup.ps1','LifeCommon.ps1')) {
    $body = [IO.File]::ReadAllText((Join-Path $PSScriptRoot $name))
    [IO.File]::WriteAllText((Join-Path $stage $name),$body,(New-Object Text.UTF8Encoding($true)))
}
foreach ($name in @('README.md','명령어.md','검증.md','dependencies.json','backup.json','GriefPrevention-config.yml',
        'GriefPrevention-messages.yml','ServerStart.cmd','ServerStop.cmd')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $stage -Force
}
Copy-Item -LiteralPath (Join-Path $repo 'korean-economy/src/main/resources/config.yml') -Destination (Join-Path $stage 'KoreanEconomy-defaults.yml') -Force
$hashes = Get-ChildItem -LiteralPath $stage -File -Recurse | Where-Object Name -ne 'SHA256SUMS.txt' | Sort-Object FullName |
    ForEach-Object { (Get-FileHash -LiteralPath $_.FullName).Hash.ToLowerInvariant() + '  ' + $_.FullName.Substring($stage.Length + 1).Replace('\','/') }
[IO.File]::WriteAllLines((Join-Path $stage 'SHA256SUMS.txt'),[string[]]$hashes,(New-Object Text.UTF8Encoding($true)))
Add-Type -AssemblyName System.IO.Compression.FileSystem
$target = Join-Path $release 'Korean-Life-Server-1.2.0.zip'
$temp = $target + '.' + [Guid]::NewGuid().ToString('N') + '.partial'
[IO.Compression.ZipFile]::CreateFromDirectory($stage,$temp,[IO.Compression.CompressionLevel]::Optimal,$false)
Copy-Item -LiteralPath $temp -Destination $target -Force
[IO.File]::Delete($temp)
$digest = (Get-FileHash -LiteralPath $target).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $release 'SHA256SUMS-Korean-Life-Server.txt'),"$digest  Korean-Life-Server-1.2.0.zip`n")
Get-Item -LiteralPath $target | Select-Object FullName,Length
