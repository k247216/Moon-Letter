<!-- Deployment/person/device identifiers in this public document are anonymized. -->
# 本地双机验收（2026-10-03）

服务地址：`http://192.0.2.10:8080`。2026-10-03 14:49 已启动，电脑端本机和局域网健康检查均返回 `UP`。进程 PID=26388；这是现场信息，重启后 PID 可能变化。

使用现有 `infra-postgres-1`，数据库 `moon_letter`。启动前已备份到被 Git 忽略的 `infra/local-runtime/before-local-start-20261003-144902.sql`；保留已有成员和记录。媒体使用 `server/storage` 本地存储。无需重装 V9 APK。

## 手机操作

1. 两台手机连接能访问电脑的同一局域网。先用手机浏览器打开 `http://192.0.2.10:8080/actuator/health`，应看到 `"status":"UP"`。
2. 已绑定的第一台手机：进入「我们」→ 右上角设置 →「换手机或重新配对」→「生成配对码」。码一次有效，15 分钟内使用。
3. 尚未绑定的另一台手机：选择「我是伴侣」，服务器地址填 `http://192.0.2.10:8080`，输入自己的称呼和配对码。
4. A 写文字并发布，B 拉刷新查看；再验证照片及回应，确认各端署名和图片详情。录音先验证本机录制/回放，跨端录音仍需这次实体设备验收。
5. 独立相册当前仍为各手机本机数据，不列入跨设备同步验收。不要清除应用数据。

已有绑定会话时直接使用，不需要重新初始化。若第一台也未绑定，需要当前启动密钥，可在本机 PowerShell 获取：

```powershell
cd E:\MoonLetter\Moon-Letter
.\scripts\start-local-server.ps1 -ShowSetupKey
```

该密钥只用于首设备初始化/找回；重新初始化会替换第一位成员旧会话。密钥以 Windows 用户加密方式保存在被 Git 忽略的本机目录，不写入 APK 或文档。

## 服务保持和恢复

本机已在后台运行服务。双机验证期间电脑保持开机，Docker 保持运行。重启电脑后，在 PowerShell 执行：

```powershell
cd E:\MoonLetter\Moon-Letter
.\scripts\start-local-server.ps1
```

脚本读取现有容器配置、备份数据库，并启动服务；日志在 `infra/local-runtime/server.out.log` 和 `server.err.log`。不要使用空的新数据库替代现有数据库。

当前 JDK 已有 Windows 公用网络防火墙放行规则。若手机浏览器访问超时，而电脑健康检查正常，需要检查 Wi-Fi 客户端隔离及防火墙；管理员 PowerShell 可添加只允许本地子网的规则：

```powershell
New-NetFirewallRule -Name MoonLetter-Local-8080 -DisplayName 'Moon Letter local phone testing (8080)' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8080 -RemoteAddress LocalSubnet -Profile Any
```

## 本轮补验结果

- 服务端当前源码 `mvn -o -DskipTests package` 构建通过。
- 真实 Spring HTTP + PostgreSQL + 两个 Room 的文字链路、照片回应链路、换机恢复测试各 1 项，3/3 通过。分别使用 `moon_letter_slice_test` 等独立测试库，未清理用户数据库。
- 相册组合超时根因：用例继承了先前保存的照片视图偏好。修正为运行前保存/重置、运行后恢复偏好。`ProductVisualAcceptanceTest`、`IndependentAlbumScreenTest`、`AlbumViewPreferenceTest` 组合 8/8 通过。
- 产品 APK 保持 V9，SHA-256 不变：`049FC564F6EF601F2983ED73E8D56CC73B3F6E9FC07E085C0BFCF42CA3E1CB17`。此次新增启动脚本和测试隔离修复，无产品 UI 变更。
- 手机的实际网络可达性和实体双机结果仍由此次实测确认，电脑端健康检查和测试夹具不代替实体手机验收。
