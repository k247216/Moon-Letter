<!-- Deployment/person/device identifiers in this public document are anonymized. -->
# 月笺接手现状与真实坑（2026-10-02）

## 最新交付覆盖说明：v10（2026-10-03，本节优先）

当前推荐 `MoonLetter-responsive-v10-20261003.apk`，versionCode=10，SHA-256 `17998F0F9E2B53A948AB0D19503C823642E2F09D8EBC4A8C389EE0FAEE3AC581`。修复前台立即/3 秒接收、发送即时尝试、头像资料媒体链路和音频持久缓存，改版共同记录、日期选择及倒计时，仅设置保留测试通知。证据见 `docs/testing/2026-10-03-v10-responsive-acceptance.md`。两台实体手机的本轮验收仍待用户；局域网服务健康，跨省云端/独立相册共享未部署。下方 V9 及更早信息为历史，不覆盖本节。

## 历史交付说明：v9

2026-10-03 14:49 补充：本地 Spring 服务在 8080 启动，电脑端 `http://192.0.2.10:8080/actuator/health` 为 UP，既有数据库启动前已备份。新增 `scripts/start-local-server.ps1` 保存本机加密启动密钥并支持重启；当前 JDK 有公用网络防火墙许可。三个真实服务双端门禁 3/3 通过，相册组合偏好隔离修复后 8/8 通过。详见 `docs/testing/2026-10-03-local-two-phone-runbook.md`。云端尚未部署。

当前交付 `android/app/build/outputs/apk/debug/MoonLetter-polished-v9-20261003.apk`，versionCode=9，versionName=`0.1.0+local-product-repair-v9-20261003`，37,484,841 字节，SHA-256 `049FC564F6EF601F2983ED73E8D56CC73B3F6E9FC07E085C0BFCF42CA3E1CB17`。V9 重点是本地产品修复：指导图风格首页与时间轴、类型化详情、真实录音、独立相册保存、地图点选、本机提醒和首次身份设置；云端服务器、媒体上传和双机闭环仍按当前阶段暂缓。完整证据和限制见 `docs/testing/2026-10-03-v9-local-acceptance.md`。

关键原生验证：状态栏/同篇补写/提醒/录音/城市故事 5/5，时间轴与详情 21/21，编辑器 24/24，我们/关系 15/15，Room 追加 2/2；独立相册核心屏幕单独运行 2/2。组合视觉相册用例仍有 2 项超时，未宣称全套视觉验收通过。

历史 v8 交付仍保留在下方旧记录中，不是当前推荐下载包。

本轮优化首页密度/底栏、相册原图保存、371 个真实城市地图与搜索、个人页层级和身份优先初始化。最终原生 79 项全部通过；JVM 136 项中 126 通过、10 项受 Android 9 runtime/真实服务凭据门禁阻塞。四项 Important 审查发现均先复现再修复；旧头像副本清理与纯白控件全面像素覆盖暂缓。完整证据与边界见 `docs/testing/2026-10-03-v8-native-acceptance.md` 和 `docs/testing/visual-v8/`。

独立相册没有接入云盘；个人头像只在本机保存；街道瓦片未在模拟器成功加载，不代表大陆详细地图服务验收。未提交、推送、部署或采购。下面的 v7/v6 及服务端 PID、地址、健康状态均是历史现场快照，不是本轮重新核实的运行状态。

## 历史交付说明：v7

当前交付包为 `MoonLetter-journal-albums-map-v7-20261002.apk`，versionCode=7，SHA-256 `EA06FDBB360EB9E4D3AFB794D27E28B6A6692644568F0267C56B662D69748F31`，36,652,981 字节。本轮目标按 `docs/design/2026-10-02-corrected-product-boundaries.md`，**独立相册不再复用日记**；主导航地图是真实省界/坐标点选，旧保存快照回调已移除。已建立 API 34 Pixel 6 模拟器；37 项本轮原生测试及 33 项相关 JVM 测试通过，详情/限制见 `docs/testing/2026-10-02-v7-native-acceptance.md`。街道外部瓦片未在模拟器成功加载；云盘和正式云服务未部署。保持用户已有脏工作区，无提交/推送。


