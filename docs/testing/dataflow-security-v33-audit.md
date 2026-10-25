# v33 数据链路与云端安全独立审查

审查日期：2026-10-10（Asia/Shanghai）。对象：`E:/MoonLetter/Moon-Letter` 的当前工作区代码，包含已有未提交、未跟踪的源码；不是对 HEAD 或已部署制品的审查。

## 结论与证据等级

首要问题是换机恢复允许空间内一名成员取得另一名成员身份，与 PRIVATE 内容的作者隔离冲突。另有删除内容仍进入记录快照、媒体追加缺少 READY 校验、重试队列乱序和永久错误持续重试、默认导出漏掉部分日期记录等可从代码确定的问题。

下文“确证”指入口、判断条件、数据写入/返回链路在源码中明确成立，**没有执行攻击、构建、集成测试或真实请求**。P1 表示应优先处理的身份隔离问题；P2 表示需要修复的数据一致性、删除语义或导出完整性问题。没有证据证明陌生人无需凭据可跨空间读取，也没有证明线上 OSS 为公开桶。不能把这些静态发现描述为线上已泄露或实机已复现。

本次只新增本文档；不修改产品代码，不编辑正在并行重构的 `MemoryBookExporter.kt`、`LocalExportBuilder.kt` 和导出 UI；未运行 Gradle/Maven，未读取 `.env`、运行时令牌或私钥，未连接用户数据库、服务器、OSS 或手机。审查中途工具额度短暂阻塞，恢复后继续；没有用历史构建日志替代验证。

## 1. 确证风险

### F1 / P1：成员间换机恢复可以绕过 PRIVATE 身份隔离

- 位置：`server/src/main/java/com/twomemory/app/couple/CoupleController.java:33-42`；同目录 `CoupleService.java:142-153`、`:80-98`、`:124-132`、`:274-284`；`server/src/main/java/com/twomemory/app/auth/DeviceSessionService.java:43-53`。
- 触发：空间已满两人，B 持有自己的合法会话。B 调用空间的 `pairing-token` 接口；服务只要求 B 是成员，并自动把 `rejoinTarget` 设置为非 B 的另一成员 A。返回的恢复凭据随后可由 B 自行提交给公开 `/api/v1/couple/pair`。消费恢复凭据仅验证 A 仍是成员，没有验证请求者确为 A，也不需要 A 的独立恢复证明。
- 确定后果：签发 A 的新会话并撤销 A 原会话。后续 PRIVATE 判断看到的是 A 的 userId，因而允许读取 A 的私密记录、媒体和导出。`EntryService.java:224-233`、`MediaService.java:96-124`、`ExportService.java:33-43` 中的作者过滤无法抵御此身份替换。B 还可使 A 被登出。
- 边界：这是**有合法空间成员凭据**的成员间提权；不是匿名猜码、陌生人跨空间问题。15 分钟期限、一次性消费、随机凭据和限流不会阻止生成者自行消费。若产品明确接受“彼此可恢复并接管对方身份”，应承认 PRIVATE 只提供日常界面隔离；当前代码无法提供对同空间另一成员的身份安全隔离。
- 最小修复：满员空间恢复必须额外验证目标成员独占的恢复证明，或由目标成员本人批准；在补齐机制前停止仅凭另一成员会话签发目标身份的恢复能力。不要仅在 UI 隐藏入口。恢复仍应保留目标会话轮换和一次性消费。
- 待验证：使用隔离测试空间及合成内容执行完整 B→恢复 A→读 PRIVATE 的链路，断言修复后无法冒用，且 A 合法换机、B 会话仍正常。不得用用户真实私密内容做验证。

### F2 / P2：删除块后，原始内容仍通过当前快照和历史变更返回

