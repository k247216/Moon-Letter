# 月笺 M1 验收记录

状态：**NOT VERIFIED / 未通过验收**
权威规格：`docs/superpowers/specs/2026-10-01-self-use-m1-design.md`
执行计划：`docs/superpowers/plans/2026-10-01-self-use-m1-implementation.md`
本机复核：`docs/reviews/2026-10-01-m1-round-review.md`（基线 `5a38d39`）

## 1. 当前证据的正确解释

| 已有内容 | 可证明 | 不能证明 |
|---|---|---|
| 服务端现有 28 个测试通过的历史记录 | 部分 service、controller 映射和数据库约束曾通过组件测试 | 服务可按 README 启动；真实认证；真实 HTTP 双端同步；正确游标并发语义 |
| `server/.../e2e/TwoDeviceSyncTest.java` | 内存模型中的部分重试/同步想法 | Spring Boot、PostgreSQL、HTTP、真实事务或两台设备；其 `FakeServer` 不能计作 E2E |
| 已删除的 `android/.../TwoDeviceScenarioTest.kt` | 历史上只有简单 JVM 数据模型断言 | 不能作为两个 Room、Retrofit、WorkManager、服务端或 UI 数据链证据；当前 A3/E1 证据改由 `TwoDeviceRecordingLoopTest` 提供 |
| Android Room/KSP 与 Compose 源码 | 工程骨架和目标模块已存在 | `assembleDebug`、instrumented test、真机运行或视觉一致性 |
| 六张 UI 参考图 | 视觉方向（质感、结构、层级）明确 | 当前 App 已按方向实现或已接真实数据；参考稿不是像素基线 |

因此，在下表全部满足前，不得使用“完成”“双端稳定”“E2E 已通过”或“可交付”等表述。

## 2. 必须提交的验收证据

| Gate | 必须证明 | 建议命令或材料 | 当前状态 | 证据 |
|---|---|---|---|---|
| S1 服务启动 | 干净环境配置、Flyway、health | `mvn spring-boot:run` + health 响应 | **PASS（Task 1）** | 见 §6 记录 1 |
| S2 认证与隔离 | bootstrap、一次配对、两 token、第三方拒绝 | real HTTP integration test | **PASS（Task 2/3；不含 H4 恢复）** | 见 §6 记录 2–4 与复核报告 |
| S3 同步正确性 | typed mutation、幂等、业务写入与 change 同事务 | server focused tests | **PASS（Task 5）** | 见 §6 记录 5 |
| S4 游标并发 | 同空间事务逆序压力下不漏变更 | `ChangeFeedOrderingTest` 重复运行 | **PASS（Task 6）** | 见 §6 记录 6 |
| S5 Server E2E | `RANDOM_PORT` + real PostgreSQL + real HTTP | `SelfUseRecordingLoopE2ETest` | **PASS（Task 7）** | 见 §6 记录 7 |
| A1 Android 构建 | 所有模块编译并产出 APK | `./gradlew :app:assembleDebug` | **PASS（Task 8 构建；connected smoke 未运行）** | 见 §6 记录 8 与复核报告 |
| A2 Room/outbox | 本地事务、重启保留、page+cursor 原子提交 | JVM 双 Room 夹具（Robolectric + 真实 Room 数据库文件） | **PASS（Task 9）** | 见 §6 记录 9 |
| A3 Android 网络链 | Retrofit、真实 Room、真实 server | JVM 夹具内的真实 Retrofit → 真实 Spring Boot/PostgreSQL | **PASS（Task 10/11）** | 见 §6 记录 10、11 |
| E1 双端闭环 | A 离线写入后在 B 的 Room 与 UI 出现 | 夹具日志（两个 Room + 真实 HTTP）+ 至少一台真机的互见录像；证据须写明配对端用的是真机还是具名模拟器 | **部分通过（Task 11 夹具链路 PASS；真机 UI 互见录像 NOT RUN）** | 见 §6 记录 11 |
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

- **Commit**：`703aa3a build(server): add reproducible runtime configuration`。
- **环境**：Windows 11；OpenJDK 21.0.2（`E:\jdk21-extract\jdk-21.0.2`）；Maven 3.9.15；Docker 29.6.2 运行 `postgres:18-alpine`（infra/compose.yaml，卷挂载已修正为 `/var/lib/postgresql`）。
- **TDD 红灯证据**：`mvn -Dtest=CoupleDiaryApplicationTest test`（配置实现前）→ `Tests run: 3, Errors: 3`，ApplicationContext 加载失败（缺数据源配置）。
- **中间红灯**：JDK 25 上 surefire 触发 Mockito/Byte Buddy agent 附加失败（`Could not initialize plugin: MockMaker`）；按交接文档要求切换 JDK 21 后消除。surefire 已配置 `-XX:+EnableDynamicAgentLoading`。
- **绿灯**：`mvn -Dtest=CoupleDiaryApplicationTest test`（JAVA_HOME=JDK 21，PostgreSQL 为 compose 实例）→ `Tests run: 3, Failures: 0, Errors: 0`，`EXIT=0`；断言 Flyway 已应用迁移且 M1 全部表存在（app_user、couple_space、entry、entry_block、entry_revision、comment、media_asset、idempotency_record、sync_change 等 14 张）。
- **启动验证**：`mvn spring-boot:run -Dspring-boot.run.profiles=dev` → `GET http://127.0.0.1:8080/actuator/health` 返回 `{"status":"UP","groups":["liveness","readiness"]}`。
- **已知环境怪癖**：本机存在遗留环境变量 `SERVER__PORT=63834`，被 relaxed binding 读取导致端口冲突；启动时用 `--server.port=8080` 显式覆盖，已写入 README。

