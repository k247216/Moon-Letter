# 月笺 M1 验收记录

状态：**NOT VERIFIED / 未通过验收**
权威规格：`docs/superpowers/specs/2026-10-01-self-use-m1-design.md`
执行计划：`docs/superpowers/plans/2026-10-01-self-use-m1-implementation.md`

## 1. 当前证据的正确解释

| 已有内容 | 可证明 | 不能证明 |
|---|---|---|
| 服务端现有 28 个测试通过的历史记录 | 部分 service、controller 映射和数据库约束曾通过组件测试 | 服务可按 README 启动；真实认证；真实 HTTP 双端同步；正确游标并发语义 |
| `server/.../e2e/TwoDeviceSyncTest.java` | 内存模型中的部分重试/同步想法 | Spring Boot、PostgreSQL、HTTP、真实事务或两台设备；其 `FakeServer` 不能计作 E2E |
| `android/.../TwoDeviceScenarioTest.kt` | 简单 JVM 数据模型断言 | 两个 Room、Retrofit、WorkManager、服务端或 UI 数据链；相同字符串断言不能计作双端验收 |
| Android Room/KSP 与 Compose 源码 | 工程骨架和目标模块已存在 | `assembleDebug`、instrumented test、真机运行或视觉一致性 |
| 六张 UI 参考图 | 视觉方向（质感、结构、层级）明确 | 当前 App 已按方向实现或已接真实数据；参考稿不是像素基线 |

因此，在下表全部满足前，不得使用“完成”“双端稳定”“E2E 已通过”或“可交付”等表述。

## 2. 必须提交的验收证据

| Gate | 必须证明 | 建议命令或材料 | 当前状态 | 证据 |
|---|---|---|---|---|
| S1 服务启动 | 干净环境配置、Flyway、health | `mvn spring-boot:run` + health 响应 | **PASS（Task 1）** | 见 §6 记录 1 |
| S2 认证与隔离 | bootstrap、一次配对、两 token、第三方拒绝 | real HTTP integration test | NOT RUN | — |
| S3 同步正确性 | typed mutation、幂等、业务写入与 change 同事务 | server focused tests | NOT RUN | — |
| S4 游标并发 | 同空间事务逆序压力下不漏变更 | `ChangeFeedOrderingTest` 重复运行 | NOT RUN | — |
| S5 Server E2E | `RANDOM_PORT` + real PostgreSQL + real HTTP | `SelfUseRecordingLoopE2ETest` | NOT RUN | — |
| A1 Android 构建 | 所有模块编译并产出 APK | `./gradlew :app:assembleDebug` | NOT RUN | — |
| A2 Room/outbox | 本地事务、重启保留、page+cursor 原子提交 | JVM 双 Room 夹具（Robolectric + 真实 Room 数据库文件） | NOT RUN | — |
| A3 Android 网络链 | Retrofit、真实 Room、真实 server | JVM 夹具内的真实 Retrofit → 真实 Spring Boot/PostgreSQL | NOT RUN | — |
| E1 双端闭环 | A 离线写入后在 B 的 Room 与 UI 出现 | 夹具日志（两个 Room + 真实 HTTP）+ 至少一台真机的互见录像；证据须写明配对端用的是真机还是具名模拟器 | NOT RUN | — |
| E2 故障恢复 | 超时重试、重复 ID、逆序重连、进程重启 | 脚本、日志、数据库查询 | NOT RUN | — |
| D1 备份恢复 | 数据与媒体可恢复且校验一致 | 恢复演练报告 | NOT RUN | — |
| D2 选择性导出 | JSON + 可读文档 + 原媒体，无密钥 | 样例导出包与检查清单 | NOT RUN | — |
| U1 视觉一致性 | 四个 M1 页面接真实数据；按规格 §8 三条判据（像纸/像使用者自己的/不像软件）在真实设备上复核，参考稿是方向不是像素基线 | 真实设备截图与差异记录（390×844 为基准之一） | NOT RUN | — |
| U2 适配与可用性 | 小屏、键盘、大字号、滚动、触控目标 | 截图/录像/检查表 | NOT RUN | — |
| H1 记录成本 | 真机实测解锁→保存成功 ≤10 秒、≤4 次交互 | 两台真机各计时一次的秒数与交互数 | NOT RUN | — |
| H2 系统分享入口 | 分享图片/文字直达一条已自动保存的可编辑草稿 | intent 配置、真机录像、双端互见日志 | NOT RUN | — |
| H3 每周回看与提醒 | 到点呈现一条更早记录；无内容时安静跳过；对方新记录一次本地通知 | 真机录像、跳过场景、设置可关闭证明 | NOT RUN | — |
| H4 会话恢复 | 吊销全部会话后用本地管理命令重新进入并双端一致 | 命令、退出码、恢复后拉取比对 | NOT RUN | — |
| H5 备份作为运行能力 | 每日自动执行、健康记录行连续 7 天、版本化、异机副本、介质加密 | `backup_health` 查询结果、调度配置、副本位置 | NOT RUN | — |
| H6 开始真实使用 | Task 11 通过当日在两台真机开始写真实记录 | `docs/testing/real-use-log.md` 的开始日期与已知缺陷清单 | NOT RUN | — |
| H7 无统计功能自查 | 未引入条数/字数/活跃/间隔统计、已读列表、"对方没写"提示 | 自查清单与评审记录（规格 §3.4） | NOT RUN | — |
| H8 可修改性（所有权） | 不配置也能直接用；使用者在应用内自行改掉称呼/身份色/主题/封面，约 2 分钟、零重新构建；已有记录逐字节不变；主题与封面不产生 change row；只有桌面图标名提前决定 | 真机操作录像、改动前后记录校验、change_feed 查询、Task 12b 测试输出 | NOT RUN | — |

