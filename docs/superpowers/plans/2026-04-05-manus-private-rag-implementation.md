# LoLoManus Private RAG Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build isolated storage and retrieval for LoLoManus private Markdown knowledge, keep LoveApp on public knowledge only, and persist chat history for both products with separate conversation/message tables.

**Architecture:** Split the backend into two knowledge domains and two chat-history domains. LoveApp keeps its dedicated public vector store plus its own conversation/message persistence. LoLoManus gains a dedicated private vector store filtered by anonymous `userId`, plus its own conversation/message persistence. The frontend generates a persistent anonymous `userId` and sends it only for LoLoManus upload/chat flows.

**Tech Stack:** Spring Boot, Spring AI, PostgreSQL/pgvector, JPA or JDBC-based persistence, Vue 3, Vite, JUnit 5, Mockito, Vitest

---

### Task 1: Define persistence schema for split conversations and messages

**Files:**
- Modify: `src/main/resources/application.yml`
- Create: `src/main/resources/schema.sql` or Flyway/Liquibase migration under the repo's existing migration location
- Test: `src/test/java/com/yupi/yuaiagent/YuAiAgentApplicationTests.java`

- [ ] **Step 1: Write the failing persistence startup test**

```java
package com.yupi.yuaiagent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class YuAiAgentApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateSplitConversationAndMessageTables() {
        Integer loveConversation = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'love_app_conversation'",
                Integer.class
        );
        Integer loveMessage = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'love_app_message'",
                Integer.class
        );
        Integer manusConversation = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'manus_conversation'",
                Integer.class
        );
        Integer manusMessage = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'manus_message'",
                Integer.class
        );

        assertThat(loveConversation).isEqualTo(1);
        assertThat(loveMessage).isEqualTo(1);
        assertThat(manusConversation).isEqualTo(1);
        assertThat(manusMessage).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -Dtest=YuAiAgentApplicationTests test`
Expected: FAIL because one or more tables do not exist yet.

- [ ] **Step 3: Add the minimal schema or migration**

```sql
create table if not exists love_app_conversation (
    id bigserial primary key,
    conversation_id varchar(128) not null unique,
    title varchar(255),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table if not exists love_app_message (
    id bigserial primary key,
    conversation_id varchar(128) not null,
    role varchar(32) not null,
    content text not null,
    sequence_no integer not null,
    created_at timestamp not null default current_timestamp
);

create table if not exists manus_conversation (
    id bigserial primary key,
    conversation_id varchar(128) not null unique,
    user_id varchar(128) not null,
    title varchar(255),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table if not exists manus_message (
    id bigserial primary key,
    conversation_id varchar(128) not null,
    role varchar(32) not null,
    content text not null,
    sequence_no integer not null,
    created_at timestamp not null default current_timestamp
);
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -Dtest=YuAiAgentApplicationTests test`
Expected: PASS with the four tables present.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/application.yml src/main/resources/schema.sql src/test/java/com/yupi/yuaiagent/YuAiAgentApplicationTests.java
git commit -m "feat: add split chat history schema"
```

### Task 2: Add dedicated vector-store beans for LoveApp and LoLoManus private knowledge

**Files:**
- Modify: `src/main/java/com/yupi/yuaiagent/config/VectorStoreConfig.java`
- Modify: `src/main/java/com/yupi/yuaiagent/rag/LoveAppVectorStoreConfig.java`
- Create: `src/main/java/com/yupi/yuaiagent/rag/ManusPrivateVectorStoreConfig.java`
- Test: `src/test/java/com/yupi/yuaiagent/rag/RagKnowledgeIngestServiceTest.java`

- [ ] **Step 1: Write the failing vector-store test for private metadata targeting**

```java
@Test
void shouldWritePrivateKnowledgeIntoManusVectorStoreWithUserMetadata() {
    VectorStore manusPrivateVectorStore = Mockito.mock(VectorStore.class);
    RagKnowledgeIngestService service = new RagKnowledgeIngestService(manusPrivateVectorStore);
    MockMultipartFile file = new MockMultipartFile(
            "file", "notes.md", "text/markdown", "# title\n\nbody".getBytes(StandardCharsets.UTF_8)
    );

    service.ingestMarkdown("anon-123", file);

    ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
    Mockito.verify(manusPrivateVectorStore).add(captor.capture());
    Document first = captor.getValue().getFirst();
    Assertions.assertEquals("anon-123", first.getMetadata().get("userId"));
    Assertions.assertEquals("knowledge_upload", first.getMetadata().get("sourceType"));
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -Dtest=RagKnowledgeIngestServiceTest test`
Expected: FAIL because `ingestMarkdown(String, MultipartFile)` does not exist yet or does not store `userId`.

- [ ] **Step 3: Add the minimal split vector-store configuration**

```java
@Configuration
public class ManusPrivateVectorStoreConfig {

    @Bean
    VectorStore manusPrivateVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .vectorTableName("manus_private_vector_store")
                .initializeSchema(true)
                .build();
    }
}
```

```java
@Service
public class RagKnowledgeIngestService {

