<!-- Deployment/person/device identifiers in this public document are anonymized. -->
# 月笺 V25：声音小记与重要日子

生日姓名、日期由你们自己设置，没有预填 0509、0423，也没有绑定第一位/第二位。

入口：「我们」→「纪念日与倒计时」→生日 / 纪念日 / 惊喜卡。
标题、正文、公历日期、每年重复与可选照片可以编辑，并可预览；生日/纪念日双方可管理，惊喜卡仅发送者可编辑或撤回。卡片必须收到服务端确认才显示保存成功，失败保留表单。

生日/纪念日当天打开应用展示暖色手绘小贺卡；惊喜卡在指定日展示给伴侣。当天多张合为一组翻页，关闭后在这台设备当天不重复，仍可在列表回看。不同账号的关闭状态隔离。不在记录编辑或详情中弹出。当前使用前台同步，不是后台精确推送；离线设备不会立刻收到新卡/撤回。

录音入口保留原录制/停止/试听/重录/确认/取消链路，改为声音小卡、真实振幅波形和暖色大按钮。波形是最近32次真实采样，不是完整文件波形，试听时保持静止。

## 服务器必须更新一次

更新归档：`E:\MoonLetter\Moon-Letter\infra\local-runtime\cloud-transfer\moonletter-occasions-v25-update-20261004.tar`

在电脑 PowerShell 运行（不要在远端 Linux 终端执行 Windows 路径的 scp）：

```powershell
scp "E:\MoonLetter\Moon-Letter\infra\local-runtime\cloud-transfer\moonletter-occasions-v25-update-20261004.tar" root@192.0.2.10:/root/
```

随后在服务器 SSH 终端执行：

```sh
tar -xf /root/moonletter-occasions-v25-update-20261004.tar -C /opt/moonletter
sh /opt/moonletter/update-private-app-v25.sh
```

最终需看到 health `UP` 和 `V25 backend healthy`。新建V13日子表，保留旧纪念日表/API、原文件、数据库、密钥和端口；更新脚本保留旧JAR并在失败时还原应用文件（不回退数据库迁移）。无需重新配对、清数据。现有公历纪念日迁移到新列表；旧API后续不与新列表双向写入。旧服务器不支持新接口时不会清空缓存，但不能保存新卡。

这次没有代替你登录或更新 ECS。公网 HTTPS/备案状态不因本次更新改变，原有USB/隧道连接仍需保持。

## 验证

服务端 Maven package：115 tests，0失败/错误/跳过。
Android八个已运行模块：204 tests，0失败/错误/跳过；其中 app 90 tests。包括真实客户端→Spring/PostgreSQL→伴侣读取附图与撤回，拒绝保存/断网保留缓存、延迟GET不覆盖已保存卡片、异常200响应保留缓存。

APK SHA256：`283adf7c243c6f80757bf9c6a64dbe5cc9e6817f73946055872ad2c53f68bd55`。
服务端JAR SHA256：`9ca4dac17e843137bba009545f9c283d2de287fc75d8b562d20f7d41972e0db3`。

测试在隔离数据库和模拟器执行，不在真实手机/平板重置配对或创建测试记录。

最终原生：手机18项通过（含真实录音文件、离线重播、账号切换、发送时返回保护及地图/时间轴回归）；相册独立进程12项通过；平板尺寸5项通过。截图已实际查看，分别保存为 `recorder-v25.png`、`birthday-v25.png` 及对应 `-tablet.png`。
第一次合并原生运行有两处测试控件定位不准确与一处相册跨测试协程 Looper 异常；修正定位并将相册独立进程复跑后均通过。没有用首次失败日志冒充通过。
