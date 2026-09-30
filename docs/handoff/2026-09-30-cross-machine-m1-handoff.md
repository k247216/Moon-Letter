# 月笺（Moon Letter）跨机器开发交接

更新时间：2026-10-01（并入 `docs/human-scale-principles.md`、Task 0、Task 11a/11b/11c 与 Robolectric 优先的证明方式）
当前分支：`m1-stable-recording-loop`
当前结论：**M1 NOT VERIFIED；Task 1 已落地（服务可启动），但记录闭环、认证与 Android 链路仍是架构骨架，不是可交付闭环。**

## 1. 权威资料与冲突处理

执行前必须阅读：

1. `docs/human-scale-principles.md`（长期取舍顺序，规格与计划未覆盖时的裁决依据）
2. `docs/superpowers/specs/2026-10-01-self-use-m1-design.md`
3. `docs/superpowers/plans/2026-10-01-self-use-m1-implementation.md`
4. `docs/testing/m1-acceptance.md`
5. `docs/design/reference/` 与 `docs/design/brand/`

`2026-09-30-couple-diary-design.md` 和 `2026-09-30-m1-stable-recording-loop.md` 已降为历史参考。发生冲突时，以 2026-10-01 的规格与执行计划为准；两份文档未覆盖的取舍，按 `docs/human-scale-principles.md` 的顺序裁决。不得继续实现跨作者同块合并、依赖 FCM 的秒级承诺或旧的伪 E2E 验收。

## 2. 两台机器的职责

### 本机：统筹与一致性复核

- 维护产品范围、数据规范、任务顺序和验收标准；
- 审查另一台机器提交的 commit、日志、测试和截图；
- 在真实设备截图上按 §6 的三条判据复核真实数据页面，参考稿只用于核对方向（质感、结构、层级），不做像素比对；
- 发现偏差时给出具体文件、场景和验收条件，不在本机安装 Android SDK 或开发业务功能。

### 开发机：实现与取证

- 按新执行计划顺序开发：Task 0 决策确认 → Task 1–14，其中 Task 11 之后紧接 11a/11b/11c，Task 12 之后紧接 12b（应用内可改的身份与外观）；
- 配置 JDK、Android SDK、PostgreSQL、一台具名真机或 API 37 模拟器和对象存储；
- 双端闭环以 JVM 上的 Robolectric 夹具（两个真实 Room 数据库 + 真实 Retrofit 到真实 Spring Boot）为主要证明手段，真机/模拟器用于冒烟和 Task 14 录像；
- 完成服务端、Android、真实双端同步、备份导出和设备视觉验证；
- 每个任务提交独立 commit，并把证据写回 `docs/testing/m1-acceptance.md`；
- 待定项按执行计划 Task 0 处理：主数据表示未答复时走默认方案 A 并在回传中标注；**开始真实使用的日期（卡 Task 11a）与桌面图标名称（卡打包）不能默认**，必须按 §8 格式回传索取。称呼、身份色、封面与措辞不要提前索取，由 Task 12b 交付为应用内可改项。

## 3. 当前代码事实

已有内容包括 PostgreSQL/Flyway V1–V5、服务端 controller/service 雏形、Android 多模块结构、Room entity/DAO 接口、同步状态机骨架、Compose 页面骨架和六张批准视觉稿。

以下内容尚未成立，开发机不得视为已完成：

- Task 1 已落地（`application.yml` + `application-dev.yml` + actuator health，验收 Gate S1 = PASS），但这只证明服务能在开发机启动，不证明认证、同步或数据链；
- Spring Security 和真实登录/初始化未接入，`X-User-Id` 可被客户端任意伪造；
- 发布、修改和冲突路径存在丢失 actor/成员校验的问题；
- `/sync/operations` 只写客户端 payload，未调用真实业务变更；业务 service 也未稳定追加 change feed；
- 全局 identity cursor 在并发事务逆序提交时可能永久漏变更；
- Android 没有完整 Retrofit/OkHttp、Room `SyncStore` 和 app 级依赖接线，cursor 仍可能使用常量；
- UI 仍包含硬编码演示数据；
- Android 构建与 instrumented test 尚无可信通过证据；
- 服务端 `TwoDeviceSyncTest` 使用 `FakeServer`，Android `TwoDeviceScenarioTest` 仅做字符串断言，均不是端到端验收；
- 历史“28 tests pass”只能保留为组件级证据，不能推导 M1 完成；
- **Task 2 正在本机工作树进行中，尚未提交**：`auth/SecurityConfig.java`、`auth/BootstrapController.java`、`auth/BootstrapService.java`、`auth/DeviceSessionAuthenticationFilter.java`、`V6__device_sessions_and_bootstrap.sql`、`auth/AuthenticatedUser.java` 的改动、各 controller 改读 principal，以及 `src/test/.../auth/BootstrapAuthenticationTest.java`。开发机在看到这批代码的 commit 之前**不要开始 Task 2**，否则会出现两套并行的认证实现；提交后先比对再决定是否继续。
- 现有 V1–V5 迁移与本规格存在多处冲突（枚举值、字段长度约束、幂等键、change 序号、配对码路径、对象存储 `head()` 占位）。逐项清单见规格 §6.5，任何冲突必须在写代码前先改迁移或改规格，不能靠运行时代码绕过。