- 位置：`server/src/main/java/com/twomemory/app/entry/EntryService.java:147-152`、`:168-174`、`:417-432`；`server/src/main/java/com/twomemory/app/sync/ChangeFeedService.java:99-124`；`android/core/database/src/main/java/com/twomemory/database/RoomSyncStore.kt:135-155`。
- 触发：共享记录作者删除自己的 TEXT/媒体块，记录仍为可读的共享记录。
- 确定后果：删除只设置 `deleted_at`。`loadEntry` 查询没有过滤删除块，仍返回其 `payload` 和 `asset_id`，仅加 `deleted=true`。删除后的 UPDATE 变更也序列化该快照。手机按删除标记隐藏块，但仍保存原 payload。新安装/从旧 cursor 拉取的合法成员还能取得旧共享变更中的内容，因为历史投影只处理整篇记录的 PRIVATE/草稿/删除状态，未按当前块删除状态净化历史 payload。
- 边界：媒体原文件的伙伴读取仍受活跃引用检查；这里确认的是删除块正文/元数据仍在 API 和变更中传输。不是证明删除后伙伴仍能下载无其他引用的媒体原文件，也不是数据库软删除本身必然错误。
- 最小修复：删除块对普通读取与同步只输出必要墓碑身份/版本，移除正文与媒体引用；读取历史变更时按当前授权及块状态净化旧快照。数据库审计保留可另设受限路径。只改 UI 过滤或只改最新 UPDATE 不能消除旧 cursor 重放。
- 待验证：删除文本后，用伙伴会话分别 GET 当前记录、从 cursor=0 重建；断言响应中无合成删除正文，删除标记仍能正确撤下旧本地块。

### F3 / P2：记录追加与头像绑定可以引用尚未完成的媒体

- 位置：`server/src/main/java/com/twomemory/app/entry/EntryService.java:138-174`；`server/src/main/java/com/twomemory/app/media/MediaService.java:89-125`；`server/src/main/java/com/twomemory/app/couple/CoupleService.java:190-204`；`server/src/main/java/com/twomemory/app/media/MediaController.java:83-99`。
- 触发：作者创建上传 ticket 后，尚未上传/完成，或者资产已 FAILED，把该 assetId 通过 `/entries/{id}/changes` 或同步 APPEND_BLOCK 追加到已发布记录；头像更新也可提交同空间本人所有但非 READY 的资产。
- 确定后果：`applyChanges` 只调用 `readAsset`（检查成员/可读性，不检查状态），写入并发出 UPDATE，未调用 `requireReady`。头像 SQL 只检查资产存在、空间和所有者，未要求 READY 或 IMAGE。下载原件接口也未做 READY 门禁，最终可能在对象不存在时失败；缩略图明确返回 409。伙伴收到有效记录/头像引用，但附件无法正常读取。恶意/异常客户端可以直接触发，正常客户端上传门禁并不能替代服务端验证。
- 边界：创建草稿和 publish 已调用 `requireReady`（`EntryService.java:81`、`:98`），相册添加明确要求同空间 READY IMAGE（`SharedAlbumService.java:67-84`）。不能据此称所有媒体入口都缺校验。这里只确认追加/头像状态缺口，不声称已获得任意他人资产。
- 最小修复：事务内为新增/更换的有效引用统一验证目标空间、可读权限、READY 和媒体种类匹配；头像限定同空间本人 READY IMAGE。删除墓碑不应要求已删除资产仍 READY。原件下载增加明确的非 READY 响应。
- 待验证：UPLOADING/FAILED ticket 追加和头像绑定应被拒绝，成功上传后可正常绑定；已有引用或 READY 的重试不得误拒绝。

### F4 / P2：重试等待期间，同记录后续操作会越过前序操作

