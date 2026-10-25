<!-- Deployment/person/device identifiers in this public document are anonymized. -->
# 月笺 Android APP 备案填写信息

本页全部为公开信息，不含私钥或密码。使用 2026-10-03 生成的正式签名证书，不能再填旧 Android Debug 证书信息。

备案 APK：[MoonLetter-filing-release-v12-20261003.apk](../../android/app/build/outputs/apk/release/MoonLetter-filing-release-v12-20261003.apk)。仅用于上传检测，不必安装到现有测试手机。

已成功构建 Release，验证 APK v2 / v3 签名通过，包名 `com.twomemory.app`，显示名称“月笺”，versionCode 12，无 application-debuggable 标记。APK SHA-256：`34805CA527D67946A328A3B93F6A0D54B7E75CEB690B348657A438BE66FB573C`。尚未在手机安装正式签名包，不把已有 Debug 版功能验收当作 Release 安装验收。

| 字段 | 填写值 |
| --- | --- |
| App 名称 | 月笺 |
| 运行平台 | Android（安卓） |
| 安卓平台软件包名称 | `com.twomemory.app` |
| 证书 MD5 指纹 | `23119F6538473A8FF3E7EE1BB431083F` |
| 安卓平台后台服务域名 / 接入阿里云的域名 | `api.example.invalid`（不要加 https:// 或端口） |
| 云服务实例 | 已购买的广州 ECS，公网 IP `192.0.2.10` |

## 公钥

以下为 RSA 公钥模数的十六进制值，复制整行，不包含代码框标记、空格或换行。

```text
980ADA1D6FB2BD7F9A8AD9E91D8E7A131FB94A842B4747BBCBBA96ED5BEC04E9501B66F6CCF29744FE1CB5C466608E86F2A7FDDDCA1F5C8FDFB3F6324950BE6A3732A7DDD1D3306ACA59D97E4914DADE3FBF03F6FE9DA930DA65112E406557C963B1914C1C68D040DF73D9CB789F703FEBE41AE3ED1957BF9D8E01747E4C16D0D2AE12D12A01BBE9F97A07CA52A813FCC8529F328B23B9232656E71111448091A11E0F8C4DF41762FAD5B69F1768FA1E6EAB87570B85D7A499C5865748FB54F2B5FFEAB4EDE9CDB1EA445EB18B44A42D908464869390F6DB757ED3B3263216A991227CE68F4A9C4F88F545069291DF2C836B457149C4D11C8F0CED797B1B35687B9CFE3E8CDE70841DB5E2FC4455A6D812485F8EFB1F6F41E3EF4FBA7E3635B8DA0BB17F292B3E990B5FCEBEE59418247E08D40E32F1372D8DCD3B83A5CD333A1F6C25C535B83B718957A2121EB905A3A77217875C79F0BE272378EED79890AC9E1A644A405F2221B285182986A346B45714A7E439D26EF7F4112B615A397B9B
```

也可以将正式签名 APK 上传阿里云备案智能助理，让它自动提取包名、公钥、MD5，核对与本页一致。

安卓备案填写的是**签名证书 MD5**，不是 APK 文件的 MD5，也不是 HTTPS 证书。官方说明：https://help.aliyun.com/zh/icp-filing/basic-icp-service/user-guide/fill-in-app-feature-information

此域名是应用后端 API 的自有域名；如备案页面另有“全部后台服务域名”以及第三方服务项，应根据实际 OSS 直接上传链路如实填写，并向备案审核人员确认 OSS 厂商域名的填写位置，不把 OSS 桶地址当作自有 API 域名。