接手基线：**`0eb895d`**（分支 `m1-stable-recording-loop`，已 push）。本文件自身在 `0eb895d` 之后的**一笔文档提交**里；当前会话在该基线之上继续开发，改动仍在工作树中，尚未提交或 push。
本文件是**执行交接**：只写现状、待办和我踩过的真实坑，不改任何已批准规格——产品形态见 [self-use M1 规格](../superpowers/specs/2026-10-01-self-use-m1-design.md) 与 [指导图功能规格](../superpowers/specs/2026-10-01-guided-ui-and-feature-completion-design.md)，逐轮证据见 [验收台账](../testing/m1-acceptance.md)（记录 1–43），真机动作清单见 [feature-checklist.md](../testing/feature-checklist.md)。

**当前会话更新**：已在此基线之上补充开发凭据环境化、Room 1→2 迁移保数据测试、纪念日服务端/客户端共享链路和 Windows/JDK Tomcat NIO2 启动回退；本轮完成了面向指导图的纸张视觉重构、阿里云 ECS/OSS 云媒体基础和类型化时间轴卡片，并重新生成可安装 APK。所有改动未提交、未 push。以下现场数字仍以快照为基线，新增验证结果见本文后续对应条目。

---

## 1. 现场快照（每条都可自己复核，别信我的转述）

