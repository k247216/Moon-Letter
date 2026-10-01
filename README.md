# 月笺 (Moon Letter)

双人私密日记 Android 应用。对外中文名称已确定为「月笺」，`Moon Letter` 仅作英文工程代号；身份色、封面和措辞仍由使用者在应用内自行修改。即使另一方尚未加入，任何一方也可以独立记录、回看和保管自己的生活。

## 我们坚持的边界

- 不监控位置，不保存实时轨迹；
- 不做打卡、连续天数或关系压力机制；
- 不让 AI 代写回忆；
- 数据可以完整备份、恢复和导出；
- 记录成本优先于功能数量——错过的一天无法补写；
- 不做关系记分牌：单次信号可以有，条数、字数、活跃与间隔天数的统计和排名不能有；
- 连续性优先于完整性：换机、丢机或重装之后仍要能自己接上，不依赖维护者在场；
- 桌面名称已确定为「月笺」；身份色、封面和措辞由使用者决定，参考图与规格在此让位——但体现为"随时能在应用内改"，不是"使用前必须先选"；默认值必须开箱可用。

取舍顺序见 `docs/human-scale-principles.md`。

## 当前项目状态

**M1 NOT VERIFIED。** 服务端 S1–S5、Android A1–A3 的开发机证据已经本机静态复核接受，Robolectric 夹具已贯通 `Room → HTTP → PostgreSQL → Change Feed → 第二个 Room`。台账 70/118。

复核列出的阻塞项已在代码里闭合：H4 本地会话恢复命令与真库测试、outbox 严格 FIFO 与协程取消传播、WorkManager 重试结果、Keystore 密封的会话令牌、损坏会话回绑定页、响应式时间线与 Scaffold 遮挡。仍未完成的是必须用设备才能证明的部分：真机互见（E1）、故障恢复演练（E2/H4 真机侧）、记录成本计时与通知到点（H1/H3）、备份导出恢复（D）、视觉对照（U）。**JVM 证据不代表产品可交付**，不能因为链路变绿就宣布 M1 完成。

当前权威资料：

- `docs/human-scale-principles.md`（长期取舍顺序，跨里程碑有效）
- `docs/superpowers/specs/2026-10-01-self-use-m1-design.md`
- `docs/superpowers/plans/2026-10-01-self-use-m1-implementation.md`
- `docs/handoff/2026-09-30-cross-machine-m1-handoff.md`
- `docs/testing/m1-acceptance.md`
- `docs/testing/open-day-runbook.md`（开档当天的操作顺序与真机门清单）
- `docs/reviews/2026-10-01-m1-round-review.md`
- `docs/design/reference/`
- `docs/design/brand/`

2026-09-30 的规格和计划仅保留为历史参考，不再作为 M1 执行依据。

## 等待使用者决定的事项

执行计划 Task 0 只提前索取**单向门**——一旦有真实内容或已打包就很难改的东西：

- 开始真实使用的日期与每周可投入时间上限——卡住 Task 11a，不能默认；

主数据表示已确定为 PostgreSQL 权威（方案 A），Android 桌面名称已确定为「月笺」。

称呼、身份色、封面和措辞**不再提前索取**。它们由计划 Task 12b 交付为应用内可改项：默认值开箱可用，使用者在任意一个下午想改成"自己的"都能改，不需要重新构建也不需要找人。空白不算所有权，能改才算。

## 本地基础设施

推荐使用 Docker Desktop 启动本地依赖：

```bash
docker compose -f infra/compose.yaml up -d
```

默认服务：PostgreSQL `localhost:5432`，MinIO API `localhost:9000`，MinIO Console `localhost:9001`。首个纯文字竖切只需要 PostgreSQL；图片任务开始时才需要 MinIO。
Compose 中的账号和密码只用于本地开发，禁止带入生产环境。

如果 Docker daemon 尚未启动，服务端约束测试也可以使用本机 PostgreSQL：

```bash
brew services start postgresql@18
cd server
TEST_DB_URL=jdbc:postgresql://localhost:5432/postgres \
TEST_DB_USER="$USER" TEST_DB_PASSWORD="" \
mvn test -Dtest=SchemaConstraintTest
```

测试会在 `moon_letter_test` 独立 schema 中清理和迁移，不会清理默认数据库中的其他 schema。

## 环境与云端配置边界

本地 M1 验证不需要购买云端账号。开发机当前需要 JDK 21、Maven、Android SDK 37、PostgreSQL 和一个本地 `BOOTSTRAP_SECRET`。**不存在 `RECOVERY_SECRET`**：规格 §5.4 允许的两条恢复路径里选了前一条——`BOOTSTRAP_SECRET` 可以重复使用并找回创始成员自己的槽位（卸载、丢机或恢复备份后仍属于同一空间和同一身份），加上只在服务端本机执行、关闭 web 的 `SessionAdminCommand`。代价是 `BOOTSTRAP_SECRET` 属于长期凭据，泄露即等于第一位成员的槽位被接管，因此服务端端口不得公网可达，密钥只留在服务端本机环境或未提交的 `.env`。

连续性语义还有两处：配对令牌由服务端判定类型，`INVITE` 让新成员加入，`REJOIN` 让已存在但重装的成员回到自己的槽位；还没有会话的那台设备，可以由另一台在「我们」页直接生成配对码。通知全部是本机通知，未接 FCM，也不含正文与条数。

