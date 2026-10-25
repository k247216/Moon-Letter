param([string]$Tests = 'OccasionApiTest,OccasionMigrationTest,OccasionAuthenticationTest',
      [string]$Log = 'occasion-tests.log',
      [ValidateSet('test', 'package')][string]$Goal = 'test', [string]$JarName = '',
      [ValidatePattern('^moon_letter_[a-z0-9_]+_test$')][string]$Database = 'moon_letter_occasion_v25_test')
$ErrorActionPreference = 'Stop'
$containerInfo = docker inspect infra-postgres-1 | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw 'Existing PostgreSQL container cannot be inspected.' }
$testDatabaseEnv = @{}
foreach ($item in $containerInfo[0].Config.Env) {
    $parts = $item -split '=', 2
    $testDatabaseEnv[$parts[0]] = $parts[1]
}
$env:TEST_DB_USER = $testDatabaseEnv['POSTGRES_USER']
$env:TEST_DB_PASSWORD = $testDatabaseEnv['POSTGRES_PASSWORD']
$env:TEST_DB_URL = "jdbc:postgresql://localhost:5432/$Database"
$env:TEST_DB_ADMIN_URL = 'jdbc:postgresql://localhost:5432/postgres'
$env:JAVA_HOME = 'E:/jdk21-extract/jdk-21.0.2'
$previousTestJavaOptions = $env:JAVA_TOOL_OPTIONS
$taskTempDirectory = ((Join-Path (Split-Path $PSScriptRoot -Parent) '.gradle-tmp') -replace '\\','/')
$env:JAVA_TOOL_OPTIONS = "-Djava.net.preferIPv4Stack=true -Djava.io.tmpdir=$taskTempDirectory -Djdk.net.unixdomain.tmpdir=$taskTempDirectory -XX:-UsePerfData"
try {
    $exists = docker exec infra-postgres-1 psql -U $env:TEST_DB_USER -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$Database'"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot check isolated database.' }
    if (-not $exists) {
        docker exec infra-postgres-1 createdb -U $env:TEST_DB_USER $Database
        if ($LASTEXITCODE -ne 0) { throw 'Cannot create isolated database.' }
    }
    $mavenArgs = @('-o', $Goal, '-Dmoon-letter.server.tomcat.nio2-connector=true')
    if ($Tests) { $mavenArgs += "-Dtest=$Tests" }
    if ($JarName) { $mavenArgs += "-Dmoon-letter.jar-name=$JarName" }
    & 'D:/apache-maven-3.9.15/bin/mvn.cmd' @mavenArgs *> (Join-Path $PSScriptRoot $Log)
    $testExit = $LASTEXITCODE
    Get-Content (Join-Path $PSScriptRoot $Log) -Tail 45
    exit $testExit
} finally {
    Remove-Item Env:TEST_DB_PASSWORD -ErrorAction SilentlyContinue
    $env:JAVA_TOOL_OPTIONS = $previousTestJavaOptions
}