- 位置：`android/core/database/src/main/java/com/twomemory/database/OutboxDao.kt:22-31`；`android/core/sync/src/main/java/com/twomemory/sync/SyncEngine.kt:36-65`、`:109-113`。
- 触发：同一记录有前序 CREATE/APPEND 和后续 PUBLISH/APPEND。前序网络失败，被设为将来重试；到下一轮同步时，重试时间尚未到，但后续行已到期。
- 确定后果：DAO 用 `nextAttemptAtEpochMillis <= now` 过滤前序，仅阻止较早的 CONFLICT，没有阻止较早的 PENDING 或 MEDIA_PENDING。查询因此返回后续行；引擎“一次失败即退出”只保证当前批次，不保证下一轮不越过。可能先发送 PUBLISH 到尚不存在的记录，或让带旧 baseVersion 的操作发生冲突；若前序已被服务端接受但 ACK 丢失，顺序与本地确认仍会分离。后续是否最终恢复取决于具体响应，未证明所有场景永久丢数据。
- 最小修复：DAO 对同一实体只释放最早尚未确认的操作，等待前序重试/媒体门禁/冲突解决；不同记录可以独立继续。若业务坚持全空间 FIFO，则等待队首，需明确其阻塞成本。保持 operationId 重试幂等。
- 待验证：用可控时钟模拟 CREATE 失败→重试时间未到→PUBLISH 已到期，断言 PUBLISH 不发送；另一个记录仍按既定策略推进。覆盖服务端成功但丢 ACK 场景。

### F5 / P2：永久拒绝与临时故障合并为无限重试，缺少可处理状态

- 位置：`android/core/network/src/main/java/com/twomemory/network/RetrofitFactory.kt:94-108`；`android/core/sync/src/main/java/com/twomemory/sync/SyncEngine.kt:49-65`、`:109-113`；`android/core/database/src/main/java/com/twomemory/database/OutboxDao.kt:60-75`。
- 触发：操作因格式错误返回 400，因权限/已收起记录返回 403，或目标状态导致其他永久 4xx。
- 确定后果：除 401、409 外都映射 RETRYABLE_FAILURE；引擎持续设 PENDING 与退避，没有终止次数或永久失败分类。该行到期后每次失败即结束当前 push 批次，延迟后续可发送记录；DAO 的“待处理拒绝”观察只识别 CONFLICT，这些永久拒绝不进入该状态。与此同时 pull 仍会进行，因此不能称所有入站同步也完全停止。实际指数上限是 256 秒（`1 shl 8`），不是注释/外部描述可能想象的 300 秒。
- 最小修复：按错误码区分需要重新绑定、可修正永久错误、冲突及临时失败。永久失败保留内容和错误原因，提供修正/重试入口，阻止同实体依赖操作越过，但允许无关实体继续。429 遵循 Retry-After，网络/5xx 保留退避。
- 待验证：400/403/409/429/500、超时、撤销后401的合成返回；观察队列、用户可见错误与后续独立记录，不仅检查最终 HTTP 成功。

### F6 / P2：默认不传日期的 export 并非全部可见记录

- 位置：`server/src/main/java/com/twomemory/app/export/ExportController.java:35-39`；同目录 `ExportService.java:40-44`；`server/src/main/java/com/twomemory/app/entry/EntryService.java:48-49`、`:70-72`。
- 触发：合法可见记录的 occurred_at 在 1970 年之前或当前时间之后，调用 export 不传 from/to。
- 确定后果：null from 被转换为 `to_timestamp(0)`，null to 被转换为 `now()`，这些记录静默不进入返回 entries；entryCount 只统计实际返回记录，无法显示遗漏。创建入口没有把时间限制为此区间。不会改变云端原记录，但使用者若把此 JSON 当全量留存，所得副本不完整。
- 边界：这是默认日期语义的缺口，不是证明普通历史记录都漏导；显式传覆盖范围可避免。若产品有意只导出 epoch 至今，应明确响应范围和命名，不能宣传“全部”。本次不推定手机端导出 UI 当前如何调用该接口。
- 最小修复：缺少日期参数时取消对应上下界（如 `(?::timestamptz IS NULL OR ...)`）；响应记录实际选择条件，检查 from<=to，非法日期给明确 400。
- 待验证：合成的 1969、当前、未来日期记录及显式/空日期参数，核对导出 ID 集合与预计集合。

