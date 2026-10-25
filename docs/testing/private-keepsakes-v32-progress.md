# 执行记录 — 2026-10-10-private-keepsakes

计划：docs/superpowers/plans/2026-10-10-private-keepsakes.md

- Ruling: 用户要求持续推进，不重复等待设计确认；使用现有 dirty feature 分支 m1-stable-recording-loop，不创建丢失未提交工程状态的 worktree，不自动提交全工程。
- 视频任务由 Galileo 独立实现；后端 capsule由 Erdos；后端记录/卡片隐私由 Hubble；Android胶囊由 Banach。主线程承担 Android模型/Room/编辑器/卡片/提醒/集成。
- 视频已有红测试2项（上一轮）；此前实现补丁被审批额度阻断，未执行。代理正在实现。
- Room撤回测试 RED：DELETE 投影仍保留本机记录/块，已据此加撤回逻辑；未完成绿色验证。
- 卡片私密测试 RED：PRIVATE SURPRISE仍弹到recipient、不提醒author；已据此加 visibility 读取与 opensFor；未完成绿色验证。
- 草稿 visibility持久化测试已通过首次运行；另需验证恢复编辑器时不回退共享。
- Ruling: 共同记录包含双方贡献，不允许作者单方面隐藏共同记录；当前只对 PERSONAL 开放隐私切换。成本：共同记录仍共享，界面说明，不悄悄改变他人贡献权限。
- 安全保护：PRIVATE保存需先确认服务端支持隐私，否则旧服务器会忽略新字段；新增 authenticated capabilities preflight 由backend实现。新APK不能在旧服务器上假装私密保存成功。
- 远端服务器本轮尚未修改；没有USB实际设备验收，没有清理任何业务数据。

## 2026-10-10 集成验证

- 服务端最终打包：151 tests，0 failures/errors/skips；`server/private-keepsakes-package-final.log`。制品包含 V14 隐私及 V15 云端胶囊。
- Android 第一次全量出现旧 Room fixture 未注册 4→5 迁移，以及 Robolectric 未初始化 WorkManager 导致已确认保存被误报失败；已修测试注册及 best-effort 调度。第二次全量 `infra/local-runtime/private-v32-full-final.log` BUILD SUCCESSFUL，301 tests、0 failures/errors/skips（9模块 XML 汇总），APK和设备测试APK编译成功。
- 真实 HTTP/PostgreSQL 双客户端集成：私密创建/作者恢复、共享/隐藏撤下，以及胶囊提前正文剥离/到期读取、PRIVATE SURPRISE 无 recipient 均已验证。不是生产/跨省/物理设备验收。
- 独立审查：Critical 同空间账号切换复用 Room；Important 卡片撤回仍用旧缓存提醒、年度提醒当天续排缺失。交付前修复并追加回归（进行中）。
- 部署脚本只替换 app，先备份并隔离恢复验证；迁移至14+后禁止直接降级旧JAR，否则旧版忽略隐私。迁移后失败采取停止app而非不安全自动回滚。shell syntax已用离线只读容器通过。
- 视频仍需真实原视频/设备解码验收；自动测试证明导入字节与下载/播放器状态链路，不证明所有 HEVC/MOV 等编码兼容。

## 最终交付核对

- 3项审查已修：用户独立数据库+旧库归属别名（A返回保留原队列/媒体/游标），SessionScopedContent在账号变化时销毁旧remember页面/DAO句柄；提醒Worker必须确认刷新成功；年度送达当天续排下一年。
- Capsule旧本机草稿仅在本人确认后导入，默认PRIVATE；PRIVATE surprise未绑定recipient时可保存，之后确认partner后可手动转为SHARED。
- Windows SQLite原生路径过长导致新库回归失败，已缩短数据库文件名（完整SHA256仍作归属校验）；Room未做破坏迁移。
- 视频回归修测试IO可见结果等待及活动Dialog查找；历史视频用例在整套原生Robolectric中出现过窗口初始化超时，定向7项通过、最终app全量158项通过，保留失败截图/状态采集以便继续观察。真实Android播放器未验证，不以这些结果推断所有编码兼容。
- 最终app日志 `infra/local-runtime/private-v32-video-window-diagnostic.log` BUILD SUCCESSFUL；9模块XML汇总320 tests，failures/errors/skips均0。随后 `infra/local-runtime/private-v32-artifacts-final.log` aggregate tests + app/debugAndroidTest assemble BUILD SUCCESSFUL（255 up-to-date，使用上述验证缓存），没有重跑或隐瞒跳过的测试。
- server 151 tests全绿；JAR复制自已验证package，SHA256 9bc503eeffc0a3adbb710cee76b9c346e6b8359e30d3888c769bf8462915c413，确认包含V14/V15和CapsuleController。
- V32 APK versionCode32 / 0.1.0+private-keepsakes-v32-20261010，签名指纹74001d60036e79f897f761523c7cdbd87580b0a04256125973ee4bf66fd8a015与原包一致；SHA256 9849e142cfdb7efb04319bbacffa391a51cb73e36696b9dd0efa6c4c3329421e。
- 服务端tar解包+sha256sum校验+sh -n已在断网只读容器中通过；只含JAR、hash、更新脚本、README，不含配置/密钥/清数据工具。仍未执行远端更新及USB/跨省双机验收。
