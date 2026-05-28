package com.wudao.kms.retrieval.fusion;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * RRF (Reciprocal Rank Fusion) 融合检索服务
 * 参考 hybrid-rag-tech-assistant 的核心思路
 * 
 * Dense 检索擅长语义理解（"温度过高" 能匹配到 "超温报警"）
 * Sparse 检索擅长精确匹配（设备型号 "30XA"、报警代码 "E05"、具体数值 "80°C"）
 * RRF 融合后效果优于任何单一方法
 */
@Service
public class RRFFusionService {

    /**
     * RRF 常量 k（参考论文推荐值 60）
     */
    private static final int RRF_K = 60;

    /**
     * Dense 检索结果
     */
    public record RetrievalResult(
        String docId,
        String content,
        double score,
        int rank,
        String source,      // "dense" 或 "sparse"
        Map<String, String> metadata
    ) {}

    /**
     * 融合后的结果
     */
    public record FusedResult(
        String docId,
        String content,
        double rrfScore,
        Map<String, String> metadata
    ) {}

    /**
     * RRF 融合多路检索结果
     * 
     * 公式：RRF_score(d) = Σ 1/(k + rank_i(d))
     * 
     * @param denseResults Dense 向量检索结果（按相似度排序）
     * @param sparseResults Sparse BM25 检索结果（按 BM25 分数排序）
     * @param topK 返回前 K 个结果
     * @param denseWeight Dense 权重（默认 0.5）
     * @param sparseWeight Sparse 权重（默认 0.5）
     * @return 融合后的结果列表
     */
    public List<FusedResult> fuse(
            List<RetrievalResult> denseResults,
            List<RetrievalResult> sparseResults,
            int topK,
            double denseWeight,
            double sparseWeight) {
        
        Map<String, Double> fusedScores = new LinkedHashMap<>();
        Map<String, String> contents = new HashMap<>();
        Map<String, Map<String, String>> metadatas = new HashMap<>();
        
        // Dense 路
        for (int i = 0; i < denseResults.size(); i++) {
            RetrievalResult r = denseResults.get(i);
            String docId = r.docId();
            double contribution = denseWeight / (RRF_K + i + 1);
            fusedScores.merge(docId, contribution, Double::sum);
            contents.putIfAbsent(docId, r.content());
            metadatas.putIfAbsent(docId, r.metadata());
        }
        
        // Sparse 路
        for (int i = 0; i < sparseResults.size(); i++) {
            RetrievalResult r = sparseResults.get(i);
            String docId = r.docId();
            double contribution = sparseWeight / (RRF_K + i + 1);
            fusedScores.merge(docId, contribution, Double::sum);
            contents.putIfAbsent(docId, r.content());
            metadatas.putIfAbsent(docId, r.metadata());
        }
        
        // 排序并取 Top-K
        return fusedScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(entry -> new FusedResult(
                entry.getKey(),
                contents.get(entry.getKey()),
                entry.getValue(),
                metadatas.getOrDefault(entry.getKey(), Map.of())
            ))
            .toList();
    }

    /**
     * 默认等权融合
     */
    public List<FusedResult> fuse(
            List<RetrievalResult> denseResults,
            List<RetrievalResult> sparseResults,
            int topK) {
        return fuse(denseResults, sparseResults, topK, 0.5, 0.5);
    }

    /**
     * 去重（基于 docId）
     */
    public List<RetrievalResult> deduplicate(List<RetrievalResult> results) {
        Set<String> seen = new HashSet<>();
        List<FusedResult> deduped = new ArrayList<>();
        // 保留首次出现的结果
        return results.stream()
            .filter(r -> seen.add(r.docId()))
            .toList();
    }
}
