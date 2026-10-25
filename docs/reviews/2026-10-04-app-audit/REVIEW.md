**月笺：项目现状、体验与性能审查**

审查日期：2026-10-04（Asia/Shanghai）。对象：当前工作区源码、现有 V26 Debug APK、Android 模拟器中的实际界面、部署与验收脚本。按两位成员长期私有使用评价，不把公开注册、多租户、商业运营当作现有项目的必需功能。

**总体判断**

月笺已经具备有辨识度的产品外观，以及比较完整的记录、双人共享、离线保存和回忆整理能力。它的优势是情感表达和数据正确性设计；主要短板是媒体处理链路不统一、部分同步任务相互等待、长期数据增长后的成本没有受到充分控制，以及正式发行和真实公网运行尚缺最终证据。

当前适合继续作为两人私有应用完善。建议先集中提高“进入页面就能看见内容、上传不挡住收取、断网后能恢复、换正式包不丢数据”这四项能力，再扩展新功能。

**本次证据与限制**

- 读取了 Android 导航、编辑、时间线、相册、地图、音频/导出相关实现，以及 Room、网络、同步、会话、服务端权限、媒体、相册、纪念卡、数据库迁移和部署配置的核心链路。不是逐行覆盖所有测试和每个历史版本。
- 在现有 API 34 模拟器中查看了 8 个页面/状态，保存原始截图并逐张检查；设备版本经 `getprop ro.build.version.sdk` 核对。没有新建真实记录、配对、删除内容或修改正式服务。
- 安装包与工作区 `app-debug.apk` 的 SHA-256 相同：`99c05234d0b88b1c520ea0742a5c6078633771f7125955ab469b5644bebdc7ee`。版本为 `0.1.0+public-ready-v26-20261004`，APK 37,800,300 字节，约 36.05 MiB。这证明截图来自该包，不代表本次重新构建了源码。
- 模拟器是空数据、待配对的测试状态，连接地址为本机不可用的测试端点。因此截图可以支撑视觉、入口和文案发现，不能证明真实双端同步、媒体传输与后台通知成功。
- Android 模型/网络/同步测试复跑被 JVM `Unable to establish loopback connection` 阻断；Maven 的定向媒体测试被依赖解析与网络限制阻断。没有将旧日志中的“247 项通过”作为本次通过结论。日志保存在本目录。
- 未登录 ECS，未核验真实安全组、OSS ACL、自动备份运行状态、正式证书及异地网络延迟。项目的[跨省验收记录](E:/MoonLetter/Moon-Letter/docs/testing/public-cross-province-acceptance.md)仍标注“未执行”。
- 未做 TalkBack、系统大字体、真实低端机、长期存储增长和正式 Release 性能测试；本报告不声称完整无障碍合规。

**当前产品与技术现状**

| 方面 | 现状 | 判断 |
|---|---|---|
| 产品组织 | 时光、相册、记录、地图、我们；个人/共同记录、多媒体、回应、草稿、回看、纪念日/惊喜、纪念册导出 | 核心产品结构已经成立，下一轮应收敛质量 |
| Android | Kotlin + Compose；app、core、feature 分模块；minSdk 26、targetSdk 37；当前可审查包为 Debug V26 | 原生体验基础好，正式 Release 验收独立进行 |
| 本地持久化 | Room v4、迁移、outbox、同步游标、独立相册原件、自动草稿 | 离线和重试思路正确 |
| 服务端 | Spring Boot + PostgreSQL + Flyway；会话鉴权、空间权限、幂等、版本与变更流 | 对两人私有使用足够，不必先拆微服务 |
| 云媒体 | 私有 S3/OSS；上传有签名 URL、完成校验；下载目前经 Java 服务中转 | 上传方向好，下载方向是明显改进点 |
| 地图 | 本地地理手账默认可用；WebView + 本地 Leaflet + OSM 在线街道瓦片 | 弱网有底图兜底，CPU 和外部瓦片分别优化 |
| 同步/通知 | 前台每轮结束后等 3 秒；WorkManager 作为补偿；FCM 发布器仍为空实现 | 前台能主动刷新，后台即时收到尚未实现 |
| 公网与发行 | HTTPS 代理、端点迁移、备份工具已准备；实际公网与跨省验收待做；正式签名已有文档 | “准备好工具”与“已经运行可靠”必须分开 |

