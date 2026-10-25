param([string]$Log = 'stable-reading-v39-server-full.log')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
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
$env:JAVA_HOME = 'E:/jdk21-extract/jdk-21.0.2'
$taskPreviousJavaOptions = $env:JAVA_TOOL_OPTIONS
$taskTmp = ((Join-Path $taskRoot '.gradle-tmp') -replace '\\','/')
$env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$taskTmp -Djdk.net.unixdomain.tmpdir=$taskTmp -XX:-UsePerfData"
try {
    $taskLog = Join-Path $taskRoot "infra/local-runtime/$Log"
    & 'D:/apache-maven-3.9.15/bin/mvn.cmd' -o -f (Join-Path $taskRoot 'server/pom.xml') '-Dmoon-letter.jar-name=moon-letter-stable-reading-v39' package *> $taskLog
    $taskExit = $LASTEXITCODE
    Get-Content -LiteralPath $taskLog -Tail 35
    exit $taskExit
} finally {
    Remove-Item Env:TEST_DB_PASSWORD -ErrorAction SilentlyContinue
    $env:JAVA_TOOL_OPTIONS = $taskPreviousJavaOptions
}
