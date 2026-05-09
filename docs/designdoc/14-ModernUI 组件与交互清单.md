# ModernUI 组件与交互清单

> **目的**：作为 Vanilla V2 / Web 等其他 UI 实现的"对齐规格说明书"。记录当前 ModernUI 实现的**全部** UI 元素、交互行为、布局尺寸、配色 token，让其它消费者按照本文档复刻时不会出现"看到啥抄啥、漏一堆细节"的问题。
>
> **使用方式**：实装 Vanilla V2 / Web 等 UI 时，**先**对照本文档列清单 → **再**对应到当前实现 → 缺失的逐条补齐。后续 ModernUI 有改动时，请同步更新本文。
>
> **当前 V2 状态**：基础框架已就位（Overview / Storage / Power 三个 Panel + 几个基本组件），但缺失大量交互（Dialog、右键菜单、Donut、进度条精细分段、收藏按钮、KPI 详情弹窗等）。详见每章末尾的 *V2 vs ModernUI 差异表*。
>
> 来源：扫描 `src/main/java/com/yuyinrl/resourceobserver/client/modernui/` 下全部源码（2026-05-09）。
>
> ---

## 目录

- [1. 根容器（ResourceTerminalFragment）](#1-根容器resourceterminalfragment)
- [2. 主题 token（ModernUiTheme + UiThemeTokens）](#2-主题-tokenmodernuitheme--uithemetokens)
- [3. 通用 widget 清单](#3-通用-widget-清单)
- [4. 全局交互模式](#4-全局交互模式)
- [5. 国际化与文本](#5-国际化与文本)
- [6. Overview Page](#6-overview-page)
- [7. Storage Network Page](#7-storage-network-page)
- [8. Crafting 子 Tab](#8-crafting-子-tab)
- [9. Power Network Page](#9-power-network-page)
- [10. V2 对齐总览（缺失项汇总）](#10-v2-对齐总览缺失项汇总)
- [11. 实装路线建议](#11-实装路线建议)

---

## 1. 根容器（ResourceTerminalFragment）

### 1.1 尺寸计算

参考：`ResourceTerminalFragment.java:72-77, 522`

- 面板宽度 = `屏幕宽度 × PANEL_SCALE (0.90f)`
- 面板高度 = `屏幕高度 × PANEL_SCALE (0.90f)`
- 居中放置于 stage，外边距 10dp
- **UI 缩放档位**：3 档（S / M / L），点击 Chrome 上的缩放按钮循环切换。
  - `textScale`：1.0f / **1.2f**(默认) / 1.45f
  - `layoutScale`：1.0f / **1.2f**(默认) / 1.45f
  - 切换时整个 UI **rebuild**

### 1.2 Tab 切换（Chrome 顶栏）

参考：`ResourceTerminalFragment.java:534-543, 692-712, 756-773`

- **Tab 数量**：3 个主 Tab（Overview / Storage Network / Power Network） + 可选 Dev Tab（仅 `DevMode.isDev()`）
- **位置**：顶部水平排列
- **高度**：`chromeH - dp(4)` ≈ 24dp
- **Tab 间距**：左 4dp（`leftGap(dp(4))`）
- **样式**：胶囊圆角（`radius=1000dp`） + 波纹反馈
  - 字号：`10sp × textScale`
  - Padding：`12dp × 3dp`
  - 激活：文本 `CYAN`，背景 `TAB_ACTIVE` (#1F4E82)
  - 未激活：文本 `TEXT`，背景 `TAB_INACTIVE` (#16253C)
  - Hover：缩放 1.03（`addHoverScaleEffect`）
  - Press：缩小 0.95（`addPressScaleEffect`）

### 1.3 Chrome 顶栏其它按钮

参考：`ResourceTerminalFragment.java:280-304, 717, 721`

| 按钮 | 尺寸 | 功能 |
| --- | --- | --- |
| 🌐 Web | `22dp × 22dp` 圆形 | 触发 `requestWebToken(false)`，弹 `WebAccessDialog` |
| S/M/L | `22dp × 22dp` 圆形 | 循环切换 UI 缩放档位，触发 rebuild |
| 关闭 ✕ | `22dp × 22dp` 圆形 | `ROSE` 色 + 红色波纹，关闭整个面板 |

### 1.4 内容容器

- **ScrollView**：`ResourceTerminalFragment.java:590-593, 660-672`，重建后通过 `OnPreDrawListener` 恢复滚动位置，避免可见闪烁
- **底部渐隐遮罩**：`DialogChrome.addScrollFadeOverlays`，14dp 高梯度（PANEL_BG → 透明）
- **dialogOverlay**：与 contentContainer 同级 FrameLayout，承载所有弹窗，不受重建影响

### 1.5 没有的东西

- ❌ **底部状态栏 / footer**（代码中未发现）—— 所有交互都在顶栏 + 内容区

---

## 2. 主题 token（ModernUiTheme + UiThemeTokens）

### 2.1 颜色 token（UiThemeTokens.java）

| Token | ARGB 值 | 用途 |
| --- | --- | --- |
| `PANEL_BG` | `0xE00A1020` | 主面板背景（深蓝 + 半透明） |
| `PANEL_BORDER` | `0xFF2A3C62` | 主面板边框 |
| `SECTION_BG` | `0xCC101A30` | 内容区段背景 |
| `SECTION_BORDER` | `0xFF1E2A44` | 内容区段边框 |
| `HEADER_BG` | `0xFF111A2E` | 页眉/列头背景 |
| `CARD_BG` | `0xCC0F172A` | 卡片背景 |
| `CARD_BORDER` | `0xFF1F2A42` | 卡片边框 |
| `TITLE` | `0xFFFFFFFF` | 标题文本（白色） |
| `TEXT` | `0xFFCFD8E8` | 正文文本（浅灰蓝） |
| `TEXT_MUTED` | `0xFF7B8AA8` | 次要文本（暗灰蓝） |
| `DIVIDER` | `0xFF22314F` | 分割线 / 普通按钮边框 |
| `CYAN` | `0xFF22D3EE` | 生产 / 正面 / 激活 |
| `EMERALD` | `0xFF34D399` | 盈余 / 库存 / 正增长 |
| `AMBER` | `0xFFF59E0B` | 警告 / 不稳定 |
| `ROSE` | `0xFFFB7185` | 消耗 / 亏损 / 危险 |
| `BLUE` | `0xFF60A5FA` | 信息 / 链接 |
| `TAB_ACTIVE` | `0xFF1F4E82` | 激活标签页 |
| `TAB_INACTIVE` | `0xFF16253C` | 未激活标签页 |

**Status 映射**（`ModernUiTheme.statusColor`）：
- `POSITIVE` → `EMERALD`
- `WARNING` → `AMBER`
- `NEGATIVE` → `ROSE`

### 2.2 对话框尺寸常量（ModernUiTheme，经 layoutScale 缩放）

| 常量 | 默认值 | 用途 |
| --- | --- | --- |
| `DIALOG_SMALL_W` | 200dp | 输入框 / 简短确认 |
| `DIALOG_PROGRESS_W` | 240dp | 进度提示（不可关闭） |
| `DIALOG_KPI_SMALL_W/H` | 360 × 280dp | 紧凑 KPI 详情 |
| `DIALOG_KPI_W/H` | 420 × 360dp | 完整 KPI 详情 / 存储详情 |
| `DIALOG_REVIEW_W` | 560dp | 合成审核（高度 wrap） |
| `DIALOG_XL_W/H` | 720 × 560dp | 合成树详情 |

### 2.3 圆角规则

| 场景 | 半径 | 工厂方法 |
| --- | --- | --- |
| 面板外框 | 10dp | `panelBackground()` |
| 卡片 | 8dp | `cardBackground()` |
| 区段 | 6dp | `sectionBackground()` |
| 表格行 | 4dp | `rowBackgroundStateful()` |
| 胶囊按钮 / Tab | 1000dp（完全圆） | `tabBackground()` / `chromeCircleBg()` |

### 2.4 边框规则

所有边框统一 1dp 宽，颜色见 `2.1` 表（PANEL_BORDER / CARD_BORDER / SECTION_BORDER / DIVIDER）。**无独立阴影定义**，由半透明颜色（alpha 通道）模拟深度。

### 2.5 文本大小（sp，统一乘 textScale）

| 场景 | 字号 |
| --- | --- |
| 大标题 | 12 ~ 14sp |
| 中标题 / 卡片数值 | 10 ~ 11sp |
| 正文 | 9sp |
| 辅文 / 标签 | 8sp |
| 小字 / Tier Chip | 7 ~ 7.5sp |

---

## 3. 通用 widget 清单

> 所有 widget 文件位于 `src/main/java/com/yuyinrl/resourceobserver/client/modernui/`。

### 3.1 ChartView（折线图）

- **文件**：`ChartView.java:1-250+`
- **数据**：`List<FlowPoint>`（多系列），按 `ChartPage` / `LineMode` / `SmoothingMode` 切换
- **绘制参数**：
  - 曲线：Hermite 平滑，`CURVE_SUBDIVISION = 1.0f`
  - 线宽 2.0f；零轴 1.0f；网格 1.0f
  - 网格：水平 8px 间隔（`#223E5C84`），竖直按宽度 10 等分（`#152F4668`）
  - 零轴：`#BBD5F3`，alpha 0.50
- **Tooltip**（悬停时同时展示同时间槽全部系列）：
  - 背景 `#E6101C2E`，边框 `#FF3A5478`，圆角 6dp
  - Padding 8dp，字号 11sp，行高 15px，色块 8 × 8px
  - hover 圆点 4px 半径
  - **吸附距离**：60px（`HOVER_SNAP_DISTANCE`）

### 3.2 DonutChartView（甜甜圈图）

- **文件**：`DonutChartView.java:1-349`
- **几何**：
  - 内圈占外圈 62%（`INNER_RATIO = 0.62f`）
  - 段间隙 1.2°（`SEG_GAP_DEG`）
  - 底色环 `#FF0A1020`，中心圆孔 `#FF0D1526`，余量环 `#FF162236`
- **中心文字**：两行（如 "85.0%" + "Headroom"），可配色
- **Hover 交互**（`onHoverEvent`、`hitTest`）：
  - 悬停段 +3px 外扩，alpha 255
  - 其他段 alpha 140（暗化）
- **Tooltip**：
  - 背景 `#E0101828`，边框 `#40FFFFFF`，圆角 4dp
  - Padding 6dp，行高 16px，色块 8px
  - **边界自适应**（`drawTooltip`）：超出右边界则左翻，超出上下边界则贴边
- **数据契约**：`Segment(label, value, color)` 列表

### 3.3 MiniDonutView（迷你环形进度）

- **文件**：`MiniDonutView.java:1-80`
- **用途**：表格单元格内显示负载占比 + 中心百分比
- **几何**：内圈占外圈 58%（`INNER_RATIO = 0.58f`）
- **格式**：`String.format("%.0f%%")`

### 3.4 InlineItemIconView + ItemTextureCache

- **InlineItemIconView**：`InlineItemIconView.java:1-75` —— 列表项内绘制物品 / 流体图标
- **ItemTextureCache**：`ItemTextureCache.java:1-322` —— 两阶段缓存
  1. **CPU 阶段**（Minecraft 渲染线程，`ScreenEvent.Render.Post`）：渲染到 32×32 FBO → `glReadPixels` → Bitmap
  2. **GPU 阶段**（MUI 绘制线程，`onDraw`）：Bitmap → Image（通过 `Core.peekUiRecordingContext`）
- **每帧渲染上限**：50 个，避免帧率骤降
- **重试**：InlineItemIconView 最多 120 次轮询（约 2 秒），失败后放弃

### 3.5 按钮组件（ModernUiTheme 工厂方法）

#### 3.5.1 普通按钮 `tinyButton`（L254-269）
- 字号：`10sp × textScale`，颜色 `TEXT`
- Padding：`8dp × 4dp`
- 三态背景（`buttonBackgroundStateful`）+ 40% 白波纹
- 按下缩小 0.95，松开反弹 1.0

#### 3.5.2 Chrome 图标按钮 `chromeIconButton`（L280-304）
- 尺寸：`22dp × 22dp`
- 圆形背景 + 波纹（`chromeCircleBg`）
- 普通图标：白色 hover/press
- 关闭按钮（`isClose=true`）：`ROSE` 色 + 红色波纹
- Hover：缩放 1.03

#### 3.5.3 三态背景（`stateful`，L181-202）
| 状态 | 亮度增量 |
| --- | --- |
| 普通 | 基础 |
| Hover | +12% |
| Press | +22% |

#### 3.5.4 行背景（`rowBackgroundStateful`，L191-203）
- Critical 行（如亏电）：深红 warning 背景
- 普通行：透明 / 弱高亮

### 3.6 Tab 组件
见 [1.2](#12-tab-切换chrome-顶栏)。

### 3.7 文本工厂

- `createText(...)` (L224-235)：基础 TextView，支持单行 / 截断
- `addText(linearLayout, ...)` (L237-252)：快速添加文本行，支持自定义宽度、topMargin、颜色、字号
- `compact(value)` (L377-382)：B / M / K 格式化（`compact(1_500_000) → "1.5M"`，精度 `.1f`）
- `tr(key, args...)` (L385-387)：i18n 翻译

### 3.8 Section 容器
- `sectionBackground()`（L314-328）：圆角 6dp + 边框，LinearLayout VERTICAL
- `setClipChildren(false)` 允许子元素溢出（动画用）

### 3.9 Dialog 组件

#### 3.9.1 WebAccessDialog
- **文件**：`WebAccessDialog.java:1-100+`
- **尺寸**：420dp × WRAP
- **遮罩**：`#AA04070E`（半透深黑）
- **内容**（L54-97）：
  1. 标题（CYAN，13sp）
  2. 警告文本（TEXT_MUTED，10sp）
  3. URL 显示框（HEADER_BG 背景）
  4. "不可靠连接"提示（AMBER）
  5. 按钮行（复制 / 重新生成）

#### 3.9.2 DialogChrome（共用工具）
- **文件**：`DialogChrome.java:36-64`
- **scrollFadeOverlays**：14dp 高渐隐遮罩，PANEL_BG → 透明，提示可继续滚动

#### 3.9.3 PopupWindow 跟踪
- **机制**（`ResourceTerminalFragment.java:127-148`）：
  - `trackPopup(p)` → WeakReference 列表维护
  - `dismissAllPopups()` → Fragment.onPause / onDestroy 时统一关闭
  - 自动清理已失效引用

### 3.10 动画工厂（ModernUiTheme，全部位于 L424-624）

| 动画 | 用途 | 时长 |
| --- | --- | --- |
| `fadeIn()` | Alpha 0→1，DECELERATE | — |
| `slideInFromBottom()` | 下滑 + 淡入 | — |
| `scaleIn()` | 0.85→1.0 缩放 + 淡入，OVERSHOOT | 250ms |
| `addHoverScaleEffect()` | hover 1.03，DECELERATE | 150ms |
| `addHoverLiftEffect()` | translationY -2dp | 150ms |
| `addPressScaleEffect()` | down 0.95 / up bounce 1.0 | 100ms / 200ms |
| `staggerSlideIn()` | 列表瀑布动画 | 间隔 40ms |
| `expandRows()` | 分组展开，逐行淡入 | 180ms |
| `collapseRows()` | 分组折叠，逐行淡出 | 150ms |
| `animateStarToggle()` | 星标弹跳 1.5x | 300ms |
| `animateCardRemove()` | 0.8 缩放 + 淡出 | 200ms |

### 3.11 搜索框防抖

- **文件**：`ResourceTerminalFragment.java:316-324`
- **延迟**：200ms
- **流程**：用户输入 → `scheduleSearchUpdate(query)` → 200ms 后 → `bridge.setSearchQuery(query)`
- 连续输入只有最后一次生效

### 3.12 右键菜单 / Context Menu

> 当前实现仅在 Overview Page 出现，详见 [6.4](#64-右键菜单)。

- **位置**：`OverviewPageBuilder.dismissActiveMenu(terminal)` (L448)
- **menuId 定义**（L52-66）：
  - `CREATE_GROUP` / `MOVE_TO` / `CLEAR_GROUP`
  - `ASSIGN_BASE` / `RENAME_GROUP` / `DELETE_GROUP`
  - `ID_DATA_ITEMS` / `ID_DATA_ENERGY`
- **机制**：基于 MUI `MenuPopupHelper`，`event.getRawX/Y()` 锚点定位
- **关闭**：界面暂停 / 点击外部自动关闭

### 3.13 刷新机制

- **快速刷新**：500ms（图表细粒度窗口）
- **正常刷新**：1000ms
- **搜索防抖**：200ms

### 3.14 增量刷新（关键性能机制）

- **入口**：每个 PageBuilder 都有 `tryIncrementalUpdate(...)`
- **判断条件**：`computeStructuralKey()` —— 结构（行数、KPI 数量、列定义）不变就走增量
- **执行**：通过 `tag` 在 ViewTree 上定位 TextView / DonutChartView，原地更新文本 / 颜色 / 数据
- **收益**：避免每次 onDataChanged 都重建整棵树，保留搜索框焦点、ScrollView 位置、Donut hover 状态

---

## 4. 全局交互模式

### 4.1 Hover

| 元素 | 反馈 |
| --- | --- |
| 按钮 / Tab / 卡片 | 缩放 1.03（150ms DECELERATE） |
| 卡片内子元素 | translationY -2dp |
| 表格行 | 30~50% 高亮背景 |
| Donut 段 | +3px 外扩 + alpha 255，其它段 alpha 140 |
| Chart 折线 | 同时间槽全系列 Tooltip 显示，60px 吸附 |

### 4.2 点击

- 按下 0.95（100ms） → 松开反弹 1.0（200ms OVERSHOOT）
- 波纹反馈：按钮/Tab 40% 白；关闭按钮 40% 红；卡片 20% 白

### 4.3 右键
仅 Overview Page 表格行 / 分组头有右键菜单（参见 3.12 / 6.4）。其它页面**未实现**右键。

### 4.4 键盘
- 无自定义快捷键
- E 键关闭界面：Fragment.onPause → dismissAllPopups
- 文本输入框激活时（`textInputActive=true`）拦截 E 键

### 4.5 拖拽
**未实现**。当前没有任何拖拽交互。

### 4.6 滚动
- ScrollView 自动保持位置（重建时 `OnPreDrawListener` 恢复）
- 底部渐隐遮罩提示可继续滚动

### 4.7 焦点管理
- 搜索框焦点在重建期间通过 `isRebuilding()` flag 屏蔽 focusChange，避免抖动
- 重建后 `requestFocus + setSelection` 恢复光标位置

---

## 5. 国际化与文本

### 5.1 i18n 模式
所有 UI 文本走 `tr(key, args...)`，调用 `Component.translatable(key, args).getString()`。lang 文件：`assets/resourceobserver/lang/{en_us,zh_cn}.json`。

### 5.2 关键 key 前缀
- `screen.resourceobserver.storage.*`
- `screen.resourceobserver.crafting.*`
- `screen.resourceobserver.power.*`
- `screen.resourceobserver.overview.*`
- `gui.resourceobserver.web_access.*`

### 5.3 数值格式化
- **紧凑数字**：`ModernUiTheme.compact(value)` → "1.5M" / "1.5K" / "1.5B"，精度 `.1f`
- **百分比**：`String.format(Locale.ROOT, "%.0f%%", value * 100)`

---

## 6. Overview Page

> **文件主体**：`OverviewPageBuilder.java`
> **数据契约**：`OverviewViewModel`
> **V2 对照**：`client/screen/v2/overview/`

### 6.1 元素清单（按视觉从上到下）

#### 6.1.1 标题区 + 连接状态卡片
- 标题区右侧：36dp 高状态卡片
- 圆点 EMERALD（在线）/ ROSE（离线） + 文本
- 边框色随状态变化

#### 6.1.2 KPI 卡片组（4 张水平）
- 字号：14sp（数值）
- 各张独立状态色（CYAN / EMERALD / AMBER / ROSE）
- 数值变化时**脉冲动画**（`playKpiPulse`，KPI 值变化时触发）
- **Click**：弹出对应 KPI 详情对话框（小弹窗 360×280dp）
- **Hover**：lift 1.04x scale

#### 6.1.3 折线图区（ChartView）
- 工具栏 4 按钮（PopupMenu 形式）：
  1. **时间窗口**（5min / 15min / 1h / 6h / 24h）
  2. **页面切换**（THROUGHPUT / BOTTLENECK / ...）
  3. **线模式**（ALL / POSITIVE / NEGATIVE）
  4. **平滑度**（SMOOTH / RAW）
- 图例：动态根据 series 生成
- **RadioGroup 数据类型**：`ITEMS` / `ENERGY` 互斥单选，Energy 无数据时禁用
- 工具栏 PopupMenu 互斥：打开新菜单时旧菜单自动关闭

#### 6.1.4 关注列表（Watchlist）
- 分页卡片，1~3 张/页（按容器宽度自适应）
- 公式：`cardW = (listWidth - (perPage-1) * gap) / perPage`
- 每卡 12×12dp 图标
- 显示：净流量 + 库存 + 删除按钮 ✕
- 入场动画：`staggerSlideIn`
- 选中高亮（CYAN 边框）
- **删除流程**：✕ 点击 → `animateCardRemove`（淡出 + 0.8 缩放，200ms） → 发送 `TOGGLE_WATCH` → 本地先删
- 末页删除若卡片不足，自动回退页

#### 6.1.5 主表格
- **行高**：`scaledDp(18)` ≈ 18dp
- 列：
  1. 星标 ✕/★（16dp）
  2. 物品图标 + 名称
  3-6. 数据列（净流量 / 库存 / Burn Rate / Buffer 等）
- 字号：9sp
- 行背景：三态 + critical 红 warning（亏损时）
- 增量刷新：原地更新单元格，不销毁视图树
- **星标交互**：`animateStarToggle`（1.5x 弹跳 + 旋转，300ms）→ 发送 `TOGGLE_WATCH`

#### 6.1.6 表格分组（Section）
- 可展开 / 折叠（`expandRows` / `collapseRows`）
- 分组头**右键菜单**：重命名 / 删除（系统分组无菜单保护）
- 分组计数显示

#### 6.1.7 表格表头
- 可排序列：激活列 CYAN + ▲/▼ 箭头 + 按钮背景
- 非激活列：muted + stateful 背景
- 三态反馈（hover / press）

#### 6.1.8 表格筛选 + 搜索
- 状态筛选下拉（all_status / deficits_only）
- 搜索框（100dp 宽）+ 防抖 200ms + 清除按钮
- 重置按钮
- **跨页保持**：搜索词存在 ViewModelBridge

### 6.2 整体布局
- 各区高度按 `clamp(contentH * 系数, min, max)` 计算（响应式）
- KPI 区：`clamp(contentH * 0.15f, 54dp, 100dp)`
- 关注列表：1-3 卡片/页（按宽度）
- 表格：剩余空间，ScrollView 包裹

### 6.3 KPI 详情弹窗
- 尺寸：`DIALOG_KPI_SMALL_W/H` = 360×280dp
- 标题 + 关闭按钮 ✕
- ScrollView 可滚动内容区
- 内容：提示 + 分隔线 + 通道数据
- 遮罩淡入 + 对话框 OVERSHOOT 缩放 + 淡入（250ms）
- 内容**实时刷新**（`dialogContentRefresher` callback 在数据更新时同步）

### 6.4 右键菜单

#### 6.4.1 表格行右键
| 菜单项 | 子菜单 | 行为 |
| --- | --- | --- |
| 新建分组 | — | 弹分组命名弹窗 |
| 移动到 | 已有分组列表 | 改 group |

#### 6.4.2 分组头右键
| 菜单项 | 行为 |
| --- | --- |
| 重命名 | 弹分组命名弹窗 |
| 删除 | 删除分组（仅非系统分组） |

#### 6.4.3 分组命名弹窗
- 尺寸：180dp 宽
- EditText + Cancel/Confirm 按钮
- 标题切换（新建 / 重命名）

### 6.5 V2 vs ModernUI 差异表

| 模块 | ModernUI | V2 | 差距 |
| --- | --- | --- | --- |
| KPI 卡片 | 4 张 + hover/press 动画 + 脉冲动画 + 点击弹详情 | OverviewKpiCard 已实现，**无动画 + 无详情弹窗** | 🟠 |
| 图表工具栏 | 4 按钮 PopupMenu + RadioGroup ITEMS/ENERGY | LineChart 基础渲染 | 🔴 缺工具栏 |
| 关注列表 | 分页 + 删除 + 入场动画 | OverviewWatchlist 已实现，**无分页 + 无删除按钮 + 无动画** | 🟠 |
| 表格行 | 星标 + 右键菜单 + critical 红背景 + 增量刷新 | OverviewTableRow 已实现，**无星标 + 无右键 + 全量重建** | 🔴 |
| 表格分组 | 可展开 + 右键管理 + 折叠动画 | OverviewTableSection 已实现，**无右键 + 无动画** | 🔴 |
| 表格筛选 | 防抖搜索 + 跨页保持 + 重置按钮 | 部分已实现 | 🟡 |
| KPI 详情弹窗 | 360×280 弹窗 + 滚动 + 实时刷新 | **完全缺失** | 🔴 |
| 存储详情弹窗 | 420×360 弹窗 + AE2 通道详情 | **完全缺失** | 🔴 |
| 分组命名弹窗 | 180dp + EditText + 确认 | **完全缺失** | 🔴 |
| 行右键菜单 | 二级子菜单（新建分组 / 移动到） | **完全缺失** | 🔴 |
| 分组头右键 | 重命名 / 删除 | **完全缺失** | 🔴 |
| 增量刷新 | structuralKey 判断 + 原地更新 | **每次全量重建** | 🟠 |
| UI 全局缩放 | textScale / layoutScale 三档 | **不支持** | 🟡 |
| 响应式布局 | clamp 公式 + 卡片自适应数 | **可能固定布局** | 🟠 |

### 6.6 Overview 实装易踩坑

🔴 **高危**：
1. **增量刷新机制**：structuralKey + tag 在 ViewTree 上定位，V2 直接全量重建会卡顿
2. **对话框层级**：dialogOverlay / contentContainer 双层容器，V2 BaseWidget 框架可能没有模态层管理
3. **响应式百分比**：`clamp(Math.round(contentH * 0.12f), min, max)` 不要硬编码
4. **PopupMenu 互斥**：打开新菜单时旧菜单需自动关闭

🟠 **中危**：
5. **搜索框焦点**：rebuild 时通过 flag 屏蔽 focusChange
6. **右键菜单坐标**：`event.getRawX/Y()` → 锚点偏移，坐标系差异
7. **关注列表卡片等宽**：严格按公式计算
8. **KPI 脉冲动画**：在 `tryIncrementalUpdate` 内做值变化检测

🟡 **低危**：
9. Status 色彩映射 EMERALD / AMBER / ROSE / CYAN
10. UI 缩放系数全局应用
11. 表格 critical 状态红背景
12. 表头三态反馈
13. ScrollView 渐隐遮罩
14. 动画插值器选择（OVERSHOOT / ACCELERATE / DECELERATE）

---

## 7. Storage Network Page

> **文件主体**：`StorageNetworkPageBuilder.java`
> **数据契约**：`StorageNetworkViewModel`
> **过滤枚举**：`ui/state/TableSortMode.java` / `TableStatusFilter.java` / `TableGroupFilter.java`

### 7.1 顶部统计区
- 已用 / 总量 / 空闲百分比
- 进度条（线性，颜色按阈值变化）

### 7.2 子 Tab 切换
- 二级 Tab：`storage.subtab.items` / `storage.subtab.crafting`

### 7.3 节点 / 使用 / 库存健康度区
- `storage.section.nodes` / `storage.section.usage` / `storage.section.inventory_health`
- 每个 section 都是独立的 sectionBackground 卡片

### 7.4 主表格
- **行高**：`scaledDp(16)` ≈ 16dp（默认 layoutScale=1.2 时实际 ~18dp）
- **列定义**（i18n key → 字段）：

| 列名 | i18n key | 数据字段 | 排序支持 | 默认对齐 |
| --- | --- | --- | --- | --- |
| 标识 | `storage.col.identity` | 物品图标 + name + id | ✓ | 左 |
| 库存 | `storage.col.stock` | currentAmount | ✓ | 右 |
| 净流量 | `storage.col.delta` | deltaPerTick | ✓ | 右 |
| 燃烧率 | `storage.col.burn_rate` | burnRate | ✓ | 右 |
| 缓冲 | `storage.col.buffer` | bufferRemaining + 倒计时 | — | 右 |

### 7.5 排序 / 过滤交互

#### 7.5.1 TableSortMode 切换
- 按列头点击切换升降序（▲/▼ 图标）
- 激活列：CYAN + 按钮背景
- 非激活：muted + stateful

#### 7.5.2 TableStatusFilter
- 全部 / 流入 / 流出 / 静止 / **仅亏损**（`deficits_only`）
- 下拉菜单形式

#### 7.5.3 TableGroupFilter
- 物品 / 流体 / 全部
- 切换按钮组

### 7.6 搜索框
- 防抖 200ms（同 [3.11](#311-搜索框防抖)）
- 占位符：`screen.resourceobserver.search.hint`
- 清除按钮 ✕

### 7.7 收藏 / Watchlist
- 行星标 ✕/★ → `TOGGLE_WATCH`
- 收藏栏切换（与 Overview 共享 watchlist 数据）

### 7.8 Buffer 倒计时
- 独立 1s tick（`countdownRunnable`，L196）
- 每秒原地更新 buffer 单元格文本，不重建整行

### 7.9 空态
- `storage.no_items`：列表为空
- `storage.below_threshold`：低于阈值
- `storage.items_count`：物品数显示

### 7.10 V2 vs ModernUI 差异
- V2 行高 = `ROW_H = 22dp`，ModernUI = `scaledDp(16)`，需统一
- V2 KPI 高度 = 36dp，ModernUI = `clamp(contentH * 0.15f, 54dp, 100dp)`
- V2 表头高度 = 12dp，ModernUI = WRAP_CONTENT
- V2 缺：Buffer 倒计时、收藏交互、行右键菜单、增量刷新

---

## 8. Crafting 子 Tab

> **文件主体**：`CraftingSubTabBuilder.java`
> **数据契约**：`CraftingViewModel`、`CraftingPlanResultPayload`、`CraftingTreeNode`

### 8.1 KPI 行
- `crafting.kpi.cpu_total` / `cpu_busy` / `storage_bytes` / `coprocessors`
- 不可靠数据时：`crafting.unreliable` 标记

### 8.2 任务列表（jobs）
- 标题：`crafting.jobs_title`
- 字段：物品图标 + 名称 + 数量 + 进度条 + ETA + CPU
- 进度条按完成度变色
- 空态：`crafting.jobs_empty` / `crafting.jobs_idle`
- 剩余短文：`crafting.jobs_remaining_short`

### 8.3 可合成物品列表（craftables）

参考：`CraftingSubTabBuilder.java:353-399`

- 标题：`crafting.craftables_title`
- 行 padding：`4dp × 2dp`，行间距 `topMargin 4dp`

| 元素 | 尺寸 | 颜色 | 内容 |
| --- | --- | --- | --- |
| 物品图标 | `10×10dp` | — | InlineItemIconView |
| 名称 | flex=1 | TEXT, 9pt | displayName / itemId |
| 物品 ID | wrap | TEXT_MUTED, 8pt | itemId 灰字 |
| 一键下单按钮 | wrap | CYAN, 8pt | `crafting.order` |
| 按钮背景 | — | SECTION_BG + DIVIDER 边框 | 圆角 3dp，padding `6×2dp` |

- 截断：超过 120 行显示 `crafting.craftables_truncated`
- 空态：`crafting.craftables_empty` / `craftables_no_match`

### 8.4 下单流程（三步对话框）

#### 8.4.1 下单输入框 `showOrderPopup`
参考：L402-499
- 尺寸：`DIALOG_SMALL_W` = 200dp × WRAP
- 内容：
  - 标题（11pt TITLE）`crafting.order_title`
  - 数量输入框（10pt TEXT，预设 "1"，hint `order_hint`）
  - 取消（TEXT_MUTED）+ 确认（CYAN）
- 确认逻辑：
  1. 验证数量 (Long.parseLong)，≤0 忽略
  2. `setCraftingPlanCallback(callback)`
  3. 发送 `UiActionType.PLACE_CRAFT_ORDER`
  4. 弹 `showPlanningPopup`

#### 8.4.2 计算中提示 `showPlanningPopup`
参考：L516-552
- 尺寸：`DIALOG_PROGRESS_W` = 240dp × WRAP
- 不可点击关闭（`setTouchable(false)`）
- 文字：`crafting.plan_calculating`（带 displayName, amount 参数）
- 持久化：static `currentPlanningPopup`，在 `showReviewPopup` 时自动关闭

#### 8.4.3 计划审核对话框 `showReviewPopup`
参考：L565-808
- 尺寸：`DIALOG_REVIEW_W` = 560dp × WRAP
- 触发：`CraftingPlanResultPayload` 回调

**结构**：

1. **标题**：`crafting.plan_title`（finalOutputDisplayName + 计算量），TITLE 12pt
2. **状态行**：
   - 成功 `plan_ok` → CYAN
   - 模拟 `plan_simulation` → `#FFFF6464`
   - 错误 `plan_error` → `#FFFF6464`
   - 字号 10pt
3. **最终产物卡片**：
   - 背景：SECTION_BG + CYAN 1dp 边框 + 圆角 8dp + padding 8dp
   - 图标 22×22dp + 名称 11pt TITLE + 产量提示（"per_exec / 次 × times 次"，8pt `#FF80B0C8`）
   - 数量 13pt CYAN，"× compact(amount)"
4. **滚动区**（260dp 高）—— 用料 / 缺料 / 副产物：
   - 头部 9pt TEXT_MUTED，上 6dp 边距
   - 行：图标 14×14dp + 名称 9pt + 数量 9pt
   - 颜色：用料 TEXT，缺料 `#FFFF6464`，副产物 `#FFFFC864`
5. **CPU 选择器**（cpus.size() ≥ 4 时）：
   - 标题 9pt TEXT_MUTED
   - Auto Chip + N 个 CPU Chip（横向滚动）
   - Chip 9pt，padding `6×2dp`，rightMargin 4dp
   - 选中：CYAN + `0x33A8FFFF` 背景 + CYAN 边框
   - 未选中：TEXT_MUTED + SECTION_BG + DIVIDER 边框
   - 圆角 8dp
6. **按钮行**（END | CENTER_VERTICAL，上 8dp）：
   - 查看树（可选，treeRoot 非空）：`#FFA0E0A0` → `showTreePopup`
   - 取消：TEXT_MUTED → `CANCEL_CRAFT_PLAN`
   - 确认：
     - 文字：simulation 时 `plan_submit_anyway`，否则 `plan_submit`
     - 颜色：simulation `#FFFFC864`，否则 CYAN
     - hasPlanId=false 时禁用 (TEXT_MUTED)
     - 发送 `CONFIRM_CRAFT_ORDER`，payload = `planId[|cpuName]`

### 8.5 合成树视图 `showTreePopup`
参考：L1006-1235
- 尺寸：880dp × WRAP
- 标题：`crafting.tree_title`，TITLE 12pt
- 进度条（progressFraction>0 时）：6dp 高
- 双向滚动容器：
  - 垂直 ScrollView（**Ctrl+滚轮 缩放**：上=×1.1，下=÷1.1，范围 0.4~2.5x）
  - 水平 HorizontalScrollView
  - SECTION_BG + 圆角 8dp + SECTION_BORDER 1dp，640dp 高，padding 12dp

**节点卡片** (`buildNodeCard`)：
- 尺寸：170dp × WRAP
- 背景：CARD_BG + 圆角 8dp + 1dp 边框
- 边框颜色规则：
  - 缺料 / 环形 / 截断：ROSE
  - 深度 0（最终产物）：AMBER
  - 非叶节点：EMERALD
  - 叶节点：CARD_BORDER
- 第 1 行：图标 16×16dp + 名称 10pt TITLE + 前缀 `[缺]` / `[环]` / `[…]`
- 第 2 行（可选）：itemId 7.5pt TEXT_MUTED
- 第 3 行：
  - 数量 11pt（缺料 ROSE，否则 CYAN）
  - 产率提示 8pt TEXT_MUTED
  - Tier Chip：strokeColor 7pt + 1dp 边框 + 圆角 4dp + padding `4×1dp`
    - rare（缺料/环形/截断） / legendary（depth=0） / uncommon（有子节点） / common（叶节点）

**树连线**：水平 14×1dp + 垂直 1×match_parent，DIVIDER 颜色，子节点列 left padding 10dp。

### 8.6 i18n key 全集
```
crafting.empty / craftables_count / craftables_title
craftables_empty / craftables_no_match / craftables_truncated
jobs_title / jobs_empty / jobs_idle / jobs_remaining_short
kpi.cpu_total / cpu_busy / storage_bytes / coprocessors
unreliable / order / order_title / order_hint
order_cancel / order_confirm
plan_calculating / plan_title / plan_ok / plan_simulation / plan_error
plan_used / plan_missing / plan_emitted
plan_cpu_select / plan_cpu_auto
plan_submit / plan_submit_anyway
tree_view_btn / tree_title
```

### 8.7 Payload / Action 类型
- `UiActionType.PLACE_CRAFT_ORDER`：`itemId, networkIds, amount`
- `UiActionType.CONFIRM_CRAFT_ORDER`：`planId[|cpuName]`
- `UiActionType.CANCEL_CRAFT_PLAN`：`planId`
- `CraftingPlanResultPayload`：finalOutput*, planId, status, bytes, message, usedItems, missingItems, emittedItems, cpus, treeRoot
- `CraftingTreeRequestPayload` / `CraftingTreeResponsePayload`

### 8.8 V2 vs ModernUI 差异
- V2 当前 Crafting tab 仅 placeholder，**所有内容缺失**

---

## 9. Power Network Page

> **文件主体**：`PowerNetworkPageBuilder.java`（2200+ 行）
> **数据契约**：`PowerNetworkViewModel` / `PowerSnapshot`

### 9.1 KPI 卡片组
参考：L659-717

- 4 张卡片：有效输入 / 有效输出 / 储能 / 排除量
- i18n: `power.kpi.effective_input / effective_output / stored_energy / external_excluded`
- **Click**：`showKpiDetailDialog`，按 KPI 类型分支：
  - `populateInputDetail` (L904-995)
  - `populateOutputDetail` (L998-1077)
  - `populateStorageDetail` (L1080-1215)
  - `populateExcludedDetail` (L1218-1310)

### 9.2 EffectiveStats 计算（关键聚合逻辑）
参考：L66-132

**问题**：外部存储被同时包含在输入和输出统计中。

**解决**：
```
EffectiveStats = computeEffectiveStats(vm, effectiveExtIds)
  → excludedKeys = 手动选中 + 自动识别的 双向储能 extId
  → 从总统计中减去这些 key 对应的设备速率
  → totalInputPerTick = rawInput - excludedInput
  → totalOutputPerTick = rawOutput - excludedOutput
```

### 9.3 负载分布甜甜圈图
参考：L1395-1557 (`buildCapacityPieChart`) + DonutChartView 全文件

- 几何：见 [3.2](#32-donutchartview甜甜圈图)
- **双模式切换**：
  - 模式 A：含余量（HEADROOM 段）→ 中心显示 utilization%
  - 模式 B：仅显示负载 → 中心显示总功率
  - i18n key: `power.donut.toggle.used_only` / `donut.center_utilization`
- 扇区配色按设备类型映射（输入/输出/内部）
- Hover 高亮 + Tooltip
- **增量更新**：保留 hover 状态，仅 `setData()`

### 9.4 设备列表
参考：L2064-2156 (`buildDeviceList`)

- 行高：`scaledDp(22)` （DEVICE_ROW_HEIGHT_DP=22）
- **去重逻辑**（防同方块多接口重复计数）：
  - 第一遍：key=`externalRef.extId()` 或 deviceName（坐标级）
  - 第二遍：key=`stripCoordinates(rawDisplayName)`（去坐标） → "Device ×3"
- **6 列**：

| 列名 | i18n key | 数据 |
| --- | --- | --- |
| 设备名 | `power.col.device` | 图标 + name |
| 分类 | `power.col.category` | 类型徽章（color-coded） |
| 能耗 | `power.col.energy` | 当前 I/O |
| 负载占比 | `power.col.load_share` | 内联条形图（FrameLayout 高亮段） |
| 供能占用 | `power.col.load_util` | **10 段分段仪表条**（阈值 40%/70%/90% 四档变色） |
| 容量利用率 | `power.col.cap_util` | 进度条 |

### 9.5 过载告警 (`overload`)
- i18n: `power.overload.risk.critical / warning / low` / `overload.reserve`
- 阈值规则：
  - Headroom：5%（ROSE）/ 15%（AMBER）
  - 储能填充：10%（NEGATIVE）/ 90%（WARNING）
- 告警横幅：颜色 ROSE / AMBER / EMERALD

### 9.6 外部控制台面板
参考：L2168-2203 (`buildShareContextBar`)

- i18n: `power.external_console.title` / `metric.input` / `metric.output`
- 显示外储统计

### 9.7 供能仪表 (`buildSupplyGauge`)
参考：L2211-2253

- 10 段小方块横向排列
- 阈值变色：40% / 70% / 90% 四档
- **进度条宽度同步**：使用 LinearLayout + weightSum 立即应用，避免 layout pass 延迟闪烁
  ```
  barTrack.setWeightSum(1.0f)
  barTrack.addView(fill,   LayoutParams(0, h, ratio))
  barTrack.addView(spacer, LayoutParams(0, h, 1.0f - ratio))
  ```

### 9.8 KPI 详情弹窗 `showKpiDetailDialog`
参考：L725-887

- 尺寸：`DIALOG_KPI_W/H` = 420×360dp
- 全屏 dimOverlay 拦截背景点击
- ScrollView 可滚动内容区
- 4 个 case 分支调用不同 populate 方法

### 9.9 接口列表弹窗 `showInterfaceListDialog`
参考：L1639-1785

- 用于外储组管理
- 行高：`scaledDp(14)` (DEBUG_ROW_HEIGHT_DP=14)
- 复选框管理 effectiveExtIds 集合 → 重新计算 EffectiveStats

### 9.10 ChartView 集成（预留）
参考：L1952-2032 (`buildGridChart`)

- 当前用 GridChart 替代，后续可换 ChartView
- 时间序列数据：`List<FlowPoint>`
- 切换 ChartPage / LineMode / SmoothingMode

### 9.11 增量刷新流程
```
onDataChanged()
    ↓ tryIncrementalUpdate()
    ├─ 查找 DonutChartView (TAG_DONUT)
    ├─ recompute effectiveStats
    ├─ donut.setData() ← 保留 hover
    ├─ updateTaggedTextViews(KPI values)
    ├─ updateTaggedText(summary, grid, risk)
    ├─ 更新 grid load bar (宽度 + 颜色)
    ├─ 重建 device list
    └─ return true
    ↓ 若 false → rebuild 整个页面
```

### 9.12 整体布局尺寸（dp）

| 区域 | 公式 | min | max |
| --- | --- | --- | --- |
| KPI 区 | `contentH × 0.15f` | 54 | 100 |
| 左列宽 | `innerW × 0.33f` | 80 | — |
| 右列宽 | `innerW - leftW - colGap` | 80 | — |
| 饼图 | `min(leftW - 16, pieH - 56)` | 64 | — |
| 图表 | `contentH × 0.20f` | 60 | 140 |
| 设备行高 | `scaledDp(22)` | — | — |
| 接口行高 | `scaledDp(14)` | — | — |

innerW = `max(160, contentW - 8)`，sectionGap = 6dp。

### 9.13 V2 vs ModernUI 差异

| 模块 | ModernUI | V2 | 差距 |
| --- | --- | --- | --- |
| KPI 卡片 | 4 张 + 详情弹窗 | PowerKpiCard 已实现，无弹窗 | 🔴 |
| 负载饼图 | DonutChartView + tooltip + 双模式 | **完全缺失** | 🔴 |
| 设备列表 | 6 列含分段仪表 | PowerDeviceRow 6 列基础 | 🟠 |
| 过载告警 | 横幅 + 阈值变色 | PowerOverloadAlertRow 已实现 | 🟢 |
| 消费者列表 | 含去重计数 | PowerConsumerRow 已实现，未验证去重 | 🟡 |
| KPI 详情弹窗 | 420×360 + 4 类型分支 | **完全缺失** | 🔴 |
| 接口列表弹窗 | 外储组管理 | **完全缺失** | 🔴 |
| 上下文条（负载占比单元格） | FrameLayout 内联 | **完全缺失** | 🟠 |
| 供能占用分段仪表 | 10 段 + 阈值色 | LoadSegmentBar 简化版 | 🟠 |
| 外部控制台面板 | 独立卡片 | **完全缺失** | 🟠 |
| 折线图 ChartView | 预留位置 | **完全缺失** | 🟡 |

### 9.14 易踩坑

🔴 **高危**：
1. **KPI 聚合**：外储被双向计入 → 必须做 EffectiveStats 排除
2. **DonutChart hover 状态丢失**：rebuild 时务必走 `tryIncrementalUpdate` 保留 view 实例
3. **设备去重**：两阶段（坐标级 + 干净名级）
4. **进度条宽度闪烁**：用 weightSum 而非 post()

🟠 **中危**：
5. **弹窗模态化**：dimOverlay + setClickable(true) 双重拦截
6. **文本/布局缩放**：用 `scaledDp(terminal, x)` 不要硬编码 dp
7. **Tooltip 越界**：自适应右翻 / 上下贴边

---

## 10. V2 对齐总览（缺失项汇总）

### 10.1 完全缺失（红色：必须补）

| 类别 | 元素 | 涉及页面 |
| --- | --- | --- |
| **Dialog 框架** | dialogOverlay 模态层 + dimOverlay 遮罩 + scaleIn/fadeIn 动画 | 全局 |
| **Dialog 实例** | KPI 详情（小） | Overview、Power |
| | KPI 详情（大） | Overview |
| | 存储详情 | Overview |
| | 分组命名 | Overview |
| | 下单输入 | Crafting |
| | 计算中提示 | Crafting |
| | 计划审核 | Crafting |
| | 合成树（双向滚动 + 缩放） | Crafting |
| | 接口列表 | Power |
| | WebAccess（已迁移） | Chrome |
| **右键菜单** | 表格行右键（新建分组 / 移动到子菜单） | Overview |
| | 分组头右键（重命名 / 删除） | Overview |
| **图表** | DonutChartView（含 Hover Tooltip） | Power |
| | MiniDonutView | Storage / Power |
| | ChartView（折线图 + 工具栏 + RadioGroup） | Overview |
| **交互** | 收藏按钮 ✕/★ + 弹跳动画 | Overview / Storage |
| | 增量刷新机制（structuralKey + tag） | 所有 |
| | UI 全局缩放（textScale / layoutScale 三档） | Chrome |
| **图表工具栏** | 时间窗口 / 页面 / 线模式 / 平滑度（4 PopupMenu） | Overview |
| **进度条** | 10 段分段仪表条 + 阈值变色 | Power |
| | 上下文条（FrameLayout 内联） | Power |
| | Buffer 倒计时 1s tick | Storage |

### 10.2 部分实现（橙色：需对齐细节）

| 元素 | V2 当前 | 需补充 |
| --- | --- | --- |
| KPI 卡片 | 静态 | hover/press 动画 + 脉冲动画 + 点击弹窗 |
| 表格分组 | 静态 | 展开/折叠动画 + 右键管理 |
| 关注列表 | 静态 | 分页 + 删除 + 入场动画 |
| 设备列表 | 6 列基础 | 分段仪表 + 上下文条 + 去重逻辑 |
| 过载告警 | 横幅 | 阈值变色完整规则 |

### 10.3 配色 / 尺寸校准（黄色：必须 1:1 对齐）

- VanillaTheme 引入完整 UiThemeTokens 颜色（CYAN / EMERALD / AMBER / ROSE 四主色 + 全部背景/边框）
- 行高统一：V2 `ROW_H=22dp` → 改为 `scaledDp(16~18)`
- 字号：V2 绝对像素 → 乘 textScale
- 圆角规则统一（10/8/6/4/1000dp）

### 10.4 全局机制（蓝色：架构层）

- **Dialog/Popup 跟踪**：trackPopup + WeakReference 列表 + dismissAllPopups
- **PopupMenu 互斥**：打开新菜单时旧菜单自动关闭
- **搜索框焦点管理**：rebuild flag + requestFocus + setSelection
- **滚动位置恢复**：OnPreDrawListener
- **图标缓存**：CPU/GPU 两阶段 + 每帧 50 上限

---

## 11. 实装路线建议

> 后续 V2 对齐工作应按"基础 → 弹窗 → 图表 → 交互优化"四阶段推进。

### 11.1 Phase A — 基础对齐
1. 引入完整 UiThemeTokens 到 VanillaTheme
2. 统一行高 / 字号 / 圆角规则
3. 引入 textScale / layoutScale 三档全局缩放

### 11.2 Phase B — Dialog 框架
1. 实现 dialogOverlay 模态层
2. 实现 dimOverlay + scaleIn/fadeIn 动画
3. 实现 trackPopup / dismissAllPopups
4. 移植 PopupMenu 互斥机制
5. 按页面优先级实现具体 Dialog（先 KPI 详情，后下单流程，最后合成树）

### 11.3 Phase C — 图表组件
1. 移植 DonutChartView（用 GuiGraphics 的 fill/drawLine 绘制扇区 + hit test + tooltip）
2. 移植 MiniDonutView（简化版本）
3. 移植 ChartView（Hermite 平滑、Tooltip、HOVER_SNAP）
4. 实现进度条分段仪表（10 段 + 阈值色）

### 11.4 Phase D — 交互完善
1. 行右键菜单（含锚点坐标计算）
2. 收藏按钮 + 弹跳动画
3. 分组展开/折叠动画
4. KPI 脉冲动画
5. 关注列表分页 + 删除 + 入场瀑布动画

### 11.5 Phase E — 性能优化
1. 增量刷新（structuralKey 判断 + tag 定位）
2. 搜索框焦点管理
3. ScrollView 位置恢复
4. 图标缓存对齐

### 11.6 优先级建议
- **P0**（影响可用性）：Dialog 框架 + DonutChart + KPI 详情弹窗 + 收藏交互 + 配色对齐
- **P1**（影响体验）：右键菜单 + 分组动画 + 增量刷新 + ChartView
- **P2**（锦上添花）：UI 缩放 / 入场动画 / 分页交互

---

## 附录 A：关键文件 → 行号速查

| 功能 | 文件 | 行号 |
| --- | --- | --- |
| 根容器尺寸 | ResourceTerminalFragment.java | 72-77 |
| Tab 配置 | ResourceTerminalFragment.java | 692-712 |
| Tab 样式 | ResourceTerminalFragment.java | 756-773 |
| Chrome 按钮 | ResourceTerminalFragment.java | 280-304, 717, 721 |
| Popup 跟踪 | ResourceTerminalFragment.java | 127-148 |
| 搜索防抖 | ResourceTerminalFragment.java | 316-324 |
| 滚动位置恢复 | ResourceTerminalFragment.java | 660-672 |
| Theme 工厂方法 | ModernUiTheme.java | 81-624 |
| 颜色 token | UiThemeTokens.java | — |
| Donut 完整实现 | DonutChartView.java | 1-349 |
| Donut hit test | DonutChartView.java | 122-155 |
| Donut tooltip | DonutChartView.java | 273-333 |
| Chart 框架 | ChartView.java | 35-100 |
| 物品图标缓存 | ItemTextureCache.java | 1-322 |
| Power EffectiveStats | PowerNetworkPageBuilder.java | 66-132 |
| Power KPI 弹窗 | PowerNetworkPageBuilder.java | 725-887 |
| Power 饼图构建 | PowerNetworkPageBuilder.java | 1395-1557 |
| Power 接口弹窗 | PowerNetworkPageBuilder.java | 1639-1944 |
| Power 设备列表 | PowerNetworkPageBuilder.java | 2064-2156 |
| Crafting 下单弹窗 | CraftingSubTabBuilder.java | 402-499 |
| Crafting 计划审核 | CraftingSubTabBuilder.java | 565-808 |
| Crafting 树视图 | CraftingSubTabBuilder.java | 1006-1235 |
| Overview menuId | OverviewPageBuilder.java | 52-66 |
| Overview 菜单关闭 | OverviewPageBuilder.java | 448 |

---

## 附录 B：关键参数速查

```
Panel 占屏比例：90%
Chrome 高度：28dp
Tab 字号：10sp × textScale
UI 缩放档位：1.0 / 1.2 / 1.45（默认 1.2）
快速刷新：500ms（图表） / 正常 1000ms
搜索防抖：200ms
图标缓存：32×32px / 每帧上限 50 个
Donut 内圈比：62% / Mini Donut 58% / Hover 外扩 3px / 段间隙 1.2°
Chart Hover 吸附：60px / 线宽 2px / 网格 8px
表格行高：scaledDp(16~22)
按钮 Hover 缩放：1.03 (150ms DECELERATE)
按钮 Press 缩放：0.95 (100ms) / 反弹 1.0 (200ms OVERSHOOT)
对话框入场：scaleIn 0.85→1.0 + fadeIn (250ms OVERSHOOT)
卡片删除：0.8 缩放 + 淡出 (200ms)
Stagger 间隔：40ms
分组展开：180ms / 折叠：150ms
星标弹跳：1.5x (300ms)
```
