# 公网跨省上线 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement task-by-task. 本轮仅准备，不提前部署公网。

**Goal:** 保持V25初版外观和功能，备案通过后用正式HTTPS域名无USB双端使用，并保留原空间和本机待上传内容。
**Architecture:** 同一ECS继续使用原PostgreSQL/私有OSS；Caddy仅开放HTTPS入口。客户端迁移origin而不是重新创建身份；先验明新端点空间，再保留DB并迁移所有origin作用域。
**Tech Stack:** Kotlin/Compose/Room/WorkManager、Spring/PostgreSQL18、Docker Compose、Caddy、私有OSS。
**Spec:** ../../deployment/2026-10-04-v25-initial-release-readiness.md

## Global Constraints

- 冻结V25视觉和功能，不删除现有数据、不重新bootstrap、不卸载、不改签名以求快速上线。
- 仅用户确认备案通过后开放公网入口；数据库5432和后端8080继续不公开。
- 不向日志、聊天或客户端写入服务器/OSS/签名私钥秘密。
- 当前dirty工作保留，除非用户明确要求，不提交、重置或清理仓库。

## Review Focus

- 新端点返回其他空间/成员、认证失败：不得改本机配置或发送旧待上传队列。
- 切换前有未上传照片/草稿：保持记录、路径、UUID、队列及作用域，不仅重新下载云端内容。
- 迁移或应用进程中断：原地址和数据库可继续恢复，重复执行不得重复创建照片。
- 正式证书不同：首次跨省测试保持覆盖签名，正式迁移单独验证，绝不卸载清数据。
- 手机离线/后台：不承诺实时送达，恢复同步后双方状态收敛。

### Task 1: 同一ECS安全切换端点

**Files:** 新增 `android/app/src/main/java/com/twomemory/app/ServerEndpointMigration.kt`；修改 `SyncSession.kt`、`CloudConnectionActivity.kt`、`AlbumCloudSync.kt`；测试新增 `android/app/src/test/java/com/twomemory/app/ServerEndpointMigrationTest.kt`。
**Interface:** `suspend fun migrate(context: Context, destination: String)`；只接受既定正式HTTPS域名，使用现有会话`SessionApi.readSpace()`验证ID；不调用bootstrap/pair。
- [x] RED：旧会话/草稿/每日关闭及身份校验测试先失败；真实原件队列集成另补，无其独立RED声明。
- [x] GREEN：401、错误coupleId、错误成员、陌生域名、网络失败不改配置。
- [x] 验证后原子切换；不可变storageOrigin替代跨存储移动。锁内读取当前会话，串行绑定提交、失败回滚、旧worker/进程启动重新排队。
- [x] GREEN：真实隔离后端及媒体闭环；启动hook的相册-only调度、排队同步和失败commit重试验证。
- [ ] 实际设备进程死亡/冷启动磁盘故障时间窗：上线验收仍需补，不把hook测试当成完整OS死亡实测。

### Task 2: 备份与HTTPS接入

**Files:** 复用 `scripts/backup.sh`、`restore.sh`、`infra/aliyun/compose.https.yaml`、`Caddyfile.https`；必要修正必须有本地验证，不在正式库尝试。
**Interface:** 输入现有私有部署/真实域名；输出443受信任证书及独立数据库恢复报告，不增加公网DB/API直通。
- [ ] 只读核对ECS当前V25、表和OSS索引；备份到受限路径，在独立数据库恢复，比对数量/抽样记录；确认自动备份是否实际配置。
- [ ] `config --quiet`及Caddy验证后启动代理，保持项目名/卷名、.env和原件；使用既有caddy镜像避免强制拉取失败。
- [ ] 不同网络实测证书、HTTP转HTTPS、无凭据拒绝访问、公网actuator404、原本127.0.0.1健康UP、OSS私有HTTPS上传/下载。

本地准备已完成：一致快照dump、源库/全表数量和内容指纹、独立库恢复比对、错误备份拒绝、容量保护、每日timer模板、只读检查/私有准备脚本、备案变量显式门禁、公网检查脚本。Caddy离线验证通过且禁止隐式拉镜像。远端执行无可用SSH授权，机主先按releases/v26/README.md执行；以上未勾选项保持真实远端/公网门槛。

### Task 3: 覆盖包与跨省真实验收

**Files:** `android/app/build.gradle.kts`增加新版本标识；产物进入下一版本release目录；验收报告新增 `docs/testing/public-cross-province-acceptance.md`。
**Interface:** Task1迁移入口+Task2HTTPS；输出双方同版本、不同网络、不接USB的验收记录。
- [ ] 同原测试签名覆盖，两台设备分别验证包哈希和空间/成员；不得卸载/清数据。正式发行签名与异机私钥备份另设明确门槛。
- [ ] 按spec矩阵执行记录/相册/语音/视频/头像/惊喜/私密/离线队列/删除/保存原图，记录实际延迟及网络类型。
- [ ] 验证进程和服务器重启后恢复，复核备份和证书续期；失败保持旧数据库/JAR/对象，不执行destructive恢复。
- [ ] 所有门槛有证据才宣布跨省可用；失败具体记录，不把本机或局域网测试当跨省验收。

覆盖包V26使用原测试签名，9模块247项通过；验收报告模板已建立。真实手机未安装，USB reverse及SSH隧道不恢复。正式签名迁移、OSS异机保护及跨省验收仍未完成。
