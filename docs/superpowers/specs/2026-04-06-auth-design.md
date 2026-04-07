# 注册登录与账号化会话设计

## 目标
在现有 `LoveApp`、`LoLoManus`、私有 Markdown 上传、会话持久化的基础上，引入正式的注册与登录能力，并将现有业务身份从前端匿名 `userId` 切换为真实账号身份。

本次设计的目标是：
- 支持 `username + password` 注册与登录
- 使用 `JWT + localStorage` 维护登录态
- 未登录用户直接显示登录 / 注册页，不能进入主应用
- `LoveApp`、`LoLoManus`、Markdown 上传、历史能力全部鉴权
- 业务接口统一从 JWT 中获取用户身份，不再信任前端传递的身份参数
- 旧匿名 `userId` 数据不迁移，后续由人工直接清理

## 已确认边界

### 登录方式
- 注册方式：`username + password`
- 登录态：`JWT + localStorage`
- 未登录时：直接显示登录 / 注册页

### 数据迁移
- 旧匿名 `userId` 数据不迁移
- 已存在的匿名知识、匿名会话数据后续直接清理
- 本次不做匿名身份与正式账号的绑定逻辑

### 生效范围
- `LoveApp` 聊天需要登录
- `LoLoManus` 聊天需要登录
- Markdown 上传需要登录
- 历史会话能力需要登录

## 核心身份模型

### 1. 真实账号是唯一正式身份
新增 `user` 表，系统中的正式身份来源统一为账号主键 `user.id`。

后续所有需要用户归属判断的业务都应使用这个真实 `userId`：
- `LoveApp` 会话
- `LoLoManus` 会话
- LoLoManus 私有知识向量数据
- 历史会话读取

### 2. conversation_id 和 user_id 的关系
`conversation_id` 与 `user_id` 是一对多归属关系：

- 一个 `user_id` 可以拥有多个 `conversation_id`
- 一个 `conversation_id` 只能属于一个 `user_id`

这意味着：
- `user_id` 表示“这次会话属于哪个用户”
- `conversation_id` 表示“这个用户的一次具体聊天会话”

查询或读取某次聊天时，必须同时满足：
1. 能找到这条 `conversation_id`
2. 该会话的 `user_id` 等于当前 JWT 中解析出的用户 id

### 3. 匿名 userId 退场
现有前端匿名 `userId` 仅作为历史过渡方案存在，不再是正式业务身份。

本次注册登录上线后：
- 聊天请求不再依赖匿名 `userId`
- 上传请求不再依赖匿名 `userId`
- 业务用户身份统一来自 JWT
- 前端匿名身份工具从主链路移除

## 存储设计

### 1. 新增 user 表
建议字段：
- `id`
- `username`
- `password_hash`
- `created_at`
- `updated_at`

约束：
- `username` 唯一
- `password_hash` 只存加密后的密码，不存明文

### 2. 调整 love_app_conversation
当前 `love_app_conversation` 只表示会话本身，本次需要新增：
- `user_id`

作用：
- 让 `LoveApp` 的每条会话都能归属于一个真实用户
- 支持按当前登录用户隔离读取历史

### 3. 保留 manus_conversation.user_id
`manus_conversation` 当前已存在 `user_id` 字段，本次不删除、不改列名，但语义切换为：
- 原来：匿名用户 id
- 现在：真实账号 id

### 4. message 表保持简洁
`love_app_message`
`manus_message`

这两张表不需要新增 `user_id`，因为可以通过：
- `message.conversation_id`
- `conversation.user_id`

完成归属判断。

这样可以避免消息表重复存储用户身份，保持结构清晰。

### 5. 私有知识向量归属切换
`manus_private_vector_store` 不需要改物理表结构。

但写入 metadata 时，`userId` 的含义切换为真实账号 id，而不是匿名 id。

这意味着 LoLoManus 的私有知识检索将基于：
- 当前登录用户 JWT 中解析出的真实 `userId`

而不是前端传入的任意参数。

## 接口设计

### 1. 认证接口
新增：
- `POST /auth/register`
- `POST /auth/login`
- `GET /auth/me`

职责：

`POST /auth/register`
- 接收 `username + password`
- 校验用户名是否重复
- 写入 `user`
- 返回注册后的登录结果或最小用户信息

`POST /auth/login`
- 校验用户名和密码
- 登录成功后签发 JWT
- 返回：
  - `token`
  - `userInfo { id, username }`

`GET /auth/me`
- 读取 Bearer Token
- 解析当前登录用户
- 返回当前用户信息

