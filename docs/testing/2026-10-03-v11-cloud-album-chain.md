<!-- Deployment/person/device identifiers in this public document are anonymized. -->
# V11 相册链路验证与私有 ECS 测试

## 已验证范围

- 独立相册不创建记录：Room V4 加入服务地址、情侣空间、创建者/添加者、服务器资产与删除标记。
- V1/V2/V3 升级到 V4 保留记录、队列、分类、封面和原图路径；没有 destructive migration。
- 后台先拉墓碑、再上传和关联、再拉快照；相册任务与记录同步分开，避免上传阻塞记录；断网保留队列，WorkManager 重试。
- 两个真实 Room 数据库通过真实 HTTP、Spring、PostgreSQL 和本地对象存储验证：原图一致、重复关联去重、缓存丢失恢复、双端照片/相册删除。
- 上传中断后关闭再打开数据库，待上传照片保留；失败不阻塞伴侣照片进入；恢复后完成上传。
- 应用 JVM 66 项、数据库 34 项、网络 9 项、同步 19 项通过。图像测试使用原生渲染。SDK28 模拟依赖下载超时，三个相册 JVM 类转为已缓存的 SDK34；API28 不属于本轮已验证范围。
- 最终签名 APK 原生 Android14：相册 UI/查看/保存、录音缓存、共同记录、提醒 24 项，数据库升级/导入/EXIF 9 项，真实麦克风录音/试听/确认 1 项通过。Android8–9 的 DocumentExport 类因 SDK 范围在 Android14 上不执行，不计入通过数量。
- 初次额外原生回归使用了错误提醒类名（ClassNotFound），按源码核验类名后重新运行 4/4 通过；没有隐瞒失败。
- 局域网后端已升级到 V12，PID 40096，8080 health UP。最终升级前备份：infra/local-runtime/before-local-start-20261003-195102.sql；不是清库。
- 独立只读评审发现传输预算饥饿、后台续传遗漏、墓碑封面反复重试、跨服务器 WorkManager 名称碰撞，已修复并复审确认四项关闭。新增九张照片测试首先误用了同次导入去重，校正为九次独立导入后，旧预算逻辑确实失败（预期一张成功、实际零张），修复后通过；不是把测试数据错误当作产品故障证明。
- 真实双 Room 相册套件最终 3/3 通过；每项独立重启服务并重建隔离测试数据库，避免测试顺序依赖。覆盖前八张持续失败后的第九张进展、后台剩余任务续传回调、恢复收敛和删除封面的 dirty 清理。
- Linux 容器 prod/S3 配置启动 health UP、无重启/无 OOM；使用虚构凭据只验证配置启动，不代表 OSS 权限成功。自行创建的临时启动检查容器已清理，局域网服务与数据库继续运行。

## 产物

APK：android/app/build/outputs/apk/debug/MoonLetter-cloud-album-v11-20261003.apk

versionCode 11，versionName 0.1.0+cloud-album-v11-20261003。
SHA256 A79B767F1000C4F6B284D68F3857D34C65B3C9B94AE8E046AAF8A9FAA9FEA3B4。
沿用既有测试签名，模拟器覆盖安装成功，V2/V3 验签通过；仍非正式发行签名。

后端：server/target/moon-letter-cloud-album-v11-20261003.jar
SHA256 66CEFAF3D581022054BE80D20586D96B1B192F113FA2AF5804F11AABCC4E8C83。
本轮修复后端原图上限与常见栅格格式：64 MiB、JPEG/PNG/WebP/HEIC/HEIF/AVIF/GIF/BMP/WBMP；拒绝超限、零维度和非白名单 MIME。新验证先因原来的 20 MiB 限制失败，再通过。
本轮服务端 93 项完整验证通过（Windows NIO2，隔离数据库）；没有声称实际上传过 64 MiB 到 OSS。

## 必须告知的测试副作用

