# 双人日记 App 产品、视觉与数据设计规格

> **历史文档：M1 已被取代。** 当前 M1 权威规格为 `docs/superpowers/specs/2026-10-01-self-use-m1-design.md`。本文件只用于追溯完整产品愿景，不得据此宣称 M1 已实现或执行旧的身份、同步、冲突与验收方案。

日期：2026-09-30  
状态：历史参考
首发平台：Android；iOS 后续接入相同服务端 API

## 1. 产品目标与边界

本产品是只服务于一对伴侣的私密记录工具。核心体验是把双方的日常想法、照片、视频、语音、音乐、城市故事和共同游记沉淀为一条可持续浏览、可追溯修改、可稳定双端同步的时间线。

首期必须保证：

- 一对伴侣组成一个共享空间，每个空间固定最多两名有效成员。
- Android 本地离线可写、联网后可靠同步，另一端通常在秒级感知更新。
- 个人记录与共同记录使用不同编辑体验，但进入同一时间线。
- 共同记录按内容块保存作者，并保留每次发布修改的版本快照。
- 图片、视频、语音使用独立对象存储；数据库只保存元数据和引用。
- 支持暖米色与纯白主题，双方可独立选择，不互相覆盖。
- 可导出结构化数据、可阅读文档和原始媒体。

明确不做：

- 群组、关注、公开动态、陌生人社交。
- 实时定位、后台持续定位、行动轨迹或伴侣位置监控。
- 实时多人光标协同或复杂 CRDT。
- 首期 AI 生成、AI 总结或推荐。
- 首期复杂账号体系、角色权限后台和跨端共享业务代码。

## 2. 已批准视觉基准

### 2.0 品牌名称与启动图标

- App 对外中文名称固定为「月笺」；`Moon Letter` 只作英文工程代号。
- 启动图标采用已批准的「月印封笺」方案：珊瑚色闭合日记本封面、鼠尾草绿窄书脊、月金色月牙和书签、暖米色背景与纸页。
- 图标必须保持扁平、圆润的卡通插画感，禁止改为写实布料、写实 3D 或摄影风格。
- 不在图标中加入姓名、字母、爱心、人物或文字；双人语义通过珊瑚色和鼠尾草绿的层级关系表达。
- 权威矢量稿与 Android 落地尺寸见 `docs/design/brand/moonjian-icon-spec.md`。

以下 PNG 是实现的权威视觉目标。不得用“相似风格”替代；页面完成后必须在相同 390 × 844 视口、相同内容状态下逐屏截图对比。

| 页面 | 参考文件 | 核心约束 |
|---|---|---|
| 时光首页 | `docs/design/reference/home-timeline.png` | 左侧缝线式单列时间轴、连续下滑、可换封面、固定五栏导航 |
| 我的记录 | `docs/design/reference/personal-entry-editor.png` | 无边框自然书写、精确记录时间、统一附件工具栏 |
| 共同记录 | `docs/design/reference/shared-entry-editor.png` | 模块化内容块、作者标识、排序与异步补充视角 |
| 共同相册 | `docs/design/reference/shared-album.png` | 月份分组、瀑布式媒体网格、照片与视频统一浏览 |
| 城市地图 | `docs/design/reference/city-map.png` | 城市级聚合、故事列表、无实时定位和轨迹 |
| 我们 | `docs/design/reference/couple-profile.png` | 双方资料、纪念日、胶囊、主题和导出入口 |

### 2.1 全局视觉约束

- 基准画布为 Android 竖屏 390 × 844；实现必须适配不同 Android 尺寸，不得拉伸参考图。
- 默认主题为暖米色；纯白主题只替换颜色和纸张表面，不改变结构、间距与图标。
- 主身份色：小满为低饱和珊瑚色；阿屿为低饱和鼠尾草绿；纪念强调使用少量月光金。
- 功能图标必须来自同一套细线描边图标库，线宽、视觉尺寸和基线一致；禁止混用 emoji、填充图标和手绘功能符号。
- 手帐元素只使用纸张纹理、单处胶带、缝线、轻量桂花/月相和手写下划线，不堆叠装饰。
- 正文优先保证可读性；中文正文字号不得低于 14sp，触控目标不得小于 48dp。
- 底部导航固定为 `时光 / 相册 / ＋记录 / 地图 / 我们`，编辑器内不显示主导航。
- 首页顶部封面、我们页横幅均可替换。中秋元素为纪念语义，不是节日主题皮肤。

