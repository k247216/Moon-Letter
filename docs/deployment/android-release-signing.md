<!-- Deployment/person/device identifiers in this public document are anonymized. -->
# 月笺正式 Android 签名

生成日期：2026-10-03。应用包名保持 `com.twomemory.app`，首次正式签名证书为 RSA 3072 / SHA256withRSA，有效期至 2056-09-25。后续正式 APK 必须复用此证书，不能每次重新生成。

## 本机密钥保管

私有目录：`E:/MoonLetter/Moon-Letter/infra/local-runtime/release-signing/`。

- `moonletter-release.p12`：加密的正式私钥库。
- `signing-password.user.dpapi.clixml`：Windows DPAPI 加密的私钥库密码，已在你的 Windows 账户 `DEV-PC\开发者` 下验证可解密并打开私钥库。
- `signing-password.dpapi.clixml`：构建沙箱账户的加密凭据，仅用于本机代理继续签名，不是你的密码备份。
- `moonletter-release-public.pem`：公开证书，不含私钥。

目录已限制为你的 Windows 用户、构建沙箱账户和 SYSTEM 访问；整个 `infra/local-runtime/` 已被 Git 忽略。密码未写入 Gradle、源码、命令参数或聊天。

**尚未完成异机备份。** 仅复制 DPAPI 文件到其他电脑不能恢复密码。请将私钥库备份到安全的离线介质，并把密码另存到自己的密码管理器。可以在自己的本机 PowerShell 中读取密码（不要将输出粘贴到聊天、日志或截图）：

```powershell
(Import-Clixml -LiteralPath 'E:/MoonLetter/Moon-Letter/infra/local-runtime/release-signing/signing-password.user.dpapi.clixml').GetNetworkCredential().Password
```

## 后续签名

先构建 `:app:assembleRelease`，然后使用以下命令签名。输入 APK 必须是新构建的 unsigned Release APK，不能覆盖或重新生成密钥。

```powershell
$taskSigningRoot = 'E:/MoonLetter/Moon-Letter/infra/local-runtime/release-signing'
$env:MOONLETTER_SIGNING_PASSWORD = (Import-Clixml -LiteralPath "$taskSigningRoot/signing-password.user.dpapi.clixml").GetNetworkCredential().Password
try {
    & 'E:/Android/Sdk/build-tools/37.0.0/apksigner.bat' sign --ks "$taskSigningRoot/moonletter-release.p12" --ks-key-alias moonletter-release --ks-pass env:MOONLETTER_SIGNING_PASSWORD --key-pass env:MOONLETTER_SIGNING_PASSWORD --out 'E:/MoonLetter/Moon-Letter/android/app/build/outputs/apk/release/MoonLetter-release.apk' 'E:/MoonLetter/Moon-Letter/android/app/build/outputs/apk/release/app-release-unsigned.apk'
    if ($LASTEXITCODE -ne 0) { throw '签名失败' }
    & 'E:/Android/Sdk/build-tools/37.0.0/apksigner.bat' verify --verbose --print-certs 'E:/MoonLetter/Moon-Letter/android/app/build/outputs/apk/release/MoonLetter-release.apk'
    if ($LASTEXITCODE -ne 0) { throw '签名验证失败' }
} finally {
    Remove-Item Env:MOONLETTER_SIGNING_PASSWORD
}
```

证书 SHA-256 应始终为 `FBDD4C43B6A764B6A29D527DF6DF94306BD49EF4CD4D29CACA499EDC0A105443`。

## 与现有测试版的区别

当前手机 V12 使用 Android Debug 签名。此正式签名 APK 没有配置签名轮换，不能直接覆盖该测试版。不要为安装备案包卸载手机中的测试版；卸载会丢失本机数据库、未上传照片和会话。先用本包提交备案，后续单独处理测试版到正式版的数据与身份迁移。

正式签名不代表公网 HTTPS 已通，也不代表备案已通过。当前产品界面与云端功能源码保持 V12，不因签名更换而重写。
