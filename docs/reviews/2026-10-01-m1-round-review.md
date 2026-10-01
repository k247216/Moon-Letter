# M1 第一轮推进复核

复核日期：2026-10-01
复核基线：`5a38d39 fix(android): refresh timeline snapshot when returning to the tab`
复核范围：Task 1–11、设备绑定追加提交、验收记录、执行计划状态和仓库卫生
结论：**服务端 S1–S5 与 Android A1–A3 的开发机证据可以落章；M1 仍为 NOT VERIFIED。** H 类 0/8、E1 只有 JVM 链路、E2/D/U 未完成，且存在会话锁死与同步重试缺陷。

## 1. 接受的证据

| Gate | 复核结论 | 依据与边界 |
|---|---|---|
| S1 | PASS | `703aa3a`；配置、Flyway、health 与真实 PostgreSQL 记录完整 |
| S2 | PASS（仅当前定义） | `787ce73`、`34e7f3e`、`1b6c41b`；bootstrap、配对、bearer、第三方隔离成立。**不代表 H4 会话恢复完成。** |
| S3 | PASS | `7f84d70`；类型化操作、幂等回放、失败回滚有真实数据库/HTTP 证据 |
| S4 | PASS | `7fa0bd5`；每空间序列、唯一约束和并发阻塞测试与实现一致 |
| S5 | PASS | `f410974`；RANDOM_PORT、真实 HTTP/PostgreSQL、双 token 和分页链路成立。进程级数据恢复仍归 E2/H4 |
| A1 | PASS（构建） | `319b6b6`；APK 与 JVM 测试成功。`connectedDebugAndroidTest` 未运行，不得外推为设备可用性 |
| A2 | PASS（JVM Room） | `ebb4f4a`；两个真实 Room 文件、outbox/cursor 原子性与重开库证据成立 |
| A3 | PASS（JVM 真实网络链） | `453d978`、`8491f67`；真实 Retrofit → Spring Boot → PostgreSQL → 第二 Room 成立。真机 UI 互见仍未完成 |

本机没有复跑 Windows/Android 环境，而是对提交、实现路径、测试形态和证据记录进行了静态交叉核对。开发机的命令输出仍是执行证据来源；本复核只负责是否接受其支撑对应 Gate。

## 2. 阻塞问题（按风险排序）

### P0：H4 会话恢复不存在

全仓库没有 `SessionAdminCommand`、`SessionRecoveryTest` 或可执行的 `RECOVERY_SECRET` 路径。当前行为是：

- bootstrap 只允许空安装；
- 换发/吊销依赖已有有效会话；
- 配对令牌由已认证成员生成；
- `DeviceSessionService.issueSession` 不会先吊销旧会话，单活跃会话唯一约束会使直接换机签发失败。

两台设备同时丢失会话后无法恢复既有存档。Task 2 只能部分勾选，Task 11a、H4、E2 和最终验收必须保持未通过。下一轮第一优先级应是本地管理命令、无 token 审计记录、全会话吊销/重签发测试和真实恢复演练。

### P1：Android outbox 重试链不闭合

- `SyncEngine.pushPending` 遇到 `RETRYABLE_FAILURE` 或异常后仍继续发送后续操作，不满足严格 FIFO。
- `catch (Throwable)` 会吞掉协程取消并把它记为普通重试；应重新抛出 `CancellationException`。
- `SyncWorker` 忽略 `pushPending()` 的结果；push 被安排重试而 pull 成功时，worker 返回成功，不会自动触发下一次 WorkManager 重试。

必须先补三条失败测试，再修改实现：首条失败时第二条未调用、取消向上传播、push retryable 使 worker 返回 retry。完成前 E2 不得通过。

### P1：设备凭据和开发网络配置不适合真实使用

- bearer token 以明文写入普通 `SharedPreferences`；至少应由 Android Keystore 支持的加密存储保护。
- manifest 全局 `usesCleartextTraffic="true"`，默认地址硬编码为开发机局域网 IP；只能作为开发构建配置，不能进入真实使用候选包。
- `SyncSession.load` 对损坏 UUID 直接抛异常，没有清理损坏会话或回到绑定页。

这些问题不推翻 JVM 网络链证据，但会阻止 Task 11a 在真机开始真实使用。

### P1：时间线不是持续响应式更新

最新提交只在切回 `timeline` tab 时重新读取一次快照。如果同步在用户停留时间线期间完成，界面不会立即变化；必须离开并返回才刷新。Room DAO 应暴露 `Flow`，ViewModel 持续收集，或同步完成后显式刷新。

### P1：备份、恢复与真实使用全部未开始

