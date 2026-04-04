# 新前端重建设计（简洁实用型）

## 目标
在不改后端接口的前提下，重建一个简洁、稳定、可直接使用的 AI 聊天前端，覆盖恋爱助手与 LoLoManus 两个入口。

## 约束
- 保持目录：`yu-ai-agent-frontend`
- 保持技术栈：Vue3 + Vite
- 保持后端接口：`/api/ai/love_app/chat/sse`、`/api/ai/manus/chat`
- 优先可用性和可维护性，不做花哨动画

## 方案
- 采用单页双标签布局：`恋爱助手`、`LoLoManus`
- 每个标签独立会话区与输入区
- 使用 `fetch + ReadableStream` 处理 SSE 流式返回
- 新增统一状态提示（连接中、完成、异常）
- 新增基础配置区：API Base URL

## 组件设计
- `App.vue`：主框架（标题、标签、配置）
- `components/ChatPanel.vue`：通用聊天面板
- `utils/sse.js`：SSE 解析与流事件分发
- `style.css`：简洁主题样式

## 错误处理
- 网络错误：提示并保留已返回内容
- 流解析错误：记录并终止当前请求
- 重复发送：请求中禁用发送按钮

## 验收标准
- 本地 `npm run dev` 可启动
- 能调用两个后端接口并看到流式结果
- 页面无报错，交互完整可用
