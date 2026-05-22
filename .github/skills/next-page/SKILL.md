---
name: next-page
description: 设计 Next.js 页面（触发词 /next-page）。输出页面目标、结构、组件、状态、API、交互、错误处理、样式与文件布局。
---

# next-page — Next.js 页面设计

## 触发
- 斜杠：`/next-page`

## 输出格式

1. **页面目标**：用户使用此页面要完成什么
2. **结构**：布局区块（header / main / sidebar / ...）
3. **组件**：拆分清单与复用关系
4. **状态**：local / global / server 状态来源
5. **API**：调用的端点、入参、返参
6. **交互**：点击/输入/导航行为
7. **错误处理**：加载失败、空数据、权限
8. **样式**：Tailwind / CSS Modules / 主题
9. **文件**：建议的文件路径与命名

## 规则
- 优先 App Router 约定
- 服务端组件优先，必要时再 'use client'