根 README 仍描述 V10 和旧云端状态，较新的状态分散在 V25/V26 文档。建议保留历史，但新增一份唯一的“当前版本说明”：客户端包哈希、服务端 JAR 哈希、数据库版本、部署方式、已验证场景、待验证门槛。大量未提交改动和日志也让可复现性变差；本次没有提交或清理它们。

**值得保留的优点**

1. **产品语言统一。** 暖纸色、手写标题、克制的珊瑚色、地图回忆与共同空间，在步骤 1–8 中保持一致。大多数主入口有文字，不依赖纯图标猜测。记录、照片、声音、城市和回看围绕同一条关系叙事展开。
2. **数据正确性意识强。** 自动草稿包含 300 ms 防抖、返回和 ON_STOP 保存；媒体未就绪时记录不会被当作完整发布；outbox 与幂等 operationId 防止重试重复；变更与游标原子应用；数据库有明确迁移，而非清库重建。[草稿保存](E:/MoonLetter/Moon-Letter/android/app/src/main/java/com/twomemory/app/AppNavigation.kt:824)、[同步引擎](E:/MoonLetter/Moon-Letter/android/core/sync/src/main/java/com/twomemory/sync/SyncEngine.kt:39)、[幂等服务](E:/MoonLetter/Moon-Letter/server/src/main/java/com/twomemory/app/sync/IdempotencyService.java:23)
3. **权限不只依赖界面隐藏。** 个人草稿读取、媒体关联、惊喜日期与收件人、相册成员和修改者，都有服务端检查。媒体缩略图缓存命中前仍先鉴权；未来惊喜以服务端日期规则判断。[媒体权限](E:/MoonLetter/Moon-Letter/server/src/main/java/com/twomemory/app/media/MediaService.java:83)、[纪念卡权限](E:/MoonLetter/Moon-Letter/server/src/main/java/com/twomemory/app/occasion/OccasionService.java:24)
4. **原件与预览已经部分分开。** 相册只对可见单元请求预览，预览并发为 2、原图为 1；缓存写入有临时文件、空间校验与取消处理；保存相册照片复制原字节，避免把缩略图当原件导出。[相册加载器](E:/MoonLetter/Moon-Letter/android/app/src/main/java/com/twomemory/app/AlbumImageLoader.kt:20)、[照片导出](E:/MoonLetter/Moon-Letter/android/app/src/main/java/com/twomemory/app/AlbumPhotoExporter.kt:25)
5. **有断网后的可用性和数据带走能力。** 地理手账不用在线瓦片；原件与已下载媒体能在本机重看；纪念册支持离线阅读。本机胶囊的说明明确指出只在本机保存，避免假装实现云端封存。
6. **公网切换考虑了历史数据。** `storageOrigin` 与网络地址分开，迁移先核对同一空间与成员，再原子提交；没有通过重新 bootstrap、卸载或搬移整套文件来实现换地址。[端点迁移](E:/MoonLetter/Moon-Letter/android/app/src/main/java/com/twomemory/app/ServerEndpointMigration.kt:15)
7. **已有实际运维工具。** 私有部署限制 Java 堆、连接池、线程及日志；备份脚本使用一致快照，恢复检查包含内容指纹，HTTPS 入口隐藏 actuator。这些是好基础，仍需确认远端实际启用。

**本次界面审查：步骤与健康情况**

