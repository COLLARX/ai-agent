# LoLoManus 私有 RAG 与双会话存储设计

## 目标
在不引入登录注册功能的前提下，完成以下产品边界与存储结构调整：

- `恋爱助手` 继续只基于预处理公共恋爱知识回答
- `LoLoManus` 支持基于当前匿名用户上传 Markdown 的私有检索增强
- 恋爱助手与 LoLoManus 的知识存储物理拆分
- 恋爱助手与 LoLoManus 的历史会话和消息持久化物理拆分
- 前端通过匿名 `userId` 在同一浏览器内维持私有知识和私有会话连续性

## 已确认边界

### 产品边界
- 用户上传的 Markdown 只增强 `LoLoManus`
- 用户上传的 Markdown 不增强 `恋爱助手`
- `恋爱助手` 与 `LoLoManus` 是两个独立入口

### 身份边界
- 当前无登录注册
- 前端生成并持久化匿名 `userId`
- 同一浏览器后续上传和 `LoLoManus` 对话都复用该匿名 `userId`

### 存储边界
- 不采用单张向量表混存方案
- 采用多张表分域存储
- 历史消息采用“会话表 + 消息表”两层结构
- 恋爱助手和 LoLoManus 的历史消息分表存储

## 存储方案

### 知识存储

#### 1. 恋爱助手公共知识向量表
建议命名：`love_app_vector_store`

职责：
- 存储恋爱助手的预处理公共恋爱文档 chunk
- 仅服务 `恋爱助手`
- 启动时由本地预置 Markdown 导入

特点：
- 与用户无关
- 无需 `userId`
- 只承载稳定的公共知识

#### 2. LoLoManus 私有知识向量表
建议命名：`manus_private_vector_store`

职责：
- 存储用户上传的私有 Markdown chunk
- 仅服务 `LoLoManus`
- 检索时按匿名 `userId` 做隔离

metadata 至少包含：
- `userId`
- `docId`
- `fileName`
- `uploadedAt`
- `chunkIndex`
- `sourceType=knowledge_upload`

这个表中不存恋爱助手公共知识，避免知识域污染。

### 历史对话存储

#### 3. 恋爱助手会话表
建议命名：`love_app_conversation`

职责：
- 记录恋爱助手的会话主记录
- 为每次聊天提供独立会话归档

建议字段：
- `id`
- `conversation_id`
- `title`
- `created_at`
- `updated_at`

#### 4. 恋爱助手消息表
建议命名：`love_app_message`

职责：
- 存储恋爱助手每个会话下的消息明细

建议字段：
- `id`
- `conversation_id`
- `role`
- `content`
- `created_at`
- `sequence_no`

#### 5. LoLoManus 会话表
建议命名：`manus_conversation`

职责：
- 记录 LoLoManus 的会话主记录
- 会话与匿名 `userId` 绑定

建议字段：
- `id`
- `conversation_id`
- `user_id`
- `title`
- `created_at`
- `updated_at`

#### 6. LoLoManus 消息表
建议命名：`manus_message`

职责：
- 存储 LoLoManus 每个会话下的消息明细

建议字段：
- `id`
- `conversation_id`
- `role`
- `content`
- `created_at`
- `sequence_no`

本次优先持久化最终消息：
- `user`
- `assistant`

不把工具调用中间过程、推理轨迹、调试日志写入长期消息表。

## 为什么采用拆表方案

### 对比单表混存
单表方案虽然初期开发快，但有以下明显问题：

- 公共知识与私有知识混在一起，排查困难
- 恋爱助手误读私有知识的风险更高
- 后续做数据迁移、删除、统计和治理会越来越复杂
- 聊天记录和知识记录容易耦合到一套难维护的模型上

### 拆表收益
- 产品边界与物理存储边界一致
- 排查问题时能快速定位到具体产品线
- 恋爱助手与 LoLoManus 可以独立演进
- 后续若增加文档管理、删除、统计、导出能力，扩展点更清晰

## 前端设计

### 匿名 userId
前端新增匿名身份工具：

- 首次打开页面时生成匿名 `userId`
- 使用 `localStorage` 持久化
- 上传私有 Markdown 时带上 `userId`
- 访问 `LoLoManus` 时带上 `userId`

推荐格式：
- `anon-<timestamp>-<random>`

### 上传面板
上传面板定位只服务 `LoLoManus`，必须明确展示提示：

> 上传知识仅增强 LoLoManus，不影响恋爱助手