### 2.2 页面行为

#### 时光首页

- 时间线按 `occurred_at DESC, id DESC` 连续分页，不按天分页。
- 日期只是分组标题；底部自动加载下一页。
- 个人记录显示作者头像和身份色；共同记录同时显示双方身份。
- “过去的今天”和“本周小结”作为时间线中的特殊内容单元，不新增首页仪表盘。
- 顶部封面可更换；失败时保留旧封面直至新资源上传完成。

#### 我的记录

- 点击 `＋记录` 后直接进入编辑器，不先选择媒介类型。
- 默认聚焦正文；支持文字、图片、视频、语音、音乐、城市内容。
- 顶部明确显示可修改的故事发生时间，例如 `2026年9月30日 · 20:18`。
- 草稿仅创建者可见；发布后伴侣可见并可评论。

#### 共同记录

- 内容由有序块组成，每个块保存创建者和最后修改者。
- 双方修改不同块时自动合并；同时修改同一块时不得静默覆盖。
- 发布一次形成一个可回溯版本；允许查看和恢复旧版本。
- 对方既可以新增“自己的视角”块，也可以编辑已有共享块。

#### 相册

- 默认按月份浏览全部已发布媒体；视频显示时长与播放标记。
- 相册集是媒体引用集合，不复制底层文件。
- 删除相册集不得删除原记录或原媒体；删除原媒体需展示引用影响。

#### 地图

- 只在用户主动添加城市时请求一次位置或接受手动城市选择。
- 首页地图使用城市级聚合；故事详情才可使用该条记录的精确快照。
- 不保存后台轨迹，不展示双方实时位置。

#### 我们

- 双方可分别修改自己的头像和昵称。
- 主题偏好属于个人，不同步覆盖伴侣设置。
- 中秋纪念日使用农历八月十五表达，不能固化为每年同一公历日期。
- 数据与导出入口在此页面，导出任务在服务端异步生成。

## 3. 系统架构

### 3.1 Android

- Kotlin + Jetpack Compose。
- 单向数据流：Composable 只渲染 `UiState` 并上报事件；ViewModel 调用用例或仓储。
- Room 是客户端界面的唯一读取来源。
- Retrofit/OkHttp 访问 REST API；序列化使用 Kotlinx Serialization。
- WorkManager 处理同步、媒体上传和失败重试。
- DataStore 只保存主题、同步游标等轻量偏好，不保存业务记录正文。

模块边界：

```text
app
core:model
core:database
core:network
core:sync
core:designsystem
feature:timeline
feature:editor
feature:album
feature:map
feature:couple
```

### 3.2 服务端

- Spring Boot 提供 REST API、认证、业务校验、版本控制和导出任务。
- PostgreSQL 保存关系数据、版本、同步变更日志和媒体元数据。
- S3 兼容对象存储保存原图、缩略图、视频和语音。
- FCM 只发送“有新变更，请同步”的提示，不承载权威业务内容。
- Redis、WebSocket、消息队列首期不引入；出现明确容量需求后再增加。

### 3.3 一致性原则

- 服务端数据库是最终权威数据源。
- Android 本地写入业务表和 `outbox_operation` 必须处于同一 Room 事务。
- 所有写 API 必须携带客户端生成的 `operation_id`，服务端按该 ID 幂等。
- 媒体上传成功不等于记录发布成功；服务端只允许引用状态为 `READY` 的媒体。
- 删除采用软删除和同步墓碑；清理原始媒体前必须经过保留期和引用检查。

## 4. 数据类型约定

- 主键：UUID v7；客户端可离线生成。
- 时间：API 使用 ISO-8601 UTC，例如 `2026-09-30T12:18:00Z`；数据库使用 `timestamptz`。
- 用户选择的发生时区单独保存 IANA 标识，例如 `Asia/Shanghai`。
- 枚举：API 使用稳定大写字符串；未知枚举客户端必须容错，不得崩溃。
- 文本：UTF-8；服务端按 Unicode 码点校验长度，不能按字节或 UTF-16 单元误判。
- 分页：游标分页，不使用页码。游标绑定排序字段与最后一条 ID。
- 金额不存在；天数、时长统一使用整数，媒体时长单位为毫秒。
- 经度 `decimal(9,6)`，范围 `[-180, 180]`；纬度 `decimal(8,6)`，范围 `[-90, 90]`。
- 所有业务实体包含 `created_at`、`updated_at`；可同步删除实体包含 `deleted_at`。

