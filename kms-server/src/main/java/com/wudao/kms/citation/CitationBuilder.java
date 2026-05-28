package com.wudao.kms.citation;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 引用溯源构建器
 * 参考 hybrid-rag-tech-assistant 的引用格式：
 * 每个事实性陈述都附带 [source: 文件名, p.页码] 引用标记
 * 
 * 作用：
 * 1. 让用户可以验证回答的来源
 * 2. 提高回答的可信度
 * 3. 便于追溯错误
 */
@Service
public class CitationBuilder {

    /**
     * 引用条目
     */
    public record Citation(
        String sourceFile,
        int pageNumber,
        String sectionTitle,
        String excerpt,     // 被引用的原文片段
        double relevance    // 相关性分数
    ) {}

    /**
     * 带引用的回答
     */
    public record CitedAnswer(
        String answer,
        List<Citation> citations,
        double overallConfidence
    ) {}

    /**
     * 为 LLM 回答添加引用标记
     * 
     * 策略：
     * 1. 在 System Prompt 中要求 LLM 使用 [source: file, p.N] 格式引用
     * 2. 后处理：验证引用是否真实存在
     * 3. 补充：为未引用的关键事实添加引用
     * 
     * @param llmAnswer LLM 原始回答
     * @param retrievedChunks 检索到的文档分块（用于引用验证）
     * @return 带引用的回答
     */
    public CitedAnswer buildCitations(String llmAnswer, List<RetrievedChunk> retrievedChunks) {
        List<Citation> citations = new ArrayList<>();
        
        // 1. 提取 LLM 已有的引用
        Pattern citePattern = Pattern.compile("\\[source:\\s*(.+?),\\s*p\\.(\\d+)\\]");
        Matcher matcher = citePattern.matcher(llmAnswer);
        
        Set<String> citedFiles = new HashSet<>();
        while (matcher.find()) {
            String file = matcher.group(1).trim();
            int page = Integer.parseInt(matcher.group(2));
            citedFiles.add(file + ":" + page);
            
            // 验证引用是否存在
            RetrievedChunk matchingChunk = findMatchingChunk(retrievedChunks, file, page);
            if (matchingChunk != null) {
                citations.add(new Citation(
                    file, page,
                    matchingChunk.sectionTitle(),
                    matchingChunk.content().substring(0, Math.min(200, matchingChunk.content().length())),
                    matchingChunk.score()
                ));
            }
        }
        
        // 2. 为未引用的关键段落补充引用建议
        for (RetrievedChunk chunk : retrievedChunks) {
            String key = chunk.sourceFile() + ":" + chunk.pageNumber();
            if (!citedFiles.contains(key) && chunk.score() > 0.8) {
                // 高相关但未被引用，添加为"建议引用"
                citations.add(new Citation(
                    chunk.sourceFile(),
                    chunk.pageNumber(),
                    chunk.sectionTitle(),
                    chunk.content().substring(0, Math.min(200, chunk.content().length())),
                    chunk.score()
                ));
            }
        }
        
        // 3. 计算整体置信度
        double confidence = citations.isEmpty() ? 0.0 :
            citations.stream().mapToDouble(Citation::relevance).average().orElse(0.0);
        
        return new CitedAnswer(llmAnswer, citations, confidence);
    }

    /**
     * 生成引用摘要（用于 UI 展示）
     */
    public String formatCitationsForDisplay(List<Citation> citations) {
        if (citations.isEmpty()) {
            return "⚠️ 无引用来源";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("📚 引用来源：\n");
        for (int i = 0; i < citations.size(); i++) {
            Citation c = citations.get(i);
            sb.append(String.format("[%d] %s (第%d页) - %s\n",
                i + 1, c.sourceFile(), c.pageNumber(), c.sectionTitle()));
        }
        return sb.toString();
    }

    /**
     * 构建 System Prompt 中的引用指令
     */
    public String getCitationPromptInstruction() {
        return """
            ## 引用规则
            你在回答问题时，必须为每个事实性陈述标注来源。
            使用以下格式：[source: 文件名, p.页码]
            
            示例：
            - 设备最高工作温度为 85°C [source: 操作手册v2.pdf, p.12]
            - 当出现 E05 报警时，应检查传感器接线 [source: 故障排除指南.pdf, p.34]
            
            如果检索到的文档中没有相关信息，请明确说明"根据现有知识库未找到相关信息"。
            不要编造引用来源。
            """;
    }

    private RetrievedChunk findMatchingChunk(List<RetrievedChunk> chunks, String file, int page) {
        return chunks.stream()
            .filter(c -> c.sourceFile().contains(file) || file.contains(c.sourceFile()))
            .filter(c -> c.pageNumber() == page)
            .findFirst()
            .orElse(null);
    }

    /**
     * 检索到的文档分块
     */
    public record RetrievedChunk(
        String docId,
        String sourceFile,
        int pageNumber,
        String sectionTitle,
        String content,
        double score
    ) {}
}
