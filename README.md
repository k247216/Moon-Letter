# 月笺 (Moon Letter)

双人私密日记 Android 应用。对外中文名称固定为「月笺」，`Moon Letter` 仅作英文工程代号。即使另一方尚未加入，任何一方也可以独立记录、回看和保管自己的生活。

## 我们坚持的边界

- 不监控位置，不保存实时轨迹；
- 不做打卡、连续天数或关系压力机制；
- 不让 AI 代写回忆；
- 数据可以完整备份、恢复和导出。

## 当前项目状态

**M1 NOT VERIFIED。** 当前仓库已有服务端、Android、Room、同步和 UI 骨架，但真实的 `Room → HTTP → PostgreSQL → Change Feed → 第二个 Room` 双端闭环尚未通过验收。现有组件测试和演示页面不能代表产品已完成。

当前权威资料：

- `docs/superpowers/specs/2026-10-01-self-use-m1-design.md`
- `docs/superpowers/plans/2026-10-01-self-use-m1-implementation.md`
- `docs/handoff/2026-09-30-cross-machine-m1-handoff.md`
- `docs/testing/m1-acceptance.md`
- `docs/design/reference/`
- `docs/design/brand/`

2026-09-30 的规格和计划仅保留为历史参考，不再作为 M1 执行依据。

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

本地 M1 验证不需要购买云端账号。开发机当前需要 JDK 21、Maven、Android SDK 37、PostgreSQL、两个独立 Android 数据目录和本地 `BOOTSTRAP_SECRET`。图片阶段再启用 S3 兼容对象存储。

后续正式部署前需要准备：

- PostgreSQL 生产实例：`JDBC_DATABASE_URL`、`JDBC_DATABASE_USERNAME`、`JDBC_DATABASE_PASSWORD`；
- S3 兼容对象存储（生产可选云厂商，开发使用 MinIO）：`S3_ENDPOINT`、`S3_REGION`、`S3_BUCKET`、`S3_ACCESS_KEY`、`S3_SECRET_KEY`；
- 推送通知（FCM）：非 M1 正确性依赖，后续只在接入通知优化时配置；
- Android 本地构建：JDK 21、Android SDK 37、Gradle 9.8。SDK 命令行工具的 Google 许可需要开发者在本机明确接受，项目不会自动代签。

所有密钥只放在未提交的 `.env` 或 CI secret 中，不写入源码、迁移文件和 APK。

## 服务端

当前分支尚缺少可复现的运行配置，不能把 `mvn spring-boot:run` 视为已验证命令。开发机应先完成新计划 Task 1，并将最终启动命令、health 响应和环境变量写回 `docs/testing/m1-acceptance.md`。

组件测试可在明确的测试数据库配置下运行，但结果只计作组件证据。

## Android

Android SDK 37 和 Java 21 是构建前置条件：

```bash
cd android
./gradlew :app:assembleDebug
```

上述构建当前仍是待验证项；只有命令、退出码、APK 和设备测试证据齐全后才能在验收记录中标记通过。

## 分支约定

实现工作在 `m1-stable-recording-loop` 分支完成。另一台开发机按执行计划逐任务提交；本机负责范围、证据和 UI 一致性复核。
