# v7：原生验证与交付记录

本轮工作按 `docs/design/2026-10-02-corrected-product-boundaries.md` 执行；工作区保留既有未提交改动，未提交、未推送。本记录是模拟器开发证据，不是实体双机验收或生产上线证明。

## APK 身份

- 文件：`android/app/build/outputs/apk/debug/MoonLetter-journal-albums-map-v7-20261002.apk`
- 包名：`com.twomemory.app`
- versionCode：7
- versionName：`0.1.0+journal-albums-map-20261002`
- 字节数：36,652,981
- SHA-256：`EA06FDBB360EB9E4D3AFB794D27E28B6A6692644568F0267C56B662D69748F31`
- 原始 `app-debug.apk` 与交付文件 hash 相同；aapt 确认版本，apksigner v2 签名验证通过；已在模拟器覆盖安装。

## 环境与验证范围

Windows / E 盘 SDK、JDK 21；Android 14 / API 34 Google APIs x86_64，Pixel 6 配置，1080×2400；模拟器 `emulator-5556`。API 37 模拟器遇到输入/图形不稳定，改用 API 34 收集可重复的原生证据，没有将环境失败当产品通过。

构建命令在 `android/`：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :feature:timeline:assembleDebugAndroidTest :feature:editor:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest :core:model:test :core:designsystem:testDebugUnitTest :feature:timeline:testDebugUnitTest :feature:editor:testDebugUnitTest --max-workers=2
```

本轮相关模块 JVM 测试 33 项，0 failures / 0 errors（model 9、designsystem 3、timeline 13、editor 8）。不宣称整个项目、服务端或所有 Android 模块全套测试已通过；database Robolectric 运行曾阻塞在外部 Android runtime 下载，因此相册持久化和迁移改用真实 Android SQLite/Room 原生测试，不以取消的 JVM 运行充当通过。

原生测试覆盖：

- APP 8 项：底栏文字基线一致；实际地图点选切换故事；五项编辑工具；独立分类相册创建无记录副作用；真实文件导入后拼贴/全屏翻页；缺失图片提示；WebView 有效画布、城市桥接及降级图层。
- Timeline 11 项：日期章节、图文与音乐卡片、隐藏同步状态、详情全文/图片浏览/重发入口，以及截图采集。
- Editor 10 项：个人/共同编辑统一、真实媒体回调、地图选城、作者身份、伙伴只读视角；保存后分离附件不删除持久媒体；同一城市重复选择幂等。
- Database 8 项：v1/v2→v3 保留记录、正文、队列、游标、评论；分类/相册重新打开；照片私有副本及源文件消失；异常导入清理与部分成功；相册不写 entries/outbox；EXIF 90°照片显示尺寸和解码方向。

## 视觉证据

`visual-v7/timeline/` 为时间轴封面及正文原生截图，`visual-v7/app/` 为地图、编辑器、独立相册及全屏照片。部分文件在 `visual-qa/` 子目录，为 adb pull 保留的原始结果。数据仅为测试夹具，不注入正式产品或用户数据库。参照 `docs/design/reference/` 对照检查。

首页默认封面新增高分辨率背景，文字独立排版；中文正文/标题嵌入文楷，沿用珊瑚、鼠尾草绿、米色信纸。生成素材及提示词见 `docs/design/cover-v7-asset.md`。

## 审查问题与裁定

只读独立审查发现并修复：

1. 保存后的媒体分离会删除记录仍引用的唯一文件：回归测试修复前失败，修复后仅分离引用，不直接删文件。代价：废弃草稿可能留有孤立文件；后续垃圾回收必须同时检查数据库与草稿引用，不能按当前编辑器猜所有权。
2. 同一城市重复选入导致重复故事：增加选择幂等和历史投影按 entry＋city 去重；保留不同城市的原有多地点记录，不擅自删历史附件。
3. 相册 EXIF 方向被忽略：保留原图字节，修正显示宽高和解码旋转/镜像；原生 90° JPEG 测试先失败后通过。
4. 部分瓦片成功后全局降级底图消失：地理底图始终在独立底层，瓦片在上层；失败有提示，不用透明全局省界来隐藏失败区域。独立底层断言修复前失败。

地图零高度另有 RED→GREEN：原生诊断发现 CSS 视口高度为 0，JS 内部画布亦为 0；修复使用 WebView 实际 innerHeight 明确布局并重算地图。复测画布 412×373 CSS 像素，图钉/省界可见，选城回调过滤正确。

审查未判断的实体设备键盘/大字体、真实公网瓦片请求头/缓存和国内服务授权，不等于通过。审查的小项“底部导航缺少读屏选中态”留待后续无障碍优化。

## 当前限制与下一步

- 相册为本机独立分类存储，尚未接云盘，不写日记上传队列；当前不能跨设备获取相册。
- 默认离线地图是真实省界/海岸线及主要城市坐标，不包含街道、地形或 POI。联网街道模式在本模拟器没有成功加载外部瓦片（诊断 tiles=0），仅验证了本地底图、有效画布、降级和桥接；不得宣称大陆街道服务已验收。正式版本需另行确认国内地图供应商及授权。
- 底图来源、Leaflet 许可和上线限制见 `android/core/designsystem/src/main/assets/map/ATTRIBUTION.md`。
- 旧的记录媒体投影相册源码仍保留供历史测试，但主导航只进入独立相册；地图旧的“保存城市快照”写入回调已从主导航移除。
- 未购买或部署 ECS/OSS，未上传私人照片；旧开发服务地址不等于云端上线。双机同步、备份恢复、系统照片选择器完整真机链路仍需实体设备验收。
- 安装时覆盖旧版，不先卸载/清数据；若用户已选自定义首页封面，继续保留该封面，默认新图只用于没有自定义封面的情况。
