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
| H4 会话恢复 | 吊销全部会话后用本地管理命令重新进入并双端一致 | 命令、退出码、恢复后拉取比对 | **部分通过**：CLI 已在克隆库按文档跑通；重装后的整本书回补由 `ReinstalledPhoneRebuildsWholeBookTest` 在真 Room + 真 Spring Boot 上证明；**两台真机双端一致仍 NOT RUN** | 见 §6 记录 23、25、26 |
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

### 记录 19：开档前的数据封存与清空（2026-10-01）

- **现场**：dev 库 `moon_letter` 的 5 条记录实测为标题 `u` / `h` 的键盘乱码，**全部 DRAFT、`row_version=0`、无评论、无媒体**（`server/storage` 为空目录）。它们是配对调试时留下的，不是内容。
- **封存（仓库外，不入版本库）**：`E:\MoonLetter\archive\pre-open-2026-10-01\` 下 `moon_letter-pre-open.dump`（pg_dump custom，可还原）与 `moon_letter-pre-open.sql`（含 5 条 entry 的 INSERT，可读）。私密内容绝不提交进 git。
- **还原演练先行**：把 dump 还原进临时库 `moon_letter_wipe_drill` 验证 `entry=5 / entry_block=5 / sync_change=5 / last_space_sequence=5 / device_session=2` 后才动手删除；演练库与容器内临时 dump 已删除，不留第二份副本。
- **删除范围**：单事务清空 `entry_block / entry_contributor / entry / sync_change`（各 5 行）与 `entry_revision / comment / time_capsule`（0 行）。
- **刻意保留**：`couple_sync_state.last_space_sequence=5` **不回卷**——两台手机的本地 cursor 已经走到 5，回卷会让新记录落在 cursor 之下而永远拉不到；`idempotency_record` 5 行保留，使任何仍在手机 outbox 里的旧操作被服务端判定为重放而不是把刚删掉的记录写回来；`app_user / user_profile / couple_space / couple_member / device_session / space_pairing_code` 全部保留，空间与配对关系不变，只清书。
- **未完成 / 必须知道的边界**：**这只清了服务器**。两台手机各自的 Room 里那 5 条 DRAFT 仍在，时间线还会显示它们；本机无设备也无法远程清。开档当天的动作是：在两台手机上「设置 → 应用 → 清除数据」后重新配对（或卸载重装），从「我们的第一页」开始写。此步骤必须与「她正式加入」同一天做，否则她会先看到 5 页乱码。
- **恢复命令（若需要）**：`docker cp moon_letter-pre-open.dump infra-postgres-1:/tmp/r.dump && docker exec infra-postgres-1 pg_restore -U moon_letter -d moon_letter --no-owner /tmp/r.dump`（恢复到空库，不要覆盖已有真实记录）。

### 记录 20：无明显破绽——响应式呈现、草稿归属与假数据清理（2026-10-01）

- **Commits**：`d107bba`（响应式呈现、草稿分槽、遮挡与假数据清理的全部 Android 改动）、`fe697d7`（详情页状态栏 inset + 本条台账两处更正）、以及 docs 提交本身。**更正记录 18 的「本地领先 origin 34 个提交」**：`562e402`/`94a08d7`/`d7ed275` 在其后已按授权推送，`git branch -vv` 当前实测 ahead 4；推送仍是一次一批、逐次授权，不视为长期许可。
- **门槛清单第 3 项**「无明显破绽：Scaffold 遮挡、时间线刷新这些毛刺清掉」，顺带收尾第 2 项的呈现面与第 4 项的签署观感。服务端本轮**未改动**。
- **时间线毛刺的根因**：`TimelineViewModel` 收的是一个一次性 `loader`，靠 `LaunchedEffect(selectedKey, editingMode)` 与发布后的 `refresh()` 手动重查——离开再回来才刷新，同步线程写入（WorkManager 拉到伴侣的新记录）时页面**不会动**，她看到的永远是进页时的那一帧。改为 `observer: () -> Flow<List<TimelineItem>>`，唯一入口是 Room 的失效驱动流；`refresh()` 与两处轮询 `LaunchedEffect` 全部删除，「本地写完立刻可见」变成一条测试而不是一个操作顺序（`aSingleLocalWriteReachesTheScreenWithNoRefreshCall`）。
- **两个隐藏的呈现缺陷**：① 旧 `timelineSnapshot()` 带 `LIMIT 100`，第 101 页回忆会**静默消失**——删掉该查询，只留无上限的 `observeTimeline()`，`ORDER BY occurredAtEpochMillis DESC, id DESC` 一份实现；② 每条记录一次 `blocks(id)` 的 N+1，改为一条全表 `observeBlocks()` 流在内存里分组，一次发射拼出预览与首图。记录详情页同样从 `loader`+`open()`+`reload()` 改为三条流 `combine(observeEntry, observeBlocks, observeForEntry)`，伴侣的回应落到本页时不需要重开。
- **Room 单行流的坑（决定了签名）**：`Flow<Entity?>` 在**查无结果时根本不发射**，删除条目后页面会停在旧内容；因此 `observeEntry` 有意返回 `Flow<List<EntryEntity>>` 再 `firstOrNull()`，让「没有这条记录」成为一个真实的发射值。
- **草稿归属（原来会写丢内容）**：`DraftStore` 只有 `title/body/photos/mode` 四个无前缀键，个人稿与共同稿**共用一个槽**。切到「共同记录」时旧稿被覆盖，且 `editingMode = draft.mode` 会用**存下的模式**覆盖用户刚选的类型；清空正文后 `save` 提前 return 使槽位残留，旧内容会「复活」。改为按 `mode.name` 分槽、空内容时**擦除**该槽、发布成功只清对应模式，并抽出 `openEditor(mode)` 让入口、切模式、底栏三处走同一条路（切模式先 `keepDraft()` 再开新槽）。
- **键盘遮挡**：`targetSdk 37` 强制 edge-to-edge，`MainActivity` 已调 `enableEdgeToEdge()`、manifest 无 `windowSoftInputMode`，键盘弹起会顶到内容区；两个编辑器的 bottomBar（发布按钮所在）此前只吃 `navigationBarsPadding()`，于是键盘**盖住发布按钮**，而写记录正是最高频动作。补 `.imePadding()` 是这套布局下的正解。记录详情页的 topBar 是普通 `Row` 而不是 `TopAppBar`，没有人消费状态栏 inset，返回按钮会压在状态栏时钟下面——加 `statusBarsPadding()` 让这条栏自己长高，内容仍落在它下方（编辑器的 `EditorTopBar` 在 content 槽里，已由 M3 Scaffold 的 `safeDrawing` 顶边距托住，无需改动）。
- **签署观感**：编辑器直接画 `R.drawable.moonletter_avatar_xiaoman` 配 `contentDescription = "小满头像"` 与字面量 `Text("小满")`，共同记录还按 `block.author == "小满"` 挑第二张插画——与她刚在配对时取的真名毫无关系。新增 `AuthorMark(name, size, accent)`（与时间线 initials 同一形状），编辑器传 `ownName = cachedNames.own`、无名时回退「我」；同时删掉 `SharedEditorScreen` 里点了没反应的「＋ 在这里添加内容」假文本与恒为空的 `blocks` 参数。
- **假数据与死控件清理（含一条硬违规）**：`CoupleScreen` 的「已相伴 1097 天」「中秋节 · 还有 360 天」「月相 · 中秋」和整张「下一个纪念日」卡**属于统计口径**，是产品红线，直接删除而不是加设置项；`SettingsRow` 的箭头图标暗示可点而无人接线，改为行尾「还没开放」文本；地图预览「杭州 · 0」「杭州的故事」换成「还没有留下地点」「我们的故事」；相册预览删掉假的「新建相册」。`album / map` 两个底栏标签仍按参考图保留（只清假数字），是否彻底移除留给用户决策。
- **死代码与依赖**：分页路径（`PagingSource`、`androidx.room:room-paging`、`androidx.paging:paging-runtime`）从来没有任何界面消费过，连同 toml 别名一并删除。
- **两个离线坑（都会伪装成代码回归）**：① 删掉 paging 后 `androidx.lifecycle:lifecycle-common` 解析回落到 2.3.1，而离线缓存里没有它，`:core:database:kspDebugUnitTestKotlin` 报 `No cached version available for offline mode`——用 `testImplementation("androidx.lifecycle:lifecycle-common:2.9.3")` 显式钉到已缓存版本，而不是把 paging 加回来；② `:core:database:compileDebugAndroidTestKotlin` 需要 `room-testing` 与 `androidx.test:runner`，二者**从未下载过**，即该模块仪器测试在此之前**连编译都做不到**，本轮破例走一次联网取回后离线复现通过。
- **夹具修复**：`feature/editor` 的 androidTest 断言的是「发布这篇记录」「同一段回忆，两个视角」等**当前实现里不存在的文案**——又一件从未通过的假绿灯，重写为 4 条真断言（发布时间戳、附件按钮集、真名签署且**不出现**「小满」、无名时回退「我」、共同记录的视角提示）。
- **新增测试**：`TimelineViewModelTest`（6）、`EntryDetailViewModelTest`（6），沿用 `feature/couple` 的 JVM 范式（`Dispatchers.setMain(UnconfinedTestDispatcher())`，不用 `runTest`，因为 `viewModelScope` 走 `Main.immediate`）。详情页六条钉的是：晚到的回应出现在**已打开**的页面上、新发射不吞掉她正在输入的半句、一次点击恰好投递一条去过空白的回应、空回应不落库、发送失败保留原文并说明原因、改名连已渲染的回应一起重贴标签。
- **绿灯**：`TEMP/TMP=E:/tmp JAVA_HOME=E:/jdk21-extract/jdk-21.0.2 ./gradlew --offline testDebugUnitTest :core:model:test :app:assembleDebug` → **BUILD SUCCESSFUL**，单元测试 **44 条全绿、0 失败 0 错误**：`SyncEngineTest` 11、`RoomSyncStoreTest` 7、`MediaGatedOutboxTest` 5、`TimelineViewModelTest` 6、`EntryDetailViewModelTest` 6、`CoupleViewModelTest` 5、`SetupViewModelTest` 2、两个真链路竖切各 1（真 Room×2 + 真 HTTP + 真 Spring Boot + 真 PostgreSQL）。四个模块的 `compileDebugAndroidTestKotlin` 与 `:app:assembleDebug` 均通过。
- **兼容性副作用（开档当天正好吸收）**：旧版 SharedPreferences 的无前缀 `title/body/photos/mode` 成为死数据，**跨越本次升级的未发布草稿会被忽略**。因为开档本就要求两台手机清除应用数据后重新配对，这条不单独处理；若将来还有别的升级路径，需要写迁移。
- **诚实记录 / 未做**：
  1. 以上仍是 **JVM/Robolectric 证据**。edge-to-edge 下 `imePadding()` 是否真的让发布按钮露出来、时间线滚动的稳定性、`AuthorMark` 的排版、详情页流式更新的实际手感，都要真机才算数——门槛第 3 项只能算「代码层已修」，第 4 项（视觉过参考图）仍未验收。
  2. `CoupleScreen` 无头像时仍用参考图里的两张角色插画作为默认像（真名已经走 `Text(name)`）。它属于「我们自己的画」这一视觉意图，未改；若她希望默认就是名字首字母，是一行改动。
  3. 全表 `observeBlocks()` 对两个用户是刻意的取舍；记录数增长到千级才需要考虑按需订阅。
  4. 本轮未触碰 #7（每周回看、新记录本地通知）与 #9 清单；`baseVersion` 固定 0、`DEFAULT_DEV_BASE_URL` 硬编码 IP、图片无磁盘缓存、IMAGE payload 外泄本机路径、主题不跨设备、无 Room 迁移测试等仍在。

### 记录 21：卸载重装后回不去——配对数据与恢复链路（2026-10-01）

- **Commits**：`52b1f59`（服务端恢复语义 + V10 迁移 + 新验收测试）、`9d3ca4c`（Android 侧「我们」页生成配对码与绑定页文案），docs 提交另计。`git log origin/m1-stable-recording-loop..HEAD` 实测**本地领先 8 个提交**——顺带更正记录 20 的「ahead 4」：`f7b8d21`（记录 19 的封存清库台账）当时也还没推。推送仍是一次一批、逐次授权。
- **用户报的现场**：「卸载软件之后重新生成的秘钥还留存，应该能重新生成才对」。
- **实测根因（不是密钥没留存，是三道门同时关上）**：dev 库 `moon_letter` 当时是「空间已满 2 位 / outstanding 配对码 0 / 活跃会话 2」。① `POST /api/v1/bootstrap` 的语义是「安装只能初始化一次」，只要 `app_user` 非空就 409；② `POST /api/v1/couple/pair` 见满员空间直接 409 `couple space is full`；③ 客户端只在 bootstrap 成功后展示一次配对码，**之后再也没有再生成的入口**（`SyncSession` 里根本没有这条调用）。唯一剩下的出路是 `SessionAdminCommand` CLI，而它要求传 `userId`——丢手机的人不知道自己是谁。三者合起来 = 完全锁死。
- **恢复语义（服务端）**：
  - 密钥从「一次性创建空间」改为「打开创始成员自己的槽位」：安装已有成员时，`reclaimFoundingMember` 取最早未删除空间的最早活跃成员，`INSERT … ON CONFLICT (user_id) DO NOTHING` 保profile，`issueReplacementSession` 发新会话，返回**原来的 userId / coupleId**。密钥的权力没有变大（它本来就能建整个空间），但现在它同时是创始设备的恢复凭据。
  - 配对码有两种，**由服务器按活跃成员数决定**，客户端不需要也不该自己猜：有空位 = `INVITE`（新建第二位成员），已满 = `REJOIN`（`space_pairing_code.rejoin_user_id` 绑定另一位成员的 user_id，V10 新增可空列 + 部分索引）。对用户仍然只有一个概念「配对码」。
  - `consumeRejoinToken` 先校验目标仍是该空间活跃成员，再只吊销**她自己的**会话，返回**同一个 userId**——已有记录的归属不断；另一台手机的会话不受影响（这是「恢复一台不会把另一台踢下线」的实测点）。
  - 容量判断改为按 `couple_member` 活跃成员数计算，不再读 `couple_space.status`（`createSpace` 从不维护它，满员空间照样是 `UNPAIRED`）。
  - `issueOutstandingToken` 在插入新码前把该空间所有未消费码置 `consumed_at`，一个空间**只留一个**有效码；旧码留在世上等于一张过期门票随时能把人踢下线。
  - 两条恢复路径都**不改名**：改名是 rename 入口的职责，重装不是改名。（正常开档流程不受影响：`INVITE` 配对走 `ensureProfile(partnerId, displayName)`，她打的称呼会落库。）
- **客户端补的是「缺失的那一步」**：`我们` 页新增「换手机或重新配对」——按需生成配对码、可长按复制、并把服务器给的 kind 一起显示；`REJOIN` 时明说这个码认的是另一位成员自己的位置。生成失败会**清掉屏上旧码**（请求新码已经让它作废，留着就是假信息）。`PairingTokenResultDto.pairingTokenKind` 贯通到 `SetupScreen`，绑定页文案不再声称「空间已建好」（同一次调用可能是在恢复已有空间），409 文案改成现在真实的原因。
- **服务端绿灯**：`JAVA_HOME=/e/jdk21-extract/jdk-21.0.2 mvn -o test` → **56 条全绿**。新增 `DeviceReplacementPairingTest`（真实 HTTP + 独立库 `moon_letter_replace_test`：INVITE/REJOIN 判定、重复 bootstrap 返回同一身份、旧会话 401 而伴侣仍 200、REJOIN 回到同一 userId、码单次使用与轮换后失效、`couple_member`/`app_user` 恒为 2、恢复不覆盖已改的名字）；`BootstrapAuthenticationTest` 第 3 步从「第二次 bootstrap 永久 409」改写为新语义；`CoupleApiTest` 跟随 `CreateSpaceResult` 新字段。
- **我自己写出来的一盏假红灯**：`pairStatus()` 期望 409，却报「expected success, got 409」——`TestRestTemplate` 对 4xx **返回 ResponseEntity 而不抛异常**，我让它穿过 `ok()` 断言，于是服务端行为正确、测试助手错误。改为直接读状态码，顺手删掉 `readSpaceStatus()` 里永远不会触发的 `catch`。
- **Android 绿灯**：`./gradlew --offline testDebugUnitTest :app:assembleDebug` → **BUILD SUCCESSFUL，48 条单元测试 0 失败 0 错误**（上轮 44 + 本轮 4：`CoupleViewModelTest` 5→8、`SetupViewModelTest` 2→3）。新增的是「拿到的码带上服务器给的槽位」「生成失败把屏上旧码一起丢掉」「藏起来不向服务器发任何请求」「绑定页把 kind 一路带到待配对面板」。
- **真 HTTP 演练（与 JVM 证据分开记）**：把 dev 库**克隆**成 `moon_letter_rehearse`（`CREATE DATABASE … TEMPLATE moon_letter`，当时源库 0 连接），服务器指向克隆库跑在 18080，用 curl 跑用户现场状态：错密钥 403 → 重复 bootstrap **201** 并 reclaim 到 `e4706489`（存名 `k` 未被改写）→ 生成的码 `kind=REJOIN` → 用它 pair 得到**同一个** `5f644891`（`未命名` 保留，成员数仍 2）→ 已用码重放 409。演练后 `DROP DATABASE moon_letter_rehearse`，脚本删除，**dev 库未被写**（源库与克隆库 `entry` 均为 0，与记录 19 的清空一致）。这台机器上 8080 当前**没有服务在跑**。
- **必须知道的边界 / 未做**：
  1. **第二位成员重装不能用密钥找回自己**（密钥只认创始槽位），必须由另一台还在的手机生成 `REJOIN` 码；两台同时丢则先密钥回到第一位、再当场生成码找第二位——这条顺序已在演练里跑通。
  2. `REJOIN` 码是 **bearer 凭据**：拿到它的那台手机在 15 分钟窗口内就是她。单次使用 + 只能覆盖一个已存在的槽位，但**它必须直接发给对面那台手机**，不要发到群里、不要截图外传。这是本设计接受的代价，也是开档当天要交代她的一句。
  3. `app_user` 非空但空间里一个活跃成员都不剩时，bootstrap 只会 409「no member left to recover」，**没有自助重置入口**。现无任何代码路径把成员置为 `left_at`/`deleted_at`，所以不可达；将来加「离开空间」时必须同时补这条，否则整个安装永久锁死。
  4. 恢复动作不写 `session_admin_audit`（该表目前只由 CLI 写），所以「谁在什么时候把谁的会话换掉了」在库里查不到。自用两人场景暂不补，但它是一条可审计性缺口。
  5. **落地需要的动作**：服务端要用本轮构建**重启**（V10 列在 dev 库已存在，但运行中的进程必须是新代码）；两台手机要装新 APK。开档当天仍是「两台都清除应用数据 → 第一位初始化 → 另一位用新生成的码配对」。
  6. 未在真机验证：长按复制、`REJOIN` 说明文案的实际读感、以及「另一台手机生成码 → 本机粘贴」这条双手协作的真实耗时（门槛第 5/6 项的一部分，用户已明确接受真机成本后续再精进）。界面复刻与视觉细节本轮刻意只做够用即可，另有其人负责。

### 记录 22：情感钩子——每周回看与新记录本地通知（2026-10-01）

- **Commits**：`138aaea`（Android 侧全部实现与测试），docs 提交另计；`git rev-list --count origin/m1-stable-recording-loop..HEAD` 实测**本地领先 10 个提交**，推送仍逐次授权。
- **对应门槛第 5 项 / plan Task 11c**。服务端本轮**未改动**。验收人是伴侣本人，代码层达成不等于她那一侧成立。
- **挑选规则（`core/sync/WeeklyReview.pick`，纯函数）**：只在**已发布**记录里挑（私密草稿永不参与），年龄门槛 8 天（「明显更早」，本周之内的不算），优先离 365 天最近的一条，逐级回退；平手取更近的那条（她更可能记得自己写过）。**挑不到就返回 null——安静跳过**，不生成内容、不显示「本周没有内容」、不凑数。
- **调度**：`PeriodicWorkRequest` 7 天周期 + `setInitialDelay(millisUntilNext(星期, 整点))`。`schedule(force)` 区分两种入口：开屏/进程启动用 `ExistingPeriodicWorkPolicy.KEEP`（**不能**重排队，否则她每次打开 App 都把下周的提醒往后推），只有她真的改了开关或时间才 `CANCEL_AND_REENQUEUE`。关掉开关既取消已排队的周期任务，worker 内部也重读一次开关——先排队后关闭的那一跑仍然什么都不发。
- **新记录通知挂在同步 worker 里，而不是界面里**：`SyncEngineRegistry.onCycleCompleted` 是 app 注入的钩子，**签名刻意无参数**——`pulled` 这个数不该离开 worker，任何地方都不该留下一个可被显示的计数。
- **两条不是「顺手」的决定**：
  - 钩子在** outcome ≠ FAILURE ** 时都跑，而不是 `pulled > 0` 时跑。`SyncWorker.enqueue` 用 `REPLACE`，她一发新记录就会取消正在拉取的那一轮：那一轮可能已经把 change 落进 Room 却没能跑到钩子，下一轮 `pulled = 0` 就**永远不会**通知。漏一次是永久性的，所以宁可多跑一次幂等检查（一次索引查询 + 一次 prefs 读）。
  - 排序键用 SQLite 的 **`rowid`（到达顺序）而不是 `occurredAtEpochMillis`**。写「上周六」的记录日期更早、到达更晚，按日期排序它永远当不上「最新」，通知就永久漏掉——而回填日期是这个产品最普通的用法。`ORDER BY occurredAt DESC` 只留给每周回看（那里要的正是"多久以前"）。回归测试：`aBackdatedRecordAnnouncesWhenItIsTheOneThatJustArrived`。
- **首见不广播**：设备第一次看到伴侣的记录时只把水位推到那条（`NewEntryAction.REMEMBER`），不发通知——把本来就存在的旧记录说成「TA 刚写了一条」是假信息。之后水位只存**一个 entry id**，多条一起到达也只有一条通知。
- **关闭语义**：`newEntryNoticeEnabled = false` 的 setter 顺手删掉 `lastNotifiedEntryId`，所以再打开时不会把关闭期间到达的记录当新的补发；`NotificationPreferences` 的全部状态是 2 个开关 + 1 个 entry id + 星期/整点 + 一个「权限问过没」标记。通知 id 固定（2001/2002），新的替换旧的，不会堆成需要清理的历史。
- **无统计自查（H7 的代码层）**：对新增/改动文件 grep `count|未读|统计|已读|活跃|badge|total` —— 命中只有说明「这里没有什么」的注释和一个既有的 `AvatarBadge` 组件名。没有条数、字数、天数、间隔、已读回执，也没有「对方没写」的反向提示。
- **进程装配搬到 `MoonLetterApplication`**：`SyncEngineRegistry.factory` 此前只在 `triggerSync` 里赋值，而它是 `lateinit`。WorkManager 完全可以只凭一条后台任务启动一个从未显示过界面的进程，那时 `factory.create()` 抛 `UninitializedPropertyAccessException`——一个真实的潜在崩溃，顺带也是通知在后台永远不响的原因。现在 Application 与 `triggerSync` 都走 `installProcessHooks`。
- **深链**：`MainActivity` 用 `mutableStateOf` + `onNewIntent` 持有 `openEntryId`（通知在 App 已开着时也要生效），`AppNavigation` 只在**本地仍有这条记录**时打开它，否则什么都不做——不给一个空壳详情页。回看与新记录两条通知都直达那一条记录。
- **入口**：`我们` 页新增「回看与通知」卡：两个开关、星期 chips（横向滚动）、`TimePicker` 只取整点（关掉回看时时间控件不显示），文案里写明「不含内容、不数条数、不攒未读」。视觉复刻与排版细化按分工留给另一个人。
- **我自己写出来的一盏红灯（不是假红灯，是真 bug）**：第一版 `pick()` 把**绝对时间戳**与 365 天这个**时长**直接相减比大小，于是所有候选的距离都是同一个量级、永远挑中最老的一条。`WeeklyReviewTest` 的 `pickTakesTheRecordClosestToAYearOld` 与 `pickBreaksATieTowardsTheMoreRecentRecord` 先把它抓红，改成用「现在 − 发生时间」算年龄后转绿。
- **绿灯**：`TEMP/TMP=E:/tmp JAVA_HOME=E:/jdk21-extract/jdk-21.0.2 ./gradlew --offline testDebugUnitTest :core:model:test :app:assembleDebug` → **BUILD SUCCESSFUL**，单元测试 **69 条全绿 0 失败 0 错误**（口径与记录 20/21 一致，不含 `:core:model:test` 的 1 条；上轮 48 + 本轮 21）。新增：`WeeklyReviewTest` 8（挑选与到点延时）、`NewEntryNoticeStateTest` 6（水位与开关后不留痕迹）、`NoticeChainTest` 7（真 Room + 真 SharedPreferences + Robolectric 的 `ShadowNotificationManager`：首见不播报、迟到一条只一条、自己的记录与草稿都不播、关闭后既静音也不留 id、倒填日期仍到达、回看对年轻档案与关闭状态都返回 null）。
- **必须知道的边界 / 未做**：
  1. **WorkManager 只保证窗口不保证整点**：周期任务受 Doze、省电与系统调度影响，「周日 20:00 到点就来」在 JVM 上无法证明（`millisUntilNext` 只证明**首次延时的算术**对）。真机必须验一次（H3）；同理，时间选择器只到整点也是这个权衡的一部分。
  2. 通知本身**未真机验证**：小图标（新加的单色月牙 vector）在状态栏的实际观感、两个渠道在系统设置里的名字、`POST_NOTIFICATIONS` 弹窗的时机（绑定后首次进 App 且只问一次）、以及点开直达那一条记录的真实手感。她若拒绝权限，两条链路都**静音而不是改用应用内角标**（这是刻意的，`NotificationManagerCompat.notify` 用 `runCatching` 包住）。
  3. **「TA」是写死的第三人称**，没有换成伴侣真名：名字缓存在 `AppNavigation` 的 prefs 里，而通知在 worker 进程路径上发。要显示真名是「多读一个 prefs 键」的量级，本轮按 plan 原文「TA 写了一条新的」实现，是否换成真名留给她本人判断。
  4. **删除/撤回记录时这条链会需要重做**：当前客户端没有任何把 `entries.deleted` 置 1 的写路径（只有 comments 有），所以「水位按 id 去重」不会因为某条记录消失而误报。将来加删除时，`rowid` 排序与 id 去重**两处都要一起改**，否则会把旧记录说成刚写的。
  5. 编辑已发布记录（现无此功能）不会造成重复通知：REPLACE 会让该行排到 `rowid` 末尾，但 id 不变 → 判定仍是 `NONE`。记在这里是为了下次不必重新推一遍。
  6. 关闭开关**不收回已经贴在通知栏的那一条**（她划掉即可），重新打开也不补发关闭期间的记录——见上面的水位删除规则。
  7. 未做：把当周挑中的那条也在 App 内呈现（打开就看到的卡片）。plan 只要求通知直达；界面复刻与参考图对齐另有其人。
  8. 落地动作与记录 21 相同且仍未完成：服务端用新构建重启 8080（当前无进程），两台手机装 `:app:assembleDebug` 产出的新 APK；开档当天的顺序以 `docs/testing/open-day-runbook.md` 为准——**还在用的那台不必清数据**，直接进「我们」页生成配对码，只有丢会话的那台走绑定/恢复路径。

### 记录 23：文档台账对齐与开档准备（2026-10-01）

- **Commits**：`57e6686 fix(server): make the documented session recovery command actually run`、`40fb3a7 test(server): pin the table names the open-day runbook counts`，docs 提交另计；`git rev-list --count origin/m1-stable-recording-loop..HEAD` 实测**本地领先 13 个提交**（docs 提交之前），推送仍逐次授权。
- **本轮没有新增产品能力**，做的是把台账、复核、README 与代码对齐，并把开档当天的操作顺序写成 `docs/testing/open-day-runbook.md`。台账 **70/118**（复核基线 62/118 → Task 2 恢复两项 + Task 11c 六项）。

- **给文档写命令时才发现恢复命令是哑的（真问题，不是文档问题）**：`SessionAdminCommand.run` 只读 `System.getProperty(...)`，而类注释、计划与 README 的用法全部写成 Spring 程序参数 `--moon-letter.admin.mode=...`。照文档敲的命令**解析不到 mode，直接当成普通启动返回**——web 已关，于是进程安静地跑起来又什么都不做。更要命的是第二处：`Mode.valueOf("ISSUE")` 找不到常量（枚举叫 `ISSUE_REPLACEMENT`），即便用 `-D` 传进去，文档里写的 `issue` 这个值本身也进不了分支。**这两处都只在真机锁死那天才会暴露**，而那天正是它唯一的用途。
  - 红灯：新增 `SessionAdminCommandInvocationTest`（纯 JUnit，不碰数据库）。首跑 `找不到符号 resolveRequest`（编译红），实现后再红两次：`moon-letter.admin.mode must be revoke-all or issue, was: issue` 与 precedence 用例漏传 user-id 导致的 `missing required argument`——后者是测试自己写错，修测试；前者就是上面那个真实缺陷。
  - 绿灯：`resolveRequest` 同时接受程序参数与系统属性（参数优先），`parseMode` 接受 `issue`/`issue-replacement`/`issue_replacement` 与 `revoke-all`/`revoke`；mode 有值而 user-id 缺失时抛带键名的错误而不是安静退出；未知 mode 抛错而不是继续启动 web。`run` 对 `revoke-all` 打印明确结果而不是 `token (shown once): null`。
  - 顺带修正：`executeForTest` 原来在 switch **之前**无条件 `queryForObject(couple_id …)`，`REVOKE_ALL` 根本不需要 coupleId，成员行缺失时会先撞上 `EmptyResultDataAccessException`。改为签发时才反查（`coupleIdOf`，查不到给出「该用户不属于任何空间，没有可恢复的东西」）。coupleId 从不接受手填——填错等于把会话签进别人的空间。
- **CLI 真跑过一遍（克隆库，dev 库未被写）**：`CREATE DATABASE … TEMPLATE moon_letter`（源库当时 0 连接）→ 用 README 原文那条命令跑 `revoke-all`，退出码 0、目标成员会话由 active 变 revoked、另一成员会话未受影响、`session_admin_audit` 落一行 `REVOKE_ALL` 且 `note` 为空 → 再跑 `issue`，退出码 0 且 stdout 只打印一次 43 字符令牌 → `DROP DATABASE`。**dev 库全程未连**，收尾核对仍是 `entry=0 … session=2`。
- **表名进了测试**：runbook 要求用 `entry / entry_block / comment / sync_change / media_asset / couple_space / couple_member / device_session` 计数来证明「一条真实记录产生一行 entry」。写这段时发现 `sync_change`、`media_asset` 都改过名，文档里的表名一旦写错，前后对比会永远等于 0 且看起来一切正常。新增 `SchemaTableNamesTest`（Flyway + Testcontainers 或 `TEST_DB_URL`，与 `SchemaConstraintTest` 同一套取库方式）钉住这八个名字，并显式断言三个旧名字不存在。
- **文档修正清单**（都按代码现状改，未凭印象）：README 删掉不存在的 `RECOVERY_SECRET` 前置条件、改为记录本地 CLI 恢复与 `BOOTSTRAP_SECRET` 可重复找回自己槽位的语义与其代价（该密钥现在是长期凭据，泄露＝第一位成员整槽被接管，服务端端口不得公网可达）；README 服务端启动改成 jar + 环境变量注入并写明 `mvn spring-boot:run --moon-letter.…` 传不进应用这条真机 403 根因；复核报告追加 §6 后续状态表（P0 与四条 P1 的技术部分逐条给提交号，同时写明「仍然成立的部分」是所有需要设备的证据）；交接快照 §3 五条问题标为已闭合、§4 顺序标出第 4 项才是当前第一优先级、§2 表里「服务器 8080 运行中」和「时间线仍非持续响应式」两处过时表述已改；计划 Task 2 第 82/83 项勾选并附证据与边界（真机失联演练仍归 H4），Task 11c 六项勾选并记录文件划分偏差（`core/sync/WeeklyReview.kt` + `app/notifications/WeeklyReviewWorker.kt`，非计划里的单个 `core/sync/WeeklyReviewWorker.kt`）。
- **一处故意不勾**：Task 2「bootstrap 密钥已消费」保持未勾选，并写明它被使用者的决定取代——重装后必须仍能用同一密钥找回自己的位置，与一次性密钥互斥。把代价和约束写在同一行，避免下一个人把它当漏项补掉。
- **本轮未做**：真机一切（E1 互见、E2 断网、H1 计时、H3 到点、H4 双端演练、D1 异机还原、U1/U2 视觉）；legacy 偏差清单（`CAPSULE_LOCKED/ARCHIVED`、`RoomSyncStore.mapState`、发布草稿时 `baseVersion` 固定 0、硬编码 `DEFAULT_DEV_BASE_URL = "http://10.138.79.194:8080"`、cleartext、图片无磁盘缓存、IMAGE payload 带本机绝对路径、主题不跨端同步、无 Room migration 测试）——这些是下一件事，开档前只挑会影响真实数据的那部分先修。

### 记录 24：开档前的两处链路风险——本机路径外泄与发布版里的开发机地址（2026-10-01）

- **Commits**：`5f0c0e6 fix(android): keep a device file path off the wire`、`b52c969 fix(android): stop falling back to the development machine in a release build`，docs 提交另计；`git rev-list --count origin/m1-stable-recording-loop..HEAD` 实测**本地领先 16 个提交**（docs 提交之前），推送仍逐次授权。
- **选择标准只有一条**：开档之后哪两处会让**她的**数据或她的手机出问题。其余偏差（枚举命名、`mapState`、主题不跨端、图片磁盘缓存）是质量债，不阻塞开档，留在后面。

- **一、IMAGE payload 把本机私有目录路径送出去了**。`LocalEntryWriter.blockJson` 对非文字块原样转发 Room 里存的 payload，而图片块的 payload 是 `{"localPath":"/data/user/0/com.twomemory.app/files/…","mime":"image/jpeg"}`。后果不是"不好看"：这条字符串进了 `sync_change` 行、进对端手机的 `entry_blocks`，**永久留在变更流里**（变更流是追加式的，改一处要重放全部）。照片本身走 assetId，路径在对方手机上不对应任何文件，纯泄露。
  - 红灯：把 `aPictureOnlyEntryCarriesBothItsLocalFileAndItsAsset` 改写成 `aReleasedPictureCarriesItsAssetButNeverItsLocalPath`——它原先**断言的就是泄露行为**（`JSONObject(block.getString("payload")).getString("localPath")` 等于本机路径），这正是"测试绿着但契约是错的"。新断言：线上不含 `localPath`、mime 仍在、**Room 里仍然保留**（上传 worker 要从那里读文件）。首跑 `assertFalse` 参数顺序写反导致编译红，改 `assertFalse(String, boolean)` 后是行为红。
  - 绿灯：`wirePayload` 只发 `{"mime": …}`；payload 不是 JSON 时（裸路径）也不再回显，落到固定 mime。**契约形状未变**：服务端 `blockTextPayload` 本来就接受字符串或对象，仍按字符串发送，服务端零改动。
  - 顺带核对：`ServerAddress` 之后确认对端渲染不依赖 `localPath`（`AppNavigation.entryPath` 对空串返回 null → `EntryPhoto.load(localPath=null, assetId)` 走下载），`TwoDevicePictureAndCommentLoopTest` 全绿即双 Room 环路与主题渲染未受影响。
- **二、发布版 APK 里硬编码着开发机的局域网 IP**。`const val DEFAULT_DEV_BASE_URL = "http://10.138.79.194:8080"` 同时是绑定页的预填值和 `SyncSession.load()` 在**没有已存地址时**的回退值。她的 IP 每天变；预填值错了她改一下就行，真正的问题是回退值：release 版一旦 prefs 里没有 baseUrl，就会安静地去连一台不存在的机器，表现成"同步转圈、记录像丢了"，而这正是这个产品最不能出现的观感。
  - 新增 `ServerAddress.resolve(stored, isDebugBuild)`：存过的地址优先；**只有 debug 构建**才回退到开发机；release 没有地址就返回 null。`SyncSession.load` 收到 null 时 `return null` → 落回绑定页问一次，而不是拿猜测的地址去连（**故意不清 prefs**：那条会话记录没坏，缺的只是配置，清掉等于把她的令牌扔了）。
  - `app/build.gradle.kts` 开 `buildConfig = true` 以取 `BuildConfig.DEBUG`。测试 3 例（存过的优先 / debug 回退 / release 无地址）；绑定页 `canSubmit` 已要求 `serverUrl.isNotBlank()`，所以 release 预填空串只会让她输入一次。
- **修正记录 23 的一处误判**：那份清单把 cleartext 列为遗留偏差，实际 `app/src/debug/AndroidManifest.xml` 里才有 `usesCleartextTraffic`，`src/main` 已无该标志（`3a50c8f` 就修好了）。记录 23 是当轮证据，不回头改；正确表述写在这里。
- **数字**：Android JVM 单测 **73 例全绿**（含新增 3 例与改写的媒体门禁用例），`:app:assembleDebug` 成功；服务端本轮**未改动**，仍是 63 例全绿。
- **仍然未做**：真机一切；`baseVersion` 语义（create 与 publish 同批入队时固定 0 是**对的**，服务端只对 PUBLISH 校验 `rowVersion`，其余动作不看它；风险在于将来出现"发布一条早已单独创建过的旧草稿"这条路径时必须改成本地记录的版本号，届时 `SyncSession.publish` 是唯一入口）；图片磁盘缓存；主题跨端；Room migration 测试；`EntryMode.COLLABORATIVE` 命名（服务端与 Postgres `entry_mode` 枚举同名同值，改名是数据层迁移不是改注释，**开档前不做**）。
- **补记（同轮，核对代码后）**：偏差清单里的「无 Room migration 测试」需要精确一点——迁移**存在且已接线**：`AppDatabase` 有 `MIGRATION_1_2`（v2 加 `comments` 表）并在 `build()` 里 `addMigrations(...)`，全仓库**没有任何** `fallbackToDestructiveMigration`，`exportSchema = true` 且 `schemas/…/1.json`、`2.json` 都已导出。缺的是**证明它保数据的测试**：`MigrationTestHelper` 只在 instrumentation 里跑，而本机模拟器静默退出，所以 1→2 至今只有"写了"没有"验过"。这条在开档后第一次改 schema 之前必须补上——真机上升级失败的表现就是应用打不开，而她不会把它读成 schema 版本问题。

### 记录 25：开档手册默认了手机是干净的，而它不是（2026-10-01）

- **现场**：`fb56861` 写的手册 §1 第 1 步是「还在的那台打开应用：已经绑定过就直接进『我们』页」。这句从**服务器视角**完全正确（记录 19 之后库确实空了），从**她的视角**是错的：删库里的行不会变成变更流里的删除动作，两台手机各自的 Room 里那 5 条标题 `u` / `h` 的乱码草稿一条都没少，时间线照旧列出来。按手册原样走完开档，她打开应用的第一眼就是 5 页乱码——正好撞在门槛清单「身份体面／第一眼」这一条上，也和本台账记录 19 自己写下的边界互相矛盾。手册是这一轮新写的，矛盾没有扩散到代码，但它是**开档当天唯一被照着执行的顺序**，所以危害等于现场。
- **改法**：§1 第 1 步改为「两台都先 设置 → 应用 → 月笺 → 清除数据（或卸载重装）」，并写明这一步会连会话令牌与服务器地址一起清掉，因此动手前手上要有开发机局域网地址与 `BOOTSTRAP_SECRET`；后续步骤重编号为 bootstrap 找回原成员位 →「我们」页生成配对码 → 对端以 `REJOIN` 加入 → 各自取名字 → 回看与通知。§0 第 3 步补一句「**服务端空了不等于手机端干净**」，并在 §1 明写**真实内容出现之后绝对不要用清除数据当清理手段**——开档当天它是唯一手段，之后它是不可再生内容的删除键。
- **顺带钉住的操作风险**：`SessionAdminCommand` 作用在参数指向的那个库上。README §会话恢复 现在写明演练要 `CREATE DATABASE … TEMPLATE moon_letter` 克隆、练完 `DROP DATABASE`，`revoke-all` 会当场作废两台手机正在用的令牌；`issue` 是先吊销再签发，旧令牌立刻无用。手册 §3 的 H4 那一行加了同样的前置（只能开档前做，或紧跟一次 `scripts/backup.sh`），且必须接着 `issue`。
- **必须知道的边界**：本轮只改了文档，**代码与测试未动**，服务端 63 例／Android 73 例的数字与记录 24 相同。「清除数据后重新绑定」这条路径现在是纸面顺序，不是验证过的顺序——它本身、以及它依赖的 bootstrap 找回槽位与 `REJOIN`，都还没有一次真机证据（H4/H6 侧）。

### 记录 26：重装的那台手机把整本书拉回来了——客户端证据补上（2026-10-01）

- **Commits**：`5cfc152 test(android): prove a wiped phone gets its whole book back`，docs 提交另计。
- **补的是哪一块证据**：记录 25 说清数据是开档当天唯一的手机端清理手段，但那一串动作里最要命的一半**从来没有被测过**：三台设备夹具都是从 cursor 0 开始、空间里还没有内容。而绑定页写着「绑定只需要一次。换手机后在这里重新登录即可」——这句话的真实含义是「一台 cursor 归零的空手机，能从一个已经有内容的空间把全部历史拉回来」。这是本仓库唯一不可逆的失败模式（她重装一次，回忆没了），此前只有服务端侧证据（`SessionRecoveryTest`、`reclaimFoundingMember`），客户端侧为零。
- **新竖切 `ReinstalledPhoneRebuildsWholeBookTest`（真 Room 文件 + 真 Retrofit + 真 Spring Boot + 真 PostgreSQL，端口 18082，独立库 `moon_letter_reinstall_test`）**，七步：
  1. 两台设备都走**出厂的那条界面链路**（`SetupViewModel.bootstrap` / `pair`）绑定；第一位顺手把称呼改成「小满呀」——故意和重装时会重新填的那个名字不同。
  2. 内容：A 两条已发布、B 一条已发布、A 在 B 那条下回一句。计数钉死：A 推 `4` 次操作、B 推 `2`、A 再推 `1`（评论），变更流共 `4` 行，**两台各自从 cursor 0 拉到 4 行**。
  3. **模拟清除应用数据**：关掉 Room、删掉库文件、清空 `moon_letter_session`。三条断言证明这一清是真的清空——`SyncSession.load` 返回 null（回绑定页而不是崩）、时间线为空、`currentCursor == 0`。
  4. 用**同一个** `BOOTSTRAP_SECRET` 重新 bootstrap：`userId` 与 `coupleId` 都必须是原来那一个（不是第三个成员），且这台手机新生成的配对码是 `REJOIN`（空间两个位置都占着），而空间里的称呼仍是「小满呀」——重装时重新声明的「小满」没有覆盖掉她改过的名字。
  5. **旧令牌当场失效**：`issueReplacementSession` 先吊销再签发，所以拿被清掉那台手机上的旧 token 去拉，得到 `needsRePair=true`、`pulled=0`，且**游标一步没动**（失败的页不推进 cursor，这条顺带被钉住了）。
  6. 新令牌一次 `pullAll` 拉回 `4` 行：三条记录全部 `PUBLISHED`、B 那条的正文在本机可读、那句回应也在、`refreshNames` 拿回「小满呀 / 阿屿」。
  7. 重装后仍**能写**：A 发第四条，B 拉到 `1` 行并显示为 `PUBLISHED`。
- **故意保留的推送顺序**：A 的那句回应必须在 B 那条记录推上去**之后**才推。outbox 是严格 FIFO，一条引用了服务端从未见过的记录的评论会失败并把整条队列卡住——这个顺序本身就是被测语义的一部分，代码注释里写明了。
- **红灯核验（性质检查，非 TDD 首发）**：临时把 `RoomSyncStore.currentCursor` 改成恒返回 `9_999L`（即"这台手机以为自己早就跟上了"），竖切立刻在**第一次 pull** 就红：`expected:<4> but was:<0>`（`:185`）。红的位置比预想的更早，因为该变异影响所有 pull；随后按位还原（`git diff --stat` 空）并用 `--rerun` 强制真跑一次，绿。**未**证明的点：变异只验了「cursor 错了就拉不回东西」，没有单独验「清数据那一步是否真的清了」——那三条断言仍是直接的现场断言。
- **数字**：Android JVM 单测 **74 例全绿**（含 `:core:model:test` 的 1 条；上轮 73 + 本条 1），`:app:assembleDebug` 成功。服务端本轮**零改动**，`mvn -o test` 复跑 **63 例全绿 0 失败**。
- **必须知道的边界**：这是**真 Room + 真服务端但在 JVM 上**的证据，按 §2 的口径不能写成「真机已验证」。H4 的表格状态因此改为「部分通过」：CLI 克隆库演练 + 客户端重装竖切已在，**两台真机各自清除数据后重新绑定、并互相看见对方的历史**这一步仍然只能当天在人手里做（`docs/testing/open-day-runbook.md` §1）。

### 记录 27：指导图核心 UI 壳层交接（2026-10-01）

- **Commit**：`926ebdb feat(ui): wire guided app shell and profile controls`，已推送到 `origin/m1-stable-recording-loop`。
- **本轮内容**：首页默认采用暖米色参考层级（标题「我们的时光」、中秋副标题、日期/具体时间进入纸卡、左侧缝线式连续时间轴）；底栏固定为「时光 / 相册 / ＋记录 / 地图 / 我们」，淡紫工具栏与选中胶囊；封面仍可由用户主动替换；「我们」页保留头像/名字/暖米色/纯白主题入口、未来功能显示「还没开放」，并显示 M1/版本证据。
- **验证边界**：本机只执行 `git diff --check` 与源码检查；未运行 Gradle、Android SDK、模拟器或真机。导航/个人页 Compose 测试、390×844 截图和真实 Room 数据链路均 **NOT RUN**，不能据此更新 U1/U2/H1/H6/H8 或宣称 M1 通过。
- **下一步**：SDK 机器用该 SHA 构建 APK，覆盖安装旧包保留数据，执行 `docs/testing/real-use-log.md` 的 A/B 双机首条记录闭环，并回填设备/系统/日志/截图证据。

### 记录 28：相册与城市页接入真实本地投影（2026-10-01）

- **Commit**：`d697e94 feat(ui): make album and city views data-driven`，已推送到 `origin/m1-stable-recording-loop`；计划文档末尾修正为 `15c9971`。
- **实现**：相册从 Room `entry_blocks` 的已发布 IMAGE/VIDEO 派生月份、日期、作者和来源记录；图片复用本机/远端 asset 渲染，视频明确回到原记录。城市页从已发布 LOCATION block 派生城市故事，地图区域不可用时列表仍能阅读；两页都不请求实时定位、不造演示数据。
- **测试边界**：新增 `FutureFeatureScreensTest`，但本机未运行 Compose instrumentation、Gradle 或真机；因此相册/城市页仍为 **NOT RUN**，服务端媒体/位置接口尚未接通，不能标记 V2/V3 或 M1 完成。