## 4. 产品实现边界

- 一个人未配对时也能记录和回看，配对不应成为写日记的前置条件。
- 草稿只对作者可见；已发布个人记录对伴侣可见并可评论。
- 共同记录是同一主题下的双视角：每人维护自己的块，明确作者和具体时间；M1 不允许编辑对方块，也不做跨作者合并。
- 首个交付切片只做个人纯文字：`Room → Outbox → HTTP → PostgreSQL → Change Feed → 第二个 Room`。
- 图片只能在上述链路真实通过后开始；视频、语音、音乐、相册、地图、胶囊、倒计时、过去的今天和聚合式「本周小结」保留路线图但暂停开发。注意区分：**回看一条更早的记录（Task 11c 每周回看）属于 M1 必须项**，被暂缓的是把记录累计成小结或评分。
- 同步触发为启动、回前台、手动刷新和恢复网络；后台由 WorkManager 尽力执行，不承诺固定分钟数。FCM 后置且只触发拉取。
- 不监控位置、不做打卡连续天数、不使用 AI 代写、数据必须可备份恢复和选择性导出。
- 记录成本是硬指标：从首页到开始书写 ≤4 次交互、≤10 秒，`＋记录` 直达书写态且不先选媒体类型；度量方法见规格 §3.5，任何提高成本的改动按回归处理。
- 不做关系记分牌：条数、字数、活跃天数、间隔天数、已读名单、“对方很久没写”之类聚合统计在服务端、客户端、调试面板和本地设置里都不允许存在；边界见规格 §3.4。
- 会话不能锁死：吊销与签发都要求已有有效会话、bootstrap 只在空库可执行，因此必须存在本地 CLI 或 `RECOVERY_SECRET` 保护的恢复路径并完成演练；见规格 §5.4。
- 系统分享入口属于 M1（Task 11b）：从相册或其他 App 分享图片/文字直接落为本地草稿，不要求先打开应用。

## 5. 强制执行顺序

| 阶段 | 对应计划 | 退出条件 |
|---|---|---|
| 0 前置决策 | Task 0 | 只定单向门：主数据表示（未答复按默认方案 A，选 B 须先回传 Task 5–7 返工评估）；开始真实使用的日期在 Task 11a 前定稿；Android 桌面图标名称给两个候选。其余偏好项**不提前问使用者**，改为交付可改能力 |
| A 可运行与安全边界 | Task 1–4 | 服务可启动；bootstrap/配对/token 生效；会话恢复路径可用；越权测试通过 |
| B 正确服务端同步 | Task 5–7 | typed operation、同事务 change、空间有序 cursor、真实 HTTP/PostgreSQL E2E 通过 |
| C Android 本地链 | Task 8–10 | APK 构建；Room/outbox/cursor 持久化；Retrofit 与真实依赖接通 |
| D 首个真实竖切 | Task 11 → 11a | A 离线写入，经真实服务端出现在 B 的独立 Room/UI；有录像与日志。11a（当天开始写真实记录）与 Task 11 同日完成，不可延后 |
| D+ 使用侧补齐 | Task 11b、11c | 系统分享入口与每周回看；不阻塞 Task 12，但不得无限期推到视觉验收之后 |
| E 共享与数据权 | Task 12、12b、13 | 双视角/评论/版本、**称呼/身份色/主题/封面的应用内可改（12b）**、图片完整性、备份作为运行能力（≥7 天连续健康证据）、恢复与导出通过 |
| F 视觉与最终验收 | Task 14 | 四个真实数据页面在真机上按 §6 的三条判据通过；H8 现场演示可改；所有 Gate 指向同一 commit |

任何阶段未满足退出条件，不能跳到后续扩功能。尤其禁止一边保留假的数据链，一边继续制作相册、地图或视频页面。

`docs/testing/m1-acceptance.md` 新增的 H 类 Gate（记录成本、分享入口、每周回看与提醒、会话恢复、备份运行能力、开始真实使用、无统计自查、可修改性）与 S/A/E/D/U 同权重，不能当作附加项跳过。

## 6. UI 一致性硬规则

