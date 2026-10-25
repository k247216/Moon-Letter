param([switch]$ShowSetupKey, [switch]$Restart, [string]$JarPath)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runtimeDir = Join-Path $projectRoot 'infra/local-runtime'
New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
$secretFile = Join-Path $runtimeDir 'bootstrap-secret.clixml'
if (-not (Test-Path $secretFile)) {
    $bytes = New-Object byte[] 24
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $secret = [Convert]::ToBase64String($bytes)
    ConvertTo-SecureString $secret -AsPlainText -Force | Export-Clixml -LiteralPath $secretFile
}
$secure = Import-Clixml -LiteralPath $secretFile
$setupSecret = [System.Net.NetworkCredential]::new('', $secure).Password
if ($ShowSetupKey) { Write-Output $setupSecret; return }
$running = $false
try { $running = (Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 3).status -eq 'UP' } catch {}
if ($running -and -not $Restart) { Write-Output 'Local server already UP on port 8080.'; return }
$jar = if ($JarPath) { (Resolve-Path -LiteralPath $JarPath).Path } else { Join-Path $projectRoot 'server/target/moon-letter-server-0.1.0-SNAPSHOT.jar' }
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw 'Build the server jar first.' }
$allowedJarRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'server/target')) + [IO.Path]::DirectorySeparatorChar
if (-not ([IO.Path]::GetFullPath($jar).StartsWith($allowedJarRoot, [StringComparison]::OrdinalIgnoreCase))) { throw 'Jar must be under this project server/target.' }
$serverToRestart = $null
if ($Restart) {
    $listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) {
        $candidate = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
        if ($candidate.Name -ne 'java.exe' -or $candidate.CommandLine -notlike "*$projectRoot*server*moon-letter*" -or $candidate.CommandLine -notlike '*--server.port=8080*') {
            throw 'Port 8080 is not the verified MoonLetter process; refusing to stop it.'
        }
        $serverToRestart = $candidate.ProcessId
    }
}
$container = 'infra-postgres-1'
$inspect = docker inspect $container | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw 'Existing PostgreSQL container was not found.' }
if (-not $inspect[0].State.Running) {
    docker start $container | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL could not start.' }
}
$databaseEnv = @{}
foreach ($item in $inspect[0].Config.Env) {
    $parts = $item -split '=', 2
    $databaseEnv[$parts[0]] = $parts[1]
}
if (-not $databaseEnv['POSTGRES_PASSWORD']) { throw 'Database password missing from existing container.' }
$backupName = 'before-local-start-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.sql'
docker exec $container pg_dump -U $databaseEnv['POSTGRES_USER'] -d $databaseEnv['POSTGRES_DB'] --file /tmp/moon-letter-start-backup.sql
if ($LASTEXITCODE -ne 0) { throw 'Pre-start backup failed.' }
docker cp "${container}:/tmp/moon-letter-start-backup.sql" (Join-Path $runtimeDir $backupName) | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Backup copy failed.' }
if ($serverToRestart) { Stop-Process -Id $serverToRestart -ErrorAction Stop }
$env:JDBC_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:5432/' + $databaseEnv['POSTGRES_DB']
$env:JDBC_DATABASE_USERNAME = $databaseEnv['POSTGRES_USER']
$env:JDBC_DATABASE_PASSWORD = $databaseEnv['POSTGRES_PASSWORD']
$env:BOOTSTRAP_SECRET = $setupSecret
$env:MOON_LETTER_STORAGE_MODE = 'local'
$java = 'E:/jdk21-extract/jdk-21.0.2/bin/java.exe'
try {
    $process = Start-Process -FilePath $java -WindowStyle Hidden -PassThru -WorkingDirectory (Join-Path $projectRoot 'server') -ArgumentList @('-jar', $jar, '--spring.profiles.active=dev', '--server.address=0.0.0.0', '--server.port=8080', '--moon-letter.server.tomcat.nio2-connector=true') -RedirectStandardOutput (Join-Path $runtimeDir 'server.out.log') -RedirectStandardError (Join-Path $runtimeDir 'server.err.log')
    $process.Id | Set-Content -LiteralPath (Join-Path $runtimeDir 'server.pid')
} finally {
    Remove-Item Env:JDBC_DATABASE_PASSWORD, Env:BOOTSTRAP_SECRET -ErrorAction SilentlyContinue
}
Write-Output ('Server launched, PID=' + $process.Id + '. Check /actuator/health before pairing.')
