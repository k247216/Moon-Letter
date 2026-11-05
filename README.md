<div align="center">

<img src="docs/design/brand/moonjian-app-icon.png" width="88" alt="月笺应用图标" />

# 月笺 · Moon Letter

**同一段回忆，两个视角。**

一个给两个人使用的私密日记 Android 应用。写下当下，回应彼此，也为以后留下一次重新看见的机会。

![Android](https://img.shields.io/badge/Android_8.0+-252329?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7f52ff?style=flat-square&logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack_Compose-4285f4?style=flat-square&logo=jetpackcompose&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6db33f?style=flat-square&logo=springboot&logoColor=white)
![Status](https://img.shields.io/badge/Status-In_Development-b66a77?style=flat-square)

[产品想法](#为什么是月笺) · [技术与运行](#开发与运行) · [版本记录](releases/v45/README.md) · [反馈问题](https://github.com/k247216/Moon-Letter/issues)

</div>

## 为什么是月笺

聊天适合即时交流，许多值得留住的细节却会很快被刷走。月笺希望为这些片段留一个安静的位置：一句话、一张照片、一段声音，或者同一件事各自不同的记忆。

任何一方都可以独立记录、回看和保管自己的生活；共同记录里的两份表达保留各自的作者与视角。

## 想保留下来的体验

- **轻一点地记录**：从一句短便签到完整图文，记录的长度由当下决定。
- **留住彼此的视角**：个人记录、共同记忆与回应各有归属，避免互相覆盖。
- **有纸张温度的时间线**：用纸片、落款和克制的装饰承载内容。
- **以后还能找回来**：围绕时间线、相册与自愿的位置快照组织回看入口。
- **数据属于使用者**：把同步、恢复、备份和导出作为持续建设的基础。

### 视觉方向

<table>
  <tr>
    <td align="center" width="33%"><img src="docs/design/reference/home-timeline.png" width="100%" alt="时间线设计参考" /></td>
    <td align="center" width="33%"><img src="docs/design/reference/personal-entry-editor.png" width="100%" alt="记录编辑设计参考" /></td>
    <td align="center" width="33%"><img src="docs/design/reference/shared-album.png" width="100%" alt="共同相册设计参考" /></td>
  </tr>
  <tr><td align="center">记录与时间线</td><td align="center">写下一个瞬间</td><td align="center">一起回看</td></tr>
</table>

以上图片来自仓库的设计参考目录，用于说明视觉方向，**不是当前版本运行截图**。V45 的实际改动与已有验证记录见[版本说明](releases/v45/README.md)。

## 一些坚持

不做关系记分牌，不用打卡或连续天数制造压力；不做后台位置追踪，不让 AI 代写回忆。位置只在使用者主动选择时成为一条记录的快照。

我们更关心：想写的时候能不能轻松留下，网络恢复后能不能继续同步，换机之后能不能接上自己的记录。

## 技术结构

```text
Android / Jetpack Compose
        ↓
Room 本地数据 + Outbox 待同步操作
        ↓
HTTP API / Spring Boot
        ↓
PostgreSQL + S3 兼容对象存储
        ↓
增量同步 → 另一端的本地数据与时间线
```

| 部分 | 技术与职责 |
| :--- | :--- |
| Android | Kotlin、Jetpack Compose，记录、阅读与个人设置 |
| 本地与同步 | Room、WorkManager、Outbox、增量 Change Feed |
| 服务端 | Java 21、Spring Boot、PostgreSQL、Flyway |
| 媒体 | S3 兼容对象存储；本地开发可使用 MinIO |
| 验证 | JUnit、Robolectric、PostgreSQL 集成测试、Android 设备测试 |

## 当前状态

当前公开源码检查点为 **V45 / `0.1.0+journal-notes-v45-20261026`**。项目处于开发验证阶段，尚未发布 GitHub Release 形式的通用安装包。

V45 聚焦便签与时间线的融合：按内容适配的纸片、纸外落款、阅读页和回应体验。该版本记录了 JVM 与隔离模拟器验证，但仍明确保留真实跨地域设备验收的边界。完整产品验收状态以[验收清单](docs/testing/m1-acceptance.md)和[实际使用记录](docs/testing/real-use-log.md)为准。

`releases/` 内是版本说明与校验资料；说明中提到的本地安装包，不应被视为已上传的公开下载文件。

## 开发与运行

当前开发分支为 `m1-stable-recording-loop`。准备 **JDK 21、Maven、Android SDK 37、Docker**。

```bash
git clone --branch m1-stable-recording-loop https://github.com/k247216/Moon-Letter.git
cd Moon-Letter

# 填写本地数据库等配置；不要提交实际密钥
cp infra/.env.example infra/.env
docker compose --env-file infra/.env -f infra/compose.yaml up -d
```

### 服务端

```bash
cd server
mvn -DskipTests package
```

配置 `JDBC_DATABASE_URL`、`JDBC_DATABASE_USERNAME`、`JDBC_DATABASE_PASSWORD` 和本地 `BOOTSTRAP_SECRET` 后，运行：

```bash
java -jar target/moon-letter-server-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev --server.port=8080
```

健康检查：`GET http://localhost:8080/actuator/health`。本地实验请使用受限网络环境；正式部署、会话恢复和备份操作先阅读[开发运行说明](docs/development-setup.md)。

### Android

在 Android Studio 中配置 SDK，并按 `android/private.properties.example` 设置自己的部署参数。项目默认占位地址不提供公共服务。

```bash
cd android
./gradlew :app:assembleDebug
```

### 验证

```bash
# 仓库根目录
bash scripts/verify-m1.sh
```

该脚本需要数据库测试配置、Android SDK 和设备／模拟器。必要环境缺失时返回 `INCOMPLETE`，不能将跳过检查当作通过；具体测试范围见脚本与验收文档。

## 文档入口

- [长期产品原则](docs/human-scale-principles.md)
- [开发运行与历史排错说明](docs/development-setup.md)
- [V45 版本说明](releases/v45/README.md)
- [M1 验收清单](docs/testing/m1-acceptance.md)
- [实际使用记录](docs/testing/real-use-log.md)
- [开档与恢复演练](docs/testing/open-day-runbook.md)
- [备份脚本](scripts/backup.sh) · [恢复脚本](scripts/restore.sh)

欢迎通过 [Issue](https://github.com/k247216/Moon-Letter/issues) 反馈问题。请使用测试记录和脱敏截图，避免公开真实日记、空间凭据和部署密钥。
