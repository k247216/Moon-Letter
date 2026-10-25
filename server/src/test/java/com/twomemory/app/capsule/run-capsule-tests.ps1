param([string]$Tests = 'Capsule*Test')
$ErrorActionPreference = 'Stop'
$serverDirectory = Resolve-Path (Join-Path $PSScriptRoot '../../../../../../..')
$info = docker inspect infra-postgres-1 | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect existing local PostgreSQL container.' }
$databaseEnvironment = @{}
foreach ($item in $info[0].Config.Env) {
    $parts = $item -split '=', 2
    $databaseEnvironment[$parts[0]] = $parts[1]
}
$previousOptions = $env:JAVA_TOOL_OPTIONS
$previousJava = $env:JAVA_HOME
try {
    $env:TEST_DB_USER = $databaseEnvironment['POSTGRES_USER']
    $env:TEST_DB_PASSWORD = $databaseEnvironment['POSTGRES_PASSWORD']
    $env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/moon_letter_capsule_v15_test'
    $env:TEST_DB_ADMIN_URL = 'jdbc:postgresql://localhost:5432/postgres'
    $env:JAVA_HOME = 'E:/jdk21-extract/jdk-21.0.2'
    $env:JAVA_TOOL_OPTIONS = '-Djava.net.preferIPv4Stack=true'
    $exists = docker exec infra-postgres-1 psql -U $env:TEST_DB_USER -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='moon_letter_capsule_v15_test'"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot check isolated capsule test database.' }
    if (-not $exists) {
        docker exec infra-postgres-1 createdb -U $env:TEST_DB_USER moon_letter_capsule_v15_test
        if ($LASTEXITCODE -ne 0) { throw 'Cannot create isolated capsule test database.' }
    }
    $mavenArguments = @('-o', '-f', (Join-Path $serverDirectory 'pom.xml'), 'test', '-Dmoon-letter.server.tomcat.nio2-connector=true')
    if ($Tests) { $mavenArguments += "-Dtest=$Tests" }
    & 'D:/apache-maven-3.9.15/bin/mvn.cmd' @mavenArguments
    $testExit = $LASTEXITCODE
} finally {
    Remove-Item Env:TEST_DB_PASSWORD -ErrorAction SilentlyContinue
    $env:JAVA_TOOL_OPTIONS = $previousOptions
    $env:JAVA_HOME = $previousJava
}
exit $testExit