## 5. 服务端关系模型与约束

### 5.1 用户与双人空间

#### `app_user`

- `id uuid primary key`
- `status enum(ACTIVE, DISABLED)` 非空
- `created_at timestamptz` 非空

#### `user_profile`

- `user_id uuid primary key references app_user`
- `display_name varchar(24)` 非空，去除首尾空白后长度 1–24
- `avatar_asset_id uuid null references media_asset`
- `theme enum(WARM_BEIGE, PURE_WHITE)` 非空，默认 `WARM_BEIGE`
- `updated_at timestamptz` 非空

#### `couple_space`

- `id uuid primary key`
- `status enum(ACTIVE, UNPAIRED, CLOSED)` 非空
- `cover_asset_id uuid null references media_asset`
- `created_at/updated_at/deleted_at`

#### `couple_member`

- `couple_id uuid references couple_space`
- `user_id uuid references app_user`
- `joined_at timestamptz` 非空
- 主键 `(couple_id, user_id)`
- 同一 `ACTIVE` 空间最多两个未删除成员；由事务内锁定空间行后校验。
- 同一用户首期最多属于一个 `ACTIVE` 空间。

### 5.2 记录与版本

#### `entry`

- `id uuid primary key`
- `couple_id uuid` 非空
- `mode enum(PERSONAL, COLLABORATIVE)` 非空
- `state enum(DRAFT, PUBLISHED, CAPSULE_LOCKED, ARCHIVED)` 非空
- `author_id uuid` 非空，必须是当前空间成员
- `title varchar(120)` 可空
- `occurred_at timestamptz` 非空
- `occurred_timezone varchar(64)` 非空
- `current_revision_no integer` 非空且大于等于 0
- `row_version bigint` 非空且大于等于 0，用于乐观锁
- `location_snapshot_id uuid` 可空
- `created_at/updated_at/deleted_at`
- 草稿只允许作者读取；`CAPSULE_LOCKED` 在解锁前只返回封面元数据，不返回正文。

#### `entry_contributor`

- `entry_id uuid`
- `user_id uuid`
- `contribution_role enum(OWNER, CONTRIBUTOR)`
- 主键 `(entry_id, user_id)`
- `PERSONAL` 记录只能有一个 `OWNER`；`COLLABORATIVE` 最多两个参与者。

#### `entry_block`

- `id uuid primary key`
- `entry_id uuid` 非空
- `type enum(TEXT, IMAGE, VIDEO, AUDIO, MUSIC, LOCATION)` 非空
- `order_key bigint` 非空
- `created_by/updated_by uuid` 非空
- `block_version bigint` 非空
- `payload jsonb` 非空
- `asset_id uuid` 仅媒体类型可用
- `created_at/updated_at/deleted_at`
- 唯一约束 `(entry_id, order_key)`；重新排序必须在一个事务内完成。
- `TEXT.payload.text` 为 1–20000 字符。
- `MUSIC.payload` 必须保存 `provider`、`share_url`、`title_snapshot`、`artist_snapshot`；外部服务不可用时仍能展示快照。
- `LOCATION` 只能引用记录所属空间可访问的位置快照。

#### `entry_revision`

- `id uuid primary key`
- `entry_id uuid` 非空
- `revision_no integer` 非空
- `base_revision_no integer` 非空
- `edited_by uuid` 非空
- `snapshot jsonb` 非空，保存该版本完整有序块与作者信息
- `change_summary varchar(200)` 可空
- `created_at timestamptz` 非空
- 唯一约束 `(entry_id, revision_no)`；版本只追加，不更新、不物理删除。

#### `comment`

- `id uuid primary key`
- `entry_id uuid` 非空
- `author_id uuid` 非空
- `body varchar(2000)` 非空
- `reply_to_id uuid` 可空且必须属于同一记录
- `created_at/updated_at/deleted_at`

### 5.3 媒体

#### `media_asset`