## 2. 已存在且需要保留的保护

| 环节 | 当前证据与保护 | 限定 |
|---|---|---|
| 会话认证 | `SecurityConfig.java:21-37` 无状态、默认鉴权；`DeviceSessionAuthenticationFilter.java:57-92` 限制输入长度、仅匹配未撤销哈希会话、清除请求上下文 | 公共路径由 SecurityConfig 再限定；不能把过滤器 startsWith 单独判为匿名绕过。会话未设置有效期，且认证查询未联合检查 ACTIVE 用户/空间状态；禁用策略需专项验证 |
| 凭据存储 | `DeviceSessionService.java:27-34,43-60` 随机 256 bit、数据库仅存哈希、替换时撤销；V6 migration `:15-17` 单用户单活跃会话 | F1 仍会让新会话以受害成员身份合法通过认证 |
| 手机密钥 | `TokenCipher.kt:25-34,40-62` AndroidKeyStore AES-GCM；`SyncSession.kt:241-250` 旧明文成功加密后才能加载；Manifest `:9` 禁止自动备份 | 未读手机实际偏好/密钥，没有验证锁屏、重装、厂商迁移行为 |
| 两人/空间 | `SpaceAccessPolicy.java:19-28` 每次检查未离开/未删除成员；`CoupleService.java:94-115` 锁空间后检查容量；V1 migration `:65-84` 两人约束 | 检查的是成员表，不是云端网关策略；bootstrap 是单安装部署管理/恢复入口，不应当作多租户注册 |
| 配对 | `CoupleService.java:80-95,274-284` 哈希、一次性、15 分钟、作废旧未用凭据、行锁；`SensitiveEndpointRateLimitFilter.java:20-43` 每来源和总预算，忽略客户端 Forwarded 身份 | 单进程限流；代理后 remoteAddr 的实际来源要测。F1 属于恢复授权，不靠增加随机度解决 |
| 私密内容 | `EntryService.java:118-123,178-213,224-233` 作者检查、隐藏他人贡献时拒绝；`CommentService.java:44-54` 私密/发布状态校验 | 作者身份本身受 F1 影响 |
| 私密同步 | `ChangeFeedService.java:33-35,99-129` audience 过滤、当前隐私状态对旧记录投影为删除、隐藏行时 cursor 仍推进；`EntryService.java:198-202` 对伙伴发撤下墓碑 | 不等于删除旧媒体文件，也未净化已删除块正文（F2） |
| 媒体访问 | `MediaController.java:37-40,69-78,86-89,97-116` 创建者/上传者检查、读取鉴权在缩略图缓存命中之前、`private,no-store`；`MediaService.java:96-124` 伙伴须有活跃且允许读取的记录/相册/头像/节日引用 | 所有者读无活跃引用门禁；多个共享引用中只隐藏一个不会撤销其余引用 |
| 上传完整性 | `MediaService.java:148-180,219-240` 类型白名单、每文件上限、声明 hash 与实际对象元数据比对；`S3ObjectStorage.java:79-99` 实际流式计数/hash 后才 PUT；`LocalObjectStorage.java:74-93` 临时文件校验后原子替换 | hash 证明字节一致，不证明 MIME/内容安全或无 EXIF；没有确认总容量/频率配额 |
| OSS 传输 | `S3ObjectStorage.java:69-76` 返回 local:// 兼容提示，未向手机签发真实 OSS PUT；原件 `:135-136` 流读取，临时文件 finally 清理 | 类名/旧注释中的 presigned 不代表当前手机持有 OSS 长期密钥或直接签名上传能力 |
| 缩略图 | `ImageThumbnail.java:19-25` 像素上限、预采样，`:31-60` 缩放 JPEG；`MediaController.java:102-113` 串行解码及 8MiB/128 项缓存上限 | 原图仍可能整体读入最多64MiB；大量不同图片负载必须实测，未证明 OOM |
| 下载落地 | `MediaApi.kt:83-109` 最大字节、长度完整性、临时文件/同步/原子移动，`:114-128` 取消 HTTP；`AlbumImageLoader.kt:21-23,41-60` 限制并发、路径边界、下载后复核删除 | 手机端未校验原件 SHA-256；不能由此推定 HTTPS 正常下载发生篡改 |
| 同步幂等 | `IdempotencyService.java:20-61` 事务内预占 operationId、校验 user/hash、重放结果；`SyncController.java:49-69` 类型调度及 hash 校验 | hash 不覆盖 coupleId；已有实体操作根据实际 entry 另查成员，未证明外部成员因此可越权 |
| 分页落库 | `ChangeFeedService.java:45-64,94-129` 空间序列事务锁、high-water、limit+1；`RoomSyncStore.kt:62-79` 数据和 cursor 一起提交、未知实体使整页失败 | 新类型发布要同步升级读取端；本次未做并发提交压力测试 |
| 调度 | `SyncWorker.kt:43-49,82-92` cycleMutex、网络约束、失败重试、401停止；`ForegroundSyncLoop.kt:7-12` 前台3秒循环；`RecordMediaSync.kt:44-57,63-69` 媒体独立队列、绑定复核 | 后台实时送达无保证，F4/F5 仍影响发送顺序 |
| 本地数据库 | `SyncSession.kt:46-99` origin+couple+user 作用域及持久所有权映射，`:403-406` worker 按当前空间重建引擎 | 数据库隔离不等于所有文件缓存都按 user 隔离 |
| 相册删除 | `SharedAlbumService.java:28-45,106-130` 完整快照含墓碑，限定相册创建者/照片上传者删除；`AlbumCloudSync.kt:81-85,174-188` 先拉后推、合并墓碑并保留本地删除意图 | 删除只是撤下元数据，非物理清除对象和手机文件 |
| 服务端导出 | `ExportService.java:29-43,77-108` REPEATABLE_READ、成员和可见性过滤、只导出未删除块/评论、固定字段选择 | 未读会话表/运行时秘密；block payload 是用户内容，不能保证用户主动写入的敏感文字被移除 |
| 部署模板 | `infra/aliyun/compose.private.yaml:36-37` app 仅 loopback 发布，Postgres 没发布端口；`compose.https.yaml:14-19` Caddy 80/443，`Caddyfile.https:3-8` 拦截 actuator | 只确认仓库模板，未确认 ECS 实际容器、端口、安全组或生效配置 |