**H 类 Gate 与 S/A/E/D/U 同权重。** 缺任意一项即为 `NOT VERIFIED`，不得因为技术链路全绿而优先标记完成——H 类衡量的是这个东西是否真的会被用起来。

本表中"两台真机"按规格 §9 第 7 条理解：至少一台真实 Android 设备，配对端优先第二台真机、确实没有时用带独立数据目录的具名模拟器。凡填写证据的 Gate 必须写明用的是真机还是模拟器、设备名与系统版本；涉及分享面板、通知到点、键盘与触控成本和视觉对照的 Gate 不接受 JVM 夹具证据。

`未经提醒的自发打开次数`（Task 11a 记录的观察基线）只作为维护者自己的判断依据，**不是通过条件**，也不得写进任何界面、报表或统计功能。

## 3. 每条证据的记录格式

每次更新一行 Gate 时，必须同时记录：

- 被验证的 Git commit（完整或可唯一识别的 SHA）；
- 操作系统、JDK、Android SDK、设备/模拟器与 PostgreSQL 版本；
- 完整命令和退出码；
- 通过/失败数量，禁止只写“已测”；
- 日志、截图、录像或导出包的仓库内路径/CI URL；
- 任何跳过项和原因。

真实 E2E 必须跨越进程边界。直接调用 service、内存 FakeServer、mock HTTP、单个 Room 数据库或静态 UI 截图只能归入组件证据。

## 4. 目标核心场景

1. 首位用户初始化，第二位用户使用一次性凭据配对；第三人无法进入空间。
2. 设备 A 断网创建个人文字记录，杀进程后重启，outbox 仍存在。
3. A 恢复网络，操作通过真实 HTTP 写入 PostgreSQL 并产生有序 change。
4. 设备 B 从自己的 cursor 拉取，业务数据与新 cursor 原子写入自己的 Room，时间线显示相同记录与作者时间。
5. 网络在服务端提交后、客户端收到响应前中断；使用同一 operation ID 重试只产生一次业务效果。
6. 两端逆序重连、分页拉取和服务端并发提交均不遗漏 change。
7. 双方分别补充同一共同记录，各自视角并列显示；任何一方都不能改写对方内容。
8. 图片上传成功但发布失败时，ready 媒体可恢复且不会重复创建对象。
9. 从备份恢复到隔离环境后，记录、版本、评论、媒体计数与抽样哈希一致。
10. 按单条和日期范围导出可读内容及原始媒体，且不含令牌、密钥和内部凭据。

## 5. 最终签署

只有所有 Gate 为 `PASS` 且指向同一个候选 commit，才能把首行改为 `VERIFIED`。任何 `NOT RUN`、`FAIL`、无证据的 `PASS` 或依赖假服务的关键链路都保持 `NOT VERIFIED`。

## 6. 开发机证据记录（Task 1 起逐条追加）

### 记录 1：Task 1 服务可复现启动（2026-10-01）

