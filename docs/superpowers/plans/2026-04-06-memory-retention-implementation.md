# Long-Term Memory Retention Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce a 5-day retention window for archived long-term chat memory stored in `vector_store`.

**Architecture:** Keep the current memory archive and retrieval flow intact. Add a small cleanup step in `MemoryService.archiveToLongTerm(...)` that deletes expired `chat_memory` rows from `vector_store` before new archival writes, and make the retention window configurable with a default of 5 days.

**Tech Stack:** Spring Boot, Spring AI, PostgreSQL/pgvector, JdbcTemplate, JUnit 5, Mockito

---

### Task 1: Add retention cleanup tests

**Files:**
- Modify: `D:/develop/code/yu-ai-agent/src/test/java/com/yupi/yuaiagent/memory/MemoryServiceArchiveFilterTest.java`
- Modify: `D:/develop/code/yu-ai-agent/src/test/java/com/yupi/yuaiagent/memory/MemoryServiceTest.java`

- [ ] Write a failing test proving expired `chat_memory` rows are deleted before archival.
- [ ] Run the targeted memory tests and confirm the new test fails for the expected reason.
- [ ] Add one failing test proving cleanup failure does not block archival.

### Task 2: Implement incremental cleanup in MemoryService

**Files:**
- Modify: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/memory/MemoryService.java`
- Modify: `D:/develop/code/yu-ai-agent/src/main/resources/application.yml`

- [ ] Add a configurable retention-days field with default `5`.
- [ ] Add a JDBC-backed delete for expired rows where `sourceType = chat_memory`.
- [ ] Call cleanup at the start of `archiveToLongTerm(...)`.
- [ ] Guard cleanup so archival still proceeds if JDBC is absent or cleanup fails.

### Task 3: Verify and review

**Files:**
- Modify only if tests reveal a necessary fix

- [ ] Run targeted tests for `MemoryService*`.
- [ ] Inspect the diff to confirm only long-term memory retention behavior changed.
- [ ] Review for safety: no effect on `manus_private_vector_store`, `manus_message`, or non-`chat_memory` rows.