双端闭环的主要证明手段是 JVM 上的 Robolectric 夹具（两个真实 Room 数据库 + 真实 Retrofit 打到真实 Spring Boot），因此不再把「两个独立 Android 数据目录」列为前置条件；一台具名真机或 API 37 模拟器只用于冒烟测试和 Task 14 录像。图片阶段再启用 S3 兼容对象存储。

后续正式部署前需要准备：

- PostgreSQL 生产实例：`JDBC_DATABASE_URL`、`JDBC_DATABASE_USERNAME`、`JDBC_DATABASE_PASSWORD`；
- S3 兼容对象存储（生产可选云厂商，开发使用 MinIO）：`S3_ENDPOINT`、`S3_REGION`、`S3_BUCKET`、`S3_ACCESS_KEY`、`S3_SECRET_KEY`；
- 推送通知（FCM）：非 M1 正确性依赖，后续只在接入通知优化时配置；
- Android 本地构建：JDK 21、Android SDK 37、Gradle 9.8。SDK 命令行工具的 Google 许可需要开发者在本机明确接受，项目不会自动代签。

所有密钥只放在未提交的 `.env` 或 CI secret 中，不写入源码、迁移文件和 APK。

## 服务端

已按执行计划 Task 1 完成可复现运行配置（`application.yml` + `application-dev.yml` + actuator health）。

启动本地数据库，打可执行 jar，再用环境变量注入密钥运行：

```bash
docker compose -f infra/compose.yaml up -d postgres
cd server && mvn -DskipTests package
BOOTSTRAP_SECRET='<只放本机环境或 .env>' \
  java -jar target/moon-letter-server-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev --server.port=8080
```

**不要**用 `mvn spring-boot:run --moon-letter.bootstrap.secret=...`：跟在 `spring-boot:run` 后面的属性不会传进应用，密钥会静默为空，真机上表现为绑定页 403 而不是配置错误。这是 2026-10-01 真机排查出的实际根因，写死在这里避免重复。

健康检查：`GET http://localhost:8080/actuator/health` 应返回 `{"status":"UP"}`。

默认 profile 不提供数据库配置时将快速失败并给出缺库错误；生产部署通过 `JDBC_DATABASE_URL`、`JDBC_DATABASE_USERNAME`、`JDBC_DATABASE_PASSWORD` 提供。开发机的 `dev` profile 使用 compose 中的本地账号密码，禁止带入生产环境。

注意：某些机器存在遗留环境变量 `SERVER__PORT`（例如值 63834），Spring Boot 的 relaxed binding 会读取它。如端口异常，用 `--server.port=8080` 显式覆盖。

## 会话恢复（H4）

两台设备同时丢会话时，在**服务端所在机器上**执行本地管理命令；它显式关闭 web，因此不可能与正在提供 HTTP 的进程并行运行：

```bash
java -jar target/moon-letter-server-0.1.0-SNAPSHOT.jar \
  --spring.main.web-application-type=none \
  --moon-letter.admin.mode=revoke-all --moon-letter.admin.user-id=<UUID>

java -jar target/moon-letter-server-0.1.0-SNAPSHOT.jar \
  --spring.main.web-application-type=none \
  --moon-letter.admin.mode=issue --moon-letter.admin.user-id=<UUID>
```

签发的那一个令牌只打印到 stdout 一次；`session_admin_audit` 记录动作与目标用户，**从不保存令牌材料**。coupleId 由成员归属反查，不需要手填。

- **它作用在你给的那个库上**：想练手就克隆一份——`CREATE DATABASE moon_letter_drill TEMPLATE moon_letter;`，把 `--spring.datasource.url` 指过去，练完 `DROP DATABASE`。对着手机正在用的库跑 `revoke-all`，两台手机会在几秒内掉线，只能靠 `issue` 出来的新令牌回去。
- `issue` 走的是「先吊销再签发」，旧令牌当场失效；拿到新令牌后要在应用里重新填一次服务器地址与它，旧设备上那份复制粘贴不来的令牌不会再有用。

更常见的场景不需要这条命令：重装但密钥还在时，重新 bootstrap 会找回创始成员自己的槽位；只是伴侣那台掉线时，还能用的那台在「我们」页生成配对码，对方用它以 `REJOIN` 回到原来的成员位。完整顺序见 `docs/testing/open-day-runbook.md`。

## 验证入口

```bash
bash scripts/verify-m1.sh   # 服务端测试 + Android JVM 测试 + 真机 connected 检查；环境缺失时以 2 退出并报 INCOMPLETE
```

`TEST_DB_URL` 未设置时脚本会跳过 PostgreSQL 约束测试并以 `INCOMPLETE` 退出——服务端真库测试需要 compose 里的 PostgreSQL 在跑，不接受把跳过的部分当作通过。备份与恢复用 `scripts/backup.sh`、`scripts/restore.sh`。

## Android

Android SDK 37 和 Java 21 是构建前置条件：

```bash
cd android
./gradlew :app:assembleDebug
```

开发机已经完成 `assembleDebug` 与 JVM 单测并产出 APK，Gate A1 的“构建”部分通过。模拟器静默退出，`connectedDebugAndroidTest` 和真机 smoke 仍未完成；因此不能把构建成功解释为设备可用或 M1 完成。

## 分支约定

实现工作在 `m1-stable-recording-loop` 分支完成。另一台开发机按执行计划逐任务提交；本机负责范围、证据和 UI 一致性复核。