test-private-cloud-chain.ps1 的 bootstrap 和 pair 在已有部署上是恢复成员，不是创建隔离测试用户。
本轮本机 API 探针调用了这一入口，因此替换了原测试成员的设备令牌；旧手机可能需要重新绑定。
成员姓名、历史记录和原图保留；未恢复或重置数据库。失败的早期探针留下了测试相册。
脚本现在默认拒绝执行，必须明确传 -AllowDeviceRebinding；建议只在隔离测试部署使用。
修复过 PowerShell 顶层 JSON 数组过滤问题后，本机探针 PASS，验证上传/下载/去重/两类墓碑。
本机 PASS 仅验证脚本与后端链路，不代表 ECS/OSS 的手机链路通过。

## 私有 ECS 更新（Windows 上传，ECS 操作）

Windows PowerShell：

```powershell
scp "E:\MoonLetter\Moon-Letter\infra\local-runtime\cloud-transfer\moonletter-v11-update-20261003.tar" root@192.0.2.10:/root/
```

ECS SSH 窗口：

```sh
tar -xf /root/moonletter-v11-update-20261003.tar -C /opt/moonletter
cd /opt/moonletter
sh update-private-app-v11.sh
```

更新脚本先验 JAR SHA256、保存旧 JAR，然后只重建 app；不覆盖 .env，不改 PostgreSQL 卷，不增加公网端口。健康检查失败不报告成功，不自动清库或回滚数据库。
更新包 SHA256：4E45EE186B723EA6B053E4E22B97BB63570105EB282BC0CE2873EAE32D2FE5EB。只包含 JAR 和更新脚本，不包含密钥或数据库。

## 备案草稿阶段的 SSH 加密隧道

域名 api.example.invalid 已解析到 192.0.2.10；本轮 PC 检查 HTTPS 443 连接超时。
当前没有可用的免交互 SSH 身份（BatchMode 返回 Permission denied）；未远程更新 ECS。

Windows 新开 PowerShell，自己输入 SSH 密码，保持窗口开着：

```powershell
ssh -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -L 127.0.0.1:18088:127.0.0.1:8080 root@192.0.2.10
```

另开窗口只读检查：

```powershell
curl.exe --connect-timeout 5 --max-time 15 http://127.0.0.1:18088/actuator/health
```

返回 UP 后，该端口通向 ECS，而不是本机后端。HTTP 只在本机 loopback，跨互联网部分由 SSH 加密。
可用 `adb reverse tcp:18088 tcp:18088` 让 USB 手机/模拟器的 127.0.0.1:18088 指向此隧道，再在 V11 绑定界面使用 http://127.0.0.1:18088。
ECS 的初始化密钥只在手机或本地终端输入，不在聊天粘贴。云端与 LAN 是不同部署，旧 LAN 令牌不能直接用于 ECS。
这种测试证明手机软件连接远端 ECS；它不等于两台手机脱离电脑、各自移动网络直连。

可选 API 探针（会替换两位成员的会话，执行前确认）：

```powershell
cd E:\MoonLetter\Moon-Letter
.\scripts\test-private-cloud-chain.ps1 -BaseUrl http://127.0.0.1:18088 -AllowDeviceRebinding
```

探针会隐藏输入 ECS BOOTSTRAP_SECRET，输出 PASS/安全阶段，不打印令牌、RAM 密钥或签名 URL。
两手机真实跨网直连、ECS 的 V11 OSS 全链路、HTTPS 和备份恢复仍待验证。备案仍是草稿，未提前公开 API。

## 后续：独立相册未同步的现场排查