### 记录 2：Task 2 设备会话认证（2026-10-01）

- **Commit**：`787ce73 feat(auth): add bootstrap and device bearer sessions`。
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

### 记录 7：Task 7 服务端真实记录回路 E2E（2026-10-01）

- **Commit**：`test(server): add real HTTP PostgreSQL recording loop`。
- **实现**：新增 `SelfUseRecordingLoopE2ETest`（`@SpringBootTest(RANDOM_PORT)` + 独立真库 moon_letter_e2e_test，全部真实 HTTP，双 bearer token）：bootstrap → 配对（B 端建号+会话）→ A 经类型化同步操作建个人草稿 + 幂等重试（仅 1 条 entry）→ A 发布 → B 分页拉取变更流（limit=1 两页，第二页含 PUBLISH）→ B 读取已发布条目 → 伪造 token 401 → 协作条目双视角（B 追加自己的块成 2 块，改 A 块 403）。配套：publish 现在也追加 PUBLISH 变更行（否则 B 永远感知不到发布）；`TwoDeviceSyncTest` 更名 `TwoDeviceSyncModelTest`（FakeServer 模型/单元测试，注明不计入 E2E 数量）。服务端重启项在本机以 `mvn spring-boot:run` 重启 + curl health 复验过（记录于 Task 1），测试内不做进程级重启。
- **结果**：`mvn test` → `Tests run: 48, Failures: 0, Errors: 0`，`EXIT=0`（数据库：本地 Docker PostgreSQL 18，`jdbc:postgresql://localhost:5432/moon_letter_e2e_test`）。
- **TDD 红灯**：三轮修正——测试端 ClassCastException（MutationResult.body 是 JSON 字符串需再解析）、协作建稿 400（BlockMutation.payload 应为 JSON 字符串而非嵌套对象）。均为测试侧问题，服务端实现无改动。

### 记录 8：Task 8 Android 可编译基座（2026-10-01）

- **Commit**：`build(android): establish verified app and test baseline`。
- **环境**：JDK 21.0.2（E:\jdk21-extract\jdk-21.0.2）、Android SDK 37.0（E:\Android\Sdk，含 emulator + google_apis x86_64 系统镜像）、Gradle 9.8.0。
- **实现**：新建 `android/gradle.properties`（useAndroidX 等）；app manifest 补 `android:theme` + 新增 `styles.xml` + INTERNET 权限；PersonalEditorScreen 的 verticalScroll import 修正为 foundation 包；所有 android 模块补 `testInstrumentationRunner`；app 补 :core:database/network/sync 依赖（WorkManager 进 APK）；androidTest 统一挂 Compose BOM + ext-junit/test-core；**去掉无 Compose 代码模块（database/network/sync）的 Compose 插件**（基线失败证据：Compose Compiler 要求 runtime 在类路径）；core:sync 的 JUnit5 依赖换成 kotlin-test-junit 统一 JUnit4。
- **结果**：`./gradlew :app:assembleDebug :app:testDebugUnitTest` → `BUILD SUCCESSFUL`（EXIT=0）；`./gradlew test`（全部模块单测）→ `BUILD SUCCESSFUL`（EXIT=0）。
- **诚实记录——connectedDebugAndroidTest 未跑**：模拟器在本宿主反复静默退出（WHPX 报 operational、guest 内核参数已发出后进程消失，多轮复现；日志存 .workbuddy/tmp）。按计划 Task 8 的既定决策，主证明手段为 Robolectric（真 Room + 真 HTTP + 真 PG），模拟器仅用于 smoke 与 Task 14 录像，故此缺口不阻塞主链路证明；后续在真机（Task 11 的两台设备）上补 smoke。

### 记录 9：Task 9 Room 存储与持久化 outbox（2026-10-01）

- **Commit**：`feat(android): persist entries outbox and sync cursor in Room`。
- **实现**：`RoomSyncStore` 实现 SyncStore（pending/markApplied/markRetry/markConflict 走 OutboxDao；applyChangesAtomically 在 Room 事务内应用变更页 + 写 cursor，REPLACE 语义使重复投递无害）；SyncCursorDao 新增；SyncStore 接口下沉 :core:model 消除模块环。
- **测试**：`RoomSyncStoreTest`（Robolectric JVM，真 Room，两个独立临时库文件代表两台设备）：条目+outbox 同事务提交、杀进程重开库后 pending 仍在、坏 payload 使整页+cursor 回滚、重复变更无害且 cursor 正确推进。
- **结果**：`./gradlew :core:database:testDebugUnitTest` → `BUILD SUCCESSFUL`（EXIT=0，5/5）。
- **环境坑（重要）**：本机中文用户名导致 Robolectric 原生运行时解压到 TEMP 时 ICU 路径乱码崩溃；解决：以 `TEMP/TMP=E:\tmp` 运行 Gradle。此坑同时解释了模拟器异常的部分嫌疑。

### 记录 10：Task 10 Retrofit 同步引擎装配（2026-10-01）