### 2. 业务接口鉴权
以下接口全部要求登录：
- `LoveApp` 聊天接口
- `LoLoManus` 聊天接口
- Markdown 上传接口
- 历史会话相关接口

请求方式统一为：
- `Authorization: Bearer <jwt>`

### 3. 去掉前端身份直传
当前前端会在部分请求中传 `userId`。

注册登录上线后，业务身份不再从前端参数读取，而应统一从 JWT 中解析。

这是本次最重要的安全边界之一。否则会出现：
- token 属于 A 用户
- 但请求参数里伪造 B 用户的 `userId`

从而带来越权风险。

因此推荐调整为：
- `AiController` 从 JWT 中取当前用户身份
- `RagController` 从 JWT 中取当前用户身份
- 前端不再把 `userId` 作为正式身份参数发送

## 前端设计

### 1. 入口页切换
应用启动流程：
1. 前端读取 `localStorage` 中的 `auth_token`
2. 如果没有 token，只显示登录 / 注册页
3. 如果有 token，调用 `/auth/me` 校验
4. 校验成功，进入现有主应用
5. 校验失败，清空 token，回到登录 / 注册页

### 2. 登录成功后的状态管理
登录成功后：
- 将 JWT 写入 `localStorage`
- 缓存当前用户信息
- 后续聊天、上传、历史请求统一自动带 Bearer Token

### 3. 主应用渲染
登录成功后才渲染：
- 上传面板
- LoveApp 页签
- LoLoManus 页签
- 聊天面板

### 4. 退出登录
前端需要具备退出登录能力：
- 清空 `auth_token`
- 清空当前用户信息
- 回到登录 / 注册页

## 后端鉴权设计

### 1. JWT 作为唯一会话凭证
JWT 建议承载最小身份信息：
- `userId`
- `username`

后端通过统一认证逻辑完成：
- JWT 解析
- 用户身份注入
- 无 token / token 无效时拦截请求

### 2. 会话归属校验
所有读取、追加、继续某个 `conversation_id` 的逻辑都必须做归属校验：
- 当前 `conversation_id` 是否属于当前登录用户

如果不属于，则拒绝访问。

### 3. 私有知识归属校验
LoLoManus 私有知识的写入与检索都基于当前登录用户：
- 上传时写入 metadata.userId = 当前登录用户 id
- 检索时按当前登录用户 id 过滤

## 错误处理

### 注册错误
- 用户名为空：返回明确错误
- 密码为空：返回明确错误
- 用户名已存在：返回明确错误

### 登录错误
- 用户不存在：返回明确错误
- 密码错误：返回明确错误
- token 生成失败：记录错误并返回统一失败结果

### 鉴权错误
- 未携带 token：返回未登录
- token 无效：返回未登录
- token 过期：返回未登录

### 业务越权
- 当前用户访问不属于自己的会话：拒绝
- 当前用户尝试读取不属于自己的私有知识：拒绝

## 测试设计

### 后端测试
- 注册成功
- 重复用户名注册失败
- 登录成功
- 错误密码登录失败
- 未登录访问 LoveApp 聊天失败
- 未登录访问 LoLoManus 聊天失败
- 未登录上传 Markdown 失败
- 已登录后 LoveApp 能按当前用户创建 / 读取会话
- 已登录后 LoLoManus 能按当前用户创建 / 读取会话
- 会话归属校验生效，不能访问别人的 `conversation_id`
- 上传的私有知识归属于当前登录用户
- LoLoManus 检索只命中当前登录用户的私有知识

### 前端测试
- 无 token 时显示登录 / 注册页
- 有 token 时调用 `/auth/me`
- `/auth/me` 成功后进入主应用
- `/auth/me` 失败后清空 token 并回到登录页
- 登录成功后 token 正确写入 `localStorage`
- 聊天与上传请求自动带 Bearer Token
- 退出登录后回到登录页

## 为什么选择方案 A

相较于继续沿用匿名 `userId`，或者引入双层用户映射模型，方案 A 的优势是：
- 身份模型最简单
- 与现有 `manus_conversation.user_id` 结构天然兼容
- 能最快把当前项目统一到真实账号体系
- 不需要为旧匿名数据设计迁移链路
- 安全边界清晰，后续维护成本低

## 本次不做的事情
- 旧匿名 `userId` 数据迁移
- 邮箱注册
- 手机号注册
- 第三方登录
- 刷新 token 机制
- 找回密码
- 用户资料页

这些都可以在后续账号体系稳定后再扩展。