| 项 | 现状 | 怎么复核 |
| --- | --- | --- |
| 服务端进程 | **当前运行**：PID 6736、`:8080`；使用 JDK 21 重新打包的当前 jar，并启用 `--moon-letter.server.tomcat.nio2-connector=true` 绕过本机 JDK NIO 回环异常 | `Invoke-RestMethod http://127.0.0.1:8080/actuator/health` 应返回 `UP` |
| 局域网地址 | `http://192.0.2.10:8080` 实测可达（debug 包预填的就是它，`ServerAddress.DEV_DEFAULT_BASE_URL`） | 手机浏览器打开该地址 |
| 运行中的 jar | `server/target/moon-letter-server-0.1.0-SNAPSHOT.jar`，当前由 JDK 21 重新打包，含当前纪念日链路和 NIO2 回退 | `Get-FileHash server/target/moon-letter-server-0.1.0-SNAPSHOT.jar -Algorithm SHA256`；当前 SHA-256 为 `E214B42F08B68CC4946F55497FF9273F859B858925049B8585D4D96B86906E95` |
| 数据库 | docker 容器 `infra-postgres-1`（5432，healthy），真实库 `moon_letter` | `MSYS_NO_PATHCONV=1 docker exec infra-postgres-1 psql -U moon_letter -d moon_letter -c '...'` |
| 库里内容 | 1 空间 / 2 用户 / 2 成员；**entry 5 条全是 DRAFT，已发布 0**；`comment` 0；`sync_change` **0 行**；`media_asset` 0；有效配对码 3 | 上面的 psql 计数 |
| 媒体目录 | `server/storage` 存在且**空**（与 `media_asset=0` 一致）；代码默认 `./storage`（`LocalObjectStorage.java:26`），进程 cwd 是 `server/`，两者同一路径 | `ls server/storage` |
| APK | `android/app/build/outputs/apk/debug/MoonLetter-attachments-city-v6-20261002.apk`，**20,906,118 字节，16:14**，`versionCode=6`、`versionName='0.1.0+attachments-city-20261002'`；包含半屏指导封面、交错手账时间轴、详情图片浏览、真实媒体选择/上传、音乐和离线城市选择、相册新建入口 | `Get-FileHash android/app/build/outputs/apk/debug/MoonLetter-attachments-city-v6-20261002.apk -Algorithm SHA256`；当前 SHA-256 为 `A4D7DE8A7FC8E81DC9A57ABA1959524BB8F10FED2F347EED38AF5E707158984C` |
| 包身份的新口径 | 旧包曾长期复用 `versionCode=1` 和 `app-debug.apk` 文件名，容易被下载缓存或安装器误判为同一包；当前改为 `versionCode=6`、固定可识别的 `versionName`，并复制为唯一交付文件名。**字节数不可单独当指纹**，应以版本号 + SHA-256 为准 | `aapt dump badging <apk>`；`Get-FileHash <apk> -Algorithm SHA256` |
| Android JVM | 设计系统、网络、时间轴、编辑器和 25 个不依赖真实数据库的 app 单元测试通过；3 个真实服务门禁因未注入 `TEST_DB_PASSWORD` 被安全前置检查挡住，未伪报通过 | `cd android && gradle :core:network:testDebugUnitTest :core:designsystem:testDebugUnitTest :feature:timeline:testDebugUnitTest :feature:editor:testDebugUnitTest :app:testDebugUnitTest --no-daemon`；宿主机无设备时仍不能替代真机触控/视觉证据 |
| 服务端测试 | 原有全套未重跑；本轮 AnniversaryApiTest 4 例 + AnniversaryRulesTest 3 例通过 | `cd server && JAVA_HOME="E:/jdk21-extract/jdk-21.0.2" mvn -q "-Dtest=AnniversaryApiTest,AnniversaryRulesTest" test` |
| androidTest | 只能 `compileDebugAndroidTestKotlin`（全模块通过）；**本机跑不了**：无模拟器、`adb devices` 为空 | — |
| Room 迁移保护 | 新增 `AppDatabaseMigrationTest`，v1 的 entries/blocks/outbox/cursor 保留断言 + v2 comments 表校验；当前已完成 `:core:database:compileDebugAndroidTestKotlin`，未执行 instrumentation | `android/core/database/src/androidTest/.../AppDatabaseMigrationTest.kt` |
| 初始化密钥 | **不在仓库里**，在 `E:\MoonLetter\.workbuddy\tmp\port8080-cmd.txt`（整条启动命令，含口令）。不要复制进仓库、文档、日志或提交 | — |

复制即用的验证命令：

```bash
cd /e/MoonLetter/Moon-Letter/android && TEMP=/e/tmp TMP=/e/tmp \
  JAVA_HOME="E:/jdk21-extract/jdk-21.0.2" \
  ./gradlew testDebugUnitTest :core:model:test :app:assembleDebug compileDebugAndroidTestKotlin
```

---

## 2. 昨晚到今晚这 11 个提交做了什么（症状 → 根因 → 证据类型）

