<!-- Deployment/person/device identifiers in this public document are anonymized. -->
# 2026-10-09 公网上线检查

用户已在本轮明确确认备案完成，允许开始公网接通。既有“不提前更新 APK”约束保留到受信任 HTTPS 检查通过；不清数据、不重新配对、不更换签名。

## 本轮实测

- A 记录：`api.example.invalid` → `192.0.2.10`。
- 外部 HTTPS：8 秒连接超时，尚未取得证书或 HTTP 响应。此结果不能区分安全组、防火墙或代理未启动。
- SSH：`BatchMode=yes` 被拒绝，当前代理没有可用服务器授权；未执行远端部署。
- 本地 V27 私有候选包 SHA256：`49688f1ccc3bc223ba20edc1a0d6c58a92af38d963949dfb8950c75ac2ca63c9`。
- 本地 V27 候选 JAR SHA256：`b0a3bcaff5017ede544dbd5e0ce55528f02b69e9b6cb4c3f1e2ac422634b2ebe`。
- 已读取当前激活脚本、Compose HTTPS 叠加配置及 Caddy 配置：显式备案门禁、仅启动代理、80/443 映射、隐藏管理入口；没有执行远端激活。

## 接下来需要服务器实际输出

1. 当前 JAR 哈希、内部健康、数据库版本及备份巡检。
2. 公网激活脚本与 HTTPS 配置是否已上传。
3. 当前监听端口及主机防火墙；云安全组另核对 TCP 80/443，8080/5432 不放行。
4. 若远端不是 V27，先执行带备份恢复验证的私有更新，再验证真实 OSS 读写；不能用本地候选校验冒充远端更新成功。
5. 激活 HTTPS 后运行 `scripts/Test-PublicEndpoint.ps1`，另核对 HTTP 跳转、证书及续期持久化。
6. HTTPS 可用后才交付同签名覆盖包，安全迁移原空间，按跨省验收矩阵在两台设备不同网络下验证。

## 后续真实进展

用户服务器输出确认 V27 JAR 与候选一致、Flyway13、UP/restarts0/OOMfalse、4相册/15照片/24图片及1音频READY。2026-10-09 自动备份服务 success，最新独立恢复报告匹配源快照指纹，巡检Warnings=0。

用户已执行公网激活脚本，Caddy启动。紧接着无域名变量的单独Compose检查报错，是调用环境遗漏，不代表代理启动失败；后续检查需前置 `MOON_LETTER_DOMAIN=api.example.invalid`。

本机重新执行 `scripts/Test-PublicEndpoint.ps1` 返回PASS：系统信任TLS、管理404、无凭据保护接口拒绝。HTTP请求返回308至HTTPS。证书经系统信任验证，主体为正式API域名，颁发者Let's Encrypt YE1，到期2027-01-07；这是证书初次签发证据，不是续期实测。

正在构建同签名V27覆盖客户端，后续真实双机迁移与媒体同步仍未验收；没有代理直接SSH写服务器、没有真实设备安装或USB映射。
