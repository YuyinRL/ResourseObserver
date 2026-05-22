---
name: mod-design
description: 设计 Minecraft Mod 功能（触发词 /mod-design）。输出功能、交互、服务端/客户端逻辑、数据同步、类设计、注册项、边界与实现步骤。
---

# mod-design — Minecraft Mod 功能设计

## 触发
- 斜杠：`/mod-design`

## 输出格式

1. **功能**：要实现的玩法/能力
2. **交互**：玩家输入、UI、方块交互
3. **服务端逻辑**：tick、数据持久化、权威计算
4. **客户端逻辑**：渲染、输入、UI
5. **数据同步**：Payload / Codec / 触发时机
6. **类设计**：包路径、关键类与职责
7. **注册项**：BLOCKS / ITEMS / BLOCK_ENTITIES / CREATIVE_TABS / PAYLOADS
8. **边界**：维度切换、卸载、玩家断线、并发
9. **实现步骤**：从注册到 UI 的有序清单

## 规则
- 遵循 NeoForge 1.21.1 + Java 21 的项目约定
- 注释一律中文
- 服务端/客户端代码严格分离
