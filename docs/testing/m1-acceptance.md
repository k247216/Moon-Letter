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
| 六张 UI 参考图 | 权威视觉目标明确 | 当前 App 已按图实现或已接真实数据 |

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
| A2 Room/outbox | 本地事务、重启保留、page+cursor 原子提交 | connected database tests | NOT RUN | — |
| A3 Android 网络链 | Retrofit、真实 Room、真实 server | instrumented integration test | NOT RUN | — |
| E1 双端闭环 | A 离线写入后在 B 的 Room 与 UI 出现 | 两设备/独立数据目录录像与日志 | NOT RUN | — |
| E2 故障恢复 | 超时重试、重复 ID、逆序重连、进程重启 | 脚本、日志、数据库查询 | NOT RUN | — |
| D1 备份恢复 | 数据与媒体可恢复且校验一致 | 恢复演练报告 | NOT RUN | — |
| D2 选择性导出 | JSON + 可读文档 + 原媒体，无密钥 | 样例导出包与检查清单 | NOT RUN | — |
| U1 视觉一致性 | 四个 M1 页面接真实数据并对照批准稿 | 390×844 截图与差异记录 | NOT RUN | — |
| U2 适配与可用性 | 小屏、键盘、大字号、滚动、触控目标 | 截图/录像/检查表 | NOT RUN | — |

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
