package com.yupi.yuaiagent.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
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
import java.util.stream.Collectors;

/**
 * Agent 侧知识检索服务：
 * 1) Markdown 结构化切块
 * 2) 关键词元数据增强
 * 3) 向量 + 关键词混合检索
 */
@Service
@Slf4j
public class AgentKnowledgeService {

    private static final int RRF_K = 60;
    private final ResourcePatternResolver resourcePatternResolver;
    private final ChatModel chatModel;
    private final SimpleVectorStore vectorStore;
    private volatile List<Document> knowledgeCorpus = List.of();

    public AgentKnowledgeService(ResourcePatternResolver resourcePatternResolver,
                                 ChatModel dashscopeChatModel,
                                 EmbeddingModel embeddingModel) {
        this.resourcePatternResolver = resourcePatternResolver;
        this.chatModel = dashscopeChatModel;
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();
    }

    @PostConstruct
    public void init() {
        List<Document> markdownDocs = loadMarkdownDocuments();
        if (markdownDocs.isEmpty()) {
            log.warn("Agent 知识库未加载到文档，混合检索将只依赖会话记忆");
            return;
        }
        // 先按 Token 做二次切块，保障每块语义密度。
        TokenTextSplitter splitter = new TokenTextSplitter(400, 100, 10, 5000, true);
        List<Document> splitDocs = splitter.apply(markdownDocs);
        List<Document> processedDocs = enrichKeywordsSafely(splitDocs);
        this.knowledgeCorpus = processedDocs;
        this.vectorStore.add(processedDocs);
        log.info("Agent 知识库初始化完成，文档块数量：{}", processedDocs.size());
    }

    public List<Document> recallHybrid(String query, int topK) {
        if (query == null || query.isBlank() || knowledgeCorpus.isEmpty()) {
            return List.of();
        }
        int candidateTopK = Math.max(topK * 2, 6);

        List<Document> vectorCandidates = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(candidateTopK).build()
        );
        List<ScoredDocument> vectorScored = buildVectorRanking(vectorCandidates);
        List<ScoredDocument> keywordScored = buildKeywordRanking(knowledgeCorpus, query, candidateTopK);

        return mergeAndRankByRrf(vectorScored, keywordScored, topK);
    }

    private List<Document> loadMarkdownDocuments() {
        List<Document> allDocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", filename)
                        .withAdditionalMetadata("sourceType", "knowledge")
                        .build();
                MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
                allDocuments.addAll(markdownDocumentReader.get());
            }
        } catch (IOException e) {
            log.error("Agent 知识库 Markdown 文档加载失败", e);
        }
        return allDocuments;
    }

    private List<Document> enrichKeywordsSafely(List<Document> documents) {
        try {
            KeywordMetadataEnricher enricher = new KeywordMetadataEnricher(chatModel, 5);
            List<Document> enrichedDocs = enricher.apply(documents);
            return enrichedDocs.stream().map(this::markKnowledgeSource).toList();
        } catch (Exception e) {
            log.warn("关键词元数据增强失败，降级为原始文档块：{}", e.getMessage());
            return documents.stream().map(this::markKnowledgeSource).toList();
        }
    }

    private Document markKnowledgeSource(Document document) {
        Map<String, Object> metadata = new HashMap<>(document.getMetadata());
        metadata.put("sourceType", "knowledge");
        return new Document(document.getText(), metadata);
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

    private List<Document> mergeAndRankByRrf(List<ScoredDocument> vectorScored,
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
                // 使用 RRF 融合不同检索通道的排名，避免分值量纲不一致。
                .peek(item -> item.rrfScore = calculateRrfScore(item.vectorRank, item.keywordRank))
                .sorted(Comparator.comparingDouble((MergeItem item) -> item.rrfScore).reversed())
                .limit(topK)
                .map(MergeItem::toDocument)
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
        Object filename = document.getMetadata().get("filename");
        String base = filename != null && !String.valueOf(filename).isBlank()
                ? String.valueOf(filename) + "::" + document.getText()
                : document.getText();
        return Integer.toHexString(Objects.requireNonNullElse(base, "").hashCode());
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
            metadata.put("rank_vec", vectorRank == Integer.MAX_VALUE ? null : vectorRank);
            metadata.put("rank_kw", keywordRank == Integer.MAX_VALUE ? null : keywordRank);
            metadata.put("score_hybrid", rrfScore);
            metadata.put("sourceType", "knowledge");
            return new Document(document.getText(), metadata);
        }
    }
}
