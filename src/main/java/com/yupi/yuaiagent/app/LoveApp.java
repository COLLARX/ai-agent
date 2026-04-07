package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.auth.AuthContext;
import com.yupi.yuaiagent.chatmemory.loveapp.LoveAppConversationService;
import com.yupi.yuaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class LoveApp {

    private static final String SYSTEM_PROMPT = """
            你是深耕恋爱心理领域的专家。
            开场先表明身份，告诉用户可以倾诉恋爱难题。
            围绕单身、恋爱、已婚三种状态提问：
            - 单身状态：询问社交圈拓展与追求心仪对象的困扰；
            - 恋爱状态：询问沟通、习惯差异引发的矛盾；
            - 已婚状态：询问家庭责任与亲属关系处理的问题。
            引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。
            """;

    private final ChatClient chatClient;

    @Resource
    private LoveAppConversationService loveAppConversationService;

    @Resource
    private VectorStore loveAppVectorStore;

    @Resource
    private Advisor loveAppRagCloudAdvisor;

    @Resource
    private VectorStore pgVectorVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    public LoveApp(ChatModel dashscopeChatModel) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        this.chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor()
                )
                .build();
    }

    public String doChat(String message, String chatId) {
        String userId = currentUserId();
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        persistLoveAppTurn(chatId, userId, message, content);
        return content;
    }

    public Flux<String> doChatByStream(String message, String chatId) {
        String userId = currentUserId();
        return withStreamPersistence(chatId, message, chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content(), userId);
    }

    record LoveReport(String title, List<String> suggestions) {
    }

    public LoveReport doChatWithReport(String message, String chatId) {
        String userId = currentUserId();
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + " 每次对话后都生成恋爱结果，标题为『用户名』的恋爱报告，内容为建议列表。")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(LoveReport.class);
        log.info("loveReport: {}", loveReport);
        persistLoveAppTurn(chatId, userId, message, String.valueOf(loveReport));
        return loveReport;
    }

    public String doChatWithRag(String message, String chatId) {
        String userId = currentUserId();
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .advisors(new QuestionAnswerAdvisor(loveAppVectorStore))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        persistLoveAppTurn(chatId, userId, message, content);
        return content;
    }

    public String doChatWithTools(String message, String chatId) {
        String userId = currentUserId();
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        persistLoveAppTurn(chatId, userId, message, content);
        return content;
    }

    public String doChatWithMcp(String message, String chatId) {
        String userId = currentUserId();
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        persistLoveAppTurn(chatId, userId, message, content);
        return content;
    }

    private void persistLoveAppTurn(String chatId, String userId, String userMessage, String assistantMessage) {
        if (loveAppConversationService == null) {
            return;
        }
        loveAppConversationService.recordTurn(
                chatId,
                userId,
                userMessage,
                assistantMessage
        );
    }

    Flux<String> withStreamPersistence(String chatId, String userMessage, Flux<String> assistantStream) {
        return withStreamPersistence(chatId, userMessage, assistantStream, currentUserId());
    }

    Flux<String> withStreamPersistence(String chatId, String userMessage, Flux<String> assistantStream, String userId) {
        StringBuilder assistantText = new StringBuilder();
        AtomicBoolean persisted = new AtomicBoolean(false);
        return assistantStream
                .doOnNext(assistantText::append)
                .doFinally(signalType -> {
                    if (persisted.compareAndSet(false, true)) {
                        persistLoveAppTurn(chatId, userId, userMessage, assistantText.toString());
                    }
                });
    }

    private String currentUserId() {
        return AuthContext.requireCurrentUser().id();
    }
}
