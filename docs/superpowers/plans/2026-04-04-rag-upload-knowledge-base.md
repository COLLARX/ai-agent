# Upload Markdown to Build RAG Knowledge Base Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an end-to-end feature that uploads a Markdown file from frontend and persists vectorized chunks into PostgreSQL `vector_store`.

**Architecture:** Add a backend ingest service + upload controller endpoint. Reuse existing primary `VectorStore` to write chunked `Document` items with `sourceType=knowledge_upload` metadata. Add a frontend upload panel and helper utils for URL building + validation.

**Tech Stack:** Spring Boot Web + Spring AI VectorStore, Vue 3 + Vite, Node test runner, JUnit 5 + Mockito

---

### Task 1: Backend TDD for upload ingest

**Files:**
- Create: `src/test/java/com/yupi/yuaiagent/rag/RagKnowledgeIngestServiceTest.java`
- Create: `src/main/java/com/yupi/yuaiagent/rag/RagKnowledgeIngestService.java`
- Create: `src/main/java/com/yupi/yuaiagent/controller/RagController.java`

- [ ] Write failing tests for markdown validation and chunk ingestion metadata
- [ ] Implement service with split + vectorStore.add
- [ ] Implement controller `POST /api/rag/upload-md`

### Task 2: Frontend TDD for upload helpers and panel

**Files:**
- Create: `yu-ai-agent-frontend/src/utils/upload.test.js`
- Create: `yu-ai-agent-frontend/src/utils/upload.js`
- Create: `yu-ai-agent-frontend/src/components/UploadKnowledgePanel.vue`
- Modify: `yu-ai-agent-frontend/src/App.vue`
- Modify: `yu-ai-agent-frontend/src/style.css`

- [ ] Write failing tests for upload URL and markdown file validation
- [ ] Implement helper utils and make tests pass
- [ ] Add upload panel to app shell and wire to backend endpoint

### Task 3: Verification

**Files:**
- Modify: `docs/superpowers/specs/2026-04-04-rag-upload-design.md` (optional summary)

- [ ] Run backend targeted tests for new service
- [ ] Run frontend `npm test` and `npm run build`
- [ ] Manual smoke: upload one `.md` and verify API success response