- **Commit**：`feat(android): wire Room Retrofit synchronization`。
- **实现**：`RetrofitFactory.kt`（Retrofit/OkHttp、bearer 注入拦截器、10s/15s 超时、UUID Gson 适配器、HTTP→状态映射：200→APPLIED、409→CONFLICT、401→UNAUTHORIZED、其余→RETRYABLE_FAILURE；`operationPayloadHash` 与服务端 SyncPayloadHasher 同一规范 operationType+"\n"+payload）；SyncStore 增加 currentCursor；SyncEngine 增加 UNAUTHORIZED 即停并报 needsRePair、pullAll（循环拉到 has_more=false）；LocalEntryWriter outbox payload 改为服务端 CREATE_PERSONAL_ENTRY 契约 JSON（org.json 构建）；SyncWorker 用 pullAll + 网络约束 + 指数退避（best effort，不承诺分钟级送达，不依赖 FCM）；app 侧 `SyncSession`（SharedPreferences 存 token/coupleId/userId/baseUrl，默认 http://10.0.2.2:8080）+ 启动与回前台触发同步。
- **测试**：SyncEngineTest 扩到 7 个：FIFO push、超时后复用同一幂等 ID 重试成功、pull 循环到 has_more=false 且游标=9、401 推/拉均停止并报 needsRePair、失败前游标不变、指数退避与冲突保留。
- **结果**：`./gradlew :core:sync:testDebugUnitTest :app:assembleDebug` → `BUILD SUCCESSFUL`（EXIT=0，7/7）；全模块 `./gradlew test` → `BUILD SUCCESSFUL`（EXIT=0）。
- **TDD 红灯**：一处测试缺陷（FakeApi 在抛超时异常前未记录 ID），修正后转绿；另修正一处 SyncEngine 缺 import。

### 记录 11：Task 11 个人文字双端竖切（2026-10-01）

- **Commits**：`8491f67 feat(sync): two-device recording loop end-to-end`（竖切与两处服务端修复）；绑定入口见记录 12。
- **测试形态**：按 Task 8 既定决策以 JVM 夹具为主证明——`TwoDeviceRecordingLoopTest`（Robolectric + **两个独立真 Room 数据库文件** + **真 Retrofit HTTP** + **真实 Spring Boot 进程**（server jar，端口 18080）+ **真 PostgreSQL**（独立库 `moon_letter_slice_test`）），位于 `android/app/src/test/`。无 fake server、无内存 store。
- **覆盖**：A 经真实绑定流程初始化（SetupViewModel+RetrofitSessionApi：bootstrap → 换发配对令牌）→ B 用同一令牌配对取得独立会话 → 会话 SharedPreferences 落盘回读 → A 离线写（Room+outbox 同事务）→ B 先拉确认为空 → A 推送 → 幂等重放（replayed=true，仅一条）→ B 从自己 Room 读到同 ID 条目（标题「傍晚散步」与块数一致）→ 反向 B 写 A 拉 → 双库杀进程重开数据完好、timeline 2 条。
- **竖切暴露并修复的缺陷**：
  1. 服务端 `SyncOperationDispatcher` 自行 `UUID.randomUUID()` 生成 entryId，无视客户端本地 ID（违背 local-first）——改为 `CreateEntryCommand` 携带客户端 entryId，服务端采纳；
  2. 服务端 `EntryView` 缺 title/occurredAt/occurredTimezone，B 端拉到裸 ID——`EntryView`/`EntryRow`/SQL 映射同步扩列。
- **结果**：`mvn test`（服务端）→ `Tests run: 48, Failures: 0`；`./gradlew :app:testDebugUnitTest` → `BUILD SUCCESSFUL`（EXIT=0），竖切 1/1 通过；`./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`（EXIT=0，app-debug.apk ≈20.5 MB）。
- **诚实记录**：E1 的真机互见录像部分仍未完成（需要两台目标设备，属 Task 11a/14 现场证据）；本记录仅覆盖 JVM 夹具链路。UI 侧（TimelineViewModel/编辑器）已确认只读 Room、经 Room 写入，但整链 UI 现场验证待真机。

### 记录 12：设备绑定入口（Task 11a 前置，2026-10-01）

- **背景**：Task 11 竖切绿了但真机装上 APK 无法绑定——`SyncSession` 只能被测试注入，App 没有任何 bootstrap/配对 UI；manifest 也没开 cleartext（真机 HTTP 到局域网服务器必被拦）。没有这个入口，Task 11a「当天开始真实使用」无从谈起。
- **实现**：`core:network` 新增 `SessionApi`/`RetrofitSessionApi`（POST /api/v1/bootstrap、/api/v1/couple/{id}/pairing-token、/api/v1/couple/pair；UUID Gson 适配器；按 baseUrl 每次构建 client，支持换机换地址）；app 新增 `SetupScreen`/`SetupViewModel`（「我是第一位」：地址+称呼+BOOTSTRAP_SECRET → 建空间 → 展示一次性配对令牌；「我是伴侣」：地址+令牌 → 配对入空间；会话经 LaunchedEffect 落盘 SharedPreferences）；`AppNavigation` 无会话时整屏进 Setup；manifest 开 `usesCleartextTraffic`（自签名 HTTP 开发期决策，M2 换 HTTPS 时收回）；默认服务器地址改为开发机局域网 IP `http://10.138.79.194:8080`。
- **测试**：竖切测试的第 1、2 步改为走真实 `SetupViewModel`+`RetrofitSessionApi` 代码路径（同一真服务端），并新增会话落盘回读断言——手机上将要执行的绑定路径首次被真 HTTP 覆盖。
- **红灯两轮**：① RetrofitSessionApi 初版用裸 Gson，UUID 反序列化崩（PairResultDto.userId），补 UUID 适配器后过；② 测试侧 setupB 忘切 PARTNER_DEVICE 模式，canSubmit 按首设备校验静默拦下（顺带确认 UI 按钮禁用态同样依赖 canSubmit，行为正确）。
- **结果**：`./gradlew :app:testDebugUnitTest` → `BUILD SUCCESSFUL`（EXIT=0）；`./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`（EXIT=0）。

