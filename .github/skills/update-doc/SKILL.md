---
name: update-doc
description: 更新设计文档与更新日志（触发词 /update-doc 或"更新文档"）。汇总今日 git 变更，分类追加到 docs/designdoc/13-更新日志.md，并在涉及架构变更时同步更新 05/06/07/08 号设计文档。
---

# update-doc — 更新设计文档与更新日志

## 触发
- 斜杠：`/update-doc`
- 自然语言：用户说"更新文档"

## 步骤

### 1. 收集今日变更
执行：
```
git --no-pager log --since="today" --oneline
git --no-pager diff --stat
```
回顾当前 session 的 plan.md、checkpoint、已完成 todo，归类为 **新增 / 修复 / 变更**。

### 2. 更新 `docs/designdoc/13-更新日志.md`
- 在最新版本号下追加条目，使用以下分类标题：
  - `### 新增`
  - `### 修复`
  - `### 变更`
- 每条引用相关文档：`[[wikilink]]`
- 更新底部"文档更新记录"表格

### 3. 同步设计文档（如有架构变更）
按需更新：
- `docs/designdoc/05-网络通信.md`
- `docs/designdoc/06-UI系统.md`
- `docs/designdoc/07-数据模型.md`
- `docs/designdoc/08-Web端设计.md`

### 4. 输出
向用户简要汇报：更新了哪些文件、各自新增了什么条目。

## 规则
- 不读取整个项目，优先 git 信息和局部文件
- 信息不足时再请求用户澄清
- 输出必须结构化
