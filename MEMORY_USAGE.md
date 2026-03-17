# 长期记忆使用指南

## 功能说明

### 1. 长期记忆向量化存储
- 自动将滑动窗口外的历史消息进行 Embedding 向量化
- 异步存储到 PgVector 数据库，避免阻塞主流程
- 默认保留最近 10 条消息在短期记忆中

### 2. 动态召回机制
- 根据当前问题自动检索相关历史记忆
- 使用向量相似度匹配早期关键信息
- 将召回内容作为 Context 补充到 Prompt

## 配置步骤

### 1. 启用数据库配置
编辑 `application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/your_db
    username: your_user
    password: your_password
  ai:
    vectorstore:
      pgvector:
        index-type: HNSW
        dimensions: 1536
        distance-type: COSINE_DISTANCE
```

### 2. 初始化数据库表
```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE vector_store (
    id UUID PRIMARY KEY,
    content TEXT,
    metadata JSONB,
    embedding vector(1536)
);
CREATE INDEX ON vector_store USING hnsw (embedding vector_cosine_ops);
```

## 使用示例

```java
@Autowired
private MemoryService memoryService;

@Autowired
private ChatClient.Builder chatClientBuilder;

// 创建基础 Agent
ToolCallAgent baseAgent = new ToolCallAgent(tools);
baseAgent.setChatClient(chatClientBuilder.build());

// 创建带记忆的 Agent
MemoryEnhancedAgent agent = new MemoryEnhancedAgent(baseAgent, memoryService);

// 运行对话
agent.run("我喜欢吃苹果");
// ... 多轮对话后
agent.run("我喜欢吃什么？"); // 自动召回早期信息
```

## 核心优势

1. **Token 节省**：只保留最近 10 条消息，大幅减少 API 调用成本
2. **防止遗忘**：早期关键信息通过向量检索动态召回
3. **异步处理**：归档过程不阻塞对话流程
4. **精准召回**：基于语义相似度匹配，而非简单的关键词
