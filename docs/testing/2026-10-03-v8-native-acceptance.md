# v8：密度、地图、相册保存和身份初始化

本轮执行 `docs/superpowers/plans/2026-10-02-v8-polish.md`，对应用户 v7 的五项验收反馈。保留既有未提交工作区；没有提交、推送、部署云服务或上传私人照片。模拟器证据不是实体双机验收。

## APK 身份（已校验）

- 文件：`android/app/build/outputs/apk/debug/MoonLetter-polished-v8-20261003.apk`
- 包名：`com.twomemory.app`，versionCode=8
- versionName：`0.1.0+polished-journal-city-profile-v8-20261003`
- 字节数：37,371,420
- SHA-256：`AA9C6664A4DC0726176D0E02892C61F0DEDAD5E1D36691699FED0E92AE47BCD7`
- 交付文件与原始 `app-debug.apk` hash 一致；aapt 确认版本；apksigner v2 验证通过。
- 本轮跨过午夜，临时 `MoonLetter-polished-v8-20261002.apk` 也已替换为同一最终构建，避免误取过渡版本；正式交付链接统一使用 20261003 文件。v7 原包保留不变。
- 覆盖安装，不先卸载或清除数据。用户自定义首页封面继续保留。

## 产品变更

1. 首页默认封面从约 43% 收至约 32%（普通字体 210–285dp），收紧日期、作者、照片和记录间距；保留文楷、信纸、缝线与拍立得。大字体单独放宽封面高度，底部说明和换图使用受约束的同一行。
2. 底栏普通图标 27→23dp，中心按钮 50→44dp；五项仍同一基线，60dp 点击区域，读屏有选中态。
3. 独立分类相册的全屏照片提供“保存到手机”。API 29+ 新建 pending MediaStore 图片，复制原字节、关闭输出流后发布；失败/取消仅清理本次目标。API 26–28 使用 CREATE_DOCUMENT，不请求宽泛存储权限。保存不改原图、不创建记录或 outbox。
4. 地图标题融入地理区域；样式切换是次级小控件，默认不常驻在线/离线大提示。离线/街道共用 371 个城市选择；新增真实地级城市、自治州等边界，城市搜索、近邻地图点击、同城重新定位。地理点位不偏移，实际文字尺寸和控件矩形参与避让，近邻城市通过真实区域缩放分开。
5. “我们”页常用纪念日、胶囊、回看与导出优先；主题、通知和配对移入真实设置。首次设置先填头像和名称，再连接空间；不按成员顺序分配女性/男性头像。默认使用称呼首字，照片存私有副本并校正 EXIF；已获服务端确认的初始名称立即缓存。
6. 补齐 Material3 容器配色，消除设置/选项中混入的默认紫色。

## 原生验收

环境：Windows、E 盘 SDK/JDK 21；emulator-5556 / Android 14 API 34 / Pixel 6 / 1080×2400。最终 v8 覆盖安装后，**79 项原生测试通过，0 失败**：app 37、timeline 14、couple 10、editor 10、database 8。完整运行输出保存在 `docs/testing/visual-v8/results/v8-native-*-final.log`；不将未在 API 34 执行的 API 26–28 专属 document 测试计入通过数。

已经实际观察的 RED→GREEN：普通封面密度/默认头像、导航选中态、次级底图菜单与搜索；相册 MediaStore/CREATE_DOCUMENT 与 UI 保存入口；头像 EXIF 90°与名称首进缓存；手账容器色。只读审查的四项 Important 已各增加先行复现：同城重新定位、长标签矩形重叠、街道来源安全区、大字体封面按钮重叠。

地图复测曾发现矩形“最坏尺寸”预估使上海图钉无法首次显示，改用真实 TextMeasurer，并调整区域视野；新增等待真实地图加载，避免把异步加载当作同城定位失败。测试断言保留，不以放宽断言隐藏问题。

## JVM 项目级结果

执行 `gradlew test --continue --offline --max-workers=2`，显式使用已缓存 Android 14 Robolectric runtime。最近完整结果为 **136 项，126 通过，10 受环境/真实服务门禁阻塞（测试运行报告为 failures）**，不宣称全套通过。

7 项缺少 Android 9 测试运行时 `android-all-instrumented-9-robolectric-4913185-2-i7.jar`：

- `AlbumMigrationTest`（2）
- `AlbumRepositoryTest`（5）

相应真实 Room 迁移/相册持久化另在 API 34 instrumentation 验证，但不能因此把上面失败改为通过。

3 个真实服务门禁未注入 `TEST_DB_PASSWORD`，安全前置检查拒绝运行：

- `ReinstalledPhoneRebuildsWholeBookTest`
- `TwoDevicePictureAndCommentLoopTest`
- `TwoDeviceRecordingLoopTest`

未读取/打印凭据或绕过门禁。`InitialProfileAvatarTest` 改用 Robolectric NATIVE 图形，避免 LEGACY 解码器将空文件伪装成图片；其 3 项现已实际通过。其他通过模块包括模型、网络、同步、通知、设计系统、编辑器、时间轴与情侣工具。

## 审查裁定与保留项

- 四项 Important 进入同一轮定向修复；以原生回归确认，不把审查“方向正确”当作修复证明。
- Minor 暂缓：旧自有头像副本安全清理；纯白主题与所有实际 FilterChip 颜色的全面像素覆盖。不能按当前引用猜旧文件所有权，也不宣称所有控件已做完整视觉验收。
- 云盘同步按用户意愿后续推进；本轮只有本机独立相册与手机保存。
- DataV 公开接口不等于商用再分发许可或地图发布审批。来源/363 features/371 选择及限制见 assets/map/ATTRIBUTION.md；城市近邻是 120km 中心距离，不是行政边界包含判断。
- 街道模式外部瓦片仍未在此模拟器加载成功（tiles=0）；本地地理降级、371 城市桥接、画布和署名可验证，不代表大陆详细地图服务验收。正式国内供应商/许可后续沟通。
- v7 及其他未提交服务端变化不在本轮审查结论内；没有重跑服务端全套。实体双机同步、真机系统相册选择器、API 26–28 document 完整保存仍待实体/对应系统验收。
- 头像目前本机私有保存，不宣称伴侣设备可收到自定义头像。

## 视觉证据

`docs/testing/visual-v8/before/visual-qa/` 是本轮重新采集的 v7 原生截图；`after/visual-qa/` 已从最终 v8 测试设备重新提取。参照 `docs/design/reference/` 核对封面比例、标题、图文疏密、城市故事和个人页，并实际查看最终首页、杭州地图、街道降级、设置及照片保存页面。截图内容是测试夹具，不注入用户数据；硬件截图用于 WebView，Compose 截图用于原生页面。
