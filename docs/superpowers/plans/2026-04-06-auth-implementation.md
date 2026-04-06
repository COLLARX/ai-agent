# Auth and Account Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add username/password registration and login with JWT, require authentication for all chat and upload flows, and replace anonymous request identity with account-backed `userId`.

**Architecture:** Introduce a small auth module on the backend with a `user` table, password hashing, JWT issuance, and a request authentication layer that injects the current user into controllers. Update conversation persistence and private knowledge ingestion to use the authenticated user id instead of client-supplied identity. On the frontend, gate the app behind login/register, persist the JWT in `localStorage`, validate it through `/auth/me`, and automatically attach Bearer tokens to business requests.

**Tech Stack:** Spring Boot, JdbcTemplate, Spring Web, JWT library already available via Maven or a small new dependency, PostgreSQL, Vue 3, Vite, JUnit 5, Mockito, Vitest

---

## File Map

- Create: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/auth/AuthController.java`
- Create: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/auth/AuthService.java`
- Create: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/auth/JwtService.java`
- Create: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/auth/AuthUser.java`
- Create: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/auth/AuthContext.java`
- Create: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/auth/AuthInterceptor.java`
- Create: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/config/WebMvcAuthConfig.java`
- Modify: `D:/develop/code/yu-ai-agent/src/main/resources/schema.sql`
- Modify: `D:/develop/code/yu-ai-agent/src/main/resources/application.yml`
- Modify: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/controller/AiController.java`
- Modify: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/controller/RagController.java`
- Modify: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/chatmemory/loveapp/LoveAppConversationService.java`
- Modify: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/chatmemory/manus/ManusConversationService.java`
- Modify: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/rag/RagKnowledgeIngestService.java`
- Modify: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/rag/ManusPrivateKnowledgeService.java`
- Modify: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/agent/LoLoManus.java`
- Modify: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/app/LoveApp.java`
- Create: `D:/develop/code/yu-ai-agent/src/test/java/com/yupi/yuaiagent/auth/AuthControllerTest.java`
- Create: `D:/develop/code/yu-ai-agent/src/test/java/com/yupi/yuaiagent/auth/AuthServiceTest.java`
- Create: `D:/develop/code/yu-ai-agent/src/test/java/com/yupi/yuaiagent/auth/AuthInterceptorTest.java`
- Modify: `D:/develop/code/yu-ai-agent/src/test/java/com/yupi/yuaiagent/YuAiAgentApplicationTests.java`
- Modify: `D:/develop/code/yu-ai-agent/src/test/java/com/yupi/yuaiagent/controller/AiControllerUserIdWiringTest.java`
- Modify: `D:/develop/code/yu-ai-agent/src/test/java/com/yupi/yuaiagent/controller/RagControllerTest.java`
- Modify: `D:/develop/code/yu-ai-agent/src/test/java/com/yupi/yuaiagent/chatmemory/loveapp/LoveAppConversationServiceTest.java`
- Modify: `D:/develop/code/yu-ai-agent/src/test/java/com/yupi/yuaiagent/chatmemory/manus/ManusConversationServiceTest.java`
- Create: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/components/AuthPage.vue`
- Create: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/utils/auth.js`
- Create: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/utils/auth.test.js`
- Modify: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/App.vue`
- Modify: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/components/ChatPanel.vue`
- Modify: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/components/UploadKnowledgePanel.vue`
- Modify: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/utils/chat.js`
- Modify: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/utils/upload.js`
- Modify: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/utils/chat.test.js`
- Create: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/components/AuthPage.test.js`

### Task 1: Add schema support for account-backed conversations

**Files:**
- Modify: `D:/develop/code/yu-ai-agent/src/main/resources/schema.sql`
- Modify: `D:/develop/code/yu-ai-agent/src/test/java/com/yupi/yuaiagent/YuAiAgentApplicationTests.java`

- [ ] **Step 1: Write the failing schema test**

```java
@Test
void shouldCreateUserTableAndLoveAppUserOwnership() {
    Integer userTable = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_name = 'app_user'",
            Integer.class
    );
    Integer loveUserColumn = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.columns where table_name = 'love_app_conversation' and column_name = 'user_id'",
            Integer.class
    );

    assertThat(userTable).isEqualTo(1);
    assertThat(loveUserColumn).isEqualTo(1);
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn "-Dtest=YuAiAgentApplicationTests" test`  
Expected: FAIL because `app_user` and `love_app_conversation.user_id` do not exist yet.

- [ ] **Step 3: Add the minimal schema changes**

```sql
create table if not exists app_user (
    id varchar(64) primary key,
    username varchar(128) not null unique,
    password_hash varchar(255) not null,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

alter table love_app_conversation
    add column if not exists user_id varchar(64);
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn "-Dtest=YuAiAgentApplicationTests" test`  
Expected: PASS with the new table and column present.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/schema.sql src/test/java/com/yupi/yuaiagent/YuAiAgentApplicationTests.java
git commit -m "feat: add auth schema foundation"
```

### Task 2: Implement registration, login, and `/auth/me`

**Files:**
- Create: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/auth/AuthController.java`
- Create: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/auth/AuthService.java`
- Create: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/auth/JwtService.java`
- Create: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/auth/AuthUser.java`
- Create: `D:/develop/code/yu-ai-agent/src/test/java/com/yupi/yuaiagent/auth/AuthControllerTest.java`
- Create: `D:/develop/code/yu-ai-agent/src/test/java/com/yupi/yuaiagent/auth/AuthServiceTest.java`
- Modify: `D:/develop/code/yu-ai-agent/src/main/resources/application.yml`

- [ ] **Step 1: Write the failing service and controller tests**

```java
@Test
void registerShouldCreateUserAndReturnToken() {
    RegisterRequest request = new RegisterRequest("alice", "secret123");
    AuthResponse response = authService.register(request);

    assertThat(response.token()).isNotBlank();
    assertThat(response.userInfo().username()).isEqualTo("alice");
}

@Test
void loginShouldRejectWrongPassword() throws Exception {
    mockMvc.perform(post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {"username":"alice","password":"wrong"}
                    """))
            .andExpect(status().isUnauthorized());
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn "-Dtest=AuthServiceTest,AuthControllerTest" test`  
Expected: FAIL because auth classes and endpoints do not exist yet.

- [ ] **Step 3: Implement the minimal auth layer**

```java
public record AuthUser(String id, String username) {}
```

```java
public AuthResponse register(RegisterRequest request) {
    validateRequest(request);
    ensureUsernameAvailable(request.username());
    String userId = IdUtil.fastSimpleUUID();
    String passwordHash = passwordEncoder.encode(request.password());
    jdbcTemplate.update(
            "insert into app_user (id, username, password_hash) values (?, ?, ?)",
            userId, request.username(), passwordHash
    );
    return issueToken(new AuthUser(userId, request.username()));
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn "-Dtest=AuthServiceTest,AuthControllerTest" test`  
Expected: PASS for registration, duplicate username protection, login success, login failure, and `/auth/me`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/yupi/yuaiagent/auth src/test/java/com/yupi/yuaiagent/auth src/main/resources/application.yml
git commit -m "feat: add username password auth api"
```

### Task 3: Enforce JWT authentication on business endpoints

**Files:**
- Create: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/auth/AuthContext.java`
- Create: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/auth/AuthInterceptor.java`
- Create: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/config/WebMvcAuthConfig.java`
- Create: `D:/develop/code/yu-ai-agent/src/test/java/com/yupi/yuaiagent/auth/AuthInterceptorTest.java`
- Modify: `D:/develop/code/yu-ai-agent/src/test/java/com/yupi/yuaiagent/controller/RagControllerTest.java`

- [ ] **Step 1: Write the failing auth-guard tests**

```java
@Test
void shouldRejectProtectedRequestWithoutBearerToken() throws Exception {
    mockMvc.perform(get("/ai/manus/chat")
            .param("message", "hello")
            .param("chatId", "session-1"))
            .andExpect(status().isUnauthorized());
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn "-Dtest=AuthInterceptorTest,RagControllerTest" test`  
Expected: FAIL because protected routes are still accessible without JWT.

- [ ] **Step 3: Implement minimal request auth**

```java
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (request.getRequestURI().startsWith("/api/auth")) {
        return true;
    }
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    AuthUser user = jwtService.parseBearerToken(header);
    if (user == null) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
        return false;
    }
    AuthContext.setCurrentUser(user);
    return true;
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn "-Dtest=AuthInterceptorTest,RagControllerTest" test`  
Expected: PASS with auth routes open and business routes protected.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/yupi/yuaiagent/auth/AuthContext.java src/main/java/com/yupi/yuaiagent/auth/AuthInterceptor.java src/main/java/com/yupi/yuaiagent/config/WebMvcAuthConfig.java src/test/java/com/yupi/yuaiagent/auth/AuthInterceptorTest.java src/test/java/com/yupi/yuaiagent/controller/RagControllerTest.java
git commit -m "feat: protect chat and upload routes with jwt"
```

### Task 4: Replace request `userId` with authenticated account identity

**Files:**
- Modify: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/controller/AiController.java`
- Modify: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/controller/RagController.java`
- Modify: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/rag/RagKnowledgeIngestService.java`
- Modify: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/rag/ManusPrivateKnowledgeService.java`
- Modify: `D:/develop/code/yu-ai-agent/src/test/java/com/yupi/yuaiagent/controller/AiControllerUserIdWiringTest.java`

- [ ] **Step 1: Write the failing identity-source tests**

```java
@Test
void manusChatShouldBindAuthenticatedUserInsteadOfRequestUserId() {
    AuthContext.setCurrentUser(new AuthUser("u-1", "alice"));

    aiController.doChatWithManus("hello", "session-1");

    verify(loLoManus).bindUserId("u-1");
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn "-Dtest=AiControllerUserIdWiringTest" test`  
Expected: FAIL because the controller still expects `userId` from request parameters.

- [ ] **Step 3: Implement the minimal identity swap**

```java
public SseEmitter doChatWithManus(String message, String chatId) {
    AuthUser authUser = AuthContext.requireCurrentUser();
    LoLoManus loLoManus = loLoManusProvider.getObject();
    loLoManus.bindSessionId(chatId);
    loLoManus.bindUserId(authUser.id());
    return loLoManus.runStream(message);
}
```

```java
public UploadResult uploadMarkdown(@RequestParam("file") MultipartFile file) {
    AuthUser authUser = AuthContext.requireCurrentUser();
    return ragKnowledgeIngestService.ingestMarkdown(authUser.id(), file);
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn "-Dtest=AiControllerUserIdWiringTest,RagControllerTest" test`  
Expected: PASS with authenticated identity replacing request-supplied identity.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/yupi/yuaiagent/controller/AiController.java src/main/java/com/yupi/yuaiagent/controller/RagController.java src/main/java/com/yupi/yuaiagent/rag/RagKnowledgeIngestService.java src/main/java/com/yupi/yuaiagent/rag/ManusPrivateKnowledgeService.java src/test/java/com/yupi/yuaiagent/controller/AiControllerUserIdWiringTest.java src/test/java/com/yupi/yuaiagent/controller/RagControllerTest.java
git commit -m "refactor: use authenticated user identity in rag flows"
```

### Task 5: Add user ownership to LoveApp and tighten conversation services

**Files:**
- Modify: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/chatmemory/loveapp/LoveAppConversationService.java`
- Modify: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/chatmemory/manus/ManusConversationService.java`
- Modify: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/app/LoveApp.java`
- Modify: `D:/develop/code/yu-ai-agent/src/main/java/com/yupi/yuaiagent/agent/LoLoManus.java`
- Modify: `D:/develop/code/yu-ai-agent/src/test/java/com/yupi/yuaiagent/chatmemory/loveapp/LoveAppConversationServiceTest.java`
- Modify: `D:/develop/code/yu-ai-agent/src/test/java/com/yupi/yuaiagent/chatmemory/manus/ManusConversationServiceTest.java`

- [ ] **Step 1: Write the failing ownership tests**

```java
@Test
void loveAppConversationShouldBelongToCurrentUser() {
    loveAppConversationService.recordTurn("love-1", "u-1", "hi", "hello");

    String userId = jdbcTemplate.queryForObject(
            "select user_id from love_app_conversation where conversation_id = ?",
            String.class,
            "love-1"
    );

    assertThat(userId).isEqualTo("u-1");
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn "-Dtest=LoveAppConversationServiceTest,ManusConversationServiceTest" test`  
Expected: FAIL because LoveApp conversations are not yet user-owned and legacy defaults still exist.

- [ ] **Step 3: Implement the minimal ownership-aware persistence**

```java
public void recordTurn(String conversationId, String userId, String userMessage, String assistantMessage) {
    ensureConversationExists(conversationId, userId);
    String existingUserId = lockConversationAndLoadUserId(conversationId);
    if (!userId.equals(existingUserId)) {
        throw new IllegalStateException("Conversation ownership mismatch");
    }
    ...
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn "-Dtest=LoveAppConversationServiceTest,ManusConversationServiceTest" test`  
Expected: PASS with both conversation domains tied to real account ids.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/yupi/yuaiagent/chatmemory/loveapp/LoveAppConversationService.java src/main/java/com/yupi/yuaiagent/chatmemory/manus/ManusConversationService.java src/main/java/com/yupi/yuaiagent/app/LoveApp.java src/main/java/com/yupi/yuaiagent/agent/LoLoManus.java src/test/java/com/yupi/yuaiagent/chatmemory/loveapp/LoveAppConversationServiceTest.java src/test/java/com/yupi/yuaiagent/chatmemory/manus/ManusConversationServiceTest.java
git commit -m "feat: bind conversations to authenticated users"
```

### Task 6: Build the frontend login/register entry flow

**Files:**
- Create: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/components/AuthPage.vue`
- Create: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/components/AuthPage.test.js`
- Create: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/utils/auth.js`
- Create: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/utils/auth.test.js`
- Modify: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/App.vue`

- [ ] **Step 1: Write the failing auth-page tests**

```javascript
it('renders auth page when no token exists', () => {
  localStorage.removeItem('auth_token')
  render(App)
  expect(screen.getByText('登录')).toBeTruthy()
})

it('stores token after successful login', async () => {
  await loginWithPassword('alice', 'secret123')
  expect(localStorage.getItem('auth_token')).toBeTruthy()
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npm --prefix yu-ai-agent-frontend test -- auth.test.js AuthPage.test.js`  
Expected: FAIL because auth utilities and entry page do not exist yet.

- [ ] **Step 3: Implement the minimal frontend auth state**

```javascript
export function readToken() {
  return globalThis.localStorage?.getItem('auth_token') ?? ''
}

export function writeToken(token) {
  globalThis.localStorage?.setItem('auth_token', token)
}
```

```vue
<AuthPage v-if="!isAuthenticated" @authenticated="handleAuthenticated" />
<MainApp v-else />
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `npm --prefix yu-ai-agent-frontend test -- auth.test.js AuthPage.test.js`  
Expected: PASS with login/register gating behavior.

- [ ] **Step 5: Commit**

```bash
git add yu-ai-agent-frontend/src/components/AuthPage.vue yu-ai-agent-frontend/src/components/AuthPage.test.js yu-ai-agent-frontend/src/utils/auth.js yu-ai-agent-frontend/src/utils/auth.test.js yu-ai-agent-frontend/src/App.vue
git commit -m "feat: add frontend auth entry flow"
```

### Task 7: Attach Bearer token to chat and upload flows, remove anonymous identity from the main path

**Files:**
- Modify: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/components/ChatPanel.vue`
- Modify: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/components/UploadKnowledgePanel.vue`
- Modify: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/utils/chat.js`
- Modify: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/utils/upload.js`
- Modify: `D:/develop/code/yu-ai-agent/yu-ai-agent-frontend/src/utils/chat.test.js`

- [ ] **Step 1: Write the failing request-auth tests**

```javascript
it('adds bearer token to chat requests', () => {
  const request = buildChatRequest('http://localhost:8523/api', '/ai/manus/chat', 'hello', 'session-1', 'jwt-token')
  expect(request.headers.get('Authorization')).toBe('Bearer jwt-token')
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npm --prefix yu-ai-agent-frontend test -- chat.test.js auth.test.js`  
Expected: FAIL because request builders do not yet add auth headers.

- [ ] **Step 3: Implement the minimal token plumbing**

```javascript
export function buildAuthHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}
```

Remove authenticated-path calls to `getAnonymousUserId()` from chat/upload request generation.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `npm --prefix yu-ai-agent-frontend test -- chat.test.js auth.test.js`  
Expected: PASS with Bearer token injection and no main-path anonymous identity usage.

- [ ] **Step 5: Commit**

```bash
git add yu-ai-agent-frontend/src/components/ChatPanel.vue yu-ai-agent-frontend/src/components/UploadKnowledgePanel.vue yu-ai-agent-frontend/src/utils/chat.js yu-ai-agent-frontend/src/utils/upload.js yu-ai-agent-frontend/src/utils/chat.test.js
git commit -m "refactor: use bearer auth in frontend business requests"
```

### Task 8: End-to-end verification and cleanup

**Files:**
- Modify only if verification reveals a required fix

- [ ] **Step 1: Run the backend targeted tests**

Run: `mvn "-Dtest=YuAiAgentApplicationTests,AuthServiceTest,AuthControllerTest,AuthInterceptorTest,AiControllerUserIdWiringTest,RagControllerTest,LoveAppConversationServiceTest,ManusConversationServiceTest" test`  
Expected: PASS for schema, auth, controller wiring, and ownership checks.

- [ ] **Step 2: Run the frontend targeted tests**

Run: `npm --prefix yu-ai-agent-frontend test -- auth.test.js AuthPage.test.js chat.test.js`  
Expected: PASS for login flow and Bearer request injection.

- [ ] **Step 3: Run the frontend build**

Run: `npm --prefix yu-ai-agent-frontend run build`  
Expected: PASS with the gated app shell.

- [ ] **Step 4: Perform a manual smoke check**

Verify:
- Register a new user
- Log in and reach the main app
- Send a LoveApp message and confirm the conversation row stores the logged-in `user_id`
- Upload a Markdown file and confirm `manus_private_vector_store.metadata.userId` matches the logged-in `user_id`
- Chat with LoLoManus and confirm the Manus conversation is bound to the logged-in `user_id`
- Remove the token and confirm the app returns to the auth page

- [ ] **Step 5: Commit**

```bash
git commit --allow-empty -m "test: verify auth gated chat flows"
```
