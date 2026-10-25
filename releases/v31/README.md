<!-- Deployment/person/device identifiers in this public document are anonymized. -->
# V31 · 对方的小狗月笺

- 新默认首页封面和首次连接页封面：奶油色纸感、手绘卡通小狗。默认图片仍可更换；已有自选背景不会被覆盖。
- 你选择“创建手账”时显示用户指定原文：`记录想对方的每一天`；对象默认“接受邀请”时显示双人文案：`记录彼此想念的每一天`。
- 已连接的共享页面同样使用“记录彼此想念的每一天”，不把两个人都写成对方。
- 通用界面使用“我／对方”，作者、头像旁和导出优先显示各自真实设置的名字，未填写时才用中性称呼。
- 入口显示“创建手账／接受邀请”；仍走既有创建者／第二成员链路，不改权限或云端用户 ID。
- 实际已填写的个人称呼仍被保留，不强改云端资料。
- 浅色默认封面使用深色字；用户自行选择照片时保留白字和遮罩，避免破坏既有照片对比度。
- versionCode 31；versionName `0.1.0+puppy-letter-v31-20261009`。

服务器无需更新。此包不会自动清空数据。此前授权的全新开始继续使用 V30 的 `FRESH-START.md` 和同一份清理工具；安装步骤改为本版 APK，“我是第一位”入口现在是“创建手账”，“我是伴侣”现在是“接受邀请”。已创建正式空间后不要再次清理手机。

## 图片资产

使用内置图片生成工具新生成并复制至项目，不是临时网络图片：

`android/core/designsystem/src/main/res/drawable-nodpi/moonletter_cover_puppy.png`

原夜景资源保留供历史视觉测试使用，但产品默认入口不再引用它。

生成提示：

> Use case: illustration-story. Asset type: production default cover illustration for a warm beige Chinese private couple journal Android app. Generate ONE finished raster illustration, landscape 3:2 composition. Primary request: an adorable cartoon puppy illustration replacing a city night photo. A small fluffy cream-and-caramel floppy-eared puppy, cheerful and affectionate, lying down with front paws together, full dog clearly visible. Style: gentle hand-drawn picture-book gouache and colored pencil on soft ivory scrapbook paper, subtle organic paper texture, muted coral blush and soft sage accents, delicate warm brown outlines, tasteful simple handmade look, not glossy 3D, not photorealistic. Wide calm negative space on the LEFT and center-left for app overlay text; puppy placed in RIGHT third because the existing homepage copy is top-left. Puppy occupies modest scale and remains visible when the banner crops slightly at top and bottom, centered vertically. Background continuous pale cream with very restrained hand-drawn curved doodles and tiny heart accents, never busy or dense. No cityscape, no night scenery, no other characters, no text, no letters, no watermark, no logos, no frame. Intended warm, cute, personal, quiet, polished journal cover, not promotional art.
