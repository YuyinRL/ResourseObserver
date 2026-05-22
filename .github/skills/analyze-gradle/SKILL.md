---
name: analyze-gradle
description: 分析 Gradle 构建配置（触发词 /analyze-gradle）。检查 Java/Gradle 版本、依赖冲突、插件配置、mixin 配置，输出问题、建议与风险。
---

# analyze-gradle — Gradle 配置分析

## 触发
- 斜杠：`/analyze-gradle`

## 检查项
- Java 版本（toolchain / sourceCompatibility）
- Gradle 版本（wrapper）
- 依赖冲突（重复、版本不兼容）
- 插件配置（顺序、版本约束）
- Mixin 配置（refmap、混入目标）

## 输出格式

1. **概述**：构建系统当前状态
2. **问题**：明确列出发现的问题
3. **建议**：修复方案
4. **风险**：升级/修改可能引发的副作用

## 规则
- 优先读取 `build.gradle`、`gradle.properties`、`settings.gradle`、`gradle/wrapper/`
- 不深入 src/
