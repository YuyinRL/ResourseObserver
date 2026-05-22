# 自定义 Skills

本目录包含 Copilot CLI 的用户级自定义 skills。每个子目录是一个独立 skill，含 `SKILL.md`（YAML frontmatter + 指令正文）。

## 全局规则（适用于所有 skill）

- 不读取整个项目，优先局部文件
- 优先使用 git 信息（log / diff / status）
- 信息不足时主动请求用户澄清
- 避免大范围扫描
- 输出必须结构化（带分级标题/列表）
- 修改前说明影响范围
- 不确定的地方必须明确标注

## Skills 列表

| 触发词 | 用途 |
|--------|------|
| `/update-doc` | 更新设计文档与更新日志 |
| `/commit` | 汇总提交所有修改 |
| `/design` | 口语需求转结构化设计 |
| `/debug` | 分析报错与异常 |
| `/analyze-java` | 分析 Java 类结构 |
| `/analyze-gradle` | 分析 Gradle 配置 |
| `/mod-design` | Minecraft Mod 功能设计 |
| `/next-page` | Next.js 页面设计 |
| `/api-flow` | API 数据流分析 |
| `/refactor` | 重构建议 |

## 使用

在 Copilot CLI 内：
- `/skills` 查看与管理
- 触发：直接发送 `/update-doc`、`/commit` 等，或用对应中文别名