表中简写文件名按上文对应目录：服务端类位于 `server/src/main/java/com/twomemory/app/<模块>/`；手机类位于 `android/app/src/main/java/com/twomemory/app/`、`android/core/<模块>/src/main/java/com/twomemory/<模块>/`；`EntryPhoto.kt`/`ImageThumbnail.java` 的确切路径见下面边界。

## 3. 删除、缓存、OSS 与导出的覆盖边界

### 撤下与清除是不同的当前行为

- PRIVATE 是云端保留、对伙伴撤下。`RecordPrivacyAction.kt:26` 已向用户说明云端仍保存、伙伴下次同步撤下及另存文件无法追回。Room 撤下会清空标题并删除该记录的块/评论行（`RoomSyncStore.kt:89-94`；`EntryDao.kt:11-18`），这项保护存在。
- Room 撤下未删除 `filesDir/downloaded-media/<assetId>`、`cacheDir/entry-previews/<assetId>.jpg` 或内存 bitmap 缓存。`android/core/designsystem/src/main/java/com/twomemory/designsystem/EntryPhoto.kt:48-57,101-122` 缓存命中可不发服务器请求。`AlbumCloudSync.kt:161-191` 合并墓碑也只更新 Room，不删除相册原件/预览。可确认文件保留；**未确认撤下后普通 UI 仍能打开它，未确认陌生应用能读取应用私有目录**。
- 记录媒体文件/内存键只含 assetId（上述 EntryPhoto 行号），相册缓存按 origin+couple（`AlbumImageLoader.kt:37-40`）但不含 user。本地数据库已按 user 隔离。跨服务器资产 ID 重用、同一手机切换成员和当前打开页面的缓存状态需要实机检查；随机 UUID 本身不意味着会自然碰撞或泄漏。最小加固方向是统一 origin+couple+user 缓存作用域，在撤下时清理仅属于该授权的文件/内存映射，处理共享引用，避免误删仍授权的副本。
- `ObjectStorage.java:5-37` 无正式对象删除方法；当前 Local/S3 实现的 delete 调用只清理临时文件；相册删除只写墓碑，media_asset 本身未在该路径标记删除。没有发现生产的无引用对象 GC。所有者经 `MediaService.readAsset` 可继续读取其未标记删除资产，即使已从相册撤下。**这证明删除不等于 OSS 原件销毁，不证明该保留违反已明确的删除合同**。需要确定保留期限、未完成上传回收、对象版本、备份生命周期及多引用计数，再实现可恢复的回收机制。
- `MediaService.java:23,53-55,69-71` ticket 暴露 15 分钟 expiresAt；`MediaController.java:64-78` 上传不查持久 ticket 有效期。当前 local:// 没有匿名上传能力，仍需合法所有者鉴权。旧 ticket 不能仅凭 expiresAt 被当作“已撤销上传授权”；是否需要真实过期门禁属于产品/资源策略。
- 原文件逐字节保存，EXIF/GPS 也保留；缩略图 `server/src/main/java/com/twomemory/app/media/ImageThumbnail.java:64-65` 说明输出不带 EXIF。没有把“保留原件”判为隐私漏洞；是否默认去位置元数据需用户明确策略。