`real-use-log.md`、`ShareReceiverActivity`、`WeeklyReviewWorker`、`ProfileEditScreen`、`PreferenceDao`、`ExportService`、`scripts/backup.sh` 均不存在。Task 11a/11b/11c/12/12b/13/14 与 H1–H8 不能因为技术链路变绿而提前。

## 3. 本轮直接修正

- `BootstrapService` 已改为基于 UTF-8 字节的 `MessageDigest.isEqual`，空配置、空输入和错误秘密均拒绝。
- `CoupleDiaryApplication` 已显式排除 `UserDetailsServiceAutoConfiguration`，避免生成无用的默认内存用户和随机密码日志。
- 新增纯 JVM `SecurityConfigurationContractTest`：先看到缺少比较方法的编译红灯，再看到自动配置排除断言红灯；实现后 `Tests run: 2, Failures: 0, Errors: 0`。
- `Scaffold` 的 `padding` 没有传给内容容器，真实设备上时间线可能被底部导航遮挡；留待 U1/U2 前修复。
- 原提交包含 Kotlin/JVM 崩溃日志；本次已删除并加入 `.gitignore`，日志仍可从 Git 历史恢复，因此如包含秘密还需轮换。本次静态抽查未把这些日志作为验收证据。

## 4. 文档状态修正

- 执行计划已按逐条证据更新为 **62/118**（复核当日数字；本轮后为 70/118，见本文 §6 与验收记录 23）；Task 1/3/5/6/9/11 的当前定义全部勾选，Task 2/4/7/8/10 只勾已有证据的子项，其余 56 项保持未完成。
- Task 2 的恢复、Task 4 的 profile 越权、Task 7 的进程重启/聚焦命令、Task 8 的 connected smoke、Task 10 的严格 FIFO/完整触发、Task 11a 之后的项目保持未勾选。
- Task 9/11 的测试路径从旧的 `androidTest`/connected 表述改为已经批准且实际执行的 Robolectric `src/test` 夹具；真机 smoke 和 UI 录像没有被取消，仍由 Task 8/11a/14 约束。
- Gate 表的 PASS 只覆盖各行明确写出的技术能力；顶部总状态继续为 `NOT VERIFIED`。

## 5. 下一轮强制顺序

1. H4 会话恢复与演练。
2. 修复 outbox FIFO、取消传播和 WorkManager retry。
3. 凭据加密、开发/真实网络配置分离、损坏会话回退。
4. 真机 smoke 与 Task 11a；建立 `real-use-log.md`。
5. Task 11b/11c、Task 12/12b、Task 13、Task 14，按计划继续。

在第 1–4 项完成前，不进入共同记录扩展、媒体、备份 UI 或视觉完成宣告。

## 6. 后续状态（同日追加，不修改上面作为当时证据的正文）

复核基线之后的提交已闭合 §2 的 P0 与三条 P1 中的技术部分；本节是读这份复核时的现行索引。

| 复核项 | 现状 | 依据 |
|---|---|---|
| P0 H4 会话恢复 | 代码与真库测试已存在：`SessionAdminCommand`（本地 CLI，web 显式关闭）+ `SessionRecoveryTest`；新增 `issueReplacementSession`＝先吊销该成员全部会话再签发（普通 `issueSession` 仍不吊销，只用于空安装），bootstrap 找回与恢复命令都走它；重装后可用 `BOOTSTRAP_SECRET` 找回创始成员槽位，掉线一方由对端生成 `REJOIN` 配对码 | `51b4a4d`、`52b1f59`、`9d3ca4c` |
| P1 outbox 重试链 | 严格 FIFO、`CancellationException` 重新抛出、worker 依据 push 结果返回 retry | `1223c53` |
| P1 凭据与网络配置 | 令牌改由 Keystore 密封存储；明文存储取消；`SyncSession.load` 对损坏会话回退绑定页 | `3a50c8f` |
| P1 时间线非响应式 | Room DAO 暴露 `observeTimeline()` / `observeBlocks()`，ViewModel 持续收集 | `d107bba` |
| P1 Scaffold 遮挡 | 记录页关闭按钮不再压状态栏 | `fe697d7` |
| P1 备份/恢复/真实使用 | `scripts/backup.sh`、`scripts/restore.sh` 已存在；每周回看与新记录通知（Task 11c）已落地；`real-use-log.md` 仍未建立，ShareReceiver/ProfileEdit/ExportService 的完成度以计划台账为准 | `138aaea`、验收记录 22 |

**仍然成立的部分**：§1 里 A1 的"不得外推为设备可用性"、§2 结论中所有需要设备的证据（真机 smoke、Task 11a、E1 互见、E2 故障恢复、H1–H8、U 类视觉）一件都没有变成真机证据，M1 继续是 `NOT VERIFIED`。§5 的第 1–3 项做完不等于通过，只等于可以在真机上开始验证。
