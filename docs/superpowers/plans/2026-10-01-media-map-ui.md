# Media and City UI Follow-up Plan

> **For agentic workers:** execute this plan task-by-task. This slice is Android Compose only; server/media/location SDK work stays on the SDK machine.

**Goal:** 将相册与城市地图从占位说明页改成真实 Room 派生的可用浏览页，严格保持指导图层级，并能回到原记录详情。

**Boundary:** 不新增 Room 表、不调用实时定位、不伪造地图瓦片、不伪造媒体。相册消费 `entry_blocks` 的 IMAGE/VIDEO；地图消费 LOCATION block 的城市字段。服务端视频/音频与一次位置快照接口由另一台机器提供后，再补写入和同步链路。

## Task 1: Replace album/map placeholders with data-driven screens

- `FutureFeatureScreens.kt` 提供 `AlbumMediaUi`、`CityStoryUi` 和可测试的空态/有数据态。
- 相册按月份分组，图片使用现有 `EntryPhoto`，视频显式标记并回到原记录；不复制媒体。
- 地图保留城市级视觉区域和城市故事列表；地图不可用时列表仍可读，不展示实时轨迹。
- 空态明确说明如何产生真实内容，不填演示记录。

## Task 2: Derive UI state at the app boundary

- `AppNavigation.kt` 收集 Room 的 entries/blocks Flow，按 `PUBLISHED` 过滤相册和城市故事。
- 通过 entry 的发生时间、作者和原 block id 构建月份、日期、时间、来源记录；点击任意媒体/城市故事打开同一详情页。
- 坚持本机边界：不让 Composable 查询 Room，不让 UI 读取或申请实时位置。

## Task 3: Add focused Compose evidence and handoff

- `FutureFeatureScreensTest.kt` 覆盖月份相册和城市故事正文可见性。
- 本机只做 `git diff --check` 和源码检查；SDK 机器运行测试并在真实 Room 数据上截图。
- 服务端补齐 VIDEO/AUDIO/LOCATION 后，再把 editor toolbar 和发布门禁接到这些 block；本 slice 不把“页面存在”写成“功能完成”。