| 步骤 | 实际查看的状态 | 健康情况与建议 |
|---|---|---|
| 1 | 首页空状态 | 主动作明确、风格统一；“记下今天”“写下第一句”和底部加号重复，空状态可减少动作竞争 |
| 2 | 个人记录编辑器空状态 | 图片/视频/语音/音乐/城市入口完整；“我的记录”究竟是本人书写还是仅本人可见，语义值得明确；完成按钮可改为与真实发布状态一致的文案 |
| 3 | 共同相册照片空状态 | 照片/相册集结构清楚；主按钮却是“查看相册集”，首次导入要多跳一步，建议“创建相册并添加照片” |
| 4 | 地理手账全国地图 | 稳定后的离线底图正常，搜索/缩放入口明确；开图期间曾看到加载态，稳定截图已重新采集；小城市文字和归属说明可读性偏弱 |
| 5 | 切换为街道地图后的全国视图 | 底图存在；成都、武汉、拉萨等城市名出现逐字断行，标签宽度与避让需要修正；此图不证明街道瓦片服务在跨省网络中稳定 |
| 6 | 我们、待配对状态 | 关系信息与功能组织清楚；“待配对”附近缺直接邀请主按钮；正式产品应优先显示连接状态，把 URL/M1/长版本名收进诊断页 |
| 7 | 设置与通知 | 后台延迟说明诚实；默认周日不在首屏可见的周一至周五中，源码支持横滑但提示弱，可改为两行或显示当前选项；普通文字对比度应提高 |
| 8 | 本机时间胶囊表单 | 本机保存/未同步/手机时间影响写得明确；入口的“未来的我们”容易让人先预期双端共享，建议入口就标“本机信件” |

![本次审查界面总览](E:/MoonLetter/Moon-Letter/docs/reviews/2026-10-04-app-audit/screenshots/overview.png)

**体验与可访问性建议**

- 在“我们”页加入明确的“邀请伴侣/重新连接”动作，以及“已连接、离线、待发送 N 项、上次同步时间”。不要只让用户读服务器地址推断是否可用。模拟器这里的端点是测试配置，不据此判断真实用户的服务已断开。
- 在编辑器就近解释“我的记录”和“共同记录”：谁可以看、何时对方可见、谁能修改各自内容。若个人记录发布后双方可见，应说明“由我书写，分享后对方可见”；无需新增另一套隐私模型。
- 把“保存到本机”“服务器已确认”“对方已读”分开。当前 outbox 能证明服务器确认，不能证明对方实际阅读；不要因此增加虚假的已读承诺。
- 保留现在的字体与色调，但给小字和可点击文本更深的颜色。按源码颜色计算，珊瑚色 `#D67B63` 与纸色 `#F8F4EB` 约 **2.78:1**，白字与该珊瑚色约 **3.06:1**，灰字 `#81786F` 与纸色约 **3.94:1**。这些是设计色计算，不是每个实际像素的合规判定；普通文字可参考 4.5:1，大字号可参考 3:1。禁用按钮应与可用按钮分开评价。[W3C 对比度说明](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)
- 下轮使用 1.3/1.5 倍字体、TalkBack、360 dp 宽屏、小屏横屏检查记录工具栏、日期、地图标签与通知设置。当前底部导航已有选中语义、地图搜索有标签，这些应保留。
- 优先保持已有视觉体系，调整文案、反馈和边界。没有证据支持现在进行一次整体重设计。

**图片加载：具体瓶颈与改进路线**

