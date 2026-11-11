param([string]$ServerDirectory)
if (!$ServerDirectory) { $ServerDirectory = Split-Path -Parent $PSScriptRoot }
. (Join-Path $PSScriptRoot 'LifeCommon.ps1')
$root = (Resolve-Path -LiteralPath $ServerDirectory).Path
$options = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'backup.json') | ConvertFrom-Json
if ($options.intervalHours -lt 0.01 -or $options.keep -lt 1 -or $options.keep -gt 365 -or
    $options.warningSeconds -lt 0 -or $options.warningSeconds -gt 600 -or
    $options.initialHeapMiB -lt 256 -or $options.maxHeapMiB -lt $options.initialHeapMiB) {
    throw 'backup.json의 백업 주기, 개수, 경고 시간, 메모리를 확인하세요.'
}
$control = Join-Path $root 'life-control'
$backupRoot = Get-LifeBackupRoot $root $options.directory
New-Item -ItemType Directory -Path $control,$backupRoot -Force | Out-Null
$lock = [IO.File]::Open((Join-Path $control 'manager.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
$status = Join-Path $control 'status.txt'
$history = Join-Path $control 'backup.log'
$active = Join-Path $control 'active.txt'
$stopRequest = Join-Path $control 'stop.request'
$backupRequest = Join-Path $control 'backup.request'
$javaProcess = $null
$compression = $null
$hostId = Get-LifeId $root

function Status([string]$Message) {
    $line = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz') + ' ' + $Message
    Write-LifeUtf8 $status $line
    [IO.File]::AppendAllText($history, $line + [Environment]::NewLine, (New-Object Text.UTF8Encoding($false)))
    Write-Host $line
}

function Start-Paper {
    Assert-LifeOffline $root
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $javaExe
    $info.WorkingDirectory = $root
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.Arguments = "-Dkoreanlife.managed=true -Xms$($options.initialHeapMiB)M -Xmx$($options.maxHeapMiB)M -jar `"$paperName`""
    if (!$options.gui) { $info.Arguments += ' nogui' }
    $process = [Diagnostics.Process]::Start($info)
    Write-LifeUtf8 $active ([string]$process.Id)
    return $process
}

function Send-Paper([string]$Command) {
    if (!$javaProcess.HasExited) {
        $bytes = [Text.Encoding]::UTF8.GetBytes($Command + [Environment]::NewLine)
        $inputStream = $javaProcess.StandardInput.BaseStream
        $inputStream.Write($bytes, 0, $bytes.Length)
        $inputStream.Flush()
    }
}

function Wait-PaperReady {
    $deadline = [DateTime]::UtcNow.AddMinutes(5)
    $log = Join-Path $root 'logs/latest.log'
    while (!$javaProcess.HasExited -and [DateTime]::UtcNow -lt $deadline) {
        if ((Test-Path -LiteralPath $log) -and (Get-Item -LiteralPath $log).LastWriteTimeUtc -ge $javaProcess.StartTime.ToUniversalTime() -and
            (Select-String -LiteralPath $log -Pattern 'Done \([0-9.]+s\)! For help' -Quiet)) { return }
        if (Test-Path -LiteralPath $stopRequest) { return }
        Start-Sleep -Milliseconds 500
    }
    throw 'Paper 기동 완료를 확인하지 못했습니다. logs/latest.log를 확인하세요.'
}

function Stop-Paper {
    if ($javaProcess.HasExited) { throw 'Paper가 정상 종료 명령 전에 중단되었습니다.' }
    Send-Paper 'save-all flush'
    Send-Paper 'stop'
    if (!$javaProcess.WaitForExit(180000)) { throw 'Paper 종료가 지연되었습니다. 이 상태에서는 월드를 백업할 수 없습니다.' }
    $log = Join-Path $root 'logs/latest.log'
    if ($javaProcess.ExitCode -ne 0 -or !(Select-String -LiteralPath $log -Pattern 'Stopping server' -Quiet) -or
        !(Select-String -LiteralPath $log -Pattern 'All RegionFile I/O tasks to complete' -Quiet)) {
        throw '월드 저장과 정상 종료가 로그에 확인되지 않았습니다.'
    }
}

function Complete-Compression([bool]$Wait) {
    if ($null -eq $script:compression) { return }
    if (!$Wait -and $script:compression.State -in @('Running','NotStarted')) { return }
    try {
        $result = Receive-Job -Job $script:compression -Wait -ErrorAction Stop
        if ($script:compression.State -ne 'Completed' -or !$result) { throw '압축 작업이 완료되지 않았습니다.' }
        Status "백업 성공: $result"
    } catch { Status "백업 실패: $($_.Exception.Message)" }
    finally {
        Remove-Job -Job $script:compression -Force
        $script:compression = $null
    }
}

try {
    Assert-LifeOffline $root
    foreach ($stale in @($stopRequest,$backupRequest,$active)) {
        if (Test-Path -LiteralPath $stale) { [IO.File]::Delete($stale) }
    }
    $javaExe = $options.javaExe
    if (!$javaExe) {
        $installed = Get-ChildItem -Path (Join-Path $env:ProgramFiles 'Java/jdk-*/bin/java.exe') -File -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match 'jdk-(\d+)' -and [int]$Matches[1] -ge 25 } |
            Sort-Object {
                ($_.Directory.Parent.Name.Substring(4) -split '\D+' | Where-Object { $_ } |
                    ForEach-Object { '{0:D8}' -f [int]$_ }) -join '.'
            } -Descending | Select-Object -First 1
        $javaExe = if ($installed) { $installed.FullName } else { (Get-Command java.exe -ErrorAction Stop).Source }
    }
    $paperName = $options.paperJar
    if (!$paperName) {
        $candidates = @(Get-ChildItem -LiteralPath $root -File -Filter 'paper-26.3-*.jar')
        if ($candidates.Count -ne 1) { throw 'backup.json의 paperJar에 사용할 Paper 26.3 JAR 파일명을 지정하세요.' }
        $paperName = $candidates[0].Name
    }
    if ($paperName -notmatch '^[\w.+-]+\.jar$' -or !(Test-Path -LiteralPath (Join-Path $root $paperName))) { throw 'Paper JAR 파일명을 확인하세요.' }

    $javaProcess = Start-Paper
    Wait-PaperReady
    $next = [DateTime]::UtcNow.AddHours($options.intervalHours)
    Status "Paper 시작. 다음 자동 백업: $($next.ToLocalTime().ToString('yyyy-MM-dd HH:mm'))"
    while (!$javaProcess.HasExited) {
        Complete-Compression $false
        if (Test-Path -LiteralPath $stopRequest) {
            [IO.File]::Delete($stopRequest)
            Stop-Paper
            break
        }
        if ($null -eq $compression -and ((Test-Path -LiteralPath $backupRequest) -or [DateTime]::UtcNow -ge $next)) {
            if (Test-Path -LiteralPath $backupRequest) { [IO.File]::Delete($backupRequest) }
            if ($options.warningSeconds -gt 0) {
                Send-Paper "say $($options.warningSeconds)초 후 월드 백업을 위해 잠시 서버를 재시작합니다."
                $deadline = [DateTime]::UtcNow.AddSeconds($options.warningSeconds)
                while ([DateTime]::UtcNow -lt $deadline -and !$javaProcess.HasExited -and !(Test-Path -LiteralPath $stopRequest)) {
                    Start-Sleep -Milliseconds 500
                }
                if (Test-Path -LiteralPath $stopRequest) { continue }
            }
            $stamp = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')
            $zip = Join-Path $backupRoot ("life-$hostId-$stamp.zip")
            $stage = Join-Path $backupRoot ('.snapshot-' + [Guid]::NewGuid().ToString('N'))
            try {
                Status '백업 준비: 월드와 플러그인 데이터를 저장합니다.'
                Stop-Paper
                New-LifeSnapshot $root $stage
                $compression = Start-Job -ArgumentList $stage,$zip,$backupRoot,$hostId,([int]$options.keep) -ScriptBlock {
                    param($Stage,$Zip,$Root,$Id,$Keep)
                    $ErrorActionPreference = 'Stop'
                    Add-Type -AssemblyName System.IO.Compression.FileSystem
                    $partial = $Zip + '.partial'
                    try {
                        [IO.Compression.ZipFile]::CreateFromDirectory($Stage,$partial,[IO.Compression.CompressionLevel]::Fastest,$false)
                        $archive = [IO.Compression.ZipFile]::OpenRead($partial)
                        try {
                            if (!$archive.GetEntry('life-snapshot.json') -or !$archive.GetEntry('server.properties')) {
                                throw '백업에 필수 파일이 없습니다.'
                            }
                        } finally { $archive.Dispose() }
                        $hasher = [Security.Cryptography.SHA256]::Create()
                        $stream = [IO.File]::OpenRead($partial)
                        try { $hash = ([BitConverter]::ToString($hasher.ComputeHash($stream))).Replace('-','') }
                        finally { $stream.Dispose(); $hasher.Dispose() }
                        [IO.File]::Move($partial,$Zip)
                        [IO.File]::WriteAllText($Zip + '.sha256',$hash)
                        $archives = [IO.Directory]::GetFiles($Root, "life-$Id-*.zip")
                        [Array]::Sort($archives,[StringComparer]::OrdinalIgnoreCase)
                        $count = 0
                        for ($i = $archives.Length - 1; $i -ge 0; $i--) {
                            $older = $archives[$i]
                            if (![IO.File]::Exists($older + '.sha256')) { continue }
                            $count++
                            if ($count -le $Keep) { continue }
                            [IO.File]::Delete($older)
                            [IO.File]::Delete($older + '.sha256')
                        }
                        $resolved = [IO.Path]::GetFullPath($Stage)
                        if ([IO.Directory]::GetParent($resolved).FullName -eq $Root -and [IO.Path]::GetFileName($resolved) -match '^\.snapshot-[a-f0-9]{32}$' -and
                            !([IO.File]::GetAttributes($resolved) -band [IO.FileAttributes]::ReparsePoint)) {
                            [IO.Directory]::Delete($resolved,$true)
                        }
                        $Zip
                    } catch {
                        if ([IO.File]::Exists($partial)) { [IO.File]::Delete($partial) }
                        throw
                    }
                }
            } catch { Status "백업 실패: $($_.Exception.Message)" }
            if (!$javaProcess.HasExited) { throw '이전 Paper 프로세스가 남아 있어 재기동을 중단합니다.' }
            if (Test-Path -LiteralPath $stopRequest) { [IO.File]::Delete($stopRequest); break }
            $javaProcess.Dispose()
            $javaProcess = Start-Paper
            Wait-PaperReady
            $next = [DateTime]::UtcNow.AddHours($options.intervalHours)
        }
        Start-Sleep -Milliseconds 500
    }
} catch {
    Status "서버 관리자 오류: $($_.Exception.Message)"
    throw
} finally {
    Complete-Compression $true
    if (Test-Path -LiteralPath $active) { [IO.File]::Delete($active) }
    if ($javaProcess) { $javaProcess.Dispose() }
    $lock.Dispose()
}