| 提交 | 症状（用户会看到什么） | 根因 | 证据 |
| --- | --- | --- | --- |
| `ef4274c` | — | 合并昨天的 UI 顶点和逐功能清单 | — |
| `b0da347` | 照片可能被提交进 git | `server/storage` 未 ignore | 仓库 |
| `bddd7b2` | **合进来的 UI 根本编译不过**：5 处错误（`"$name的视角"` 里汉字是合法 Kotlin 标识符字符必须写 `${name}`、`entry.unsent` 字段不存在、`toolsPrefs` 定义在会提前 return 的 lambda 里、designsystem 测试缺 `kotlin.test.junit`、给非空 `body` 传 `null`） | androidTest 此前从未在本机编译过，所以没人发现 | 编译 |
| `b27c406` | 时间胶囊正文保存后读不回来；导出按钮点了只说「已提交 · 等待生成」 | 服务端没有 `time_capsule`/导出实现，UI 在承诺没有的东西 | JVM |
| `ddaf590` | **被服务端拒收的记录永久隐形**；「同步失败，点击重试」标签全代码零赋值 | `markConflict` 写 `nextAttemptAt = Long.MAX_VALUE` 且 `pending` 只取 `PENDING`；另有拒收后继续遍历队列的**顺序漏洞**（同记录后续操作越序发送） | JVM + Robolectric 真 Room |
| `0aa548c` | 顶部时间不可改；`occurredTimezone` 无论手机在哪都写 `Asia/Shanghai` | `EditorUiState.timezone` 硬编码 | JVM |
| `d47d2c5` | 回看页两个能点不能用的筛选 chip；相册「视频」筛选永远空；头像选了只影响它自己那一页 | 死控件与 `painterResource(if (mine) xiaoman else ayu)` 硬编码 | 仅编译层 |
| `4a906f4` | **一条记录下多条评论，在对方手机塌成一条、时间显示 1970** | `RoomSyncStore` 用变更的 `entityId`（＝记录 id）当评论主键；且 COMMENT 载荷是手拼 JSON，与 REST 响应两套形状 | 真 HTTP + 真 PG + 真 Room |
| `35e70a7` | **她改了称呼，你这台不重启就看不到** | 改称呼走 REST `PATCH`，`appendChange` 在主源码里只有 3 个调用点（`CommentService:76`、`EntryService:100`、`SyncController:63`）、没有 PROFILE 类型；名字只在 SharedPreferences，只有进首页才重读 | 真 HTTP + 真 PG |
| `0eb895d` | 两台手机跑的哪个包**无法回答** | `versionCode=1`/`versionName="0.1.0"` 恒定，界面无任何地方读版本 | aapt |

**注意这张表的性质**：除 `bddd7b2` 外，全部**不产生可见变化**——修的都是「本该如此但实际是假的」的链路。这正是 2026-10-02 上午用户不满的直接原因（见 §5 最后一条）。

---

## 3. 「可交付给伴侣」门槛清单：现在到底在哪

| 门槛 | 状态 | 还差什么 |
| --- | --- | --- |
| 身份体面（改称呼入口、第一眼不是「未命名」） | 代码在，**真机未验** | 库里两个称呼现在是什么，接手第一件事就该看一眼；改名收敛已修但没人用手机验过 |
| 记录呈现完整（图片真显示、共同记录发布语义、评论可见） | 评论链已修，**图片与共同记录只有 JVM 证据** | 两台手机：发一条带图记录 → 对端必须看见原图；共同记录「TA 追加视角」的真机链路从未走通 |
| 无明显破绽（Scaffold 遮挡、时间线毛刺） | **未验** | 全是 Compose 渲染层，JVM 一行证明不了 |
| 视觉过参考图 | 本轮已完成第一版纸张视觉重构，仍待真机截图逐张对照 | 与 `docs/design/reference/` 逐张对照 |
| 情感钩子（每周回看 11c） | 已接 WorkManager，默认开启，**真机未验一次触发** | 需要真实跨过 7 天或改系统时间来验 |
| **开档** | **未做** | 见 `docs/testing/open-day-runbook.md`；一旦有真实内容，迁移纪律立刻生效（§4 第 1 条） |

---

## 4. 没做/做不了的，按「先做哪个」排序

