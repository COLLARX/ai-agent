package com.yupi.yuaiagent.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 记忆服务：管理短期和长期记忆
 */
@Service
@Slf4j
public class MemoryService {

    private static final int RRF_K = 60;
    private static final String SESSION_KEYWORD_SQL = """
            SELECT content, metadata
            FROM vector_store
            WHERE metadata::jsonb ->> 'sessionId' = ?
              AND metadata::jsonb ->> 'sourceType' = 'chat_memory'
              AND (
                content ILIKE ?
                OR EXISTS (
                  SELECT 1
                  FROM jsonb_array_elements_text(COALESCE(metadata::jsonb -> 'keywords', '[]'::jsonb)) kw
                  WHERE kw ILIKE ?
                )
              )
            LIMIT ?
            """;
    private final VectorStore vectorStore;
    private final AgentKnowledgeService agentKnowledgeService;
    @Nullable
    private final JdbcTemplate jdbcTemplate;
    private static final int SHORT_TERM_WINDOW = 10; // 短期记忆窗口大小
    private static final int MAX_VECTOR_RECALL_CANDIDATES = 10;
    private final Map<String, List<Document>> sessionArchiveIndex = new ConcurrentHashMap<>();

    public MemoryService(VectorStore vectorStore,
                         AgentKnowledgeService agentKnowledgeService,
                         @Nullable JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.agentKnowledgeService = agentKnowledgeService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 异步向量化存储历史消息
     */
    @Async
    public void archiveToLongTerm(String sessionId, List<Message> messages) {
        if (messages.isEmpty()) return;

        List<Document> documents = messages.stream()
                .filter(message -> message != null && message.getText() != null && !message.getText().isBlank())
                .map(msg -> {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("sessionId", sessionId);
                    metadata.put("timestamp", LocalDateTime.now().toString());
                    metadata.put("type", msg.getMessageType().getValue());
                    metadata.put("sourceType", "chat_memory");
                    metadata.put("keywords", extractKeywords(msg.getText(), 8));
                    return new Document(msg.getText(), metadata);
                })
                .collect(Collectors.toList());

        if (documents.isEmpty()) {
            return;
        }
        vectorStore.add(documents);
        sessionArchiveIndex.compute(sessionId, (key, oldValue) -> {
            List<Document> merged = oldValue == null ? new ArrayList<>() : new ArrayList<>(oldValue);
            merged.addAll(documents);
            return merged;
        });
        log.info("已归档 {} 条消息到长期记忆", documents.size());
    }

    /**
     * 动态召回相关历史记忆
     */
    public List<String> recallRelevant(String sessionId, String query, int topK) {
        return recallRelevantHybrid(sessionId, query, topK);
    }

    /**
     * 会话记忆 + 领域知识库 的混合检索召回
     */
    public List<String> recallRelevantHybrid(String sessionId, String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        List<ScoredDocument> sessionHybrid = recallSessionHybrid(sessionId, query, topK);
        List<ScoredDocument> knowledgeHybrid = agentKnowledgeService.recallHybrid(query, topK).stream()
                .map(document -> new ScoredDocument(document, extractHybridOrFallback(document)))
                .toList();

        List<ScoredDocument> all = new ArrayList<>();
        all.addAll(sessionHybrid);
        all.addAll(knowledgeHybrid);

        return all.stream()
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .limit(topK)
                .map(item -> {
                    String sourceType = String.valueOf(item.document().getMetadata()
                            .getOrDefault("sourceType", "chat_memory"));
                    if ("knowledge".equals(sourceType)) {
                        return "[知识库] " + item.document().getText();
                    }
                    return "[历史记忆] " + item.document().getText();
                })
                .toList();
    }

    private List<ScoredDocument> recallSessionHybrid(String sessionId, String query, int topK) {
        int candidateTopK = Math.max(topK * 2, MAX_VECTOR_RECALL_CANDIDATES);
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(candidateTopK)
                .filterExpression("sessionId == '" + escapeSingleQuote(sessionId) + "'")
                .build();
        List<Document> vectorCandidates = vectorStore.similaritySearch(request);
        List<ScoredDocument> vectorScored = buildVectorRanking(vectorCandidates);
        List<ScoredDocument> keywordScored = querySessionKeywordRanking(sessionId, query, candidateTopK);
        return mergeAndRankByRrf(vectorScored, keywordScored, topK);
    }

    private List<ScoredDocument> querySessionKeywordRanking(String sessionId, String query, int topK) {
        List<Document> pgKeywordCandidates = querySessionKeywordCandidatesFromPg(sessionId, query, topK);
        if (!pgKeywordCandidates.isEmpty()) {
            return buildKeywordRanking(pgKeywordCandidates, query, topK);
        }
        List<Document> sessionCorpus = sessionArchiveIndex.getOrDefault(sessionId, List.of());
        return buildKeywordRanking(sessionCorpus, query, topK);
    }

    private List<Document> querySessionKeywordCandidatesFromPg(String sessionId, String query, int topK) {
        if (jdbcTemplate == null) {
            return List.of();
        }
        String likeQuery = "%" + escapeLike(query) + "%";
        try {
            return jdbcTemplate.query(SESSION_KEYWORD_SQL, (rs, rowNum) -> new Document(rs.getString("content")),
                    sessionId, likeQuery, likeQuery, topK);
        } catch (Exception e) {
            log.warn("PgVector 关键词检索失败，回退内存检索：{}", e.getMessage());
            return List.of();
        }
    }

    private List<ScoredDocument> buildVectorRanking(List<Document> candidates) {
        List<ScoredDocument> scored = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            Document document = candidates.get(i);
            double score = extractVectorScore(document);
            if (score <= 0.0) {
                score = 1.0 - (i / (double) (candidates.size() + 1));
            }
            scored.add(new ScoredDocument(document, score));
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .toList();
    }

    private List<ScoredDocument> buildKeywordRanking(List<Document> corpus, String query, int topK) {
        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }
        List<ScoredDocument> scored = new ArrayList<>();
        for (Document document : corpus) {
            double score = keywordScore(document, queryTokens);
            if (score <= 0.0) {
                continue;
            }
            scored.add(new ScoredDocument(document, score));
        }
        if (scored.isEmpty()) {
            return List.of();
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .limit(topK)
                .toList();
    }

    private List<ScoredDocument> mergeAndRankByRrf(List<ScoredDocument> vectorScored,
                                                   List<ScoredDocument> keywordScored,
                                                   int topK) {
        Map<String, MergeItem> merged = new LinkedHashMap<>();
        for (int i = 0; i < vectorScored.size(); i++) {
            ScoredDocument item = vectorScored.get(i);
            String key = buildKey(item.document());
            MergeItem mergeItem = merged.computeIfAbsent(key, k -> new MergeItem(item.document()));
            mergeItem.vectorRank = Math.min(mergeItem.vectorRank, i + 1);
        }
        for (int i = 0; i < keywordScored.size(); i++) {
            ScoredDocument item = keywordScored.get(i);
            String key = buildKey(item.document());
            MergeItem mergeItem = merged.computeIfAbsent(key, k -> new MergeItem(item.document()));
            mergeItem.keywordRank = Math.min(mergeItem.keywordRank, i + 1);
        }
        return merged.values().stream()
                // 使用 RRF 只依赖排名进行融合，规避不同检索通道分数不可比的问题。
                .peek(item -> item.rrfScore = calculateRrfScore(item.vectorRank, item.keywordRank))
                .sorted(Comparator.comparingDouble((MergeItem item) -> item.rrfScore).reversed())
                .limit(topK)
                .map(item -> new ScoredDocument(item.toDocument(), item.rrfScore))
                .toList();
    }

    private double calculateRrfScore(int vectorRank, int keywordRank) {
        double score = 0.0;
        if (vectorRank != Integer.MAX_VALUE) {
            score += 1.0 / (RRF_K + vectorRank);
        }
        if (keywordRank != Integer.MAX_VALUE) {
            score += 1.0 / (RRF_K + keywordRank);
        }
        return score;
    }

    private String buildKey(Document document) {
        String text = Objects.requireNonNullElse(document.getText(), "");
        String source = String.valueOf(document.getMetadata().getOrDefault("sourceType", "chat_memory"));
        return Integer.toHexString((source + "::" + text).hashCode());
    }

    private double extractHybridOrFallback(Document document) {
        Object hybrid = document.getMetadata().get("score_hybrid");
        if (hybrid instanceof Number number) {
            return number.doubleValue();
        }
        return extractVectorScore(document);
    }

    private double extractVectorScore(Document document) {
        Object score = document.getMetadata().get("score");
        if (score instanceof Number number) {
            return number.doubleValue();
        }
        Object similarity = document.getMetadata().get("similarity");
        if (similarity instanceof Number number) {
            return number.doubleValue();
        }
        Object distance = document.getMetadata().get("distance");
        if (distance instanceof Number number) {
            return 1.0 / (1.0 + number.doubleValue());
        }
        return 0.0;
    }

    private double keywordScore(Document document, Set<String> queryTokens) {
        if (queryTokens.isEmpty()) {
            return 0.0;
        }
        double score = 0.0;
        Object keywords = document.getMetadata().get("keywords");
        if (keywords instanceof Collection<?> keywordCollection) {
            for (Object keyword : keywordCollection) {
                String token = normalize(String.valueOf(keyword));
                if (!token.isBlank() && queryTokens.contains(token)) {
                    score += 2.0;
                }
            }
        } else if (keywords instanceof String keywordString) {
            for (String token : tokenize(keywordString)) {
                if (queryTokens.contains(token)) {
                    score += 2.0;
                }
            }
        }
        String text = normalize(document.getText());
        for (String token : queryTokens) {
            if (token.length() >= 2 && text.contains(token)) {
                score += 1.0;
            }
        }
        return score;
    }

    private List<String> extractKeywords(String text, int maxKeywords) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Map<String, Long> tokenCount = tokenize(text).stream()
                .filter(token -> token.length() >= 2)
                .collect(Collectors.groupingBy(token -> token, Collectors.counting()));
        return tokenCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(maxKeywords)
                .map(Map.Entry::getKey)
                .toList();
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(text.split("[^\\p{L}\\p{Nd}]+"))
                .map(this::normalize)
                .filter(token -> !token.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private String escapeSingleQuote(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private String escapeLike(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /**
     * 管理滑动窗口：保留最近N条，归档旧消息
     */
    public List<Message> manageMemoryWindow(String sessionId, List<Message> allMessages) {
        if (allMessages.size() <= SHORT_TERM_WINDOW) {
            return allMessages;
        }

        // 分离短期和需归档的消息
        List<Message> toArchive = allMessages.subList(0, allMessages.size() - SHORT_TERM_WINDOW);
        List<Message> shortTerm = allMessages.subList(allMessages.size() - SHORT_TERM_WINDOW, allMessages.size());

        // 异步归档
        archiveToLongTerm(sessionId, toArchive);

        return shortTerm;
    }

    private record ScoredDocument(Document document, double score) {
    }

    private static class MergeItem {
        private final Document document;
        private int vectorRank = Integer.MAX_VALUE;
        private int keywordRank = Integer.MAX_VALUE;
        private double rrfScore;

        private MergeItem(Document document) {
            this.document = document;
        }

        private Document toDocument() {
            Map<String, Object> metadata = new HashMap<>(document.getMetadata());
            metadata.put("score_hybrid", rrfScore);
            if (vectorRank != Integer.MAX_VALUE) {
                metadata.put("rank_vec", vectorRank);
            }
            if (keywordRank != Integer.MAX_VALUE) {
                metadata.put("rank_kw", keywordRank);
            }
            return new Document(document.getText(), metadata);
        }
    }
}