### 记录 13：Task 12/13 功能链路（2026-10-01）

- **Commit**：本轮功能提交（见 git log：shared chain、media chain、scripts）。
- **Task 12 服务端**：同步 dispatcher 扩展 `CREATE_SHARED_ENTRY` / `APPEND_BLOCK` / `ADD_COMMENT` 三种操作（commentId 客户端生成、幂等重放；评论写变更流）。`SharedPerspectiveSyncTest`（独立真库 moon_letter_shared_test + 真 HTTP，双成员）：A 建共同记录 → B 追加视角块 → B 改 A 块 403 → 评论重放仅一行 → A 拉变更流见 CREATE/UPDATE/ADD → 双端读到同一条目（2 块、COLLABORATIVE）。附带：EntryService.readEntry 修正为仅个人草稿私有。
- **Task 13 服务端**：`LocalObjectStorage`（磁盘存储，条件装配 `moon-letter.storage.mode`，缺省 local；S3 需显式开启）；`POST/GET /api/v1/media/{id}/data` 原始字节上传/下载（member 校验 + sha256/size 核验 + READY 状态机）；`ExportService/ExportController`：GET /api/v1/export 输出条目+块+评论 JSON（无任何密钥）。`MediaChainTest`（独立真库）：建票 → 上传字节 → READY → 伴侣下载字节一致 → 导出 JSON 不含 token/secret。`scripts/backup.sh`/`restore.sh`：pg_dump + 存储目录归档 + backup_health.tsv 健康行（每日调度为运维动作）。
- **Android**：Room v2 `comments` 表 + MIGRATION_1_2；`RoomSyncStore` 应用 COMMENT 变更；`LocalEntryWriter.appendBlock/addComment`（Room+outbox 同事务）；`MediaApi`/`RetrofitMediaApi` + `MediaUploadManager`（同步前 best-effort 上传无 asset 的 IMAGE 块，回填 assetId 与 outbox payload）；SyncSession.triggerSync 挂接。
- **结果**：`mvn test` 全套 EXIT=0（连接池收紧为 4/上下文后并行测试不再耗尽 PG 连接）；`./gradlew test :app:assembleDebug` → BUILD SUCCESSFUL。
- **诚实记录**：共同记录的发布（PUBLISH）与图片选择器 UI 尚未接上；评论 UI 展示未做（数据链已通，属于下一轮「统一性修复」的 UI 段）。

### 记录 14：接手复核 + 变更行单一归属（2026-10-01）

- **接手现场**：上一轮执行留下一个未提交改动使**服务端整体无法编译**——`SyncOperationDispatcher` 引用了不存在的嵌套类型 `EntryService.PublishResult`（`PublishResult` 实为 `entry` 包的顶层 record 且包私有）。该半成品已保存为 `E:\MoonLetter\publish-entry-WIP.patch` 并将文件恢复至 HEAD，编译与全套测试随即转绿。本地分支 `m1-stable-recording-loop` 领先 origin **25 个提交**未推送。
- **真库现状核查（重要，改变了对 M1 的理解）**：dev 库 `moon_letter` 实测 2 users / 5 entries / 2 device sessions，而 `entry.state` **全部为 DRAFT**、`sync_change` 的 operation **全部为 CREATE、零条 PUBLISH**。即 App 的「发布」按钮只做本地草稿落库与 `saveDraft`，**发布语义在客户端整条缺失**；两条已推送的记录（A 3 条、B 2 条）从未真正发布。此前"双端互见"之所以看起来成立，是因为下一个缺陷把它掩盖了：个人草稿的 CREATE 变更行无状态过滤地进入伴侣变更流（`ChangeFeedService.readChanges` 不过滤），被对端 `RoomSyncStore.applyEntryChange` 无条件写入本地 Room，并被无 state 过滤的时间线 SQL 直接渲染——**对方的未发布私密草稿全文出现在自己设备上并显示出来**，违反规格 §4 表格与 §6.4「草稿不进入伴侣 change feed」。二者分别记为待修项（发布链、草稿可见性边界），对应 Gate E1/H4 之外的新阻塞。
- **本轮修复：一次变更操作只写一条变更行**。`CommentService.addComment` 与 `EntryService.publish` 自行追加 `sync_change`，而 `SyncController` 又按 `DispatchOutcome` 追加一次，同一次评论产生两条变更行；换一个新的 `operationId` 重放同一评论时，service 走"已存在"早退不加行、controller 仍加一行，于是**重放也在让变更流增长**（规格 §3.1 第 7 项要求重复请求不产生重复业务结果）。
- **TDD 红灯**：在既有 `SharedPerspectiveSyncTest` 增加断言"一条评论只允许一条 COMMENT 变更行"，首跑 `expected: 1 but was: 3`（`Tests run: 1, Failures: 1`），证实上述双重追加与重放增长并存。
- **实现**：`DispatchOutcome` 增加 `mutationOwnsChangeRow` 归属标记，`addComment` 置 true、`entryOutcome`（CREATE/UPDATE）置 false；`SyncController` 仅在标记为 false 时追加变更行，标记为 true 时改取 `ChangeFeedService.lastSequence(coupleId)` 作为唤醒通知的真实序号（不新增通知语义）。
- **绿灯**：`JAVA_HOME=E:/jdk21-extract/jdk-21.0.2 mvn test` → `Tests run: 53, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`，EXIT=0。
- **环境**：Windows 11；OpenJDK 21.0.2；Maven 3.9.15；Docker `postgres:18-alpine`（容器 `infra-postgres-1`，5432）；测试库 `moon_letter_*_test` 各自独立。注意系统默认 `java` 为 25，会导致 surefire 的 Mockito/Byte Buddy agent 初始化失败，必须显式指定 JDK 21。
- **未完成**：发布链（服务端 `PUBLISH_ENTRY` 类型化操作 + 客户端真正发布 + `EntryView` 暴露版本供 `baseVersion`）、个人草稿可见性边界、以及由它们挡住的 H1/H2/E1 真机证据。

