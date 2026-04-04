# A 方案（实用聊天台）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `yu-ai-agent-frontend` 内完全替换旧前端，交付一个稳定、简洁、可直接联调的双智能体聊天页。

**Architecture:** 使用单页结构承载两类聊天入口，抽离 `ChatPanel` 作为复用组件，使用 `utils/sse.js` 统一处理流式解析，使用 `utils/chat.js` 统一 URL 组装规则。

**Tech Stack:** Vue 3, Vite, 原生 fetch ReadableStream, Node 内置 test runner（TDD）

---

### Task 1: 清理旧前端结构并统一入口

**Files:**
- Modify: `yu-ai-agent-frontend/src/main.js`
- Modify: `yu-ai-agent-frontend/src/App.vue`
- Delete: `yu-ai-agent-frontend/src/router/index.js`
- Delete: `yu-ai-agent-frontend/src/views/Home.vue`
- Delete: `yu-ai-agent-frontend/src/views/LoveMaster.vue`
- Delete: `yu-ai-agent-frontend/src/views/SuperAgent.vue`
- Delete: `yu-ai-agent-frontend/src/components/HelloWorld.vue`
- Delete: `yu-ai-agent-frontend/src/components/ChatRoom.vue`
- Delete: `yu-ai-agent-frontend/src/components/AiAvatarFallback.vue`
- Delete: `yu-ai-agent-frontend/src/components/AppFooter.vue`

- [x] 建立无路由单页入口
- [x] 完全移除旧视图与旧路由文件

### Task 2: TDD 构建核心前端能力

**Files:**
- Create: `yu-ai-agent-frontend/src/utils/sse.test.js`
- Create: `yu-ai-agent-frontend/src/utils/chat.test.js`
- Create: `yu-ai-agent-frontend/src/utils/chat.js`
- Modify: `yu-ai-agent-frontend/src/utils/sse.js`
- Modify: `yu-ai-agent-frontend/src/components/ChatPanel.vue`
- Modify: `yu-ai-agent-frontend/package.json`

- [ ] 先写失败测试：SSE 解析与请求 URL 规则
- [ ] 最小实现使测试通过
- [ ] 在 ChatPanel 中接入测试覆盖的工具函数

### Task 3: UI 样式与运行验收

**Files:**
- Modify: `yu-ai-agent-frontend/src/style.css`
- Modify: `yu-ai-agent-frontend/src/api/index.js`

- [ ] 验证 `npm test` 通过
- [ ] 验证 `npm run build` 通过
- [ ] 验证 `npm run dev` 可启动并输出访问地址