1. **Room 数据保全的 instrumentation 测试已补上但尚未在设备执行**：`AppDatabaseMigrationTest` 覆盖 `MIGRATION_1_2` 的存量记录、块、outbox、游标和新 comments 表；当前机器仍无 AVD/连接设备。现在库里 0 条已发布内容，是**唯一低代价窗口**；开档之后这条会变成硬约束。最终仍需要一台能跑 androidTest 的设备。
2. **发布后编辑链路完全没有**。所以现在的设计是「写过的记录不允许改时间」（`PersonalEditorScreen.kt:178` 与 `SharedEditorScreen.kt:190` 都是 `editable = !state.saved && !state.saving`）。要放开必须有 revision 语义，否则两台手机对同一件事给两个说法。
3. **没有推送通道**。同步只在开应用/回前台/下拉刷新/刚发布后触发，所以「对方什么时候看到」取决于对方下一次同步——这是产品体验的真实天花板，不是 bug。
4. **纪念日共享链路已补第一版**：服务端提供 `GET/PUT /api/v1/couple/{coupleId}/anniversary`，V11 增加重复年份字段，服务端有日期/权限校验，Android Retrofit 与关系工具已接入；当前只把 ISO 公历日期自动写入服务端，农历文字仍留在本机，数据库集成和双设备真机链路尚未验。表原本允许多条记录，服务端更新/读取最近一条，不用唯一索引破坏既有数据。
5. **服务端时间胶囊、ZIP/媒体打包导出、相册 V2、VIDEO/AUDIO 媒体链路**都没有：只有图片会被等待、会被上传——`LocalEntryWriter.kt:273` 的 `awaitsAsset()` 与 `:254` 的 `!= BlockType.IMAGE` 直接跳过，`MediaUploadManager.kt:32` 只取 `imageBlocksWithoutAsset()`、`:48` 写死 `kind = "IMAGE"`。
6. **头像/主题/封面仍是本机偏好**：服务端字段早就在（`CoupleDtos.java:17/20` 的 `UpdateProfileRequest(displayName, avatarAssetId, theme)`、`CoupleService.java:161` 读、`:190` 写），客户端只发 `displayName`，从没上传过头像资产。
7. 质量债（Task #9）：`EntryMode.COLLABORATIVE` 改名、`RoomSyncStore.mapState`、图片磁盘缓存、主题跨设备。

---

## 5. 我踩到的真实问题（下一个 agent 一定会再踩，照抄绕法）

**环境**

- **`server/` 里跑着 :8080 时绝不能 `mvn package`**。Windows 下 jar 可写不可重命名，maven-jar-plugin 会把 32 MB 胖 jar **原地截断成 147,986 字节的瘦 jar**，报 `Unable to rename ... to ...jar.original`，而**正在运行的进程毫无察觉**、健康检查照过。绕法：把 `pom.xml` + `src` 复制到仓库外（如 `E:\tmp\server-fatbuild`）构建，验证 `Main-Class`/`Start-Class`/`BOOT-INF/lib` 齐全后 `cp` 回 `server/target/`。
- JDK 必须 `E:/jdk21-extract/jdk-21.0.2`。系统 java 25 会让 Mockito 崩；Robolectric 必须 `TEMP=/e/tmp TMP=/e/tmp`（用户名含中文，默认 TEMP 路径会炸）。
- Git Bash 里 **没有 `bc`**（用 `awk` 求和）；`docker` 需要 `MSYS_NO_PATHCONV=1`；PowerShell/`netstat` 输出是 GBK，要 `iconv -f GBK -t UTF-8`；`find` 记着 `maxdepth`——我用 `maxdepth 6` 查 APK 结果什么都没找到，仓库实际深度是 8。
- **模拟器起不来、`adb devices` 为空**，所以 Compose 层唯一可用的检查是 `compileDebugAndroidTestKotlin`；写任何界面代码前都要接受「本机无法运行它」。
- 往磁盘写凭据文件会被策略拒绝（合理）。启动命令已在仓库外那个文件里，需要重启进程就**解析那一行**、不要复制它的值到任何新文件或日志。

**代码语义（不改就会再出事故）**

