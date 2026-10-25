# Private Keepsakes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or superpowers:subagent-driven-development. Steps use checkbox syntax.

**Goal:** 修复视频并交付云端胶囊、真正的私密记录与卡片。
**Architecture:** 保留现有同步体系与 scoped cache。记录可见性由服务端过滤/撤回控制；胶囊使用独立快照 API，卡片扩展可见性；Android 继续鉴权下载原媒体。
**Tech Stack:** Kotlin Compose/Room/OkHttp；Java Spring/JdbcTemplate/Flyway/PostgreSQL。
**Spec:** docs/superpowers/specs/2026-10-10-private-keepsakes.md

## Global Constraints
- 不清数据、不改签名、不开放 OSS；公开=两人共享。
- 隐藏内容不得通过详情、媒体、评论、变更流、导出泄露。
- 服务端时钟控制胶囊；缓存按 origin/space/user 隔离。

## Review Focus
- 共享变私密：对方已有投影必须撤回。
- 正文不漏进提前同步、通知及导出。
- 隐私修改不能被 partner 或旧客户端静默改回共享。
- 下载失败、解码失败和加载状态必须区别。
- 离线保存/改账号时不得污染已确认缓存。

## Task 1: 视频播放（独立客户端）
- [ ] 复用已经失败的 VideoRecordTest 两项测试。
- [ ] VideoMemoryCard + EntryDetailScreen 视频分支；修正 EntryPhotos 导入后缀。
- [ ] 补下载重试与缓存测试，运行 app 视频回归及 compile。

## Task 2: 服务端私密记录与卡片
- [ ] V14 记录/occasion visibility=SHARED|PRIVATE，默认保留旧共享行为。
- [ ] 写真实 PostgreSQL 两身份测试：私密详情/媒体/评论/feed/export 阻断，作者恢复，共享变私密撤回，改权限只允许作者。
- [ ] 扩展协议，过滤旧 feed 正文，保留 cursor 前进语义；迁移记录共享状态到客户端。
- [ ] 卡片 save/list/edit/delete/media 同样授权，重复年份仍可修改。

## Task 3: 云端胶囊
- [ ] V15 capsule_letter 独立表，/api/v1/couple/{couple}/capsules，DTO 与 shared/private/date。
- [ ] 测试跨空间、提前正文 redaction、解锁边界、作者权限和删除。
- [ ] 以服务器北京时间日期边界为准；API 不向对方提前发送正文。

## Task 4: Android 接通
- [ ] 网络 DTO+私密字段；Room 迁移和作者记录恢复；编辑器/详情公开设置。
- [ ] Occasion 卡片存储/UI/提醒接入 visibility，不把私人卡片当未送达。
- [ ] CapsuleApi + scoped store + Screen 替换 SharedPreferences 单条胶囊，后台 refresh/reminder；失败保持本机草稿。
- [ ] 隔离数据和 UI 行为测试。

## Task 5: 整体验证与发布
- [ ] 全 server 和 Android suite、新版本 APK / JAR 同签名；验签/哈希。
- [ ] 生成保留 .env/database/OSS 的更新工具与双机验收清单。
- [ ] 独立权限审查；明确未执行真实设备/远端验收的边界。
