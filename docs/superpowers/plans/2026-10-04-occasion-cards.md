# Occasion Cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement task-by-task.

**Goal:** 用户自行管理重要日子、当日看贺卡、给伴侣发可预览心意卡，并使用可爱的真实录音面板。
**Architecture:** Spring/PostgreSQL空间授权列表；Android scoped缓存、日历规则、每日关闭标记；复用媒体上传与EntryPhoto。旧纪念日接口不破坏。
**Tech Stack:** Kotlin/Compose/Retrofit, Java/Spring/JdbcTemplate/Flyway。
**Spec:** ../specs/2026-10-04-occasion-cards-design.md

## Global Constraints

- 不删除现有情侣数据，不解除配对，无硬编码生日；保持原签名覆盖安装。
- 多条重要日子，公历、本机按日期每日一次；离线不承诺送达/立即撤回。
- 真实照片上传与授权，不公开OSS；惊喜仅伴侣接收。

## Review Focus

- 切换空间/用户：缓存与关闭标记隔离。
- 重复生日/一次性卡：日期与到期规则正确。
- 网络失败：表单保留、无假成功、旧缓存不被清空。
- 同日多卡：单层翻页，编辑中不弹。
- 跨空间照片/惊喜撤回：拒绝引用、撤回后伙伴无新读取权。

### Task 1: Backend occasion list (parallel)

- [ ] 新增V13 occasion表及旧纪念日保留迁移，kind BIRTHDAY/ANNIVERSARY/SURPRISE。
- [ ] GET /api/v1/couple/{coupleId}/occasions; PUT/DELETE /{id}，客户端UUID幂等。
- [ ] 请求 title/body/date/repeatsYearly/recipientId/photoAssetId；返回再加authorId/coupleId/updatedAt。
- [ ] 先RED真实PostgreSQL集成覆盖多条、跨空间、发送权限、删除、照片授权；GREEN及全服务端测试。

### Task 2: Recording panel (parallel)

- [ ] 原生RED验证真实语音入口不是表单、取消不确认；保留开始/停止/试听/重录/确认行为。
- [ ] Compose暖色录音机视觉，真实振幅波形，不显示原始数值；大字体/滚动/48dp目标。
- [ ] 验证录制生命周期与保存链路。

### Task 3: Android occasion experience (parent)

- [ ] 先RED日期/每日关闭/空间缓存测试。
- [ ] OccasionCards.kt负责model/API/cache/date规则；OccasionScreens.kt负责列表/编辑/预览/开屏卡。
- [ ] AppNavigation纪念日入口替换为多条列表；SyncSession周期刷新新列表，旧服务器不支持时不擦缓存。
- [ ] 先原生RED/后GREEN生日设置、当日卡片、关闭后不重复、照片预览和删除确认。

### Task 4: Integration/release

- [ ] 一次独立审查；完整客户端/服务端回归、隔离模拟器手机和平板截图。
- [ ] V25覆盖安装；生成服务器更新归档和可回滚脚本，ECS需用户SSH执行，不能声称已部署。

## 执行记录 2026-10-04

- 后端与 Android 重要日子功能已实现，生日由用户自行设置；录音面板已重排为真实波形声音小卡。
- 独立 Android 审查的四项问题已修复：延迟GET覆盖mutation、账号切换保留错误收件人弹窗、异常200清空缓存、发送中返回丢表单。前两项竞态/账号问题已实际RED→GREEN。
- Maven 115项通过；已运行Android模块204项通过，app90项。真实客户端→Spring/PostgreSQL双端附图、撤回、离线与失败保存通过。
- 最终原生手机18、独立相册12、平板5通过；首次失败及复跑详情见 releases/v25/README.md。
- 服务器更新归档与脚本已生成，APK已签名。手机已覆盖安装，平板安装确认待完成；ECS部署必须由用户SSH执行，未声称云端已上线新接口。
- 保留dirty工作树、原配对/文件/数据，不执行Git提交或清理。