### export 服务的实际范围

`ExportService.java:60-65,77-108` 返回单空间可见记录及未删除块/评论 JSON。媒体只返回 assetId，不附原件；未输出 revision 历史、独立相册、节日、胶囊、资料或对象清单。类注释提到 revisions（`:14-16`），实际查询没有它。它是选择性记录导出，**不构成全空间灾备/可离线恢复包**。不能据此宣称正在重构的手机 MemoryBook 导出也遗漏这些数据；本次未审查那些正在变化的生成器/UI。

每条记录查询块和评论（`:55-56`），全结果进 List/Map，再在 Controller `:39-45` 生成完整 JSON String；没有分页/流式输出/记录数量限制。这一成本从代码可以确定，内存溢出或服务拒绝则**未复现**，应在测试空间用合成大档案验证，不能写为线上已崩溃。优先流式导出并保持一致性快照，或有明确限制/失败提示的异步导出。Controller 未显式设置 no-store，但 Spring Security 默认响应头和代理实际配置未运行验证，不能只凭这一点断言导出被公开缓存。

### 部署与鉴权未验证项

S3 配置允许 HTTP/HTTPS（`S3StorageProperties.java:31-39`），实际协议未读取。静态模板不声明桶 ACL、RAM 细粒度授权、对象版本策略或安全组；线上是否符合最小权限未知。认证只查未撤销会话（`DeviceSessionAuthenticationFilter.java:69-77`），不联合 ACTIVE 用户/空间状态；若运维只改 DISABLED/CLOSED 而未撤销会话/成员，普通接口不必然停止访问。尚未读取/运行完整禁用管理流程，不把这项条件风险描述成已存在的用户可用绕过。

没有审查云端运行日志、真实凭据、备份内容、设备 SQLite、已发布 APK/JAR 与当前源码的一致性，也未验证新 TLS 部署是否生效。现有测试源码的存在不代表本次通过测试。

## 4. 必须用真实服务/手机验证的清单

以下是后续执行清单，本次全部未执行。先用隔离环境/测试空间、独立测试账号、合成内容；需要新授权才能访问用户实际服务或手机。测试只记录断言/状态/脱敏标识，不打印凭据。