### 记录 15：PUBLISH_ENTRY 进入类型化同步通道（2026-10-01）

- **红灯**：新增 `SharedPerspectiveSyncTest.personalDraftPublishesThroughSyncOperationWithOnePublishRow`（同一真库 moon_letter_shared_test + 真 HTTP）首跑 `Tests run: 1, Failures: 1`，服务端返回 `unknown operation type: PUBLISH_ENTRY`。同一次运行确认 `CREATE_PERSONAL_ENTRY` 的响应体已携带 `rowVersion`，即客户端有能力提交 `baseVersion`，无需为此扩 DTO。
- **实现**：`OperationType` 增 `PUBLISH_ENTRY`；`publishEntry` 要求 `baseVersion` 并调用 `EntryService.publish`（作者本人 + 版本不匹配即冲突，不静默覆盖更新的修订）；因 `EntryService.publish` 自己追加 PUBLISH 变更行，按记录 14 的归属规则声明 `mutationOwnsChangeRow=true`，控制器不再补第二条。`PublishResult` 从 `EntryDtos` 移为独立公共 record（Java 要求公共类型独占文件，与同包 `EntryView.java`、`ApplyChangesResult.java` 一致）。
- **顺带发现的测试缺陷**：`sharedEntryAppendAndCommentsReachPartnerThroughChangeFeed` 原先按下标断言变更流的前三条记录，而该库在同一上下文内被多个测试共享、变更流会累积——加入新测试后 `changes.get(1)` 断言即失效。已改为按本条 entryId 过滤后再断言顺序与实体类型，测试不再依赖执行顺序。
- **绿灯**：`JAVA_HOME=E:/jdk21-extract/jdk-21.0.2 mvn test` → `Tests run: 54, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`，EXIT=0。一次发布在 `sync_change` 中恰好一行 `ENTRY/PUBLISH`。
- **仍未完成（客户端与服务端语义各半）**：Android 侧「发布」仍只做本地 `saveDraft`，没有 `PUBLISH_ENTRY` 出箱操作，因此**真机上发布至今没有发生过一次**；且 `EntryService.publish` 追加的 PUBLISH 载荷只有 `{entryId, revisionNo}`，下一步个人草稿不再进入变更流时必须改为携带完整条目载荷，否则对端无法重建从未见过的记录。Gate E1/H1/H2 继续 NOT VERIFIED。

### 记录 16：私密草稿不再进变更流 + 客户端真发布打通（2026-10-01）

