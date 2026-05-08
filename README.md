# Resource Observer

[![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1-blue?logo=neoforge)](https://neoforged.net/)
[![Java](https://img.shields.io/badge/Java-21-red?logo=java)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)
[![Modrinth](#)](#)
[![CurseForge](#)](#)

**Resource Observer** 是一个 Minecraft NeoForge 模组，提供**游戏内资源网络实时监控**。放置观察者方块、绑定到 AE2 存储网络或 Flux 能量网络，即可在终端 GUI 或 Web 仪表盘中查看实时数据、图表和告警。

---

## 核心功能

### 游戏内终端（Modern UI）
- **三标签页仪表盘：** 总览 / 存储网络 / 电力网络，实时更新
- **流量图表：** Hermite 插值 + EMA 平滑的吞吐量与库存曲线，支持多时间窗口
- **物品关注列表：** 固定关键物品到关注列表，快速跟踪生产/消耗/库存变化
- **存储网络浏览器：** 按节点/物品浏览 AE2 网络，查看容量、类型占用、缓冲区估算
- **合成下单：** 两阶段合成下单流程（计算方案 → Review → 确认），支持合成树可视化
- **电力监控：** Flux Networks / Mekanism 设备负载分布、过载告警、外储联动
- **拼音搜索：** 支持中文拼音模糊搜索物品（集成 JEC）

### 内置 Web 仪表盘
- **零依赖 HTTP 服务：** JDK 原生 HttpServer，默认 `127.0.0.1:28080`
- **独立前端：** React 18 + Tailwind CSS 4 + Recharts 构建的现代化 Web 仪表盘
- **RESTful API：** 实时数据接口，支持 Observer 枚举、KPI 详情、合成操作
- **高精度采样：** 1200 桶环形缓冲区（0.25s 粒度），自适应时间颗粒度

### 模组兼容性
| 模组 | 类型 | 说明 |
|------|------|------|
| **AE2** (Applied Energistics 2) | 必需 | 存储网络数据源 |
| **ModernUI** (icyllis) | 必需（客户端） | 游戏内终端 UI 框架 |
| **Flux Networks** | 可选 | 能量网络数据源 |
| **Mekanism** | 可选 | 能量单位转换 |
| **Draconic Evolution** | 可选 | 能量核心外部存储适配 |
| **JEC** (Just Enough Characters) | 可选（客户端） | 拼音搜索增强 |

---

## 游戏内截图

> *以下为截图预留位置，发布时请补充实际游戏截图。*

<!-- TODO: 添加截图 -->
| 总览面板 | 存储网络 | 电力网络 |
|:---:|:---:|:---:|
| ![总览](docs/screenshots/overview.png) | ![存储](docs/screenshots/storage.png) | ![电力](docs/screenshots/power.png) |

<!-- TODO: 添加 Web 仪表盘截图 -->
### Web 仪表盘
| Overview | Storage | Power |
|:---:|:---:|:---:|
| ![Web总览](docs/screenshots/web-overview.png) | ![Web存储](docs/screenshots/web-storage.png) | ![Web电力](docs/screenshots/web-power.png) |

---

## 使用说明

### 快速开始

1. **放置观察者方块：** 在目标网络附近放置 Observer Block。
2. **绑定网络：** 手持 Binding Tool，右键观察者方块（选中），再右键 AE2 控制器 / Flux 接口（绑定）。
3. **打开终端：** 手持 Resource Terminal 右键打开仪表盘 GUI。
4. **查看数据：** 在三个标签页之间切换，查看实时资源数据。

### 绑定工具操作流程

```
右键观察者方块 → 聊天栏提示 "已选中观察者 (x, y, z)"
    ↓
右键目标网络方块 → 聊天栏提示 "已绑定到 <网络类型>"
    ↓
重复上一步可绑定多个网络到同一个观察者
```

- **解除绑定：** Shift + 右键空气清除所有绑定
- **跨维度限制：** 观察者和目标方块必须在同一维度

### 资源终端使用

- **右键打开：** 手持终端右键打开 GUI
- **Shift 查看详情：** 在物品上 Shift 悬停查看绑定信息
- **点击 KPI 卡片：** 查看详细指标分解
- **★ 收藏物品：** 点击星标将物品加入关注列表

### Web 仪表盘访问

1. 启动 Minecraft 客户端/服务器，模组会自动启动 HTTP 服务
2. 浏览器访问 `http://127.0.0.1:28080`
3. 左侧选择 Observer → 右侧查看实时数据面板

配置文件：`config/resourceobserver-web.toml`

```toml
[server]
enabled = true
host = "127.0.0.1"
port = 28080
corsAllowAll = false
```

---

## 开发

### 环境要求

- **JDK 21**（推荐 [Eclipse Temurin](https://adoptium.net/)）
- **Gradle**（通过 `gradlew` 自动下载）

### 构建命令

```bash
# 构建模组 JAR
./gradlew build

# 启动 Minecraft 客户端（开发环境）
./gradlew runClient

# 启动专用服务器
./gradlew runServer

# 运行数据生成器
./gradlew runData

# 构建 Web 仪表盘
cd "Data Visualization Dashboard"
pnpm install
pnpm build
```

### 项目结构

```
├── src/main/java/          # Java 模组代码
│   └── com/yuyinrl/resourceobserver/
│       ├── client/         # 客户端 UI（ModernUI Fragment / Vanilla Screen）
│       │   ├── modernui/   # ModernUI Canvas 页面
│       │   ├── ui/         # ViewModel + render/ + format/
│       │   └── web/        # IconRenderer 客户端图标渲染
│       ├── integration/    # 模组集成适配器
│       ├── network/        # 网络 Payload（record 类型）
│       ├── registry/       # DeferredRegister 注册
│       ├── service/        # Web ↔ BlockEntity 桥接层（v0.8.0）
│       ├── web/            # 内置 HTTP 服务
│       │   ├── handler/    # API 端点处理器
│       │   └── util/       # QueryUtil 工具
│       └── world/          # 方块 / 实体 / 物品 / 数据
│           └── block/entity/
│               ├── ObserverBlockEntity   # 主实体（1083 行，委托架构）
│               ├── Ae2DataStore          # 采样数据容器
│               ├── Ae2Sampler            # AE2 采样器
│               └── Ae2CellProber         # Cell 反射探测
├── src/main/resources/     # 资源文件（纹理、语言、Web 静态文件）
├── Data Visualization Dashboard/  # Web 仪表盘前端（Vite + React）
├── docs/designdoc/         # 设计文档（Obsidian 兼容）
└── scripts/                # 开发脚本
```

### 代码规范

- 注释使用**中文**
- Java 21 特性（record、模式匹配、密封类）
- DeferredRegister 注册模式
- 适配器模式集成可选模组
- 委托模式分离采样/探测逻辑（Ae2Sampler / Ae2CellProber）
- 服务桥接层隔离 Web ↔ BlockEntity 耦合（ObserverService）
- 详见 `docs/designdoc/11-代码规范.md`

---

## 依赖项

### Minecraft 模组依赖

| 依赖 | 版本 | 类型 |
|------|------|------|
| NeoForge | 1.21.1-21.1.206+ | 平台 |
| AE2 (Applied Energistics 2) | — | 必需 |
| ModernUI (icyllis) | 3.12.0 | 必需（客户端） |
| Flux Networks | — | 可选 |
| Mekanism | 1.21.1-10.7.18.84 | 可选 |
| Draconic Evolution | — | 可选 |
| JEC (Just Enough Characters) | — | 可选（客户端） |

### Web 仪表盘技术栈

| 技术 | 版本 |
|------|------|
| React | 18.3 |
| Vite | 6.3 |
| Tailwind CSS | 4.1 |
| Recharts | 2.15 |
| Radix UI | — |
| Material UI | 7.3 |
| React Router | 7.13 |

---

## 许可证

本项目基于 [MIT License](LICENSE) 发布。

---

## 作者

- **YuyinRL** — 设计、开发与维护
- 反馈与问题：请在 [GitHub Issues](#) 提交

---

*Made with ❤️ for the Minecraft modding community.*