1. **F1 恢复身份**：满员空间 B 生成 A 恢复凭据并尝试消费；验证独立恢复证明、会话撤销、PRIVATE timeline/changes/export/media 隔离和合法换机。bootstrap 管理秘密应只对管理者开放并验证轮换。
2. **四身份矩阵**：作者 A、伙伴 B、其他空间 C、匿名访问记录/评论/media data/thumbnail/export/相册；覆盖 PRIVATE、PERSONAL DRAFT、COLLABORATIVE DRAFT、共享、删除、成员离开、会话撤销。响应体也应无受保护正文。只验证自己持有的测试对象 ID。
3. **隐私撤下/重放**：B 在线/离线/重装；A SHARED→PRIVATE→SHARED，B 从旧 cursor 和零 cursor 拉取；确认隐藏内容不出现在重放响应、墓碑/评论恢复、当前页面及时更新、无通知正文泄漏。
4. **F2 块删除**：伙伴未曾同步与已同步两种状态，删除 TEXT/IMAGE/AUDIO 后当前 GET 与全量 changes 不含合成正文/无授权引用；保留墓碑可正确清理本地行。验证共享资产被其他活跃位置引用时的正确行为。
5. **手机缓存边界**：撤下前下载原图/音频、删除相册/照片、切换同空间成员与后端；观察普通 UI、当前查看器、通知、导出是否仍呈现撤下内容；在授权的测试设备检查文件/内存清理。区分主动另存文件与应用内部缓存。
6. **上传与就绪**：截断/超长/错误 hash、实际格式与声明 MIME 不同、旧 ticket、重复 operationId、READY 重试、断网/进程死亡；验证 F3 追加/头像拒绝非 READY、实际对象与行状态一致、无部分原件覆盖。
7. **队列与手机调度**：F4 前序退避期间后续依赖不得发送；F5 永久4xx可处理；丢 ACK 不重复记录/评论；网络切换、后台限制、进程重启、前台3秒循环、迁移服务器时绑定锁一致；不同记录和媒体队列不互相饿死。
8. **序列与并发**：同空间双写、多页>200、仅作者行穿插、事务回滚/延迟提交、分页途中隐私变更；核对每页已提交集合及 cursor，未知类型不会悄悄丢弃。数据库迁移/约束必须在实际 PostgreSQL 版本验证。
9. **相册删除传播**：A 删除、B 离线旧照片待上传、双方重连；墓碑不复活，删除/封面权限正确，重复请求安全；对象保留与清理期限有明确预期。
10. **export**：F6 过去/未来日期、空/反向/非法范围；比对 ID/块/评论集合、私密作者过滤、并发写入一致性、大量合成记录内存/耗时、实际 cache headers。媒体只含引用的服务端 JSON与手机离线纪念册分别验收。
11. **ECS/OSS/TLS**：对测试前缀验证桶匿名读/写被拒、RAM 限定桶及所需对象操作、外网仅预期端口、Postgres不暴露、actuator遮蔽、证书/HTTP升级、实际 S3 走 HTTPS；不读取或打印秘密。检查备份/版本/生命周期是否能实现约定删除及恢复目标。
12. **资源与格式**：合成高像素/不支持格式/64MiB图、多并发上传与导出、客户端中断；观察限流/超时、临时文件回收、服务内存和磁盘，确认不会耗尽实例。现有每文件上限和解码限制需要在小 ECS 的实际资源约束下确认。

## 5. 交付与后续复核

唯一新增目标：`docs/testing/dataflow-security-v33-audit.md`。未提交、未暂存、未回滚既有变更；无产品代码修复。并行重构期间行号可能变化，复核时以当前源码重新定位，尤其不要把本报告当作已部署版本认证。优先处理 F1，再处理 F2/F3 和队列 F4/F5，最后完善导出范围与上述边界验证。
