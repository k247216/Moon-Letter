<!-- Deployment/person/device identifiers in this public document are anonymized. -->
# 月笺 V26：备案前上线准备

保留V25已验收的时间轴、地图、相册、录音及重要日子样式，不再扩大产品重构。新版本主要补正式地址切换和上线保护。

「我们」→「云端连接与服务管理」→「保留数据，切换正式地址」。仅在备案通过、HTTPS准备好后使用；用原令牌验证同一空间/成员后才切换，不重新初始化或配对。数据库、原图片、相册队列、草稿、阅读和日子关闭状态保留。失败保留原连接；旧后台任务及进程启动会重新调度相册工作。已有空间不同时展示重新绑定表单。

APK仍使用原测试签名覆盖安装，不能卸载清数据。尚未安装到真实手机/平板；本轮只有独立模拟器测试。正式备案签名与测试签名不同，正式发行迁移仍单列门槛。

## 现在可以在服务器完成的私有准备

在电脑 PowerShell 上传（不要在Linux里运行Windows路径）：

```powershell
scp "E:\MoonLetter\Moon-Letter\infra\local-runtime\cloud-transfer\moonletter-public-ready-v26-20261004.tar" root@192.0.2.10:/root/
```

在服务器 SSH 中执行：

```sh
tar -xf /root/moonletter-public-ready-v26-20261004.tar -C /opt/moonletter
sh /opt/moonletter/update-private-app-v25.sh
sh /opt/moonletter/check-private-release.sh
MOONLETTER_ENABLE_BACKUP_TIMER=yes sh /opt/moonletter/prepare-private-launch.sh
```

后端沿用V25；更新脚本保留旧JAR、.env、数据库及原件，不增加公网端口。准备脚本核对私有健康、Caddy配置，并备份/独立恢复；最后安装每日04:30附近执行的私有备份timer。只有实际执行输出成功才算服务器准备完成。本轮无可用非交互SSH授权，未代替你执行ECS更新、timer或生产恢复。

备份使用同一PostgreSQL快照，保存源库名、每张表的数量/内容指纹、dump及校验清单；恢复到新建probe库比对后删除probe，不覆盖生产。每天也验证恢复。达到30份或安全空间不足时停止新增并报告失败，不自动删除已有备份。请定期把验证过的一套dump、manifest、fingerprints、sha256、restore-report通过SSH复制到另一台设备，归档后由你明确清理旧副本。同盘备份不是异机备份，也不备份OSS图片/录音原件；OSS版本控制/保留策略仍需在控制台确认。

## 备案通过后

确认域名/APP备案、DNS及80/443安全组/防火墙；保持8080、5432不公开。服务器执行：

```sh
MOONLETTER_FILING_APPROVED=yes sh /opt/moonletter/activate-public-https.sh
```

它只启动Caddy代理，不重建应用/数据库，也不自动拉取镜像。不传上述确认变量会直接拒绝。

电脑执行：

```powershell
cd E:\MoonLetter\Moon-Letter
.\scripts\Test-PublicEndpoint.ps1
```

该检查只证明受信任HTTPS和管理/认证边界，不等于双机跨省验收。之后两端覆盖安装同一V26，在应用内验证切换正式地址，再按 `docs/testing/public-cross-province-acceptance.md` 进行真实异网测试。

## 已有本地证据 / 未完成门槛

- Android全部9模块247项，0失败/错误/跳过。包含真实Spring/PostgreSQL相册原件上传→伴侣下载、同空间地址迁移、拒绝错误身份/陌生域名/重定向、失败持久化回滚及同步排队并发。
- 独立PostgreSQL备份恢复成功；破损文件、错误清单及重新计算校验和的错误内容指纹均被拒绝。真实应用V13结构也已恢复并逐表比对一致；这些是本地隔离数据，不是ECS生产演练。
- 原生手机/平板尺寸最终结果及产物校验见随包发布证据。
- 未执行：公网真实TLS/续期、ECS自动任务、OSS异机保护、实际跨省双端、正式签名发行迁移。本轮未恢复真实USB映射或SSH隧道。
