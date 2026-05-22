# 模组发布前待办事项

> 最后更新：2026-04-27 | 分支：`codex/toModernUI`
>
> 标记说明：`[x]` 已完成 / `[ ]` 待完成 / `[~]` 进行中

---

## 一、核心任务

### 1. 完善 README 文件 — `[x]` 已完成

- [x] 介绍模组的主要功能（含 Web UI、Modern UI）
- [x] 预留截图位置
- [x] 整理引用的仓库与依赖项
- [x] 附上作者联系方式
- [x] 补充使用说明（绑定流程、终端操作、Web 仪表盘）

> **后续需要：** 补充实际游戏截图到 `docs/screenshots/` 目录。

---

### 2. 实现多版本支持 — `[ ]` 待开始

- [ ] 扩展支持多个 Mod Loader（当前仅 NeoForge）
- [ ] 针对不同 Minecraft 版本进行构建适配
- [ ] 实现多版本兼容方案

> **备注：** 大型架构变更，需单独设计规划。当前分支聚焦 UI 现代化，多版本支持建议单独开分支。

---

### 3. UI 文案优化 — `[x]` 已完成

- [x] 修复 `en_us.json` 6 处编码损坏（`閳?` → `—` 破折号）— 行 327-332
- [x] 修复 `zh_cn.json` JSON 语法问题（逗号风格规范化）
- [x] 优化 AI 化表达：
  - [x] Balance hints: `"clearly ahead"` → `"exceeds consumption"` 等
  - [x] `"history buckets"` → `"data for trend analysis"`
- [x] 修复 Web Dashboard i18n 漏洞：
  - [x] `PowerNetwork.tsx`: `"Stored / Capacity"` → `t('power.kpi.storedCapacity')`
  - [x] `PowerNetwork.tsx`: `"ALL"` / `"TOP 10"` → `t('common.filterAll')` / `t('common.filterTop')`
- [x] 优化 Web 仪表盘文案：`"wait for the next observer cycle"` → `"waiting for the next update"`

> **后续可做：** 全局审查全大写标题（如 "OPERATIONS DASHBOARD"）是否保留为品牌风格。

---

### 4. 数据与逻辑 Review — `[ ]` 待开始

- [ ] 检查所有数据来源的正确性
- [ ] 审核计算逻辑（速率聚合、趋势判断、缓冲区估算等）

> **备注：** 任务范围较广，建议分模块逐步推进。可优先审查：
> 1. `HistoryRecorder` — 历史数据录制
> 2. `WebHighPrecisionSampler` — 高精度采样层
> 3. `PowerNetworkViewModelMapper` — 电力数据去重聚合
> 4. `CraftingDataCollector` — AE2 合成数据采集

---

## 二、开发与发布差异处理

### 5+6. Debug 工具管理 & 移除开发专用内容 — `[x]` 已完成

**实现方案：** 创建 `DevMode` 工具类，通过 `FMLLoader.isProduction()` 检测运行环境，保留全部代码但条件控制可见性。

- [x] 创建 `DevMode.java` 环境检测工具类
- [x] `ModItems.java`: 添加 `isDebugTerminalVisible()` 方法，保留物品注册
- [x] `ModCreativeTabs.java`: Debug Terminal 仅在开发环境显示
- [x] `ResourceTerminalFragment.java`: Dev 标签页仅在开发环境显示

**行为总结：**

| 功能 | 开发环境 (`gradlew runClient`) | 玩家版本（生产环境） |
|------|:---:|:---:|
| Debug Terminal 物品 | 创造标签页可见 | 隐藏 |
| Dev 组件展示页 | Tab 可见 | Tab 隐藏 |
| `/observer debug` 命令 | 可用 | 可用（保留） |
| Web 调试面板 (HP) | 可用 | 可用（保留，需手动开启） |
| DEBUG_10M_5S 图表窗口 | 被注释掉 | 被注释掉 |

> **设计决策：** `/observer debug` 命令和 Web 采样调试面板保留在生产环境，因为它们需要用户主动操作才会激活，不会干扰正常游玩。

**仍需处理（低优先级）：**

- [ ] `ChartWindow.DEBUG_10M_5S` — 当前已被注释掉，未暴露于 UI。后续可完全删除或始终隐藏。
- [ ] `SamplerDebugHandler` — Web API 调试端点当前无访问控制。后续可考虑在非 Dev 模式下禁用。
- [ ] `DevComponentsPageBuilder.java` — 文件保留，仅在 Dev Mode 下可访问。发布 JAR 时应包含此文件（不影响功能）。

---

## 三、低优先级优化

### 7. UI 设计细节优化 — `[ ]` 待开始

- [ ] 调整 Dialog 尺寸
- [ ] 优化界面排版
- [ ] 提升整体视觉体验

> **备注：** 需要用户具体指明哪些 Dialog/排版需要调整。

---

## 四、后续计划（非当前阶段）

- [ ] 上传模组至 CurseForge
- [ ] 上传模组至 Modrinth
- [ ] 准备发布宣传材料

---

## 本次变更摘要（2026-04-27）

### 修改的文件

| 文件 | 变更内容 |
|------|---------|
| `README.md` | 从零重写，包含完整功能介绍、使用说明、依赖项 |
| `src/.../DevMode.java` | **新增** 开发/玩家双模式检测工具类 |
| `src/.../registry/ModItems.java` | 添加 `isDebugTerminalVisible()` 方法 |
| `src/.../registry/ModCreativeTabs.java` | Debug Terminal 条件可见 |
| `src/.../client/modernui/ResourceTerminalFragment.java` | Dev 标签页条件可见 |
| `src/.../lang/en_us.json` | 修复 6 处编码损坏 + 优化 5 处文案 |
| `src/.../lang/zh_cn.json` | JSON 逗号风格规范化 + 优化中文文案 |
| `Data Visualization Dashboard/.../i18n.tsx` | 新增 3 个翻译键 + 优化 2 处文案 |
| `Data Visualization Dashboard/.../PowerNetwork.tsx` | 修复 3 处国际化漏洞 |

### 未完成项汇总

1. **多版本支持** — 需单独设计规划
2. **数据逻辑 Review** — 需分模块逐步推进
3. **UI 细节优化** — 需用户明确具体调整点
4. **截图补充** — 需实际进入游戏截取
5. **模组平台上传** — 发布阶段处理
