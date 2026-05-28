package com.wudao.kms.industrial.parser;

import com.wudao.kms.dto.DocumentContentResult;
import com.wudao.kms.service.FileContentExtractorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 设备手册解析器
 * 处理设备操作手册、维护指南等 PDF 文档
 *
 * 特化能力：
 * - 章节结构识别（设备概述→安装→操作→维护→故障排除）
 * - 故障代码表提取（E01, F05, Alarm-001 等模式）
 * - 参数规格表结构化（温度/压力/速度/单位自动识别）
 * - 安全提示标记
 */
@Slf4j
@Component
public class EquipmentManualParser implements IndustrialDocumentParser {

    @Resource
    private FileContentExtractorService fileContentExtractorService;

    /** 章节标题模式：中文数字章、阿拉伯数字节、Chapter N */
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "^(第[一二三四五六七八九十百]+[章节篇]|[0-9]+(?:\\.[0-9]+)*\\s+|Chapter\\s+\\d+|CHAPTER\\s+\\d+).*$",
            Pattern.MULTILINE
    );

    /** 故障代码模式（如 E01, F05, Alarm-001, ERR-012） */
    private static final Pattern FAULT_CODE_PATTERN = Pattern.compile(
            "(?:故障代码|报警代码|错误代码|Error|Alarm|Fault|FAULT|ALARM|ERR)\\s*[:：\\-]?\\s*([A-Z][A-Za-z]?-?\\d{2,4})",
            Pattern.CASE_INSENSITIVE
    );

    /** 独立故障代码行：E01 / F05 / Alarm-001 前后可带描述 */
    private static final Pattern FAULT_CODE_LINE_PATTERN = Pattern.compile(
            "^\\s*([A-Z][A-Za-z]?-?\\d{2,4})\\s*[:：\\-]\\s*(.+)$",
            Pattern.MULTILINE
    );

    /** 参数模式（如 温度: 80°C, 压力: 0.5MPa） */
    private static final Pattern PARAM_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fa5a-zA-Z]{2,15})\\s*[:：=]\\s*(\\d+\\.?\\d*)\\s*(°[Cc℃]|MPa|kPa|Pa|bar|m/min|mm/s|rpm|r/min|kW|W|V|A|Hz|%)"
    );

    /** 安全提示模式 */
    private static final Pattern SAFETY_PATTERN = Pattern.compile(
            "(?:⚠|警告|注意|危险|WARNING|CAUTION|DANGER|安全提示|安全须知|安全事项)",
            Pattern.CASE_INSENSITIVE
    );

    /** 设备概述关键词 */
    private static final Pattern OVERVIEW_PATTERN = Pattern.compile(
            "(?:设备概述|产品概述|设备简介|产品简介|简介|概述|OVERVIEW|INTRODUCTION)",
            Pattern.CASE_INSENSITIVE
    );

    /** 安装章节关键词 */
    private static final Pattern INSTALL_PATTERN = Pattern.compile(
            "(?:安装|装配|就位|定位|INSTALLATION|INSTALL)",
            Pattern.CASE_INSENSITIVE
    );

    /** 操作章节关键词 */
    private static final Pattern OPERATION_PATTERN = Pattern.compile(
            "(?:操作|运行|使用|启动|OPERATION|OPERATING)",
            Pattern.CASE_INSENSITIVE
    );

    /** 维护章节关键词 */
    private static final Pattern MAINTENANCE_PATTERN = Pattern.compile(
            "(?:维护|保养|检修|维修|MAINTENANCE|SERVICE)",
            Pattern.CASE_INSENSITIVE
    );

    /** 故障排除章节关键词 */
    private static final Pattern TROUBLESHOOT_PATTERN = Pattern.compile(
            "(?:故障排除|故障诊断|故障处理|异常处理|TROUBLESHOOTING|FAULT|DIAGNOSIS)",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public boolean supports(String fileType) {
        return "equipment_manual".equalsIgnoreCase(fileType)
                || "maintenance_guide".equalsIgnoreCase(fileType)
                || "设备手册".equals(fileType)
                || "维护指南".equals(fileType);
    }

    @Override
    public ParseResult parse(MultipartFile file, String docType) throws Exception {
        String fileName = file.getOriginalFilename();
        log.info("开始解析设备手册: {}, docType: {}", fileName, docType);

        // 使用已有的 FileContentExtractorService 提取 PDF 文本
        String content = extractText(file);
        if (content == null || content.isBlank()) {
            log.warn("文件内容为空: {}", fileName);
            return new ParseResult(fileName, docType, Collections.emptyList(), Map.of("error", "文件内容为空"));
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        Map<String, Object> metadata = new HashMap<>();

        // 按章节分块
        String[] sections = splitBySections(content);
        int pageNum = 1;

        for (String section : sections) {
            String trimmed = section.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // 检测分块类型
            ChunkType chunkType = detectChunkType(trimmed);

            // 提取章节标题
            String sectionTitle = extractSectionTitle(trimmed);

            // 推断章节分类
            String sectionCategory = inferSectionCategory(trimmed);

            // 提取元数据
            Map<String, String> chunkMeta = new HashMap<>();
            chunkMeta.put("sectionCategory", sectionCategory);

            if (chunkType == ChunkType.FAULT_CODE) {
                extractFaultCodes(trimmed, chunkMeta);
            } else if (chunkType == ChunkType.PARAMETER) {
                extractParameters(trimmed, chunkMeta);
            }

            if (containsSafetyWarning(trimmed)) {
                chunkMeta.put("safetyWarning", "true");
            }

            chunks.add(new DocumentChunk(
                    trimmed,
                    pageNum++,
                    sectionTitle,
                    chunkType,
                    chunkMeta
            ));
        }

        // 统计元数据
        long faultCodeCount = chunks.stream().filter(c -> c.chunkType() == ChunkType.FAULT_CODE).count();
        long paramCount = chunks.stream().filter(c -> c.chunkType() == ChunkType.PARAMETER).count();
        long safetyCount = chunks.stream()
                .filter(c -> "true".equals(c.metadata().get("safetyWarning"))).count();

        metadata.put("totalPages", pageNum - 1);
        metadata.put("totalChunks", chunks.size());
        metadata.put("faultCodeCount", faultCodeCount);
        metadata.put("parameterCount", paramCount);
        metadata.put("safetyNoticeCount", safetyCount);

        log.info("设备手册解析完成: {} - {}个分块, {}个故障代码, {}个参数段",
                fileName, chunks.size(), faultCodeCount, paramCount);

        return new ParseResult(fileName, docType, chunks, metadata);
    }

    /**
     * 使用 FileContentExtractorService 提取文本
     */
    private String extractText(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String lowerName = fileName.toLowerCase();

        if (lowerName.endsWith(".pdf")) {
            try (InputStream is = file.getInputStream()) {
                DocumentContentResult result = fileContentExtractorService.extractPdfContent(is);
                return result.getRawContent();
            }
        } else if (lowerName.endsWith(".docx") || lowerName.endsWith(".doc")) {
            // 对于 Word 格式的设备手册，使用 FileContentExtractorService 的本地提取逻辑
            // 先保存到临时文件再提取
            java.io.File tempFile = java.io.File.createTempFile("equip_manual_", lowerName.endsWith(".docx") ? ".docx" : ".doc");
            try {
                file.transferTo(tempFile);
                return fileContentExtractorService.extractContentFromLocalFileContent(tempFile.getAbsolutePath());
            } finally {
                tempFile.delete();
            }
        } else {
            // 兜底：按 UTF-8 文本读取
            return new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * 按章节分割文档
     */
    private String[] splitBySections(String content) {
        List<String> sections = new ArrayList<>();
        Matcher matcher = SECTION_PATTERN.matcher(content);
        int lastEnd = 0;

        while (matcher.find()) {
            if (lastEnd < matcher.start()) {
                String between = content.substring(lastEnd, matcher.start()).trim();
                if (!between.isEmpty()) {
                    sections.add(between);
                }
            }
            lastEnd = matcher.start();
        }
        if (lastEnd < content.length()) {
            String remaining = content.substring(lastEnd).trim();
            if (!remaining.isEmpty()) {
                sections.add(remaining);
            }
        }

        // 如果没有章节结构，按滑动窗口分块
        if (sections.isEmpty()) {
            return slidingWindowChunk(content, 600, 200);
        }

        return sections.toArray(new String[0]);
    }

    /**
     * 滑动窗口分块（chunk_size=600, overlap=200）
     */
    private String[] slidingWindowChunk(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int step = chunkSize - overlap;

        for (int i = 0; i < text.length(); i += step) {
            int end = Math.min(i + chunkSize, text.length());
            chunks.add(text.substring(i, end));
            if (end == text.length()) {
                break;
            }
        }

        return chunks.toArray(new String[0]);
    }

    /**
     * 检测分块类型
     */
    private ChunkType detectChunkType(String section) {
        if (FAULT_CODE_PATTERN.matcher(section).find() || FAULT_CODE_LINE_PATTERN.matcher(section).find()) {
            return ChunkType.FAULT_CODE;
        }
        if (PARAM_PATTERN.matcher(section).find()) {
            return ChunkType.PARAMETER;
        }
        if (containsSafetyWarning(section)) {
            return ChunkType.SAFETY_NOTICE;
        }
        // 检测表格格式（管道符分隔或制表符分隔）
        String[] lines = section.split("\\n");
        int tableLineCount = 0;
        for (String line : lines) {
            if (line.contains("|") || line.split("\\t+").length >= 3) {
                tableLineCount++;
            }
        }
        if (tableLineCount >= 3) {
            return ChunkType.TABLE;
        }
        return ChunkType.TEXT;
    }

    /**
     * 提取章节标题（取第一行非空短文本）
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
     * 推断章节分类：概述/安装/操作/维护/故障排除
     */
    private String inferSectionCategory(String section) {
        // 取前200字符做关键词匹配
        String head = section.substring(0, Math.min(200, section.length()));
        if (TROUBLESHOOT_PATTERN.matcher(head).find()) {
            return "故障排除";
        }
        if (MAINTENANCE_PATTERN.matcher(head).find()) {
            return "维护保养";
        }
        if (OPERATION_PATTERN.matcher(head).find()) {
            return "操作运行";
        }
        if (INSTALL_PATTERN.matcher(head).find()) {
            return "安装";
        }
        if (OVERVIEW_PATTERN.matcher(head).find()) {
            return "设备概述";
        }
        return "其他";
    }

    /**
     * 判断是否包含安全警告
     */
    private boolean containsSafetyWarning(String section) {
        return SAFETY_PATTERN.matcher(section).find();
    }

    /**
     * 提取故障代码到元数据
     */
    private void extractFaultCodes(String section, Map<String, String> metadata) {
        Set<String> codes = new LinkedHashSet<>();

        // 模式1：故障代码: E01
        Matcher m1 = FAULT_CODE_PATTERN.matcher(section);
        while (m1.find()) {
            codes.add(m1.group(1));
        }

        // 模式2：E01: 描述...
        Matcher m2 = FAULT_CODE_LINE_PATTERN.matcher(section);
        while (m2.find()) {
            codes.add(m2.group(1));
        }

        if (!codes.isEmpty()) {
            metadata.put("faultCodes", String.join(",", codes));
            metadata.put("faultCodeCount", String.valueOf(codes.size()));
        }
    }

    /**
     * 提取工艺参数到元数据
     */
    private void extractParameters(String section, Map<String, String> metadata) {
        Matcher matcher = PARAM_PATTERN.matcher(section);
        Map<String, String> params = new LinkedHashMap<>();
        while (matcher.find()) {
            params.put(matcher.group(1), matcher.group(2) + matcher.group(3));
        }
        if (!params.isEmpty()) {
            metadata.put("parameters", params.toString());
            metadata.put("parameterCount", String.valueOf(params.size()));
        }
    }
}
