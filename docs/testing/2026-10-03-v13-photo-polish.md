# V13 图片浏览体验打磨

范围：继续既有产品验收方向中的照片详情交互。不更换视觉主题、不公开 ECS、不改备案域名与正式签名证书。

## 变化

- 独立相册、时间轴记录详情的全屏图片浏览统一使用 ZoomablePhoto。
- 支持双指缩放、双击放大/还原、放大后的拖动；缩放范围 1–5 倍。
- 切换照片会重置前一张的缩放状态，图片裁剪在浏览区域内，不带动操作按钮。
- 增加无障碍放大/还原操作和简短手势提示。
- 相册创建、分类、删除确认、封面、保存到手机链路保持现有实现。

## 验证

- 先运行 PhotoTransformTest，最小未实现版本 4 项中 2 项失败；实现后 4 项通过。
- 首次完整应用回归受到沙箱无法读取 Robolectric 运行库/访问 Docker 的限制，13 个测试类初始化失败；使用正确权限重跑。
- 最终 :app:testDebugUnitTest 69 项、:core:designsystem:testDebugUnitTest 7 项全部通过，无失败/跳过。
- :app:assembleDebug 与 :app:assembleDebugAndroidTest 构建通过。
- emulator-5556：ZoomablePhotoTest 3 项、IndependentAlbumScreenTest 3 项、IndependentAlbumExportTest 2 项通过。
- 原生测试实际执行双击、双指 pinch、换图重置、相册操作和图片保存；没有用云端配对占用伴侣名额。

## 测试产物

`android/app/build/outputs/apk/debug/MoonLetter-photo-polish-v13-20261003.apk`

版本：`0.1.0+photo-polish-v13-20261003`，versionCode 13。

SHA-256：`870DB1B545D65A37EB6A99801AAF87D9414982FA603D0393FA013FE3B9AB2C4E`。

使用既有 Android Debug 测试签名，可覆盖 V12 测试版；不是替换已交付的正式签名备案包。

未完成项仍包括正式公网 HTTPS、真实伴侣手机云端接收验收，以及产品其他页面的后续打磨。此轮不得称为全部产品重构完成。
