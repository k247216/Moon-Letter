# Recording and Keepsakes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 让回忆容易写、容易继续看、不会丢草稿，并成为可离线保存的纪念册。

**Architecture:** 复用现有 DraftStore 序列化，以独立槽位保存历史草稿；阅读状态用按空间隔离的本机 ID 集合。纪念册独立构建器读取 Room 已发布内容与原媒体，生成离线 ZIP；现有同步接口不改变。

**Tech Stack:** Kotlin, Compose, SharedPreferences, Room, ZipOutputStream。

**Spec:** `docs/superpowers/specs/2026-10-04-recording-and-keepsakes-design.md`

## Global Constraints
- 不卸载、不清配对、记录或原始媒体；不重置数据库。
- 草稿本轮明确仅本机、暂不分享，不声称云端私密记录已完成。
- 阅读状态仅本机、按账号/服务空间隔离，不是对方可见的已读回执。
- 只包含当前本机可读取的已发布记录，不混入草稿或锁定时间胶囊。
- 不提交/推送包含先前工作的脏工作树。

## Review Focus
- 不同空间/账号切换：不共享草稿与已读状态。
- 新建或恢复草稿：不能覆盖另一篇；发布失败和强退不能丢内容。
- 对方补写旧日期：仍应标为新回忆。
- 媒体缺失或下载失败：导出不能声称完整；原始音视频不转换损失。
- 不可信正文和音乐链接：HTML不能执行注入脚本或泄露凭据。

### Task 1: 阅读状态与草稿持久化
**Files:** new `ReadingProgressStore.kt`, `DraftLibrary.kt`, tests `RecordingPersistenceTest.kt`; existing `DraftStore` serializer stays compatible.
**Interfaces:** ReadingProgressStore(context, scope).readIds()/markRead(id); DraftLibrary(context, scope).list()/save(id, mode, draft)/load(id)/remove(id).
- [x] 写测试：重建持久化、两账号隔离、多草稿互不覆盖、照片/语音/发生时间恢复、删除不删原文件。
- [x] 跑 `:app:testDebugUnitTest --tests '*RecordingPersistenceTest'`，观察缺少新接口导致红灯。
- [x] 实现两个持久化单元，复用 DraftStore。
- [x] 跑同测试直到通过，保留红绿日志。

### Task 2: 首页、草稿箱、编辑自动保存
**Files:** AppNavigation.kt, DraftLibraryScreen.kt, TimelineScreen.kt, TimelineViewModel.kt, CityMapMemoryScreen.kt.
**Interfaces:** consumes Task1; TimelineEntryUi.unread Boolean; TimelineScreen unread IDs / quick create / draft actions.
- [x] UI 测试：新回忆可打开并消失，快速记录不覆盖旧草稿，草稿箱点击继续和删除，只有已发布对方记录标新。
- [x] 新建与恢复编辑分离，自动保存加去抖；成功发布清对应 ID，不因退出空编辑删除其他草稿。
- [x] 去掉地图累计数字，保留城市查找信息。
- [x] 原生手机/平板尺寸测试通过。

### Task 3: 离线纪念册
**Files:** MemoryBookExporter.kt, tests MemoryBookExporterTest.kt; RelationshipToolsScreen.kt, AppNavigation.kt export branch.
**Interfaces:** exporter builds ZIP file plus record/media counts and incomplete media report; UI allows share/save through FileProvider/SAF, names this a 本机回忆纪念册.
- [x] 测试 ZIP 实际内容、图片/音视频原字节、日期与回应、HTML转义、私密草稿排除、缺失媒体明确失败。
- [x] 实现手账 HTML、日期章节、自然比例图片、audio/video controls、离线说明和原媒体打包。
- [x] 接入纪念册导出，保留原备份出口。
- [x] 完整客户端回归、实际生成纪念册截图检查、签名 APK。

## Progress / rulings
- User explicitly requested direct execution; no repeated design checkpoints. Work in current feature branch containing previous approved, uncommitted product work. Do not commit unrelated work.
- PDF打印版与云端私密发布 ACL 是后续阶段，不伪装本轮完成。
- Pre-flight: Task2 consumes ReadingProgressStore and DraftLibrary exactly as above; Task3 only shares export UI, not draft storage.
- Fresh review: removed duplicate global autosave; scoped external-share draft storage; immutable publication snapshot and session generation prevent switching/editing races; only delete submitted unchanged draft. Retain pending export basename across recreation and filter credential-bearing music URLs.
- Real RED: `editor-v23-race-red.log` (2 race assertions), `recording-v23-account-red-native.log` (global private copy), `recording-v23-review-red-book.log` (music credentials), `recording-v23-server-red.log` (3 actual PostgreSQL permission failures). Initial missing API compile failures were not behavioral test evidence.
- GREEN: `recording-v23-review-green.log`, `recording-v23-final-client.log` (app80/sync19/editor20/timeline23, zero failures), `recording-v23-native-final.log` (4), `recording-v23-tablet-regression.log` (19), `recording-v23-server-package.log` (100). Android offline book rendered with bundled Wenkai font and natural photo; screenshot visually inspected.
- Backend audit worker hit a usage limit; main agent implemented and tested the permission fixes directly. No cloud-private publishing UI claimed: backend now protects personal drafts through empty changes, export, and media reads. Partner raw/thumbnail access requires a live readable entry, album or avatar reference; uploader remains allowed. Updated media-chain fixture publishes a real record before partner download rather than relaxing protection.
- Deliverables: signed V23 APK and backup/rollback server JAR update archive. ECS deployment not performed from this workspace; existing domain/HTTPS constraints unchanged.