- **Commit**：本文件所在提交（`build(server): add reproducible runtime configuration`）。
- **环境**：Windows 11；OpenJDK 21.0.2（`E:\jdk21-extract\jdk-21.0.2`）；Maven 3.9.15；Docker 29.6.2 运行 `postgres:18-alpine`（infra/compose.yaml，卷挂载已修正为 `/var/lib/postgresql`）。
- **TDD 红灯证据**：`mvn -Dtest=CoupleDiaryApplicationTest test`（配置实现前）→ `Tests run: 3, Errors: 3`，ApplicationContext 加载失败（缺数据源配置）。
- **中间红灯**：JDK 25 上 surefire 触发 Mockito/Byte Buddy agent 附加失败（`Could not initialize plugin: MockMaker`）；按交接文档要求切换 JDK 21 后消除。surefire 已配置 `-XX:+EnableDynamicAgentLoading`。
- **绿灯**：`mvn -Dtest=CoupleDiaryApplicationTest test`（JAVA_HOME=JDK 21，PostgreSQL 为 compose 实例）→ `Tests run: 3, Failures: 0, Errors: 0`，`EXIT=0`；断言 Flyway 已应用迁移且 M1 全部表存在（app_user、couple_space、entry、entry_block、entry_revision、comment、media_asset、idempotency_record、sync_change 等 14 张）。
- **启动验证**：`mvn spring-boot:run -Dspring-boot.run.profiles=dev` → `GET http://127.0.0.1:8080/actuator/health` 返回 `{"status":"UP","groups":["liveness","readiness"]}`。
- **已知环境怪癖**：本机存在遗留环境变量 `SERVER__PORT=63834`，被 relaxed binding 读取导致端口冲突；启动时用 `--server.port=8080` 显式覆盖，已写入 README。

### 记录 2：Task 2 设备会话认证（2026-10-01）

- **Commit**：`feat(auth): add bootstrap and device bearer sessions`（本文件所在提交）。
- **实现**：迁移 `V6__device_sessions_and_bootstrap.sql`（device_session 表、每成员单活跃会话部分唯一索引、display_name 扩到 1–40 字符）；`DeviceSessionAuthenticationFilter`（Bearer → SHA-256 哈希查会话，直接写 401 响应避免 /error 转发覆盖状态码）；`SecurityConfig`（CSRF/表单/Basic 关闭，仅 health、bootstrap、pair、error 公开）；`BootstrapController/Service`（空安装 + BOOTSTRAP_SECRET 才可执行，SecureRandom 256-bit token，仅存哈希）；四个 controller 全部改用 `@AuthenticationPrincipal`，`X-User-Id`/`fromHeader` 从主代码清零。
- **测试**：新增 `BootstrapAuthenticationTest`（真 PostgreSQL 独立库 `moon_letter_boot_test` + RANDOM_PORT 真 HTTP）：错误密钥 403、首次 bootstrap 201 返回一次性 token、二次 409、无效 bearer 401、伪造 X-User-Id 401、token 读取空间 200。
- **结果**：`mvn test` → `Tests run: 31, Failures: 0, Errors: 0`，`EXIT=0`。
- **修复的基线缺陷**：`CoupleService.mapCouple` 缺 `rs.next()`（readSpace 必崩，bootstrap 后首次暴露）；旧 MockMvc 测试迁移到 `TestAuth.deviceSession` 认证后处理器。
- **TDD 红灯**：测试先于实现编写（当时编译失败/接口不存在即为红灯状态；首次运行 401→/error→403 的发现过程见 filter 注释）。

### 记录 3：Task 3 一次性安全配对（2026-10-01）

- **Commit**：`feat(couple): secure one-time partner pairing`。
- **实现**：迁移 V7（`code_hash`→`token_hash`；废除 couple_id 全局唯一，改为"每空间仅一条未消费令牌"部分唯一索引）；配对令牌改为 256-bit SecureRandom base64url（43 字符），仅存哈希，15 分钟过期，单次消费；`pair` 改为免认证（凭令牌本身），事务内创建第二个成员 + user_profile + 首个 device_session，返回一次性 deviceToken 与 userId；新增 `POST /api/v1/couple/{id}/pairing-token` 换发（撤销未消费旧令牌）；删除 6 位数字枚举路径（normalizePairingCode/nextPairingCode 整体移除，无双机制并存）；SecurityConfig/filter 公开路径修正为 `/api/v1/couple/pair`。
- **测试**：`PairingFlowTest`（独立真库 moon_letter_pair_test + 真 HTTP）：令牌 43 字符且两次生成不同、过期令牌 409、换发后仅一条未消费、配对后 2 成员 ACTIVE、伙伴 bearer 可读空间、读空间不泄露 pairingToken、复用 409；`CoupleApiTest` 8 个切片测试更新到新协议。
- **结果**：`mvn -Dtest=CoupleApiTest,PairingFlowTest test` → `Tests run: 9, Failures: 0, Errors: 0`，`EXIT=0`。
- **TDD 红灯**：先写 PairingFlowTest，首跑在配对响应 500（Instant→timestamptz 绑定失败）暴露问题，修复后转绿。

