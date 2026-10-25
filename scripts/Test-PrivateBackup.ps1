param([string]$Container='infra-postgres-1', [string]$Bash='D:/Git/bin/bash.exe')
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
$taskFixture='moonletter_backup_fixture_'+[Guid]::NewGuid().ToString('N').Substring(0,12)
$taskOutput=Join-Path $taskRoot ('infra/local-runtime/backup-test-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $taskOutput | Out-Null
function Check-Exit {if($LASTEXITCODE -ne 0){throw "Command failed ($LASTEXITCODE)"}}
$taskCreated=$false
try {
    & docker exec $Container createdb -U moon_letter $taskFixture; Check-Exit; $taskCreated=$true
    & docker exec $Container psql -U moon_letter -d $taskFixture -v ON_ERROR_STOP=1 -c "CREATE TABLE release_fixture(id integer PRIMARY KEY, body text NOT NULL); INSERT INTO release_fixture VALUES(1,'first'),(2,'second');"; Check-Exit
    $env:MOONLETTER_POSTGRES_CONTAINER=$Container
    $env:MOONLETTER_BACKUP_DATABASE=$taskFixture
    $env:MOONLETTER_BACKUP_DIR=$taskOutput.Replace('\','/')
    & $Bash (Join-Path $taskRoot 'infra/aliyun/backup-private.sh').Replace('\','/'); Check-Exit
    $taskDump=Get-ChildItem -LiteralPath $taskOutput -Filter '*.dump' | Select-Object -First 1
    if(!$taskDump){throw 'No complete backup'}
    if(!(Test-Path -LiteralPath ($taskDump.FullName+'.manifest'))){throw 'Missing consistent source snapshot manifest'}
    & $Bash (Join-Path $taskRoot 'infra/aliyun/verify-private-backup.sh').Replace('\','/') $taskDump.FullName.Replace('\','/') *> (Join-Path $taskOutput 'restore.log'); Check-Exit
    $taskReport=Get-Content -LiteralPath (Join-Path $taskOutput 'restore.log') -Raw
    if($taskReport -notmatch 'public.release_fixture\|2'){throw 'Restored rows differ from fixture'}
    # A valid SQL dump with a different row fingerprint must not pass recovery verification.
    $taskManifest=$taskDump.FullName+'.manifest'
    $taskOriginalManifest=[System.IO.File]::ReadAllText($taskManifest)
    [System.IO.File]::WriteAllText($taskManifest,$taskOriginalManifest.Replace('public.release_fixture|2','public.release_fixture|999'))
    & $Bash (Join-Path $taskRoot 'infra/aliyun/verify-private-backup.sh').Replace('\','/') $taskDump.FullName.Replace('\','/') *> (Join-Path $taskOutput 'wrong-manifest.log')
    if($LASTEXITCODE -eq 0){throw 'Wrong source manifest was accepted'}
    [System.IO.File]::WriteAllText($taskManifest,$taskOriginalManifest)
    $taskFingerprints=$taskDump.FullName+'.fingerprints'
    $taskOriginalFingerprints=[System.IO.File]::ReadAllText($taskFingerprints)
    [System.IO.File]::WriteAllText($taskFingerprints,$taskOriginalFingerprints.Replace('public.release_fixture|2','public.release_fixture|999'))
    & $Bash -c 'cd "$1" && sha256sum "$2" "$2.manifest" "$2.fingerprints" > "$2.sha256"' sh $taskOutput.Replace('\','/') $taskDump.Name; Check-Exit
    & $Bash (Join-Path $taskRoot 'infra/aliyun/verify-private-backup.sh').Replace('\','/') $taskDump.FullName.Replace('\','/') *> (Join-Path $taskOutput 'wrong-fingerprints.log')
    if($LASTEXITCODE -eq 0){throw 'Wrong snapshot fingerprints were accepted'}
    if((Get-Content (Join-Path $taskOutput 'wrong-fingerprints.log') -Raw) -notmatch 'source snapshot row counts/content fingerprints differ'){throw 'Expected content comparison failure'}
    [System.IO.File]::WriteAllText($taskFingerprints,$taskOriginalFingerprints)
    & $Bash -c 'cd "$1" && sha256sum "$2" "$2.manifest" "$2.fingerprints" > "$2.sha256"' sh $taskOutput.Replace('\','/') $taskDump.Name; Check-Exit
    # Corrupted backup must fail before restoring anything.
    [System.IO.File]::AppendAllText($taskDump.FullName,'corrupt')
    & $Bash (Join-Path $taskRoot 'infra/aliyun/verify-private-backup.sh').Replace('\','/') $taskDump.FullName.Replace('\','/') *> (Join-Path $taskOutput 'corrupt.log')
    if($LASTEXITCODE -eq 0){throw 'Corrupt backup was accepted'}
    "PASS: backup restored into isolated DB; 2 original rows; corrupted backup rejected. Artifacts: $taskOutput"
} finally {
    if($taskCreated -and $taskFixture -match '^moonletter_backup_fixture_[0-9a-f]{12}$') { & docker exec $Container dropdb -U moon_letter --if-exists $taskFixture }
    Remove-Item Env:MOONLETTER_POSTGRES_CONTAINER,Env:MOONLETTER_BACKUP_DATABASE,Env:MOONLETTER_BACKUP_DIR -ErrorAction SilentlyContinue
}
