param([switch]$Tablet)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskAdb = 'E:/Android/Sdk/platform-tools/adb.exe'
$taskSerial = 'emulator-5564'
$taskAvd = @(& $taskAdb -s $taskSerial emu avd name)
if ($LASTEXITCODE -ne 0 -or $taskAvd[0].Trim() -ne 'moonletter_v41_checks') {
    throw 'Refusing device tests: expected the isolated moonletter_v41_checks emulator on emulator-5564.'
}
$taskApk = Join-Path $taskRoot 'android/app/build/outputs/apk/debug/app-debug.apk'
$taskTestApk = Join-Path $taskRoot 'android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
& $taskAdb -s $taskSerial install -r $taskApk
if ($LASTEXITCODE -ne 0) { throw 'Isolated test app install failed.' }
& $taskAdb -s $taskSerial install -r $taskTestApk
if ($LASTEXITCODE -ne 0) { throw 'Isolated test APK install failed.' }
& $taskAdb -s $taskSerial shell pm grant com.twomemory.app android.permission.POST_NOTIFICATIONS
& $taskAdb -s $taskSerial shell settings put secure show_ime_with_hard_keyboard 1
if ($Tablet) {
    & $taskAdb -s $taskSerial shell wm size 1600x1000
    & $taskAdb -s $taskSerial shell wm density 320
} else {
    & $taskAdb -s $taskSerial shell wm size 1080x2340
    & $taskAdb -s $taskSerial shell wm density 420
}
$taskSuffix = if ($Tablet) { 'tablet' } else { 'phone' }
$taskLog = Join-Path $taskRoot "infra/local-runtime/paper-round-v43-device-$taskSuffix.log"
& $taskAdb -s $taskSerial shell am instrument -w -r -e expectedLayout $taskSuffix -e class 'com.twomemory.app.LittleNoteDeviceTest,com.twomemory.app.PaperWheelDeviceTest,com.twomemory.app.PaperAlbumCategoryTest' com.twomemory.app.test/androidx.test.runner.AndroidJUnitRunner *> $taskLog
Get-Content -LiteralPath $taskLog -Tail 35
if ($LASTEXITCODE -ne 0 -or -not (Select-String -LiteralPath $taskLog -Pattern 'OK \(3 tests\)' -Quiet)) {
    throw "Device acceptance failed; see $taskLog"
}