- **变更流载荷在 append 那一刻冻结进 jsonb**（`ChangeFeedService.appendChange` → `sync_change.payload::jsonb`，`readChanges` 原样返回 `payload::text`）。载荷形状一改，**旧形状的行就是永久毒丸**：严格客户端会卡住游标。所以硬顺序是**先重启服务端（新 jar），再装新 APK**，反过来会把手机钉死。真实库里现在 `sync_change = 0`，是最干净的重启窗口。
- **游标策略必须是「整页失败、游标不动」，不能静默跳过**：跳过等于永久丢一条变更且屏幕上没有任何痕迹；卡住的游标在服务端修好后会自愈。`RoomSyncStore.applyChangesAtomically` 的 `when(entityType)` 已加 `else -> throw`——**新增变更类型时必须同步两端**。
- **私密 = `DRAFT` 且 `PERSONAL` 且作者不是看的人**（`EntryService.java:172` 的 `readEntry` 守卫；投影侧同名判断在 `EntryView.java:21 privateDraft()`）。**共同草稿对空间可读**——伴侣能直接往里追加自己的视角，所以「草稿＝私密」只在 PERSONAL 上成立。推论：**已发布的 PERSONAL 记录对伴侣可见且可评论**。我曾打算「个人记录的评论不进变更流」，那是把规格改坏。
- `RoomSyncStore` 里评论主键必须来自**载荷自己的 `id`**，不是变更的 `entityId`（那是记录 id）——用后者就是 1970 与评论塌缩的来源。
- `OutboxDao.pending` 带 `NOT EXISTS(同 entityId、更早 rowid、state='CONFLICT')`（`OutboxDao.kt:25`）：一次拒收只挡**它自己那条记录**。恢复「拒收即整体停摆」会重新引入越序发送的漏洞。
- **SharedPreferences 弱引用持有监听器**：`OnSharedPreferenceChangeListener` 不放进 `remember`/字段，会随时被 GC 而静默失效。
- `DatePickerState.selectedDateMillis` 是**所选日的 UTC 零点**，不能当 instant 直接用；要按记录自己的时区换算。

**流程**

- **同一仓库有第二个会话在并发改 UI**。每次提交前 `git status`，不要 `git add -A`。
- **JVM/Robolectric 绿灯不是真机证据**，台账里已反复为这句话付过代价（用户截图证明手机跑的是旧界面）。交付时按 §2 口径写清「这是 JVM 证据」。
- **交付出零可见变化的包，必须提前说明并给出可执行的验证动作**。2026-10-02 上午的真实教训：11 个提交修的都是隐形破绽，用户只能拿 APK 文件大小判断，得出「你一晚没做任何事」——这个判断在他看到的证据下是合理的。我此后应在交付语里先说「这一轮屏幕上不会有变化，验证只有这两个动作」。
- **写进台账的数字，交付前重读一遍**：我曾把提交前那次构建的 `20,824,194 / +35e70a7-dirty` 写进记录 43，提交后实际产物是 `20,824,178 / +0eb895d`。

---

## 6. 硬约束（用户原话级别，任何一轮都不能破）

1. 只有两个用户。**永远不做统计/记分板**：条数、字数、活跃天数、间隔、已读回执、「对方没写」提示——包括设置项、调试入口和本地统计。
2. 记录成本 **≤10 秒 / ≤4 次交互**必须真机实测并写下**真实秒数与交互数**；估算不算。
3. **真实内容一旦出现，迁移纪律立刻生效**：任何 schema 改动先在含真实数据的副本上演练。
4. 密钥只进未提交的 `.env` / CI，绝不进源码、迁移、APK、**日志**或截图；私密内容不得提交进 git。
   - **接手前的实测现状**：开发库口令曾作为常量提交进仓库 5 处——`android/app/src/test/java/com/twomemory/app/RealServerHarness.kt`、`infra/compose.yaml` 两处、`server/src/main/resources/application-dev.yml`、`server/src/test/java/com/twomemory/app/CoupleDiaryApplicationTest.java`。这些值只指向本机 docker 里的库；初始化密钥 `bootstrap.secret` 则**没有**进仓库（`application.yml` 写的是 `${BOOTSTRAP_SECRET:}`，值只在仓库外那个文件里）。
   - **接手后已完成（当前工作树）**：运行配置、Compose、服务端数据库测试和 Android 真服务测试夹具均改为读取环境变量；新增 `infra/.env.example` 与 `RepositoryCredentialHygieneTest`。数据库集成测试现在要求调用方提供 `TEST_DB_PASSWORD`，不会再从源码回退。旧口令仍存在于 git 历史/GitHub，公开部署前必须轮换实际凭据，不能指望删除提交解决历史暴露。
