# 上传 Markdown 构建 RAG 知识库 设计

## 目标
支持前端上传 `.md` 文件，后端完成切分和向量化，并持久化到 PostgreSQL `vector_store`。

## 数据策略
- 复用 `vector_store` 单表
- metadata:
  - `sourceType=knowledge_upload`
  - `docId`
  - `fileName`
  - `uploadedAt`
  - `chunkIndex`

## 接口
- `POST /api/rag/upload-md`
- 表单字段: `file`
- 返回: `docId`, `fileName`, `chunks`, `message`

## 前端
- 新增上传面板
- 文件校验: 非空 + `.md`
- 上传状态: 就绪/上传中/完成/失败

## 验证
- 后端: `RagKnowledgeIngestServiceTest`, `RagControllerTest`
- 前端: `upload.test.js` + 既有 util 测试
- 构建: `npm run build`
