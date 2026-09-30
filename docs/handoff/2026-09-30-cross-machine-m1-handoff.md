# 月笺（Moon Letter）跨机器开发交接

更新时间：2026-09-30
当前分支：`m1-stable-recording-loop`
当前提交：`40ed8a4 build: wire Room KSP code generation`

## 1. 本机后续边界

本机只负责：

- 产品范围、架构边界和任务拆分；
- 设计规范与实现的一致性检查；
- 交接文档、验收清单和风险记录。

本机不再负责：

- Android SDK、模拟器、Gradle 真机编译；
- Room/KSP 生成结果修复；
- Android 具体功能、网络接入、同步运行时和媒体上传实现；
- 直接向 GitHub `origin` 推送。

另一台机器完成上述实现后，只需把分支、提交号、测试结果和截图回传，本机再做宏观复核。

## 2. 已完成基线

### 服务端

- PostgreSQL/Flyway V1–V5 数据模型与约束；
- 双人空间、简单配对、头像/昵称/主题边界；
- 个人记录、共同记录、评论、版本快照、同块冲突；
- 幂等操作、增量变更游标、第三用户隔离；
- 图片上传生命周期和媒体引用约束；
- 双端离线重连验收测试。

验证证据：服务端全量测试 28/28 通过（需要本机 PostgreSQL 与 JVM 测试代理权限）。

### Android 源码基线

- 五栏导航：`时光 / 相册 / ＋记录 / 地图 / 我们`；
- 连续时间轴首页；
- 个人记录编辑器与共同记录编辑器；
- 个人资料、昵称修改、米色/纯白主题、中秋纪念语义；
- Room Entity/DAO/Outbox/游标接口；
- WorkManager 同步状态机源码；
- Compose 设计令牌、纸张表面和统一细线图标。

本机最后一次改动已把 Room KSP、`room-compiler`、`room-paging` 和 schema 参数接入；Gradle 能解析 `kspDebugKotlin`，但实际生成仍等待 Android SDK。

## 3. 另一台机器执行顺序

### A. 环境闭环

1. 安装 JDK 21、Android SDK 37、Platform Tools、Emulator 和 Gradle 9.8。
2. 在 `android/local.properties` 配置真实 SDK 路径，或设置 `ANDROID_HOME`/`ANDROID_SDK_ROOT`。
3. 由开发者明确接受 Google SDK license；不要自动代签。
4. 创建 390 × 844 基准模拟器，保留一个较小高度设备用于键盘遮挡测试。

### B. Room 与本地写入

1. 运行 `./gradlew :core:database:kspDebugKotlin`。
2. 修复 Room 编译器提示，但不得改变数据模型语义：记录和 outbox 必须同一事务提交。
3. 运行 `./gradlew :core:database:testDebugUnitTest :core:database:connectedDebugAndroidTest`。
4. 验证进程在本地事务提交后立即被杀，重启后 outbox 仍能继续发送。

### C. 同步运行时

1. 把 `core:sync` 的 `SyncStore` 接到真实 Room DAO。
2. 把 `CoupleDiaryApi` 接到 Retrofit/OkHttp；API 时间使用 UTC，发生时间另存 IANA 时区。
3. WorkManager 使用唯一任务 `moon-letter-sync-{coupleId}`，保持 FIFO、指数退避和冲突保留。
4. FCM 只携带 `couple_id` 与 `latest_sequence`，收到后拉取变更，不在通知里放正文、媒体 URL、坐标或作者文字。
5. 验证断网、重复请求、进程被杀、双端同块编辑和游标回滚。

### D. UI 接线与视觉验收

1. 首页和编辑器从 Room/ViewModel 读取真实数据，禁止继续使用演示列表作为默认业务源。
2. 先做五个底部导航入口，再接个人记录、共同记录、资料页；相册和地图在 M1 可保留明确的 M2 空状态。
3. 用 390 × 844 截图逐页对照 `docs/design/reference/` 六张参考图。
4. 每页至少检查：结构、间距、颜色、图标线宽、文字层级、滚动、键盘、底部导航和无障碍字号。
5. 完成后把截图、设备配置、提交号和失败项写入 `docs/testing/m1-acceptance.md`。

## 4. UI 一致性硬规则

### 全局

- 默认暖米色，纯白只替换色彩和纸张表面，不改变布局；
- 正文不低于 14sp，触控目标不低于 48dp；
- 功能图标统一细线描边，禁止 emoji、填充图标和手绘功能符号混用；
- 小满使用低饱和珊瑚色，阿屿使用低饱和鼠尾草绿，月光金只做少量纪念强调；
- 编辑器内不显示主底部导航；
- 中秋是纪念语义，不做整套节日皮肤；
- 首页必须连续下滑，不按天切页。

### 页面检查表

| 页面 | 必须保持 | 不得出现 |
|---|---|---|
| 时光首页 | 左侧缝线式时间轴、日期+具体时间、可替换封面、五栏导航 | 一天一页、实时定位、仪表盘堆卡 |
| 我的记录 | 自然书写纸面、精确时间、图片/视频/语音/音乐/城市/更多工具栏 | 共同编辑标签、编辑器内底部导航 |
| 共同记录 | 内容块、双方作者标识、可追加另一视角、版本入口 | 覆盖式保存、无作者来源的合并文本 |
| 共同相册 | 月份分组、媒体网格、视频时长和播放标识 | 复制底层媒体、删除相册即删除原记录 |
| 城市地图 | 城市级聚合、故事列表、主动一次性位置快照 | 后台轨迹、实时位置、持续定位权限 |
| 我们 | 双方头像/昵称、农历八月十五、胶囊、主题、导出入口 | 修改对方资料、把主题设置同步覆盖伴侣 |

## 5. 当前静态一致性风险（仅记录，不在本机修）

1. `AppNavigation` 的 `Scaffold` 内容 padding 当前未显式传给路由，需在设备上确认时间轴是否被底部导航遮挡。
2. `PaperSurface` 接收 `contentPadding` 但没有消费，后续统一组件时需决定由容器还是调用方负责内边距。
3. 时间轴和头像目前仍是演示数据/色块，真实头像、封面和媒体加载状态需由另一台机器接线。
4. 相册与地图目前是 M2 占位，交接时不得把占位页误标为功能完成。
5. KSP 已接入但没有 SDK 生成证据；Room schema、DAO 实现和 instrumented test 必须在另一台机器验证。

## 6. 云端配置边界

M1 本地验收不需要云端账号。部署前才准备：

- PostgreSQL：`JDBC_DATABASE_URL`、`JDBC_DATABASE_USERNAME`、`JDBC_DATABASE_PASSWORD`；
- S3 兼容对象存储：`S3_ENDPOINT`、`S3_REGION`、`S3_BUCKET`、`S3_ACCESS_KEY`、`S3_SECRET_KEY`；
- FCM 服务端凭据和 Android `google-services.json`；
- 所有密钥只放在未提交 `.env` 或 CI Secret，不写入源码、迁移和 APK。

当前 `S3ObjectStorage` 仍是契约占位实现，生产发布前必须替换为真实签名/上传适配器。

## 7. 回传格式

另一台机器完成一批工作后，回传以下内容即可：

```text
分支/提交：
完成任务：
运行命令：
通过数量：
未通过或未运行：
设备型号与 Android 版本：
390×844 截图目录：
需要本机复核的问题：
```

M1 只有在服务端、Room/outbox、同步故障场景、已实现页面（以及相册/地图占位边界）和 390 × 844 截图全部有证据后才算关闭。
