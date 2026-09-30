# 「月笺」品牌与启动图标基准

日期：2026-09-30
状态：已批准

![月笺 App 图标预览](./moonjian-app-icon.png)

## 名称

- 对外中文名称：「月笺」。
- 英文工程代号：`Moon Letter`，不作中文启动器名称。
- 图标概念名：「月印封笺」，只用于设计沟通，不替代 App 名称。

## 权威图形

权威可编辑源文件为 [`moonjian-app-icon.svg`](./moonjian-app-icon.svg)。图形必须保留：

- 珊瑚色闭合日记本封面，作为主色和主轮廓。
- 鼠尾草绿窄书脊，表达双重身份色，不做封面五五分割。
- 封面中上部的月金色月牙。
- 右侧和底部的浅米色纸页，以及一枚月金色书签。
- 扁平、圆润的卡通插画感；不使用写实布料、3D 材质或金属反光。

禁止加入姓名、字母、爱心、人物、花朵、星星或文字。图标不使用任何 AI 生成的写实版本。

## 色彩

| 角色 | 色值 | 用法 |
|---|---|---|
| 暖米背景 | `#F7EFE2` | 自适应图标背景层 |
| 珊瑚主色 | `#C98273` | 日记本封面 |
| 鼠尾草绿 | `#818D78` | 日记本书脊 |
| 月金色 | `#D7B369` | 月牙与书签 |
| 纸页色 | `#EEE1CD` | 右侧与底部纸页 |
| 绑定暗绿 | `#687361` | 书脊内部绑定线 |

## Android 尺寸与分层

- 前景层和背景层画布均为 `108 × 108dp`。
- 日记本主体放入中央 `66 × 66dp` 安全区，不依赖外侧 18dp 区域表达核心语义。
- 启动器中通常以约 `48 × 48dp` 的视觉尺寸排列，不同密度使用不同像素资源，但逻辑尺寸保持接近。
- Android 工程使用两层自适应图标：
  - `res/drawable/ic_launcher_background.xml`
  - `res/drawable/ic_launcher_foreground.xml`
  - `res/mipmap-anydpi-v26/ic_launcher.xml`
  - `res/mipmap-anydpi-v26/ic_launcher_round.xml`

尺寸依据：[Android Developers — Adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)。

## 交付边界

- SVG 和 Android VectorDrawable 是当前权威资产，保证图形可编辑、可稳定缩放；`moonjian-app-icon.png` 是由 SVG 机械导出的 512 × 512 预览图。
- 此次品牌落库只修改名称与启动图标，不改动业务页面和服务端逻辑。