- 首页必须是左侧缝线式连续时间轴，可一滑到底，不按天切页；显示日期和具体时间。
- 顶部封面可替换；中秋只作少量纪念元素。
- 底部导航固定为 `时光 / 相册 / ＋记录 / 地图 / 我们`，编辑器内不显示主导航。
- 个人编辑器保持自然书写纸面；共同编辑器保留双方作者标识和独立内容块。
- 默认暖米色、可切纯白；主题不覆盖伴侣选择。
- 头像与名字可修改，但只能修改自己。
- 功能图标统一细线描边，禁止 emoji、填充图标和不同图标体系混用。
- 中文正文不低于 14sp，触控目标不低于 48dp；键盘、小屏与大字号不得遮挡核心操作。
- 390 × 844 是截图对比基准但不是固定尺寸。禁止把参考 PNG 直接铺成界面背景。
- 相册和地图在 M1 必须是明确的未开放状态，不能用演示卡片造成已完成功能的错觉。
- 六张参考图是方向，不是像素基线。视觉验收在**真实设备**上进行，判据只有三条：像纸、像使用者自己的东西、不像软件（规格 §8）。
- 名称、双方身份色、封面文案和提醒措辞由使用者决定，规格与参考图在这些项目上让位。**决定权体现为"随时能在应用内改"（Task 12b），不体现为"使用前必须先选"**：默认值必须开箱可用，空白不算交付。唯一需要提前问的是 Android 桌面图标名称（launcher 安装时读取，用户无法自由输入），给两个候选即可。向使用者提问时一律给两个具体方案让其挑选，不开放式征求评价。

## 7. 开发环境与云端配置

### 立即需要

- JDK 21、Maven；
- Android SDK 37、Platform Tools、一台具名真机或 API 37 模拟器（只用于冒烟测试和 Task 14 录像）；
- PostgreSQL；推荐通过 `infra/compose.yaml` 启动，不能使用内存数据库代替最终 E2E；
- 双端证明在 JVM 上完成：Robolectric 夹具同时打开**两个真实 Room 数据库文件**并用**真实 Retrofit 打真实 Spring Boot + 真实 PostgreSQL**。因此不再要求准备两个模拟器或两份应用数据目录；这是证明成本的选择，不是降低标准；
- 本地 `BOOTSTRAP_SECRET` 和**与之不同的** `RECOVERY_SECRET`，都只放环境变量或未提交 `.env`；
- 一份可以脱离服务端阅读的会话恢复手册（规格 §5.4），与备份介质一起保存。

### Task 13 才需要

- S3 兼容对象存储；本地优先使用 MinIO；
- `S3_ENDPOINT`、`S3_REGION`、`S3_BUCKET`、`S3_ACCESS_KEY`、`S3_SECRET_KEY`；
- 备份的运行能力（规格 §7.1）：一条与服务端凭据不共用的离机副本路径、对象存储版本化开启方式，以及备份介质的加密密钥。手机双端互传不算备份。

### 暂时不需要

- FCM 与 `google-services.json`；
- 正式云数据库、域名、CDN、短信/邮箱服务；
- iOS/Xcode 环境。

所有密钥必须留在开发机的未提交环境文件或 CI Secret。若执行任务发现新的外部依赖，必须先在交接记录说明用途、费用、替代方案和所需权限，再要求用户配置。

## 8. 开发机每次回传格式

```text
任务：Task N / 名称
分支：m1-stable-recording-loop
提交：<sha>
改动：<文件与行为摘要>
环境：<OS/JDK/SDK/设备/PostgreSQL>
测试：<完整命令>
结果：<退出码、通过数、失败数、跳过数>
证据：<日志/截图/录像/CI URL>
未完成：<明确列出；没有则写无>
风险：<会影响下一任务的事项>
```

不得只回传“已完成”“测试没问题”或一张静态截图。本机复核后才允许更新 Gate；组件测试通过不能替代真实链路证据。

## 9. 当前下一步

Task 1 已提交（`build(server): add reproducible runtime configuration`，Gate S1 = PASS）。下一个任务是 **Task 2：设备会话认证**，且必须连同规格 §5.4 的会话恢复路径与“库中不存在任何有效会话”的恢复演练一起做，不能只完成 bootstrap 和 bearer。

Task 0 不阻塞 Task 2–10：主数据表示按默认的**方案 A（PostgreSQL 权威）**实现即可，若要改选方案 B 必须先评估 Task 5–7 的返工再动工。但两项决定分别卡住后续节点：

- **开始真实使用的日期与每周投入上限**决定 Task 11a 能否同日执行；
- **桌面图标名称**需要在打包前给一次候选答复；应用内称呼、身份色、封面与措辞不提前索取，由 Task 12b 以可改入口交付，未实现它就不能声称 §6 的所有权规则成立。

另一个未回答的问题：**伴侣的手机是 Android 还是 iPhone。** 当前所有验收假设双方 Android；若其中一方是 iPhone，Task 11a 之后的设备与录像证据方案必须先在本文件更新，不能靠模拟器代替 iOS 侧的真实体验。
