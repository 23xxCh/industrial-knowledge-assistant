package com.wudao.kms.industrial.parser;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 设备手册解析器
 * 处理设备操作手册、维护指南等 PDF 文档
 * 
 * 特化能力：
 * - 章节结构识别（设备概述→安装→操作→维护→故障排除）
 * - 故障代码表提取
 * - 参数规格表结构化
 * - 安全提示标记
 */
@Component
public class EquipmentManualParser implements IndustrialDocumentParser {

    // 章节标题模式
    private static final Pattern SECTION_PATTERN = Pattern.compile(
        "^(第[一二三四五六七八九十]+章|[0-9]+\\.[0-9]*\\s|\\d+\\.\\s|Chapter\\s+\\d+)",
        Pattern.MULTILINE
    );

    // 故障代码模式（如 E01, F05, Alarm-001）
    private static final Pattern FAULT_CODE_PATTERN = Pattern.compile(
        "(?:故障代码|报警代码|错误代码|Error|Alarm|Fault)\\s*[:：]?\\s*([A-Z]-?\\d{2,3}|E\\d{2,3}|F\\d{2,3})",
        Pattern.CASE_INSENSITIVE
    );

    // 参数模式（如 温度: 80°C, 压力: 0.5MPa）
    private static final Pattern PARAM_PATTERN = Pattern.compile(
        "([\\u4e00-\\u9fa5a-zA-Z]+)\\s*[:：=]\\s*(\\d+\\.?\\d*)\\s*(°C|MPa|m/min|rpm|kW|V|A|Hz|%)"
    );

    // 安全提示模式
    private static final Pattern SAFETY_PATTERN = Pattern.compile(
        "(?:⚠|警告|注意|危险|WARNING|CAUTION|DANGER|安全提示)",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public boolean supports(String fileType) {
        return "equipment_manual".equalsIgnoreCase(fileType) ||
               "maintenance_guide".equalsIgnoreCase(fileType);
    }

    @Override
    public ParseResult parse(MultipartFile file, String docType) throws Exception {
        String fileName = file.getOriginalFilename();
        String content = extractText(file);
        
        List<DocumentChunk> chunks = new ArrayList<>();
        Map<String, Object> metadata = new HashMap<>();
        
        // 按章节分块
        String[] sections = splitBySections(content);
        int pageNum = 1;
        
        for (String section : sections) {
            // 检测分块类型
            ChunkType chunkType = detectChunkType(section);
            
            // 提取元数据
            Map<String, String> chunkMeta = new HashMap<>();
            if (chunkType == ChunkType.FAULT_CODE) {
                extractFaultCodes(section, chunkMeta);
            } else if (chunkType == ChunkType.PARAMETER) {
                extractParameters(section, chunkMeta);
            }
            
            // 提取章节标题
            String sectionTitle = extractSectionTitle(section);
            
            chunks.add(new DocumentChunk(
                section.trim(),
                pageNum++,
                sectionTitle,
                chunkType,
                chunkMeta
            ));
        }
        
        metadata.put("totalPages", pageNum - 1);
        metadata.put("totalChunks", chunks.size());
        metadata.put("faultCodeCount", chunks.stream()
            .filter(c -> c.chunkType() == ChunkType.FAULT_CODE).count());
        
        return new ParseResult(fileName, docType, chunks, metadata);
    }

    /**
     * 提取文本内容（简化实现，实际应使用 PDFBox）
     */
    private String extractText(MultipartFile file) throws Exception {
        // TODO: 集成 PDFBox / MinerU 进行实际 PDF 解析
        // 这里返回原始文本，实际实现需要：
        // 1. 使用 PDFBox 提取文本
        // 2. 使用 pdfplumber 提取表格
        // 3. 可选使用 MinerU 进行增强解析（OCR + 布局分析）
        return new String(file.getBytes(), "UTF-8");
    }

    /**
     * 按章节分割文档
     */
    private String[] splitBySections(String content) {
        // 使用正则按章节标题分割
        List<String> sections = new ArrayList<>();
        Matcher matcher = SECTION_PATTERN.matcher(content);
        int lastEnd = 0;
        
        while (matcher.find()) {
            if (lastEnd < matcher.start()) {
                sections.add(content.substring(lastEnd, matcher.start()));
            }
            lastEnd = matcher.start();
        }
        if (lastEnd < content.length()) {
            sections.add(content.substring(lastEnd));
        }
        
        // 如果没有章节结构，按段落分块（滑动窗口）
        if (sections.isEmpty()) {
            return slidingWindowChunk(content, 600, 200);
        }
        
        return sections.toArray(new String[0]);
    }

    /**
     * 滑动窗口分块（参考 hybrid-rag-tech-assistant）
     * chunk_size=600, overlap=200
     */
    private String[] slidingWindowChunk(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int step = chunkSize - overlap;
        
        for (int i = 0; i < text.length(); i += step) {
            int end = Math.min(i + chunkSize, text.length());
            chunks.add(text.substring(i, end));
            if (end == text.length()) break;
        }
        
        return chunks.toArray(new String[0]);
    }

    /**
     * 检测分块类型
     */
    private ChunkType detectChunkType(String section) {
        if (FAULT_CODE_PATTERN.matcher(section).find()) {
            return ChunkType.FAULT_CODE;
        }
        if (PARAM_PATTERN.matcher(section).find()) {
            return ChunkType.PARAMETER;
        }
        if (SAFETY_PATTERN.matcher(section).find()) {
            return ChunkType.SAFETY_NOTICE;
        }
        // 检测是否为表格格式
        if (section.contains("|") && section.split("\\|").length > 3) {
            return ChunkType.TABLE;
        }
        return ChunkType.TEXT;
    }

    /**
     * 提取章节标题
     */
    private String extractSectionTitle(String section) {
        String[] lines = section.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && trimmed.length() < 100) {
                return trimmed;
            }
        }
        return "未命名章节";
    }

    /**
     * 提取故障代码
     */
    private void extractFaultCodes(String section, Map<String, String> metadata) {
        Matcher matcher = FAULT_CODE_PATTERN.matcher(section);
        List<String> codes = new ArrayList<>();
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        if (!codes.isEmpty()) {
            metadata.put("faultCodes", String.join(",", codes));
        }
    }

    /**
     * 提取工艺参数
     */
    private void extractParameters(String section, Map<String, String> metadata) {
        Matcher matcher = PARAM_PATTERN.matcher(section);
        Map<String, String> params = new LinkedHashMap<>();
        while (matcher.find()) {
            params.put(matcher.group(1), matcher.group(2) + matcher.group(3));
        }
        if (!params.isEmpty()) {
            metadata.put("parameters", params.toString());
        }
    }
}