行为要求：
- 只允许 `.md` 文件
- 文件为空时阻止上传
- 成功后显示 `docId`、文件名、切块数
- 失败时显示清晰错误

### 聊天入口

#### 恋爱助手
- 使用自己的 `conversationId`
- 不需要 `userId` 来做知识召回
- 后端只读 `love_app_vector_store`
- 会话和消息写入 `love_app_conversation` 与 `love_app_message`

#### LoLoManus
- 使用自己的 `conversationId`
- 请求中附带 `userId`
- 后端只读 `manus_private_vector_store`
- 会话和消息写入 `manus_conversation` 与 `manus_message`

## 后端设计

### 公共知识链路
`恋爱助手` 继续保持现有预处理公共知识模式，但存储目标调整为专属公共向量表。

要求：
- 启动时加载预置恋爱 Markdown
- 只写入 `love_app_vector_store`
- 检索时只查 `love_app_vector_store`

### 私有知识上传链路
扩展现有 Markdown 上传能力，使其专门服务 LoLoManus 私有知识库。

上传接口职责：
1. 校验文件非空且为 `.md`
2. 校验 `userId` 非空
3. 读取 Markdown
4. 切块
5. 写入 `manus_private_vector_store`
6. 返回上传结果

### LoLoManus 私有知识增强链路
LoLoManus 对话时：
1. 接收 `message + conversationId + userId`
2. 基于 `userId` 从 `manus_private_vector_store` 检索私有知识
3. 将结果注入 LoLoManus 上下文
4. 生成回答
5. 落库会话和消息

当用户没有上传私有知识时：
- 不视为错误
- 直接降级为无私有知识增强的 LoLoManus 对话

### 历史消息持久化链路

#### 恋爱助手
- 首次对话时创建或确认 `love_app_conversation`
- 每轮 user / assistant 消息写入 `love_app_message`

#### LoLoManus
- 首次对话时创建或确认 `manus_conversation`
- 每轮 user / assistant 消息写入 `manus_message`
- 会话与匿名 `userId` 绑定

## 数据流

### LoLoManus 上传流
1. 前端选择 Markdown
2. 前端读取匿名 `userId`
3. 提交 `file + userId`
4. 后端写入 `manus_private_vector_store`
5. 前端展示上传结果

### LoLoManus 对话流
1. 用户发送消息
2. 前端提交 `conversationId + userId + message`
3. 后端按 `userId` 检索 `manus_private_vector_store`
4. 把召回结果注入 LoLoManus
5. 生成回答
6. 把 user / assistant 消息写入 `manus_message`

### 恋爱助手对话流
1. 用户发送消息
2. 前端提交 `conversationId + message`
3. 后端查询 `love_app_vector_store`
4. 生成回答
5. 把 user / assistant 消息写入 `love_app_message`

## 错误处理

### 上传错误
- 文件为空：返回明确错误
- 扩展名不是 `.md`：返回明确错误
- `userId` 缺失：返回明确错误
- Markdown 无可用内容：返回明确错误

### 检索错误
- 私有知识不存在：降级为普通对话
- 私有知识检索异常：记录日志并尽量不阻断主对话

### 持久化错误
- 消息写入失败时至少要记录错误日志
- 若会话主记录不存在，应先补建再写消息

## 测试设计

### 后端测试
- 上传服务能把 Markdown 写入 `manus_private_vector_store`
- 上传服务写入 metadata 时包含 `userId`
- `userId` 缺失时上传失败
- LoLoManus 检索只命中当前 `userId` 的私有知识
- 恋爱助手只查询 `love_app_vector_store`
- 恋爱助手消息写入 `love_app_message`
- LoLoManus 消息写入 `manus_message`

### 前端测试
- 匿名 `userId` 会生成并持久化
- 上传请求会附带 `userId`
- LoLoManus 请求会附带 `userId`
- 上传面板显示“仅增强 LoLoManus，不影响恋爱助手”
- 恋爱助手请求不依赖私有知识身份

## 验收标准
- 恋爱助手与 LoLoManus 的知识存储分表完成
- 恋爱助手与 LoLoManus 的会话和消息持久化分表完成
- 用户上传的 Markdown 仅进入 `manus_private_vector_store`
- LoLoManus 只检索当前匿名用户自己的私有知识
- 恋爱助手仍然只基于公共恋爱知识回答
- 上传面板文案明确说明只增强 LoLoManus

## 后续可扩展项
- `manus_private_document` 文档主表，用于文档列表、删除与状态管理
- 会话标题自动生成
- 历史会话分页查询
- 文档删除后联动删除向量 chunk

