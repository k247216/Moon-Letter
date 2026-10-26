param([switch]$Tablet, [switch]$JournalReading)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskAdb = 'E:/Android/Sdk/platform-tools/adb.exe'
$taskSerial = 'emulator-5564'
$taskAvd = @(& $taskAdb -s $taskSerial emu avd name)
if ($LASTEXITCODE -ne 0 -or $taskAvd[0].Trim() -ne 'moonletter_v41_checks') {
    throw 'Expected isolated moonletter_v41_checks emulator on emulator-5564; refusing real devices.'
}
foreach ($taskApk in @('android/app/build/outputs/apk/debug/app-debug.apk', 'android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
    & $taskAdb -s $taskSerial install -r (Join-Path $taskRoot $taskApk)
    if ($LASTEXITCODE -ne 0) { throw 'Isolated APK installation failed.' }
}
& $taskAdb -s $taskSerial shell pm grant com.twomemory.app android.permission.POST_NOTIFICATIONS
& $taskAdb -s $taskSerial shell settings put secure show_ime_with_hard_keyboard 1
if ($Tablet) {
    & $taskAdb -s $taskSerial shell wm size 1600x1000
    & $taskAdb -s $taskSerial shell wm density 320
} else {
    & $taskAdb -s $taskSerial shell wm size 1080x2340
    & $taskAdb -s $taskSerial shell wm density 420
}
$taskLayout = if ($Tablet) { 'tablet' } else { 'phone' }
$taskRound = if ($JournalReading) { 'v45' } else { 'v44' }
$taskClass = if ($JournalReading) { 'com.twomemory.app.JournalNoteDeviceTest' } else { 'com.twomemory.app.LittleNoteDeviceTest' }
$taskLog = Join-Path $taskRoot "infra/local-runtime/note-$taskRound-device-$taskLayout.log"
& $taskAdb -s $taskSerial shell am instrument -w -r -e expectedLayout $taskLayout -e class $taskClass com.twomemory.app.test/androidx.test.runner.AndroidJUnitRunner *> $taskLog
Get-Content -LiteralPath $taskLog -Tail 25
if ($LASTEXITCODE -ne 0 -or -not (Select-String -LiteralPath $taskLog -Pattern 'OK \(2 tests\)' -Quiet)) { throw "$taskRound device checks failed." }