- **Commits**：`287fad7`（变更行单一归属，见记录 14）、`aee0ca6` + `8bc29c4`（服务端）、`0136171` + `4fcc168`（客户端）。均未推送，本地领先 origin **30 个提交**。
- **推导链（本轮所有改动由此决定）**：变更流是 per-couple 单通道、没有 per-member 尾巴，因此「个人草稿不给伴侣看」**只有一种正确实现——根本不写变更行**；不写行 ⇒ 对端第一次见到这条记录就是 PUBLISH 行 ⇒ PUBLISH 载荷必须携带**完整条目投影**而非裸 id；同理，作者设备也永远拉不到自己草稿的行 ⇒ 它的 `row_version` 只能来自 push 响应，否则草稿追加一次后再发布必然 409 并进入**终态 CONFLICT**（`markConflict` 把 `nextAttemptAt` 置为 `Long.MAX_VALUE`），这条记录将永远发不出去。
- **服务端实现**：`DispatchOutcome.mutationOwnsChangeRow`（布尔）替换为三态 `ChangeRowOwnership{APPENDED_BY_MUTATION, APPENDED_BY_CONTROLLER, SUPPRESSED}`；`EntryView.privateDraft()` 判 PERSONAL+DRAFT；`entryOutcome` 对私密草稿返回 SUPPRESSED，`SyncController` 对该情形既不追加行也不发唤醒（`NO_CHANGE_ROW = -1`）；`EntryService.publish` 改为把 `loadEntry(entryId)` 全量投影写入 PUBLISH 行；`CommentService.addComment` 增加状态门禁——**只有已发布记录有读者**，否则评论会成为第二条泄漏通道（400）。
- **客户端实现**：`SyncStore.markApplied(operationId, serverEntrySnapshot = null)`；`RoomSyncStore` 在**同一 Room 事务**里删除 outbox 行并合并快照（`upsertEntry` 与 feed 应用共用一份实现；非条目载荷——评论视图、畸形 JSON——只消费操作不合并，**不能让一次服务端已接受的操作因合并失败而重投**）；feed 侧仍保持「坏 JSON 整页回滚含 cursor」的强语义，两条路径的容错差异是刻意的；`LocalEntryWriter.save(command, publish = true)` 在同事务追加 `PUBLISH_ENTRY(baseVersion=0)`（服务端 `createDraft` 固定写入 `row_version=0`，新建即发布时该版本必然正确）；`SyncSession.saveDraft` 更名 `publish` 并接线 `AppNavigation` 两处 `onPublish`。
- **可观测性**：时间线为仍是 DRAFT 的条目加「未寄出」chip（`TimelineEntryUi.unsent`）。离线时用户以为已经共享、实际只在本地——原来与正常页完全同形，属于静默失败；现在状态可见。不引入任何统计口径。
- **TDD 红灯（保留为证据）**：`SelfUseRecordingLoopE2ETest:110 expected: false but was: true`、`SharedPerspectiveSyncTest:247 expected: 0 but was: 2`——多出的两行正是个人草稿的 CREATE 与 UPDATE 泄漏。实现后 `SharedPerspectiveSyncTest:176 expected: 1 but was: 2` 暴露**同类第二个测试统计了整个 couple 的 COMMENT 行**，与记录 15 修过的 ENTRY 计数是同一缺陷，改法一致：按 `entity_id` 限定。
- **绿灯**：服务端 `JAVA_HOME=E:/jdk21-extract/jdk-21.0.2 mvn test` → `Tests run: 57, Failures: 0, Errors: 0, Skipped: 0`，EXIT=0。**（记录 17 更正：真实为 55——57 是把 `TwoDeviceSyncTest` 的陈旧 surefire 报告计入求和得来的，该测试类早在 `f410974` 已删除；当次 mvn 汇总行本身就是 55。）**Android `./gradlew testDebugUnitTest`（JDK 21 + `TEMP/TMP=E:/tmp`）→ 19/19 通过：`SyncEngineTest` 11、`RoomSyncStoreTest` 7、`TwoDeviceRecordingLoopTest` 1（真 Room×2 + 真 HTTP + 真 Spring Boot 进程 + 真 PostgreSQL）。
- **竖切按新语义重写后的断言**：A 写「完成即发布」→ outbox 依次为 `CREATE_ENTRY`、`PUBLISH_ENTRY`、本地仍 DRAFT；B 拉 0 条；A 推 2 个操作；B 再拉**恰好 1 条**变更行；重放已消费的 CREATE 得 `replayed=true` 且 B 再拉 0 条（重放不再增长变更流）；A 自己的副本经快照合并变为 PUBLISHED 且 `rowVersion=1`；B 由这一行重建出 title/state=PUBLISHED/1 个含正文的块；B 写不发布 → A 拉 0 条且查不到该条目；反向 B 发布 → A 可见。注意此处的「A 拉 0 条」前提是 A 先做一次追赶式拉取——共享变更流也包含自己写的行，这是夹具必须先归零游标的原因。
- **诚实记录 / 未完成**：
  1. E1/H1/H2 的**真机**证据仍未取得，JVM 夹具不等同于真机；feature 模块只有仪器测试（本机不可运行），且 `TimelineScreenTest` 断言的文本「共同记录 · 我们」在当前实现中并不存在——该夹具**从未通过**，属已知失效件，新增的「未寄出」chip 同样只有编译级保证。
  2. `baseVersion` 的 0 只在「新建即发布」路径成立；出现「发布一条已有草稿」的入口时，必须改用本地已合并的 `rowVersion`，否则该路径会撞终态冲突。
  3. 与规格 §6.4 的偏差仍在：`EntryMode` 仍为 PERSONAL/COLLABORATIVE（要求 SHARED）、`EntryState` 仍含 `CAPSULE_LOCKED/ARCHIVED`（要求移除，`RoomSyncStore.mapState` 仍在映射）。
  4. 毛刺清单未动：时间线仍非 Room Flow 响应式、Scaffold padding 未透传、`SyncSession.DEFAULT_DEV_BASE_URL` 硬编码局域网 IP、备份脚本未调度/未演练、无 Room 迁移测试、bootstrap 密钥无消费标记。
  5. dev 库 `moon_letter` 仍有 5 条历史 DRAFT 测试数据，待「开档」时清掉或导出封存；真实内容一旦出现，迁移纪律（先备份副本演练）立即生效。

### 记录 17：身份体面——配对即取名 + 改名真正落到服务器（2026-10-01）

- **门槛清单第 1 项**：「身份体面：改称呼入口先做——她打开第一眼不能是『未命名』」。现场核查发现这条链上有三个独立缺陷，缺一不可构成「改称呼入口」。
- **缺陷现场**：
  1. `CoupleService.pair` 给新伴侣写的 profile 固定是字面量「未命名」——她配对成功的第一眼就是它；
  2. 「我们」页的改名只写本机 SharedPreferences（`moon_letter_profile.ownName`），**服务器从未收到过一次改名**，对端永远看不到；
  3. `AppNavigation` 用 `LaunchedEffect(coupleState.ownName, coupleState.partnerName)` 取名字，而该值随输入变化——**每敲一个字符发一次 `GET /api/v1/couple/{id}`**；
  4. 掩盖性问题：`CoupleViewModel` 与 `TimelineViewModel` 把 `小满 / 阿屿` 写成默认值，真名字反而被假身份挤掉。