- 用户确认 ECS 更新脚本最终返回 UP；启动阶段 reset/empty reply 后恢复，不据此判断持续启动故障。
- 用户反馈时间轴图片之前正常，未同步的是独立相册；实际手机 APK 版本及服务地址尚未确认。更新 ECS 不会替换手机 APK，也不会自动改写手机已经保存的服务地址。
- 追加真实双 Room/HTTP/PostgreSQL 测试：旧版未绑定相册及原图在同步时认领当前成员、保留原 ID/原图路径，对方收到一致原图，两端队列收敛。相册套件 4/4 通过；本轮仅增加测试，没有发布新的生产实现或 APK，不将该通过说成现场问题已解决。
- 本机只读统计 active_albums=3、active_album_photos=3、IMAGE READY=14、AUDIO READY=1。这些包含之前的测试探针内容，不能当作用户两台手机已上传的证据。
- 本机域名 HTTPS 443 不可达、127.0.0.1:18088 没有 SSH 隧道监听，只有模拟器连接 ADB；无法读取两台实体手机或 ECS 的实际队列/鉴权/OSS 返回结果。
- 不要求为了排查清数据或重新配对，也不再运行会替换用户设备令牌的 bootstrap 探针。首先确认两手机 V11 覆盖安装，然后读取实际测试部署的相册关联和媒体状态计数，定位客户端触发、API 关联或 OSS 传输边界。
- 后续 ECS 用户只读统计：album=0、album_item=0、media_asset 无行；仅监听 127.0.0.1:8080。Windows SSH 隧道建立后，本机 18088 health 已独立复核 UP。
- 实体手机连接后已读取：versionCode=11、服务地址仍为 http://192.0.2.10:8080，设备 18088 的 adb reverse 已配置。没有直接改写设备会话或地址，也没有清数据。
- 只读复制设备 DB 与 WAL 到被忽略的 infra/local-runtime/phone-album-diagnostic-20261003；quick_check=ok，一本相册、五张照片，全部 cloudKnown=0、assetId 空，相册 serverUrl/coupleId/createdBy 为空。局域网该成员存在一条活跃会话和十条撤销历史；不能仅凭计数断言手机当前令牌已撤销。
- 增加显式 opt-in 的只读 AlbumConnectionDiagnosticTest，在手机进程使用既有加密会话请求相册 API，只输出后端地址/状态码/数量，不打印令牌。测试包构建成功，但实体手机安装被系统取消 INSTALL_FAILED_USER_RESTRICTED；探针尚未执行，等待用户开启并授权 USB 安装，不绕过系统限制。常规原生套件不默认执行现场探针。
- 用户授权 USB 安装后，重新构建并安装测试包成功；在实体手机显式执行 live_album_diagnostic=true。现场探针确实运行一项、失败一项，安全输出 authenticated=false http=401 error=IOException；不是根据 adb 命令 exit 0 判断通过。手机仍指向局域网，既有设备凭据无法通过相册接口鉴权，上传前即被拒绝。
- 这与此前本机恢复探针替换用户会话的已披露副作用一致。没有自动恢复已撤销凭据、发新凭据、改配对或将旧凭据搬到 ECS；需要明确恢复此成员登录的授权，之后再验证五张真实照片的同步。ECS 初次绑定仍是另一条待验证链路。
- 用户明确允许恢复这台手机的原成员登录后，先按手机既有 userId/coupleId 验证服务端成员关系并备份本机数据库，再使用现有 SessionAdminCommand 的 issue 模式仅轮换该成员凭据；不调用 bootstrap/pair，不修改情侣关系、头像姓名或伴侣会话。恢复凭据仅保存在被忽略目录的 DPAPI 加密 clixml 中，不打印到工具输出或文档。
- 显式 opt-in 的 AlbumSessionRecoveryTest 在实体手机执行 1/1 通过：在写回 AndroidKeyStore 加密会话前先验证新凭据可访问同空间相册；成员/空间/服务地址保持不变；原有五照片 ID、原图路径保留，上传确认后逐张 authenticated download 的 SHA256 与原图一致。
- 独立只读探针复测 1/1 通过（此前同一探针确实返回 401）。手机 DB quick_check=ok；同步拉取现有服务端测试相册后，总计五本（四活跃、一墓碑）、九照片（八活跃确认、一墓碑），未绑定相册为零。局域网服务端 active_album=4、active_photo=8、IMAGE READY=19、AUDIO READY=1；其中五张是本次恢复上传，其他包含之前测试内容，不误称新建用户数据。
- 原应用已打开供验收。仅此实体手机已恢复并验证局域网原图往返；另一台实体手机的伙伴端下载验收及 ECS/OSS 手机上传仍未完成，手机仍使用原局域网地址。