| 发现 | 代码证据 | 影响 | 建议 |
|---|---|---|---|
| 日记、地图故事封面仍读取原图 | `EntryPhotos.load → localOriginal → remoteBytes`；全局下载 Mutex；再 readBytes 解码 | 首屏本来只需小图，却等原图完整下载；一个慢资源挡住其它资源 | 统一列表/地图封面的缩略图路径；原件按查看/保存请求 |
| 相册全屏进入直接等待原图 | `AlbumLocalPhoto(original = fit)`，fit=true 使用原图 | 大图/弱网下长时间只有加载提示 | 先显示已有预览，再渐进换成 1280–2048 px 查看图；需要时取原件 |
| 首次服务端缩略图生成持有全局锁 | `synchronized(thumbnails)` 包住 OSS read 与 ImageThumbnail.render | 不同照片的冷请求串行；缓存命中请求也可能被慢生成挡住 | 上传确认后异步生成并持久化预览；同 asset 去重，生成任务有界；缩短缓存锁 |
| 缩略图仅 Java 进程内缓存 | 8 MiB/128 张，重启后丢失 | 服务重启或相册很大时反复从原图生成 | 派生图写入 OSS 或磁盘；历史照片按需回填，原件不改 |
| 下载全部由 Java 代理且装入内存 | `ResponseEntity<byte[]>`、`getObjectAsBytes()`、客户端 `.bytes()` | API 出口成为媒体瓶颈，GC/OOM 风险，缺少专门断点/Range 处理 | 服务鉴权后签发短期下载 URL，客户端直连私有 OSS；暂时不能直连时先流式中转 |
| 照片墙按整个月创建子树 | `LazyColumn` 一个 month item 内，普通 `Column.forEach` 放整个月所有照片 | 外层 Lazy 不等于逐张 Lazy；大月份一次布局很多组件，滚过的 Bitmap 可能继续占用状态内存 | 逐项懒加载的网格/瀑布流，或把月份拆为小组条目，稳定 key |
| 相册无跨单元的 Bitmap 内存缓存 | 每个单元独立 decode，日记的 LruCache 则按 8 张计数 | 重进反复解码；不同尺寸图片占用不受字节预算约束 | 统一加载器，按字节限制 Bitmap 缓存；缓存键含空间、asset、变体、版本 |
| 头像/封面在 Compose remember 内直接 decodeStream | AppNavigation 585、609、615 一带 | 解码发生在组合执行线程，大用户图片会阻塞 UI | 后台采样到实际像素尺寸，提前读尺寸并校正 EXIF |

关键源码：[日记图片](E:/MoonLetter/Moon-Letter/android/core/designsystem/src/main/java/com/twomemory/designsystem/EntryPhoto.kt:44)、[相册照片墙](E:/MoonLetter/Moon-Letter/android/app/src/main/java/com/twomemory/app/IndependentAlbumScreen.kt:384)、[全屏/列表加载](E:/MoonLetter/Moon-Letter/android/app/src/main/java/com/twomemory/app/IndependentAlbumScreen.kt:574)、[缩略图服务](E:/MoonLetter/Moon-Letter/server/src/main/java/com/twomemory/app/media/MediaController.java:90)、[OSS 读取](E:/MoonLetter/Moon-Letter/server/src/main/java/com/twomemory/app/media/S3ObjectStorage.java:103)。

建议先采用三种资源：列表/相册缩略图约 320–600 px；全屏预览约 1280–2048 px；原件保持字节不变。尺寸要根据屏幕密度与实际槽位选择，上述只是起始区间。缩略图应与原件拥有不同 key/路径/状态，不能把预览写进原件位置，也不能让“保存原图”导出预览。

上传保留原件，优先传可见预览或生成派生图；大媒体用文件流和可恢复上传。服务端允许图片 64 MiB、音频 50 MiB、视频 200 MiB，但现在两端多个路径都把完整文件读进 ByteArray。在 384 MiB Java 堆配置下，两份接近 200 MiB 的视频字节本身合计已约 400 MiB，尚未计算应用其它对象。这是静态资源预算风险，不是本次已复现的 OOM。[大小限制](E:/MoonLetter/Moon-Letter/server/src/main/java/com/twomemory/app/media/MediaService.java:20)、[堆配置](E:/MoonLetter/Moon-Letter/infra/aliyun/compose.private.yaml)

HEIC/HEIF/WebP 上传在业务上允许，而当前 Java ImageIO 缩略图能力取决于可用解码器，未见对应格式插件；不能保证所有上传格式都能生成预览。UI 已有 415 时“点开查看原图”的提示，这属于降级，并没有解决预览速度。应使用真实格式样本验收，或在客户端/OSS 生成通用 JPEG/WebP 预览。日记图片路径也没有相册解码器那样显式的 EXIF 方向修正，需要加入旋转照片样本检查。

