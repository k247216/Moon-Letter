param([string]$Log = 'memory-book-v33-android-full.log')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskJar = Join-Path $taskRoot 'server/target/moon-letter-memory-book-integrity-v33.jar'
if (-not (Test-Path -LiteralPath $taskJar)) { throw 'Build the verified V33 server JAR first.' }
$taskInfo = docker inspect infra-postgres-1 | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw 'Local test PostgreSQL unavailable.' }
$taskDbEnv = @{}
foreach ($taskItem in $taskInfo[0].Config.Env) {
    $taskParts = $taskItem -split '=', 2
    $taskDbEnv[$taskParts[0]] = $taskParts[1]
}
$env:TEST_DB_USER = $taskDbEnv['POSTGRES_USER']
$env:TEST_DB_PASSWORD = $taskDbEnv['POSTGRES_PASSWORD']
$env:TEST_DB_ADMIN_URL = 'jdbc:postgresql://localhost:5432/postgres'
$env:MOONLETTER_TEST_SERVER_JAR = $taskJar
$env:JAVA_HOME = 'E:/jdk21-extract/jdk-21.0.2'
$env:GRADLE_USER_HOME = Join-Path $taskRoot '.gradle-user'
$taskPreviousJavaOptions = $env:JAVA_TOOL_OPTIONS
$taskTmp = ((Join-Path $taskRoot '.gradle-tmp') -replace '\\','/')
$env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$taskTmp -Djdk.net.unixdomain.tmpdir=$taskTmp -XX:-UsePerfData -Drobolectric.graphicsMode=NATIVE"
Push-Location (Join-Path $taskRoot 'android')
try {
    $taskLog = Join-Path $taskRoot "infra/local-runtime/$Log"
    & (Join-Path $taskRoot '.gradle-user/gradle-9.8.0/bin/gradle.bat') --offline --no-daemon testDebugUnitTest --rerun :core:model:test --rerun :app:assembleDebug :app:assembleDebugAndroidTest *> $taskLog
    $taskExit = $LASTEXITCODE
    Get-Content -LiteralPath $taskLog -Tail 40
    exit $taskExit
} finally {
    Pop-Location
    Remove-Item Env:TEST_DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:MOONLETTER_TEST_SERVER_JAR -ErrorAction SilentlyContinue
    $env:JAVA_TOOL_OPTIONS = $taskPreviousJavaOptions
}