    private final VectorStore manusPrivateVectorStore;

    public RagKnowledgeIngestService(@Qualifier("manusPrivateVectorStore") VectorStore manusPrivateVectorStore) {
        this.manusPrivateVectorStore = manusPrivateVectorStore;
    }

    public UploadResult ingestMarkdown(String userId, MultipartFile file) {
        validateUserId(userId);
        // existing file validation + chunking
        metadata.put("userId", userId);
        manusPrivateVectorStore.add(documents);
        return new UploadResult(docId, fileName, documents.size(), "Upload and vectorization completed");
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -Dtest=RagKnowledgeIngestServiceTest test`
Expected: PASS with metadata containing `userId`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/yupi/yuaiagent/config/VectorStoreConfig.java src/main/java/com/yupi/yuaiagent/rag/LoveAppVectorStoreConfig.java src/main/java/com/yupi/yuaiagent/rag/ManusPrivateVectorStoreConfig.java src/main/java/com/yupi/yuaiagent/rag/RagKnowledgeIngestService.java src/test/java/com/yupi/yuaiagent/rag/RagKnowledgeIngestServiceTest.java
git commit -m "feat: split public and private vector stores"
```

### Task 3: Extend the upload API to require anonymous user identity

**Files:**
- Modify: `src/main/java/com/yupi/yuaiagent/controller/RagController.java`
- Modify: `src/main/java/com/yupi/yuaiagent/rag/RagKnowledgeIngestService.java`
- Test: `src/test/java/com/yupi/yuaiagent/controller/RagControllerTest.java`
- Test: `src/test/java/com/yupi/yuaiagent/rag/RagKnowledgeIngestServiceTest.java`

- [ ] **Step 1: Write the failing controller test**

```java
@Test
void uploadMarkdownShouldRequireUserId() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "k.md", "text/markdown", "# hi".getBytes());

    mockMvc.perform(multipart("/rag/upload-md")
                    .file(file))
            .andExpect(status().isBadRequest());
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -Dtest=RagControllerTest,RagKnowledgeIngestServiceTest test`
Expected: FAIL because the endpoint does not yet require `userId`.

- [ ] **Step 3: Implement the minimal request contract**

```java
@PostMapping(value = "/upload-md", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public RagKnowledgeIngestService.UploadResult uploadMarkdown(
        @RequestParam("userId") String userId,
        @RequestParam("file") MultipartFile file
) {
    try {
        return ragKnowledgeIngestService.ingestMarkdown(userId, file);
    } catch (IllegalArgumentException e) {
        throw new ResponseStatusException(BAD_REQUEST, e.getMessage());
    }
}
```

```java
private void validateUserId(String userId) {
    if (userId == null || userId.isBlank()) {
        throw new IllegalArgumentException("userId is required");
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -Dtest=RagControllerTest,RagKnowledgeIngestServiceTest test`
Expected: PASS with `userId` required and stored.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/yupi/yuaiagent/controller/RagController.java src/main/java/com/yupi/yuaiagent/rag/RagKnowledgeIngestService.java src/test/java/com/yupi/yuaiagent/controller/RagControllerTest.java src/test/java/com/yupi/yuaiagent/rag/RagKnowledgeIngestServiceTest.java
git commit -m "feat: require user id for private knowledge upload"
```

### Task 4: Persist LoveApp conversations and messages

**Files:**
- Create: `src/main/java/com/yupi/yuaiagent/chatmemory/loveapp/LoveAppConversationEntity.java`
- Create: `src/main/java/com/yupi/yuaiagent/chatmemory/loveapp/LoveAppMessageEntity.java`
- Create: `src/main/java/com/yupi/yuaiagent/chatmemory/loveapp/LoveAppConversationRepository.java`
- Create: `src/main/java/com/yupi/yuaiagent/chatmemory/loveapp/LoveAppMessageRepository.java`
- Create: `src/main/java/com/yupi/yuaiagent/chatmemory/loveapp/LoveAppChatHistoryService.java`
- Modify: `src/main/java/com/yupi/yuaiagent/app/LoveApp.java`
- Test: `src/test/java/com/yupi/yuaiagent/app/LoveAppTest.java`

- [ ] **Step 1: Write the failing LoveApp history test**

```java
@Test
void doChatShouldPersistUserAndAssistantMessages() {
    LoveAppChatHistoryService historyService = Mockito.mock(LoveAppChatHistoryService.class);
    LoveApp loveApp = new LoveApp(chatModel, historyService);

    loveApp.doChat("你好", "love-session-1");

    Mockito.verify(historyService).appendUserMessage("love-session-1", "你好");
    Mockito.verify(historyService).appendAssistantMessage(Mockito.eq("love-session-1"), Mockito.anyString());
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -Dtest=LoveAppTest test`
Expected: FAIL because LoveApp does not yet save chat history through a persistence service.

- [ ] **Step 3: Implement the minimal persistence service**

```java
@Service
public class LoveAppChatHistoryService {

    public void appendUserMessage(String conversationId, String content) {
        ensureConversation(conversationId);
        saveMessage(conversationId, "user", content);
    }

    public void appendAssistantMessage(String conversationId, String content) {
        ensureConversation(conversationId);
        saveMessage(conversationId, "assistant", content);
    }
}
```

```java
public String doChat(String message, String chatId) {
    loveAppChatHistoryService.appendUserMessage(chatId, message);
    String content = // existing model call
    loveAppChatHistoryService.appendAssistantMessage(chatId, content);
    return content;
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -Dtest=LoveAppTest test`
Expected: PASS with both roles persisted.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/yupi/yuaiagent/chatmemory/loveapp src/main/java/com/yupi/yuaiagent/app/LoveApp.java src/test/java/com/yupi/yuaiagent/app/LoveAppTest.java
git commit -m "feat: persist love app chat history"
```

### Task 5: Persist LoLoManus conversations and messages

**Files:**
- Create: `src/main/java/com/yupi/yuaiagent/chatmemory/manus/ManusConversationEntity.java`
- Create: `src/main/java/com/yupi/yuaiagent/chatmemory/manus/ManusMessageEntity.java`
- Create: `src/main/java/com/yupi/yuaiagent/chatmemory/manus/ManusConversationRepository.java`
- Create: `src/main/java/com/yupi/yuaiagent/chatmemory/manus/ManusMessageRepository.java`
- Create: `src/main/java/com/yupi/yuaiagent/chatmemory/manus/ManusChatHistoryService.java`
- Modify: `src/main/java/com/yupi/yuaiagent/agent/LoLoManus.java`
- Test: `src/test/java/com/yupi/yuaiagent/agent/LoLoManusArchitectureTest.java`

- [ ] **Step 1: Write the failing LoLoManus history test**

```java
@Test
void bindSessionShouldBindAnonymousUserConversationHistory() {
    ManusChatHistoryService historyService = Mockito.mock(ManusChatHistoryService.class);
    LoLoManus manus = new LoLoManus(..., historyService);

    manus.bindSession("manus-session-1", "anon-123");
    manus.recordUserMessage("请帮我总结文档");

    Mockito.verify(historyService).appendUserMessage("manus-session-1", "anon-123", "请帮我总结文档");
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -Dtest=LoLoManusArchitectureTest test`
Expected: FAIL because LoLoManus does not yet persist user-bound chat history.

- [ ] **Step 3: Implement the minimal persistence service**

```java
@Service
public class ManusChatHistoryService {

    public void appendUserMessage(String conversationId, String userId, String content) {
        ensureConversation(conversationId, userId);
        saveMessage(conversationId, "user", content);
    }

    public void appendAssistantMessage(String conversationId, String userId, String content) {
        ensureConversation(conversationId, userId);
        saveMessage(conversationId, "assistant", content);
    }
}
```

```java
public void bindSession(String conversationId, String userId) {
    this.sessionId = conversationId;
    this.userId = userId;
    manusChatHistoryService.ensureConversation(conversationId, userId);
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -Dtest=LoLoManusArchitectureTest test`
Expected: PASS with LoLoManus history stored under the Manus tables.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/yupi/yuaiagent/chatmemory/manus src/main/java/com/yupi/yuaiagent/agent/LoLoManus.java src/test/java/com/yupi/yuaiagent/agent/LoLoManusArchitectureTest.java
git commit -m "feat: persist manus chat history"
```

### Task 6: Add user-scoped private retrieval for LoLoManus

**Files:**
- Create: `src/main/java/com/yupi/yuaiagent/rag/ManusPrivateKnowledgeService.java`
- Modify: `src/main/java/com/yupi/yuaiagent/agent/LoLoManus.java`
- Modify: `src/main/java/com/yupi/yuaiagent/controller/AiController.java`
- Test: `src/test/java/com/yupi/yuaiagent/agent/LoLoManusArchitectureTest.java`

- [ ] **Step 1: Write the failing user-scope retrieval test**

```java
@Test
void manusShouldRetrieveOnlyCurrentUsersPrivateKnowledge() {
    ManusPrivateKnowledgeService knowledgeService = Mockito.mock(ManusPrivateKnowledgeService.class);
    Mockito.when(knowledgeService.retrieveContext("anon-123", "总结一下")).thenReturn("用户私有知识片段");

    LoLoManus manus = new LoLoManus(..., knowledgeService, historyService);
    manus.bindSession("manus-session-1", "anon-123");
    manus.preparePrompt("总结一下");

    Mockito.verify(knowledgeService).retrieveContext("anon-123", "总结一下");
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -Dtest=LoLoManusArchitectureTest test`
Expected: FAIL because LoLoManus does not yet request user-scoped private context.

- [ ] **Step 3: Implement the minimal knowledge service**

```java
@Service
public class ManusPrivateKnowledgeService {

    public String retrieveContext(String userId, String query) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .filterExpression("userId == '" + userId + "'")
                .topK(4)
                .build();
        List<Document> documents = manusPrivateVectorStore.similaritySearch(request);
        return documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
    }
}
```

```java
@GetMapping("/manus/chat")
public SseEmitter doChatWithManus(String message, String chatId, String userId) {
    LoLoManus loLoManus = loLoManusProvider.getObject();
    loLoManus.bindSession(chatId, userId);
    return loLoManus.runStream(message);
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -Dtest=LoLoManusArchitectureTest test`
Expected: PASS with private knowledge retrieval invoked using the current `userId`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/yupi/yuaiagent/rag/ManusPrivateKnowledgeService.java src/main/java/com/yupi/yuaiagent/agent/LoLoManus.java src/main/java/com/yupi/yuaiagent/controller/AiController.java src/test/java/com/yupi/yuaiagent/agent/LoLoManusArchitectureTest.java
git commit -m "feat: add user scoped manus private retrieval"
```

### Task 7: Keep LoveApp retrieval isolated from private knowledge

**Files:**
- Modify: `src/main/java/com/yupi/yuaiagent/app/LoveApp.java`
- Test: `src/test/java/com/yupi/yuaiagent/app/LoveAppTest.java`

- [ ] **Step 1: Write the failing isolation test**

```java
@Test
void doChatWithRagShouldUseOnlyLoveAppVectorStore() {
    VectorStore loveAppVectorStore = Mockito.mock(VectorStore.class);
    VectorStore manusPrivateVectorStore = Mockito.mock(VectorStore.class);
    LoveApp loveApp = new LoveApp(chatModel, loveAppVectorStore, manusPrivateVectorStore, historyService);

    loveApp.doChatWithRag("我该怎么追喜欢的人", "love-session-1");

    Mockito.verifyNoInteractions(manusPrivateVectorStore);
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -Dtest=LoveAppTest test`
Expected: FAIL if LoveApp wiring is ambiguous or shares the wrong vector store bean.

- [ ] **Step 3: Implement the minimal bean isolation**

```java
@Resource
@Qualifier("loveAppVectorStore")
private VectorStore loveAppVectorStore;
```

Use only `loveAppVectorStore` in LoveApp RAG advisors and remove any accidental dependency on Manus private storage.

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -Dtest=LoveAppTest test`
Expected: PASS with no interactions on the Manus private vector store.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/yupi/yuaiagent/app/LoveApp.java src/test/java/com/yupi/yuaiagent/app/LoveAppTest.java
git commit -m "fix: isolate love app retrieval from manus private store"
```

### Task 8: Add frontend anonymous user identity utilities

**Files:**
- Create: `yu-ai-agent-frontend/src/utils/anonymousUser.js`
- Create: `yu-ai-agent-frontend/src/utils/anonymousUser.test.js`
- Modify: `yu-ai-agent-frontend/src/utils/chat.js`
- Modify: `yu-ai-agent-frontend/src/utils/upload.js`

- [ ] **Step 1: Write the failing frontend identity tests**

```javascript
import { describe, expect, it, vi } from 'vitest'
import { getAnonymousUserId } from './anonymousUser'

describe('getAnonymousUserId', () => {
  it('reuses an existing id from localStorage', () => {
    localStorage.setItem('yu-ai-agent.userId', 'anon-existing')
    expect(getAnonymousUserId()).toBe('anon-existing')
  })

  it('creates and stores a new id when missing', () => {
    const userId = getAnonymousUserId()
    expect(userId.startsWith('anon-')).toBe(true)
    expect(localStorage.getItem('yu-ai-agent.userId')).toBe(userId)
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm --prefix yu-ai-agent-frontend test -- anonymousUser.test.js`
Expected: FAIL because the utility does not exist yet.

- [ ] **Step 3: Implement the minimal anonymous user utility**

```javascript
const STORAGE_KEY = 'yu-ai-agent.userId'

export function getAnonymousUserId() {
  const existing = window.localStorage.getItem(STORAGE_KEY)
  if (existing) {
    return existing
  }
  const created = `anon-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  window.localStorage.setItem(STORAGE_KEY, created)
  return created
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm --prefix yu-ai-agent-frontend test -- anonymousUser.test.js`
Expected: PASS with stable anonymous ID behavior.

- [ ] **Step 5: Commit**

```bash
git add yu-ai-agent-frontend/src/utils/anonymousUser.js yu-ai-agent-frontend/src/utils/anonymousUser.test.js yu-ai-agent-frontend/src/utils/chat.js yu-ai-agent-frontend/src/utils/upload.js
git commit -m "feat: add frontend anonymous user identity"
```

### Task 9: Send anonymous user identity through LoLoManus chat and upload flows

**Files:**
- Modify: `yu-ai-agent-frontend/src/utils/chat.js`
- Modify: `yu-ai-agent-frontend/src/utils/upload.js`
- Modify: `yu-ai-agent-frontend/src/components/ChatPanel.vue`
- Modify: `yu-ai-agent-frontend/src/components/UploadKnowledgePanel.vue`
- Test: `yu-ai-agent-frontend/src/utils/chat.test.js`
- Test: `yu-ai-agent-frontend/src/utils/upload.test.js`

- [ ] **Step 1: Write the failing request-shape tests**

```javascript
it('adds userId to manus chat requests only', () => {
  const url = buildChatRequestUrl('http://localhost:8523/api', '/ai/manus/chat', 'hello', 'manus-1', 'anon-123')
  expect(new URL(url).searchParams.get('userId')).toBe('anon-123')
})

it('adds userId to markdown upload', async () => {
  const formData = buildUploadFormData(file, 'anon-123')
  expect(formData.get('userId')).toBe('anon-123')
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm --prefix yu-ai-agent-frontend test -- chat.test.js upload.test.js`
Expected: FAIL because the existing helpers do not include `userId`.

- [ ] **Step 3: Implement the minimal request plumbing**

```javascript
export function buildChatRequestUrl(baseUrl, endpoint, message, chatId, userId) {
  const url = new URL(String(baseUrl || '').replace(/\/$/, '') + endpoint)
  url.searchParams.set('message', message)
  if (chatId) url.searchParams.set('chatId', chatId)
  if (endpoint.includes('/manus/') && userId) {
    url.searchParams.set('userId', userId)
  }
  return url.toString()
}
```

```javascript
export function buildUploadFormData(file, userId) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('userId', userId)
  return formData
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm --prefix yu-ai-agent-frontend test -- chat.test.js upload.test.js`
Expected: PASS with `userId` sent on the LoLoManus-only flows.

- [ ] **Step 5: Commit**

```bash
git add yu-ai-agent-frontend/src/utils/chat.js yu-ai-agent-frontend/src/utils/upload.js yu-ai-agent-frontend/src/components/ChatPanel.vue yu-ai-agent-frontend/src/components/UploadKnowledgePanel.vue yu-ai-agent-frontend/src/utils/chat.test.js yu-ai-agent-frontend/src/utils/upload.test.js
git commit -m "feat: wire anonymous user id into manus flows"
```

### Task 10: Make the upload panel explicitly LoLoManus-only

**Files:**
- Modify: `yu-ai-agent-frontend/src/components/UploadKnowledgePanel.vue`
- Modify: `yu-ai-agent-frontend/src/App.vue`
- Modify: `yu-ai-agent-frontend/src/style.css`
- Test: `yu-ai-agent-frontend/src/components/UploadKnowledgePanel.test.js` or the repo's existing UI test location

- [ ] **Step 1: Write the failing UI text test**

```javascript
it('shows that uploads only enhance LoLoManus', () => {
  render(UploadKnowledgePanel, { props: { baseUrl: 'http://localhost:8523/api' } })
  expect(screen.getByText('上传知识仅增强 LoLoManus，不影响恋爱助手')).toBeTruthy()
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm --prefix yu-ai-agent-frontend test -- UploadKnowledgePanel.test.js`
Expected: FAIL because the explanatory text does not exist yet.

- [ ] **Step 3: Implement the minimal UI change**

```vue
<p class="upload-hint">上传知识仅增强 LoLoManus，不影响恋爱助手</p>
```

Add the hint near the upload title and keep the panel visually associated with the LoLoManus area rather than the LoveApp area.

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm --prefix yu-ai-agent-frontend test -- UploadKnowledgePanel.test.js`
Expected: PASS with the LoLoManus-only hint rendered.

- [ ] **Step 5: Commit**

```bash
git add yu-ai-agent-frontend/src/components/UploadKnowledgePanel.vue yu-ai-agent-frontend/src/App.vue yu-ai-agent-frontend/src/style.css yu-ai-agent-frontend/src/components/UploadKnowledgePanel.test.js
git commit -m "feat: clarify upload scope in manus ui"
```

### Task 11: End-to-end verification

**Files:**
- Modify: `docs/superpowers/specs/2026-04-05-manus-private-rag-design.md` if verification reveals a needed spec clarification

- [ ] **Step 1: Run the backend targeted tests**

Run: `mvn -Dtest=YuAiAgentApplicationTests,RagKnowledgeIngestServiceTest,RagControllerTest,LoveAppTest,LoLoManusArchitectureTest test`
Expected: PASS for schema, upload, retrieval isolation, and chat-history persistence.

- [ ] **Step 2: Run the frontend targeted tests**

Run: `npm --prefix yu-ai-agent-frontend test -- anonymousUser.test.js chat.test.js upload.test.js UploadKnowledgePanel.test.js`
Expected: PASS for anonymous identity and LoLoManus-only request plumbing.

- [ ] **Step 3: Run the frontend build**

Run: `npm --prefix yu-ai-agent-frontend run build`
Expected: PASS with the updated LoLoManus upload UI.

- [ ] **Step 4: Perform a manual smoke check**

Run the app, then verify:

```text
1. Open the page and note the generated anonymous user ID in localStorage.
2. Upload a Markdown file from the LoLoManus area.
3. Confirm the upload response includes docId, fileName, and chunk count.
4. Send a LoLoManus prompt that should hit the uploaded content.
5. Confirm LoveApp still answers from public knowledge and is unaffected by the upload.
6. Confirm rows exist in manus_conversation/manus_message and love_app_conversation/love_app_message after chatting.
```

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-04-05-manus-private-rag-design.md
git commit -m "test: verify split rag and chat persistence flows"
```
