# Moon Letter

双人私密日记 Android 应用。产品规格和已批准的视觉参考位于：

- `docs/superpowers/specs/2026-09-30-couple-diary-design.md`
- `docs/design/reference/`
- `docs/superpowers/plans/2026-09-30-m1-stable-recording-loop.md`

## 本地基础设施

推荐使用 Docker Desktop 启动完整依赖：

```bash
docker compose -f infra/compose.yaml up -d
```

默认服务：PostgreSQL `localhost:5432`，MinIO API `localhost:9000`，MinIO Console `localhost:9001`。
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

当前 M1 不需要云端账号或密钥。后续部署前需要准备：

- PostgreSQL 生产实例：`JDBC_DATABASE_URL`、`JDBC_DATABASE_USERNAME`、`JDBC_DATABASE_PASSWORD`；
- S3 兼容对象存储（生产可选云厂商，开发使用 MinIO）：`S3_ENDPOINT`、`S3_REGION`、`S3_BUCKET`、`S3_ACCESS_KEY`、`S3_SECRET_KEY`；
- 推送通知（FCM）：服务端凭据和 Android `google-services.json`，只在接入通知任务时配置；
- Android 本地构建：JDK 21、Android SDK 37、Gradle 9.8。SDK 命令行工具的 Google 许可需要开发者在本机明确接受，项目不会自动代签。

所有密钥只放在未提交的 `.env` 或 CI secret 中，不写入源码、迁移文件和 APK。

## 服务端

```bash
cd server
mvn test
mvn spring-boot:run
```

## Android

Android SDK 37 和 Java 21 是构建前置条件：

```bash
cd android
./gradlew :app:assembleDebug
```

## 分支约定

实现工作在 `m1-stable-recording-loop` 分支完成；`main` 只保留已经审核的规格基线。