5. **未经用户批准不 push**。
6. 界面必须与指导图保持纸张、暖米色、珊瑚/鼠尾草色和情侣叙事风格；本轮已重构共享设计系统、时光首页、记录编辑器、我们页、相册、城市与回看页，后续只允许基于真机截图做细节校准。

---

## 7. 接手后前 5 件事（有依赖顺序，别乱）

1. 读 `docs/testing/m1-acceptance.md` 记录 37–43（只有这 7 条与代码同步），再读 `docs/testing/feature-checklist.md`——**注意其 §0「现场」是 10-01 21:47 的快照，已陈旧**：它写「内容全 0」，现在库里是 5 条 DRAFT；APK 身份以本文件当前快照和 SHA-256 为准。
2. 让用户装当前 `versionCode=6 / 0.1.0+attachments-city-20261002`，先在系统应用信息里读版本号确认身份，再对照指导图检查半屏封面、交错手账时间轴、类型化卡片、详情图片浏览、编辑器真实媒体入口、相册新建入口和城市选择；随后跑两个动作：同一记录下**两条**评论（应两条都在、时间是写字那一分钟）；她改名 → 他不重启、下拉刷新（名字应自己变）。云端 APK 验收还需接入真实 ECS/OSS 配置。
3. 记下真实的记录成本秒数/交互数，写进 `docs/testing/real-use-log.md`（该文件目前还是「等待 SDK 机器执行」，全是待填）。
4. 等拿到一台可用设备后执行 §4 第 1 条的 `AppDatabaseMigrationTest` instrumentation；源码与编译证据已经有了，但它仍是开档前必须钉住的最后一颗钉子。
5. 与用户确认「开档」日期；开档当天走 `open-day-runbook.md`，测试数据要清掉或封存导出，伴侣正式加入从「我们的第一页」开始写真实记录。

---

## 8. 阿里云云端基础（2026-10-02 后续）

用户已确认大陆部署、价格优先，并采用「阿里云学生 ECS + OSS；第一阶段 ECS 自托管 PostgreSQL，后续可切 RDS」的路线。设计与执行记录分别见：

- [云端设计说明](../superpowers/specs/2026-10-02-aliyun-cloud-foundation-design.md)
- [云端执行计划](../superpowers/plans/2026-10-02-aliyun-cloud-foundation-plan.md)
- [.superpowers SDD 台账](../../.superpowers/sdd/aliyun-cloud-foundation/progress.md)

当前代码已完成：

1. 服务端 `ObjectStorage` 有本地实现和 AWS SDK v2 S3-compatible 实现；OSS 凭据只从环境变量读取，APK 不含云端密钥。
2. OSS 模式返回 `uploadUrl` 短时签名地址；Android 用 PUT 直传对象，再调用服务端完成校验；本地 `local://` 路径仍兼容开发机和旧测试。
3. 私有媒体读取仍经过服务端成员权限检查；完成上传会检查 sha256、大小和对象元数据，不匹配即失败。
4. `infra/aliyun/` 提供 ECS 上的 PostgreSQL + Spring Boot + Caddy HTTPS Compose；`scripts/backup.sh` 支持把数据库备份复制到 OSS。

尚未执行、且必须由实际云账号/域名完成的动作：创建 ECS、OSS 私有桶、RAM 最小权限身份、DNS 和真实 HTTPS 联调。这些动作未获账号凭据前不代为执行；部署时先跑 Compose `config`，再健康检查，最后用真实 APK 做上传/下载验收。
