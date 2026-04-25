---
name: commit
description: 汇总并提交所有修改（触发词 /commit 或"汇总提交"）。git add -A，按更新日志生成结构化 commit message，单次提交所有变更。
---

# commit — 汇总提交

## 触发
- 斜杠：`/commit`
- 自然语言：用户说"汇总提交"

## 步骤

### 1. 收集变更
```
git --no-pager diff --stat HEAD
```
将变更分类为 **新增 / 修复 / 变更**。

### 2. 暂存
```
git add -A
```

### 3. 生成提交日志
- 版本号与内容来源：`docs/designdoc/13-更新日志.md` 最新版本条目
- 每条简洁单行，无对应分类时省略该标题
- 格式：

```
[v版本号] 更新摘要（一句话概括）

### 新增
- 功能描述 1
- 功能描述 2

### 修复
- 修复描述 1

### 变更
- 变更描述 1

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

### 4. 提交
使用 `git commit -F <临时文件>` 一次性汇总提交。

### 5. 输出
- commit hash
- 变更文件数量
- 提交日志摘要

## 规则
- 不分多次提交，单次汇总
- 必须包含 Co-authored-by trailer
