# 两人私有公网稳定性 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline; preserve dirty feature branch. No APK installation or public deployment.

**Goal:** 按用户六项清单修复已确认安全/资源风险，保留现有产品和数据。
**Architecture:** 沿用ECS/PostgreSQL/私有OSS，不引入新云服务。后端鉴权先于媒体读取/修改；下载和原件校验改流式。客户端令牌禁止Keystore失败时明文降级，兼容旧令牌安全升级；文字同步不等待长媒体。
**Tech Stack:** Java21/Spring/Servlet/SDK、Kotlin/Room/WorkManager、Docker。
**Spec:** 用户本轮六项清单及docs/testing/public-cross-province-acceptance.md。

## Global Constraints

- 冻结新增功能和视觉；不安装APK、恢复真实USB或提前开放公网。
- 保留原身份、.env、数据库、原件、既有签名；不提交/重置dirty树。
- 昨日ECS V25/V13、生产独立恢复和timer启用有用户日志证据；timer首次实际执行、OSS异机保护仍待核实。
- 本地修复不冒充生产部署；真实迁移/跨省/签名与备份异机恢复保持明确门槛。

## Review Focus

- Keystore临时失效不清会话/数据库，不诱导重建空间。
- 限流不信任任意Forwarded头，不无限增长内存；不阻断正常文字同步。
- 上传者之外不得覆盖原件；大小/哈希不符保留原文件。
- 大媒体流中断释放句柄，不返回假成功；旧已存文件不删除。
- 媒体上传取消不吞CancellationException，文字独立推进，账号切换不能用旧任务改新空间。

### Task 1：后端媒体与敏感入口

Files: media/MediaController.java, ObjectStorage.java, LocalObjectStorage.java, S3ObjectStorage.java, MediaService.java; auth/SensitiveEndpointRateLimitFilter.java and SecurityConfig.java; tests/media/MediaSafetyTest.java, MediaCloudFlowTest.java, MediaStreamingTest.java; tests/auth/SensitiveEndpointRateLimitFilterTest.java.
- [ ] RED：伙伴上传先拒绝且原文件不变；大视频新票据拒绝，不删既存资产。
- [ ] 流式读取原件和S3缺失checksum的hash；本地头校验分块读取，原件下载Resource保持HTTP接口。
- [ ] 本地原始上传有界流、大小/hash完整确认后原子提交；鉴权在读取请求前。
- [ ] 敏感POST按remoteAddr+路由10/分钟、全局60/分钟，缓存最多1024桶；返回429/Retry-After，不信任转发头。认证令牌头长度有界。
- [ ] targeted RED/GREEN，然后Maven完整test/package；无生产更新。

### Task 2：客户端令牌保护

Files: TokenCipher.kt, SyncSession.kt; test-only AndroidKeyStore provider and TokenCipherSecurityTest.kt.
- [ ] RED：无Keystore写入失败而非plain前缀；临时读取失败保留会话偏好；旧明文读取时重新加密。
- [ ] 使用仅test源码中的JCA provider支持真实AES/GCM JVM回归，产品无测试降级；覆盖绑定、迁移全模块回归。

### Task 3：调度、预览与媒体客户端

Files: MediaUploadManager.kt, SyncSession.kt, MediaApi.kt及列表消费者。
- [ ] 分开媒体任务与文字push/pull，流式上传/下载，预览先显示，原件保存不变。
- [ ] 长媒体与新文字并行、取消/离线重试/旧账号隔离测试。

### Task 4：运维与真实门槛

- [ ] 补备份失败/磁盘/内存/HTTP错误只读检查、回退限制及操作步骤。
- [ ] 本地验证、fresh review、准备更新包；按用户约定暂不安装APK/不部署公网。
- [ ] owner提供timer首次日志/异机数据库备份/OSS保护/正式私钥恢复证据。
- [ ] 备案后HTTPS/异网双端真实验收与连续观察；不可现在勾选。

## 2026-10-05本地执行状态

Task1-3本地实现与测试完成，最终后端130、Android九模块262均通过，设备测试源码仅编译通过。Task4候选私有更新包/只读巡检/回退与证据清单准备完成，尚未生产执行。一次独立审查后修复OSS覆盖权限与恢复/取消/预览/旧任务边界；OSS改后端鉴权验证再文件流写入，兼容既有上传提示，不发覆盖PUT链接。官方文档核对及真实SDK HTTP回归确认禁用aws-chunked/默认CRC尾部要求。

以上勾选项保留为原实施/真实验收要求；不把本地通过当作真正AndroidKeyStore、旧包覆盖、生产OPS/OSS恢复、域名证书或跨省双设备通过。完整结果见releases/v27/VERIFICATION.md与本地ledger。
