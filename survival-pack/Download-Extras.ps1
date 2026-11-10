param(
    [string]$MinecraftVersion = '26.3'
)
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Add-Type -AssemblyName System.IO.Compression.FileSystem
$PluginDirectory = Join-Path $PSScriptRoot 'plugins'
New-Item -ItemType Directory -Force -Path $PluginDirectory | Out-Null
$Headers = @{ 'User-Agent' = 'wjdtjddyd956-svg/economy-survival-pack/1.0' }
$Projects = @('luckperms', 'griefprevention', 'coreprotect')
$Failed = @()

# Download only matching stable releases from Modrinth, verify both host and hash,
# and stage the file until verification succeeds. Never remove installed files.
foreach ($Project in $Projects) {
    $Temporary = $null
    try {
        Write-Host "Checking $Project for Minecraft $MinecraftVersion ..."
        $GameFilter = [Uri]::EscapeDataString((ConvertTo-Json -Compress -InputObject @($MinecraftVersion)))
        $LoaderFilter = [Uri]::EscapeDataString('["paper","spigot","bukkit"]')
        $Url = "https://api.modrinth.com/v2/project/$Project/version?game_versions=$GameFilter&loaders=$LoaderFilter"
        $Versions = @(Invoke-RestMethod -Uri $Url -Headers $Headers)
        $Version = $Versions | Where-Object { $_.version_type -eq 'release' } |
            Sort-Object { [DateTime]$_.date_published } -Descending | Select-Object -First 1
        if (-not $Version) { throw "No stable release explicitly lists $MinecraftVersion. Check the official project page." }
        $File = $Version.files | Where-Object { $_.primary -and $_.filename -like '*.jar' } | Select-Object -First 1
        if (-not $File) { throw 'No primary plugin JAR found.' }
        $DownloadUri = [Uri]$File.url
        if ($DownloadUri.Scheme -ne 'https' -or $DownloadUri.Host -ne 'cdn.modrinth.com') { throw 'Unexpected download host.' }
        $Name = [IO.Path]::GetFileName([string]$File.filename)
        if ($Name -ne $File.filename) { throw 'Unexpected file name.' }
        $Destination = Join-Path $PluginDirectory $Name
        if (Test-Path $Destination) { throw "$Name already exists. Keep only one version per plugin; review it manually." }
        $Temporary = Join-Path $PluginDirectory ([IO.Path]::GetRandomFileName() + '.part')
        Invoke-WebRequest -UseBasicParsing -Uri $File.url -Headers $Headers -OutFile $Temporary
        $Expected = [string]$File.hashes.sha512
        if ($Expected.Length -ne 128) { throw 'No SHA-512 checksum in metadata.' }
        $Actual = (Get-FileHash -Path $Temporary -Algorithm SHA512).Hash
        if ($Actual -ine $Expected) { throw 'Checksum mismatch.' }
        $Archive = [IO.Compression.ZipFile]::OpenRead($Temporary)
        try {
            if (-not ($Archive.GetEntry('plugin.yml') -or $Archive.GetEntry('paper-plugin.yml'))) {
                throw 'The downloaded file is not a Bukkit/Paper plugin.'
            }
        } finally { $Archive.Dispose() }
        Move-Item -LiteralPath $Temporary -Destination $Destination
        $Temporary = $null
        Write-Host "OK: $Name" -ForegroundColor Green
    } catch {
        $Failed += $Project
        Write-Warning "$Project : $($_.Exception.Message)"
        Write-Host "Official project: https://modrinth.com/plugin/$Project"
    } finally {
        if ($Temporary -and (Test-Path -LiteralPath $Temporary)) { Remove-Item -LiteralPath $Temporary }
    }
}
if ($Failed.Count -gt 0) {
    Write-Warning "Not downloaded: $($Failed -join ', '). This is a partial result; do not assume all plugins are installed."
    exit 1
}
Write-Host 'All extra plugins downloaded. Stop the server before copying JARs into its plugins folder.'
