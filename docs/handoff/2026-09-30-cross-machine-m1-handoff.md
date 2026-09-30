# 月笺（Moon Letter）跨机器开发交接

更新时间：2026-10-01
当前分支：`m1-stable-recording-loop`
当前结论：**M1 NOT VERIFIED；当前代码是架构骨架，不是可交付闭环。**

## 1. 权威资料与冲突处理

执行前必须阅读：

1. `docs/superpowers/specs/2026-10-01-self-use-m1-design.md`
2. `docs/superpowers/plans/2026-10-01-self-use-m1-implementation.md`
3. `docs/testing/m1-acceptance.md`
4. `docs/design/reference/` 与 `docs/design/brand/`

`2026-09-30-couple-diary-design.md` 和 `2026-09-30-m1-stable-recording-loop.md` 已降为历史参考。发生冲突时，以 2026-10-01 两份文档为准；不得继续实现跨作者同块合并、依赖 FCM 的秒级承诺或旧的伪 E2E 验收。

## 2. 两台机器的职责

### 本机：统筹与一致性复核

- 维护产品范围、数据规范、任务顺序和验收标准；
- 审查另一台机器提交的 commit、日志、测试和截图；
- 对照批准稿检查真实数据页面的结构、间距、颜色、图标和适配；
- 发现偏差时给出具体文件、场景和验收条件，不在本机安装 Android SDK 或开发业务功能。

### 开发机：实现与取证

- 按新执行计划 Task 1–14 顺序开发；
- 配置 JDK、Android SDK、PostgreSQL、模拟器/真机和对象存储；
- 完成服务端、Android、真实双端同步、备份导出和设备视觉验证；
- 每个任务提交独立 commit，并把证据写回 `docs/testing/m1-acceptance.md`。

## 3. 当前代码事实

已有内容包括 PostgreSQL/Flyway V1–V5、服务端 controller/service 雏形、Android 多模块结构、Room entity/DAO 接口、同步状态机骨架、Compose 页面骨架和六张批准视觉稿。

以下内容尚未成立，开发机不得视为已完成：

- 服务端缺少可直接启动的主配置，README 中原 `spring-boot:run` 不能作为已验证路径；
- Spring Security 和真实登录/初始化未接入，`X-User-Id` 可被客户端任意伪造；
- 发布、修改和冲突路径存在丢失 actor/成员校验的问题；
- `/sync/operations` 只写客户端 payload，未调用真实业务变更；业务 service 也未稳定追加 change feed；
- 全局 identity cursor 在并发事务逆序提交时可能永久漏变更；
- Android 没有完整 Retrofit/OkHttp、Room `SyncStore` 和 app 级依赖接线，cursor 仍可能使用常量；
- UI 仍包含硬编码演示数据；
- Android 构建与 instrumented test 尚无可信通过证据；
- 服务端 `TwoDeviceSyncTest` 使用 `FakeServer`，Android `TwoDeviceScenarioTest` 仅做字符串断言，均不是端到端验收；
- 历史“28 tests pass”只能保留为组件级证据，不能推导 M1 完成。

## 4. 产品实现边界

- 一个人未配对时也能记录和回看，配对不应成为写日记的前置条件。
- 草稿只对作者可见；已发布个人记录对伴侣可见并可评论。
- 共同记录是同一主题下的双视角：每人维护自己的块，明确作者和具体时间；M1 不允许编辑对方块，也不做跨作者合并。
- 首个交付切片只做个人纯文字：`Room → Outbox → HTTP → PostgreSQL → Change Feed → 第二个 Room`。
- 图片只能在上述链路真实通过后开始；视频、语音、音乐、相册、地图、胶囊、倒计时、过去的今天和本周小结保留路线图但暂停开发。
- 同步触发为启动、回前台、手动刷新和恢复网络；后台由 WorkManager 尽力执行，不承诺固定分钟数。FCM 后置且只触发拉取。
- 不监控位置、不做打卡连续天数、不使用 AI 代写、数据必须可备份恢复和选择性导出。

## 5. 强制执行顺序

| 阶段 | 对应计划 | 退出条件 |
|---|---|---|
| A 可运行与安全边界 | Task 1–4 | 服务可启动；bootstrap/配对/token 生效；越权测试通过 |
| B 正确服务端同步 | Task 5–7 | typed operation、同事务 change、空间有序 cursor、真实 HTTP/PostgreSQL E2E 通过 |
| C Android 本地链 | Task 8–10 | APK 构建；Room/outbox/cursor 持久化；Retrofit 与真实依赖接通 |
| D 首个真实竖切 | Task 11 | A 离线写入，经真实服务端出现在 B 的独立 Room/UI；有录像与日志 |
| E 共享与数据权 | Task 12–13 | 双视角/评论/版本、图片完整性、备份恢复和导出通过 |
| F 视觉与最终验收 | Task 14 | 四个真实数据页面对照批准稿；所有 Gate 指向同一 commit |

任何阶段未满足退出条件，不能跳到后续扩功能。尤其禁止一边保留假的数据链，一边继续制作相册、地图或视频页面。

## 6. UI 一致性硬规则

- 首页必须是左侧缝线式连续时间轴，可一滑到底，不按天切页；显示日期和具体时间。
- 顶部封面可替换；中秋只作少量纪念元素。
- 底部导航固定为 `时光 / 相册 / ＋记录 / 地图 / 我们`，编辑器内不显示主导航。
- 个人编辑器保持自然书写纸面；共同编辑器保留双方作者标识和独立内容块。
- 默认暖米色、可切纯白；主题不覆盖伴侣选择。
- 头像与名字可修改，但只能修改自己。
- 功能图标统一细线描边，禁止 emoji、填充图标和不同图标体系混用。
- 中文正文不低于 14sp，触控目标不低于 48dp；键盘、小屏与大字号不得遮挡核心操作。
- 390 × 844 是权威截图基准但不是固定尺寸。禁止把参考 PNG 直接铺成界面背景。
- 相册和地图在 M1 必须是明确的未开放状态，不能用演示卡片造成已完成功能的错觉。

## 7. 开发环境与云端配置

### 立即需要

- JDK 21、Maven；
- Android SDK 37、Platform Tools、API 37 模拟器或 Android 真机；
- PostgreSQL；推荐通过 `infra/compose.yaml` 启动，不能使用内存数据库代替最终 E2E；
- 两个独立应用数据目录，用于代表设备 A、B；
- 本地 `BOOTSTRAP_SECRET`，只放环境变量或未提交 `.env`。

### Task 13 才需要

- S3 兼容对象存储；本地优先使用 MinIO；
- `S3_ENDPOINT`、`S3_REGION`、`S3_BUCKET`、`S3_ACCESS_KEY`、`S3_SECRET_KEY`。

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

开发机从新计划 Task 1 开始，不沿用旧交接中的 Room/FCM/UI 并行顺序。第一批回传应包含：服务端可启动配置、health 结果、真实 bootstrap/bearer 认证测试和对应 commit。随后再进入同步语义修复。