- `id uuid primary key`
- `couple_id/owner_id uuid` 非空
- `kind enum(IMAGE, VIDEO, AUDIO)` 非空
- `status enum(LOCAL_PENDING, UPLOADING, PROCESSING, READY, FAILED, DELETED)` 非空
- `object_key varchar(512)` 在服务端生成，客户端不得指定最终路径
- `mime_type varchar(100)` 非空并进行白名单校验
- `byte_size bigint` 非空且大于 0
- `sha256 char(64)` 非空
- `width/height integer` 图片和视频必填
- `duration_ms bigint` 视频和语音必填
- `thumbnail_asset_id uuid` 可空
- `created_at/updated_at/deleted_at`
- 默认可配置限制：图片 20 MiB/张，视频 500 MiB 且 10 分钟内，语音 50 MiB 且 30 分钟内。
- 相同空间内可按 SHA-256 去重底层对象，但不能合并权限或业务引用。

### 5.4 城市、相册与时间功能

#### `location_snapshot`

- `id uuid primary key`
- `couple_id/created_by uuid` 非空
- `latitude/longitude` 可空；手动选择城市时不保存精确坐标
- `city_name varchar(80)` 非空
- `country_code char(2)` 非空
- `source enum(SINGLE_GPS_REQUEST, MANUAL_CITY)` 非空
- `captured_at timestamptz` 非空
- 不提供持续更新字段，不创建轨迹表。

#### `album` 与 `album_item`

- `album`: `id, couple_id, title varchar(80), cover_asset_id, created_by, created_at, updated_at, deleted_at`
- `album_item`: `(album_id, media_asset_id)` 联合主键，另含 `order_key, added_by, added_at`
- 删除 `album_item` 只删除集合关系，不删除媒体。

#### `anniversary`

- `id, couple_id, title varchar(80)`
- `calendar_type enum(GREGORIAN, LUNAR)`
- `month smallint, day smallint, lunar_leap_month boolean`
- `start_year smallint` 可空
- `reminder_days smallint[]` 默认空数组
- 中秋纪念日保存为 `LUNAR, month=8, day=15, lunar_leap_month=false`。

#### `time_capsule`

- `id uuid primary key`
- `entry_id uuid unique` 非空
- `locked_by uuid` 非空
- `unlock_at timestamptz` 非空且发布时晚于服务器当前时间
- `status enum(SEALED, OPENED)` 非空
- `opened_at timestamptz` 可空
- 锁定后正文不可修改；提前打开首期不允许。

## 6. 同步协议

### 6.1 本地写入

1. 客户端在一个 Room 事务内更新业务表。
2. 同时写入 `outbox_operation`：`operation_id, entity_type, entity_id, action, base_version, payload, created_at, attempt_count`。
3. UI 立即从 Room 观察到变化并标记 `PENDING`。
4. WorkManager 在联网后顺序提交；相同 `operation_id` 重试只产生一次服务端效果。

### 6.2 增量拉取

- 服务端为每个空间维护单调递增 `change_seq`。
- 客户端调用 `GET /sync/changes?after={cursor}&limit=200`。
- 返回变更、删除墓碑和 `next_cursor`；整批写入 Room 成功后才提交本地游标。
- FCM、前台恢复、启动和手动刷新都只触发同一同步入口。

### 6.3 冲突

- 请求携带 `base_version`；与服务器不一致时返回 `409 CONFLICT` 和当前实体摘要。
- 不同 `entry_block.id` 的修改可自动合并，并产生新的记录版本。
- 同一块同时修改时保留双方版本，要求用户选择保留一方或手动合并。
- 删除与编辑冲突时默认不静默删除：恢复为可审阅冲突，直到一方确认。
- 任何冲突处理结果都写入新的 `entry_revision`。

## 7. 媒体上传

1. 客户端创建媒体元数据，取得预签名上传信息。
2. WorkManager 分片或断点上传并校验 SHA-256。
3. 服务端校验 MIME、大小与所有权，将状态设为 `PROCESSING`。
4. 生成缩略图、读取宽高/时长，完成后设为 `READY`。
5. 记录发布事务校验全部引用媒体为 `READY`；未完成时保存草稿并显示具体状态。

原始媒体和缩略图使用不同对象键。数据库每日备份，对象存储启用版本控制或等效防误删能力；手机双端副本不作为备份方案。

## 8. 派生功能