- **服务端实现（test-first）**：`PairRequest(token, displayName)` → `pair(token, displayName)` → `ensureProfile(partnerId, displayName)`；只有缺席或空白才落 `PLACEHOLDER_DISPLAY_NAME` 常量，`readSpace` 以 `COALESCE(up.display_name, ?::text)` 绑定它，SQL 里不再出现「未命名」字面量。**PATCH 改为真正的部分更新**：`display_name / avatar_asset_id / theme` 三列各自 `COALESCE(?, 原值)`。原实现要求 theme 必填并把 avatar 无条件置 NULL，于是改名会静默重置主题、清空头像，且客户端必须回传它并不拥有的字段——两台设备各存一份主题偏好，用陈旧偏好改名就把另一台的设置回滚了。缺席字段保持原值，出现但空白的名字仍然 400。
- **红/绿灯（服务端）**：`PairingFlowTest:152 expected: 200 OK but was: 400 BAD_REQUEST {code=VALIDATION_ERROR, message=theme is required}`；实现后 `PairingFlowTest` 1/1、`CoupleApiTest` 8/8。新增断言覆盖：只带名字的 PATCH 保留 `PURE_WHITE` 主题、对端 readSpace 读到新名字、改他人 403、空白名 400。
- **客户端实现**：`SessionApi.pair(..., displayName)` 与 `updateOwnProfile(...)`（PATCH + bearer，非 2xx 抛 `SetupHttpException`）；`SetupViewModel.canSubmit` 两条路径都要求称呼非空，`SetupScreen` 把「你的称呼」提为公共输入框（伴侣分支标签写明「伴侣手机上会看到」）；`SyncSession.renameOwn` = PATCH + `refreshNames` 并返回服务器存下的名字，失败上抛；`refreshNames` 不再把读不到的名字写空；`AppNavigation` 改为进入 App 时读一次（`LaunchedEffect(Unit)`）+ `LaunchedEffect(cachedNames)` 只刷标签，改名成功后再刷一次；`CoupleViewModel` 引入 `draftName / savingName / nameError` 与显式 `saveOwnName(remote)`，空白、与已存名字相同、保存进行中都不发请求，`restore` 不再从本地 prefs 恢复名字（名字唯一来源是服务器），删除无引用的 `anniversaryLabel / partnerAvatar / coverUri / keepOldCoverUntilUploadSuccess`；`CoupleScreen` 编辑区改为草稿 + 保存/取消并写明「这个名字会同时出现在你们两个人的手机上」，失败原因用 error 色显示；徽章在无名字时显示「取个名字」而非假名；`TimelineViewModel` 作者标签回退由 `小满/阿屿` 改为 `我/伴侣`。
- **测试**：新增 `SetupViewModelTest`（2）与 `CoupleViewModelTest`（5），均为 JVM 级证据；`TwoDeviceRecordingLoopTest` 扩了两步真链路——配对带名字后用 B 的 token `readSpace` 见 `{小满, 阿屿}`；`SyncSession.renameOwn("小满呀")` 后伴侣侧 readSpace 见到该名字且本机名字缓存同步。
- **绿灯**：服务端 `mvn -o test` → **Tests run: 55, Failures: 0, Errors: 0, Skipped: 0**（见记录 16 的 57 更正）；Android `./gradlew testDebugUnitTest` → 26/26（`SyncEngineTest` 11、`RoomSyncStoreTest` 7、`SetupViewModelTest` 2、`CoupleViewModelTest` 5、`TwoDeviceRecordingLoopTest` 1，其中竖切仍是真 Room×2 + 真 HTTP + 真 Spring Boot 进程 + 真 PostgreSQL），另 `:core:model:test` 通过。`:feature:couple:compileDebugAndroidTestKotlin` 通过——该模块 androidTest 依赖此前从未下载，本轮联网取回后才第一次可编译。
- **已知偏差 / 未完成**：
  1. 改名**不会实时到达对端**：PROFILE 没有变更行，对端在下次打开 App 时才收敛。这是刻意取舍（为改个称呼引入一种新实体类型 + 投影 + 归属规则，成本与风险都不成比例），但必须写进验收说明，不能算作已同步。
  2. `CoupleScreenTest` 旧版断言的「我的名字」「中秋节 · 农历八月十五」「米色」在实现里都不存在（编辑区默认折叠、chip 文案是「暖米色」、纪念日文本从未渲染），属于**从未通过的假绿灯**；已重写为断言真实存在的文本，但本机无设备，仍只有编译级保证。
  3. 同一屏仍硬编码「已相伴 1097 天」「中秋节 · 还有 360 天」与两张固定头像——同一类假身份的另一半，归 #6 处理。
  4. 主题（暖米/纯白）仍只存本机，不跨设备同步；服务器已有 theme 列，但没有客户端写入路径。

### 记录 18：图片与回应走到对端（2026-10-01）

- **Commits**：`562e402`（服务端）、`94a08d7`（Android 全链 + 夹具）。均未推送，本地领先 origin **34 个提交**。门槛清单第 2 项「记录呈现完整：图片真显示、共同记录有发布语义、评论可见」。
- **断链核查（三处独立，任一存在都让「发出去的记录不像样」）**：
  1. **发布静默死循环**：图片块未上传就进 outbox，服务端 `EntryService.publish` 的 `mediaService.requireReady` 让发布 4xx，操作只会被无限重试；同时 `OutboxDao` 旧查询把**块 payload 当成操作 payload 覆盖**、且 WHERE 漏 `CREATE_ENTRY`，一旦触发就把要发给服务端的载荷改成它不认的形状——把同步改坏而不报错。
  2. **协议层拒绝个人记录带图**：`SyncOperationDispatcher.createEntry` 仍写着 `personal entry sync accepts TEXT blocks only`，而个人记录正是这个 App 的主要形态。Android 接上照片后第一次真链路 push 就 `applied=0`（e2e 红灯保留为证据）。
  3. **呈现面缺席**：时间线用「[图片]」假文案、没有记录详情页、评论数据链已通却无处显示、行尾爱心点了没有任何反应（装饰性假控件）。