可统一使用成熟 Android 图片加载器，也可保留现有实现并补齐内存/磁盘缓存、变体、去重、取消、优先级。推荐依据是当前重复链路和缺失能力，不要求为了换库重写产品。[Android 大图加载指导](https://developer.android.com/topic/performance/graphics/load-bitmap)

OSS 可提供私有对象的限时签名访问，也可把图片处理参数纳入签名生成预览。现有项目是 S3 兼容协议，不能直接假定 S3 签名追加 OSS 图片参数就有效：应通过 OSS 原生签名验证，或存储独立派生图。CDN 放在实际跨省测量之后；若使用，仍需要私有源站和访问鉴权，不能把伴侣照片改成公开读。签名 URL 本身在有效期内是访问凭据，应短期、按资源签发，不进入日志；撤回后的已下载副本不会因此消失。[OSS 私有访问](https://www.alibabacloud.com/help/en/oss/use-a-fixed-file-url-to-access-a-file)、[图片签名参数](https://www.alibabacloud.com/help/en/oss/developer-reference/img-9)

图片优化收益首先来自少传字节。例如同一图片假设原件为 5 MB、预览为 100 KB，列表传输量约降低为 1/50；这是示例，不是对现有照片的实测，也不等于端到端速度一定提升 50 倍。

**地图加载：区分本机成本与网络成本**

地理手账的两份数据分别是省级 582,522 字节/25,240 坐标点，以及市级 3,293,667 字节/140,494 坐标点，共 **3,876,189 字节、165,734 个坐标点**。源码每次该 Composable 新进入组合时，都读取 JSON、投影坐标并创建 Path；市级边界低缩放时不绘制，却仍在首次阶段解析。缩放后又遍历绘制所有市级 region，尚未按视口排除不可见区域。[地理手账实现](E:/MoonLetter/Moon-Letter/android/core/designsystem/src/main/java/com/twomemory/designsystem/CityMap.kt:151)

建议按收益顺序：

1. 进程内缓存不可变的地理模型/路径，避免导航回来重读和投影；限制缓存对象的数量与版本，考虑低内存释放。
2. 先显示简化省级底图和城市点，用户需要区域细节时再加载市级边界。把解析与绘制分开计时。
3. 预生成全国/区域/城市不同精度的数据，在不同缩放层级选择；简化时保留共享边界、孔洞和关键地理形状，做视觉比对。
4. 为 region 建包围盒，视口剔除后再绘制；保留现在已做的文字测量缓存和标签避让。
5. 街道 WebView 中也延迟加载市级 GeoJSON，优先完成首屏；marker 当前一次创建所有城市，后续可按视口/层级管理。

街道图通过 `https://tile.openstreetmap.org/{z}/{x}/{y}.png` 下载，其速度与 ECS API 的响应是两条链路。提高 ECS 带宽不能直接解决街道瓦片慢。当前 `keepBuffer:0`、`updateWhenIdle:true` 减少请求量，但回移时是否命中缓存仍取决于 WebView/HTTP 缓存；不能据此承诺街道已离线缓存。[街道实现](E:/MoonLetter/Moon-Letter/android/core/designsystem/src/main/assets/map/street-map.html:43)

保留地理手账作为默认，街道瓦片源做成可配置，选择目标地区实际可达、响应稳定且服务条款允许的提供方。先做两地移动网络的正常浏览测试，再确定是否更换 SDK/瓦片。OSM 公共瓦片没有 SLA，要求按响应头缓存，并禁止大范围预下载/离线打包；不要为了提速批量预抓全国或城市瓦片。若需要离线街道，使用明确允许该用途的服务或自建数据。[OSM 瓦片政策](https://operations.osmfoundation.org/policies/tiles/)

步骤 5 的城市名逐字断行与 `word-break:break-all`、标签布局宽度有关。修复应让完整城市名稳定显示，再重新验证避让与 48 px 目标，不要为了字排得开把地理坐标移走。本次未测瓦片请求耗时，也未检查某商业地图接入的资质；选供应商时另行核对。

**同步与公网响应：优先改动**

1. **拉取不要被上传挡住。** 当前 `triggerSync` 先扫描并逐个上传未完成媒体，再上传头像，再 push/pull 记录，然后读取姓名、纪念卡、纪念日并调度相册。一个大视频上传或一串超时会增加整个周期耗时，实际间隔是“周期耗时 + 3 秒”，不是每 3 秒必定收到内容。[前台链路](E:/MoonLetter/Moon-Letter/android/app/src/main/java/com/twomemory/app/SyncSession.kt:377)
   建议文字/元数据优先收取；媒体为独立有界队列，发布仍等该记录媒体 READY。保留原有幂等与 FIFO 约束，不能简单把同一记录的所有操作并行发送。前台变化时短间隔，静止时延长；故障采用指数退避和抖动，避免持续请求不可用端点。

2. **减少全量读取与不变数据写回。** 相册每轮先读完整 snapshot，上传后再读一次，即使没有上传也会再读；服务端 `read` 每个相册再查询其照片，形成按相册数量增长的查询。客户端每次 merge 又 upsert 全套元数据。[客户端 snapshot](E:/MoonLetter/Moon-Letter/android/app/src/main/java/com/twomemory/app/AlbumCloudSync.kt:82)、[服务端相册查询](E:/MoonLetter/Moon-Letter/server/src/main/java/com/twomemory/app/album/SharedAlbumService.java:32)
   建议先加版本/ETag，在无变化时不返回全量；再接入相册变更流或稳定游标分页，包含删除 tombstone。列表只取相册摘要/封面，进入相册再分页照片。不能简单过滤 deleted 后让离线端复活旧照片。

3. **网络客户端复用。** SessionApi 每次构造 Retrofit 与 OkHttp，记录传输每轮也创建 transport/client，媒体票据路径同样新建 client；这不利于连接与线程资源复用。SharedAlbumApi、OccasionApi 已有静态 client。建议一个基础 client/连接池派生 API 与媒体策略，按空间获取认证，端点变更时明确失效。先记录 DNS/TCP/TLS/TTFB，验证实际收益。

4. **控制每轮本机计算。** Room 时间线和 blocks 查询无界；导航层重复订阅，`entries.map { blocks.filter(...) }` 产生随记录数×块数增长的扫描，多个投影在组合层创建。给查询加合适索引，按页加载时间线/相册，先把 blocks groupBy 一次，把重投影放到后台。分页保持可以访问最旧记录，不以“只保留前 200 条”代替。[时间线投影](E:/MoonLetter/Moon-Letter/android/app/src/main/java/com/twomemory/app/AppNavigation.kt:646)、[本机查询](E:/MoonLetter/Moon-Letter/android/core/database/src/main/java/com/twomemory/database/EntryDao.kt:35)

5. **低成本服务端改进。** 每个鉴权请求都会查询会话并写 `last_used_at`；可按分钟合并写入，吊销仍应即时有效。媒体云端完整校验可能在数据库事务中等待 OSS，改成校验阶段与短状态提交阶段，并防止并发重复确认。继续保留合适的 PostgreSQL 索引与小连接池，观察慢 SQL 后再调参，避免为了两位用户引入 Redis/消息中间件/微服务。[鉴权写入](E:/MoonLetter/Moon-Letter/server/src/main/java/com/twomemory/app/auth/DeviceSessionAuthenticationFilter.java:80)

6. **后台通知单独解决。** FcmSyncNotificationPublisher 当前是空实现；SyncMessagingService 是唤醒同步的普通封装，manifest 没有真正的推送服务注册。WorkManager 不能提供对方写完后立即唤醒另一台的保证。可先保证前台可靠，再按目标设备和网络选择实际可用的推送渠道，推送只携带变化提示与游标；回到应用仍主动对账。[推送占位](E:/MoonLetter/Moon-Letter/server/src/main/java/com/twomemory/app/sync/FcmSyncNotificationPublisher.java:12)

7. **正式 Release 再做端侧优化。** 当前 app 构建脚本未见 R8 优化与应用自身 Baseline Profile 配置；使用正式签名、非 Debug 的包做 Macrobenchmark，覆盖冷启动、时间线滚动、相册开图、地图手势。不能用模拟器 Debug 启动时间评价正式产品速度。[Android Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview)

**公网前应处理的可靠性与安全问题**

- **Keystore 失败不应静默改存明文。** TokenCipher.obtainKey 捕获任意异常；seal 获取不到 key 就返回 `plain:` + token，代码没有仅限 JVM 测试的隔离条件。正常设备通常会加密，但真实设备出现 Keystore 故障时也会进入明文分支。正式包应拒绝这一降级并给出可恢复的连接错误，测试使用明确注入的替身。这是源码确认的条件性风险，没有声称已在当前设备发生泄露。[TokenCipher](E:/MoonLetter/Moon-Letter/android/app/src/main/java/com/twomemory/app/TokenCipher.kt:26)
- **增加公网入口保护。** 在所审查的源码与代理配置中未见明确应用限流：bootstrap/pair、失败鉴权、上传票据和下载应有针对性的速率/并发限制；限制 JSON 请求体、成员媒体额度、待上传资产数量。保留强 bootstrap/配对凭据、日志脱敏和会话吊销机制。未核验远端已有防护，不能断言 ECS 完全没有限流。
- **签名切换与恢复是上线门槛。** 正式签名与现有 Debug 包不能直接当作同签名覆盖；文档明确异机签名备份未完成。不要卸载清数据解决。先确定继续同测试签名覆盖的私有验收方案与将来的正式签名迁移方案，保护未上传草稿、照片和身份。[签名说明](E:/MoonLetter/Moon-Letter/docs/deployment/android-release-signing.md)
- **备份必须包括照片原件。** PostgreSQL dump 不等于 OSS 原件备份；数据库、对象存储、签名私钥分别验证恢复。只在独立恢复库演练，不在正式库试恢复。Caddy 证书续期与自动备份 timer 需在真实服务器确认。
- **缓存要有容量与清理边界。** 下载原件在 filesDir 内，本轮未见统一磁盘配额/淘汰策略。已下载的远端副本可按容量回收并再次下载；用户导入原件、待上传文件、草稿附件必须受保护。明确删除、撤回、退出空间时缓存的处理，不误删唯一副本。
- **私有不等于端到端加密。** 当前隐私基础是 TLS、鉴权、应用沙箱和私有 OSS，不是服务端无法读取正文的端到端加密。对两人自用可以接受这个边界，但不要在产品中声称已实现端到端加密或本机胶囊的密码学封存。

**建议的实施优先级**

| 优先级 | 工作 | 验收结果 |
|---|---|---|
| 公网前门槛 | 真实 HTTPS/端点身份/备份恢复/签名迁移；Keystore 明文降级；入口限流 | 保留旧数据与待发送队列；无凭据不可读；恢复可验证 |
| 第一批性能改动 | 文字同步与媒体拆队列；图片预览统一；全屏先预览；大媒体流式或直连 OSS | 上传视频时另一端文字仍能到达；开图不等原件 |
| 第二批性能改动 | 持久预览与同资源去重；逐照片 Lazy；地理数据进程缓存、分层/视口裁剪 | 回访不重复开图；照片增多不线性占满内存；地图回访更快 |
| 第三批效率改动 | 相册增量/分页；HTTP 客户端复用；本机查询索引与投影后台化 | 无变化时不传全相册；长期数据仍可滚动检索 |
| 体验收尾 | 配对主动作、同步状态、相册首导入、地图标签、小字颜色、本机功能标记 | 用户知道内容是否保存、发送、共享，界面更容易读 |
| 长期按证据投入 | 推送、CDN、额外规格、应用级性能 Profiles | 仅解决测量已证实的问题，维护成本可控 |

**公网性能验收：建议目标，不是当前成绩**

条件固定：正式 Release 或可比的非 Debug 测试包、真实中端 Android、记录确切网络、是否冷缓存；每项重复至少 20 次，报告中位数和 P95。小样本 P95 只用于初步发现尾部问题，不当作长期 SLA。

| 指标 | 初始目标 | 测量边界 |
|---|---|---|
| 已缓存文字页面有内容 | P95 < 300 ms | 从点击到可见内容，独立测数据量 |
| 已缓存图片出现 | P95 < 200 ms | 从进入可见区域到首帧，不要求原图 |
| 普通小 JSON API | P95 < 500 ms | 有网环境的完整客户端往返，另记录服务端处理时间 |
| 正常网络首屏云图片 | P95 < 1.5 s | 固定缩略图尺寸/字节预算，分离签名请求与下载 |
| 地理手账首次可用 | P95 < 800 ms | 本机资产冷加载到可以选城 |
| 地图回访可用 | P95 < 200 ms | 进程内热缓存；另测杀进程后的冷加载 |
| 双方前台新文字可见 | P95 < 5 s | 从 A 服务器确认到 B 展示，并单独测上传大媒体同时进行 |
| 原件/视频 | 不给固定秒数 | 记录大小、首字节、有效吞吐、失败/恢复能力；受带宽约束 |
| 后台提醒 | 记录实际分布 | 权限开启/关闭、不同厂商电量限制；接推送前不承诺即时 |

场景至少包括：两地不同移动网络；Wi-Fi/移动切换；1 Mbps 限速、200–500 ms 延迟；断网重连；一个月 500/2,000 张照片和 1,000/10,000 条记录的隔离测试数据；头像大图；HEIC/WebP/旋转 JPEG；冷缩略图并发；接近上限的视频；应用/服务器重启。所有压力和异常测试在测试空间/独立库执行。

最小监测建议：客户端测同步周期、待发送队列年龄、预览命中率、DNS/TLS/TTFB/下载/解码/首帧；服务端测按接口的 P50/P95、错误率、慢 SQL、数据库池等待、缩略图生成、JVM heap/GC/OOM、出口流量。只记事件类别、耗时与脱敏标识，不记录正文、令牌或签名 URL。先有这些数据，再决定 CDN、网络地区或规格投入。

**原始截图与对应观察**

步骤 1：首页空状态。风格统一、主入口明确，重复记录入口可收敛。

![01 首页](E:/MoonLetter/Moon-Letter/docs/reviews/2026-10-04-app-audit/screenshots/01-home.png)

步骤 2：个人记录编辑器。多媒体入口齐全，分享含义需就近解释；本轮未填写或发布。

![02 编辑器](E:/MoonLetter/Moon-Letter/docs/reviews/2026-10-04-app-audit/screenshots/02-editor.png)

步骤 3：共同相册照片空状态。首次导入的主按钮不够直接；本轮未导入。

![03 相册](E:/MoonLetter/Moon-Letter/docs/reviews/2026-10-04-app-audit/screenshots/03-album.png)

步骤 4：稳定的地理手账。离线底图正常；加载中的首张截图已被稳定版本替换。

![04 地理手账](E:/MoonLetter/Moon-Letter/docs/reviews/2026-10-04-app-audit/screenshots/04-map.png)

步骤 5：街道图全国视图。城市名有逐字断行；可见底图不代表瓦片链路完成公网性能验收。

![05 街道地图](E:/MoonLetter/Moon-Letter/docs/reviews/2026-10-04-app-audit/screenshots/05-street.png)

步骤 6：我们与待配对状态。应加强配对主动作与连接反馈；地址是模拟器测试配置。

![06 我们](E:/MoonLetter/Moon-Letter/docs/reviews/2026-10-04-app-audit/screenshots/06-us.png)

步骤 7：设置。周末选项在可横滑区域外，发现性弱；底部内容可滚动，不能仅凭截屏认定不可访问。

![07 设置](E:/MoonLetter/Moon-Letter/docs/reviews/2026-10-04-app-audit/screenshots/07-settings.png)

步骤 8：本机时间胶囊。存储边界说明明确，入口应提前标注；本轮没有保存信件。

![08 时间胶囊](E:/MoonLetter/Moon-Letter/docs/reviews/2026-10-04-app-audit/screenshots/08-capsule.png)

附：颜色、地图数据、APK 大小的静态统计见 [audit-stats.json](E:/MoonLetter/Moon-Letter/docs/reviews/2026-10-04-app-audit/audit-stats.json)。以上建议均未直接修改产品源码或部署正式服务。