- “过去的今天”：按当前空间、月日和已发布状态查询过去记录；不生成重复数据。
- “本周小结”：按自然周确定性聚合文字摘要、媒体封面、音乐与去过的城市，不调用 AI；可缓存结果但源数据仍是记录。
- 倒计时：由纪念日规则和当前时区计算，不持久化每日剩余天数。
- 网易云音乐：首期接受分享链接并解析/保存展示快照；不承诺依赖非官方接口完成播放。外部解析失败时保留链接和用户可编辑标题。

## 9. 导出

用户从“数据与导出”发起异步任务。导出 ZIP 必须包含：

- `manifest.json`：模式版本、空间、用户、记录、内容块、评论、纪念日和胶囊元数据。
- `entries/`：每篇记录一份 Markdown；共同记录标明每个块的作者与版本时间。
- `index.html`：离线可阅读索引。
- `media/`：有权限的原始图片、视频和语音。
- `media-index.csv`：媒体文件、记录、时间、城市和校验值映射。

导出包不包含登录凭证、设备令牌、内部对象键和已删除且超过保留期的数据。导出下载链接短期有效，过期后删除服务端临时包。

## 10. 安全和隐私

- 所有查询和写入以 `couple_id + authenticated_user_id` 校验成员关系，不能只凭资源 UUID。
- 传输使用 TLS；密钥和对象存储凭证只在服务端保存。
- 精确坐标只对当前空间成员返回，导出时明确包含位置数据。
- 配对使用一次性短码或链接，成功后立即失效；移除伴侣属于高风险操作，需要二次确认。
- 日志不得记录正文、媒体预签名 URL、精确坐标、令牌或导出内容。

## 11. 错误与用户反馈

- 本地已保存、等待同步、上传中、同步失败、冲突待处理必须是不同状态。
- 失败不得清空编辑器或移除本地媒体。
- 所有重试必须幂等；指数退避设置上限并允许用户手动重试。
- 服务端错误返回稳定错误码、可读消息和 `request_id`；客户端不依赖服务端中文文案做逻辑分支。

## 12. 实施分解

项目拆成三个可独立验收的里程碑，避免一次实现全部功能导致同步与媒体基础不稳。

### M1：稳定记录闭环

- Android 工程、设计系统、主题、底部导航。
- 简化配对、资料和共享空间。
- 首页、我的记录、共同记录。
- Room、Outbox、Spring Boot、PostgreSQL、增量同步。
- 图片上传、评论、版本历史与冲突处理。

验收：两台 Android 设备可离线分别创建内容，恢复网络后不丢失、不重复，并能查看共同编辑版本。

### M2：富媒体与空间浏览

- 视频、语音、音乐分享。
- 共同相册、相册集。
- 城市快照与城市地图。
- 后台上传、缩略图和失败恢复。

验收：大媒体上传中断后可继续，地图不请求持续定位，相册引用删除规则正确。

### M3：时间功能与可迁移性

- 纪念日、倒计时、过去的今天、本周小结。
- 时间胶囊。
- 完整导出与恢复校验。
- iOS API 契约冻结。

验收：农历中秋计算正确；胶囊到期前正文不可获取；导出包可离线阅读且校验文件完整。

## 13. 测试与完成标准

- 数据库迁移、约束、成员越权、幂等写入和同步游标必须有集成测试。
- Android 仓储、冲突合并、Outbox 状态机和时间计算必须有单元测试。
- 使用网络断开、进程被杀、重复请求、上传中断、时钟偏差和双端同时编辑进行故障测试。
- 六个主页面必须分别通过 390 × 844 基准截图对比；布局、层级、色彩、图标、文字位置、纸张质感与底部导航逐项检查。
- 视觉实现不能直接把整张参考 PNG 当作页面背景；必须使用真实组件、真实滚动、可点击控件和适配布局。
- 核心交互、键盘遮挡、无障碍字体放大、浅色主题和纯白主题均需人工验收。

## 14. 首期验收边界

完成不是“页面能打开”，而是同时满足：

1. 视觉与批准参考图在目标状态下高度一致。
2. 数据约束在客户端和服务端均执行，服务端为最终裁决。
3. 双端离线写入、重试、去重、冲突和删除均有可重复验证结果。
4. 媒体不会因进程退出或弱网丢失。
5. 共同编辑可追溯、可恢复，任何一方的修改不会被静默覆盖。
6. 导出覆盖结构化数据、可读文档和原始媒体。