- **客户端门控实现**：outbox 新状态 `MEDIA_PENDING`——`SyncEngine` 只取 `state='PENDING'`，因此门控无需改动引擎；同一批 `CREATE_ENTRY` + `PUBLISH_ENTRY` **一起**门控（发布不得跑到缺图的创建前面），`APPEND_BLOCK` 同样门控；上传成功后 `attachAsset(blockId, assetId)` 在同一事务里写块列并按 `blockId` **在操作 payload 内原地补 `assetId`**，条目内所有图片齐了才整体放行；未放行时本地仍是 DRAFT、时间线显示「未寄出」，失败可见而非静默。删除了覆盖 payload 的危险查询。
- **服务端实现**：去掉个人 IMAGE 限制；`validateBlock` 要求 IMAGE 块必须带 assetId（没有 asset 的图块任何设备都渲染不出来）；`createDraft` 在写块后即 `requireReady`，把「引用别的空间/未就绪的资源」挡在写入时而不是等到发布。
- **呈现面实现**：`EntryPhotos` 在**选取时**把字节复制进 `filesDir/photos/<blockId>.<ext>`（photo picker 的 uri 只在本进程存活），块 payload 存 `{localPath, mime}`、assetId 存列；渲染规则=本机文件优先、否则按 assetId 下载，作者不必重下自己拍的照片；编辑器可选最多 6 张、缩略图可移除、含图草稿可跨进程恢复；记录详情页=正文 + 真图 + 已有回应 + 写一句回应并触发同步；时间线行点击进入详情并显示首图；删除假「[图片]」与装饰性爱心。
- **可靠性顺带修复**：`AppDatabase.build` 原先**每次调用都新建一个连接池**，编辑器、同步引擎、上传器、导航四处同时打开同一文件在互相抢写锁，改为进程内单例；`MediaUploadManager.uploadPendingImages` 拆出 `(session, database)` 重载，使真链路可被测试驱动；编辑器里显式移除的照片会删掉本机副本，不留孤儿文件。
- **评论门禁**：服务端只有**已发布**记录接受评论（记录 16 的规则），但详情页此前在草稿上也给输入框——写下去的操作必然 400 并无限重试。现在未寄出的记录底部写明「寄出之后，TA 的回应才会落到这一页」。
- **夹具**：新 `RealServerHarness`（真 Spring Boot jar + 独立真库 + 独立端口 + 独立本地存储目录），原竖切改用同一套 harness，两个竖切互不污染。新 `MediaGatedOutboxTest`（5）钉门控与放行语义；新 `TwoDevicePictureAndCommentLoopTest` 走真链路：真 PNG 落盘 → 写记录含图并发布 → 两操作皆 MEDIA_PENDING、引擎 push `applied=0`、伴侣拉 0 条且查不到该条目 → 上传放行后操作 payload 里的 assetId 与块列一致 → push 两个操作、作者副本合并为 PUBLISHED → 伴侣拉到 2 块、IMAGE 块带同一 assetId、`GET /api/v1/media/{id}/data` 下载字节的 sha256 与 A 挑的文件一致 → A 写评论 → 伴侣拉到该评论且 authorId 为 A → 关闭并重建 Room 后记录与评论仍在。
- **绿灯**：服务端 `JAVA_HOME=E:/jdk21-extract/jdk-21.0.2 mvn -o test` → **Tests run: 55, Failures: 0, Errors: 0, Skipped: 0**（注意：必须显式指定 JDK 21，系统默认 java 25 会让 surefire 的 Mockito/Byte Buddy agent 初始化失败，整套 @SpringBootTest 会以 initializationError 全红，容易被误读成代码回归）。Android `TEMP/TMP=E:/tmp JAVA_HOME=… ./gradlew --offline testDebugUnitTest :core:model:test :app:assembleDebug` → BUILD SUCCESSFUL，**33/33**：core:sync 11、core:database 12（RoomSyncStore 7 + MediaGated 5）、app 4（两个真链路竖切 + SetupViewModel 2）、feature:couple 5、core:model 1。
- **诚实记录 / 未完成**：
  1. 以上**全是 JVM/Robolectric 证据**，本机无设备。图片在 Compose 里是否真渲染出来、详情页排版、键盘遮挡都只有编译级保证；feature 模块的仪器测试从未成功运行过。E1/H1/H2 的真机验收仍待补。
  2. 图片只有内存 LruCache（8 张），无磁盘缓存——伴侣每次滚动都会重新下载同一张图，真机上会先见占位再见图。
  3. IMAGE 块 payload 仍把作者本机绝对路径共享到对端（对端读不到该路径、按 assetId 渲染，功能正确但这条数据在对方设备上无意义）。
  4. 未发布草稿被整页丢弃或被新草稿覆盖时，其图片文件仍会成为孤儿（本轮只处理了编辑器内显式「移除」）。
  5. 共同记录的「追加自己视角的图」入口在 UI 上仍未开放（附件工具栏只有「图片」可用，且走创建路径）；语音/音乐/位置仍是占位。
  6. 规格要求的记录成本实测（≤10 秒 / ≤4 次交互）仍无真机数据，加图后交互数会变，需实测秒数。