### 记录 4：Task 4 actor 归属校验（2026-10-01）

- **Commit**：`fix(server): enforce actor and media authorization`。
- **实现**：所有业务变更服务方法显式接收 actorId：createDraft(actorId, cmd)（拒绝代他人建稿）、applyChanges(actorId,…)（拒绝非本人块变更 + 既有块 created_by 归属校验：伴侣可追加自己的块、不可改/删作者块）、resolveConflict、readTimeline(actorId,…)；publish 增加 mediaService.requireReady（发布时所有引用媒体必须 READY 且属于本空间）；MediaService 删除可空用户重载，complete/markFailed 强制 owner。
- **测试**：新增 `EntryOwnershipTest`（独立真库 moon_letter_own_test，种子两个空间三成员）：外空间成员读条目被拒、伴侣/外来者发布他人草稿被拒（作者成功）、伴侣可追加自己块但改/删作者块被拒、发布引用 UPLOADING 媒体被拒、引用外空间 READY 媒体被拒。
- **结果**：`mvn test` → `Tests run: 39, Failures: 0, Errors: 0`，`EXIT=0`。
- **TDD 红灯**：首跑 5 个断言中 3 处期望异常类型与实际策略不符（requireMember 抛 403 ResponseStatusException），修正断言后转绿；归属逻辑本身无缺陷。

### 记录 5：Task 5 类型化幂等操作（2026-10-01）

- **Commit**：`feat(sync): dispatch typed idempotent operations`。
- **实现**：新增 `SyncOperationDispatcher`（封闭 OperationType 枚举，当前仅 CREATE_PERSONAL_ENTRY），客户端不再能提交任意 change payload；请求 DTO 改为 `{operationId, coupleId, payloadHash, operationType, payload}`，hash 对 operationType+payload 规范化；dispatcher 校验 payload（作者必须=认证用户、TEXT-only 块、必填时间/时区）后调用 EntryService.createDraft；幂等声明、业务变更、change 追加、响应存储同处 executeOnce 的一个 @Transactional 边界；entry 包的跨包 DTO（EntryMode/BlockType/BlockMutation/CreateEntryCommand/EntryView）提升为 public。
- **测试**：新增 `TypedSyncOperationTest`（独立真库 moon_letter_sync_test + 真 HTTP + 手工种子设备会话）：类型化操作改变 entry/entry_block/sync_change 表、重复 (couple_id, operation_id) 回放且无第二效果（响应经 jsonb 归一化后语义相等）、同 ID 不同 payload 409、注入失败后幂然声明/业务数据/变更行三者全部回滚且同 ID 可重试成功、未知操作类型 400。
- **结果**：`mvn test` → `Tests run: 45, Failures: 0, Errors: 0`，`EXIT=0`。
- **TDD 红灯**：首跑暴露两处测试缺陷（payload() 双次调用产生不同随机 blockId 导致 hash 自不匹配；回放体键序差异需语义比较），实现本身无缺陷，修正后转绿。

### 记录 6：Task 6 每空间有序变更流（2026-10-01）

- **Commit**：`fix(sync): serialize per-couple change sequences`。
- **实现**：迁移 V8（`couple_sync_state(couple_id, last_space_sequence)`；sync_change 增加 `space_sequence` 并建 `(couple_id, space_sequence)` 唯一索引；存量行按 change_seq 分区回填；列 NOT NULL）；`appendChange` 在变更事务内 `INSERT ... ON CONFLICT DO NOTHING` + `UPDATE ... RETURNING`（状态行 FOR UPDATE 级锁语义）分配序列，同空间并发变更按提交顺序串行化；readChanges 改按 `space_sequence` 排序/过滤，`nextSequence` 即已完整返回的最大 space_sequence，显式 `hasMore`；客户端游标不再依赖全局 identity。
- **测试**：新增 `ChangeFeedOrderingTest`（独立真库 moon_letter_order_test）：事务 A 持锁未提交时 B 无法完成分配（400ms 内未完成+线程存活），A 提交后 B 获得下一个序列；分页 after=0/limit=1 先见 A 的变更且 hasMore=true，翻页得 B 的变更——游标推进不可能跳过已提交变更。SchemaConstraintTest 增加 (couple_id, space_sequence) 唯一性与 NOT NULL 断言。
- **结果**：`mvn test` → `Tests run: 47, Failures: 0, Errors: 0`，`EXIT=0`。
- **TDD 红灯**：并发测试首版错误地在工作线程外开启事务（Spring 事务线程绑定），重写为主线程持事务 + 工作线程阻塞的正确形态后转绿。
