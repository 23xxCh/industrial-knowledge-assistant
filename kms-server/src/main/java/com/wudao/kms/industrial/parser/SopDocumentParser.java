package com.wudao.kms.industrial.parser;

import com.wudao.kms.service.FileContentExtractorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SOP 文档解析器
 * 处理标准作业程序（Standard Operating Procedure）文档
 *
 * 特化能力：
 * - 步骤序列提取（Step 1, 步骤1, 第一步 等模式）
 * - 检查项与判定标准结构化
 * - 安全注意事项标记
 */
@Slf4j
@Component
public class SopDocumentParser implements IndustrialDocumentParser {

    @Resource
    private FileContentExtractorService fileContentExtractorService;

    /** 步骤模式：Step 1 / 步骤1 / 第一步 / 1. / 1、 */
    private static final Pattern STEP_PATTERN = Pattern.compile(
            "^(?:Step\\s*(\\d+)|步骤\\s*(\\d+)|第([一二三四五六七八九十百零]+)步|(\\d+)[.、．])\\s*[：:.]?\\s*(.*)",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );

    /** 检查项模式 */
    private static final Pattern CHECK_PATTERN = Pattern.compile(
            "(?:检查|确认|验证|核实|Check|Verify|Inspect|Validate)\\s*[:：]?\\s*(.+?)(?:\\s*[，,;；]\\s*(?:标准|判定|要求|合格标准|判定标准|Accept)\\s*[:：]?\\s*(.+))?$",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );

    /** 设备/工具关联模式 */
    private static final Pattern EQUIPMENT_PATTERN = Pattern.compile(
            "(?:使用设备|所需工具|设备工具|Equipment|Tools?|所需设备)\\s*[:：]?\\s*(.+)",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );

    /** 安全提示模式 */
    private static final Pattern SAFETY_PATTERN = Pattern.compile(
            "(?:⚠|⚠️|警告|注意|危险|WARNING|CAUTION|DANGER|安全提示|安全须知|安全事项|防护要求|劳保用品)",
            Pattern.CASE_INSENSITIVE
    );

    /** SOP 编号模式 */
    private static final Pattern SOP_NO_PATTERN = Pattern.compile(
            "(?:SOP[\\-\\s]?编号|文件编号|Document\\s*No\\.?|Doc\\s*No\\.?)\\s*[:：]?\\s*([A-Za-z0-9\\-]+)",
            Pattern.CASE_INSENSITIVE
    );

    /** 版本模式 */
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "(?:版本[号]?|Version|Rev\\.?)\\s*[:：]?\\s*([A-Za-z0-9.]+)",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public boolean supports(String fileType) {
        return "sop".equalsIgnoreCase(fileType)
                || "标准作业程序".equals(fileType)
                || "作业指导书".equals(fileType)
                || "sop_document".equalsIgnoreCase(fileType);
    }

    @Override
    public ParseResult parse(MultipartFile file, String docType) throws Exception {
        String fileName = file.getOriginalFilename();
        log.info("开始解析 SOP 文档: {}, docType: {}", fileName, docType);

        // 使用 FileContentExtractorService 提取文本
        String content = extractText(file);
        if (content == null || content.isBlank()) {
            log.warn("SOP 文档内容为空: {}", fileName);
            return new ParseResult(fileName, docType, Collections.emptyList(), Map.of("error", "文档内容为空"));
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        Map<String, Object> metadata = new HashMap<>();

        // 提取 SOP 元信息
        String sopTitle = extractSopTitle(content);
        String sopNo = extractSopNo(content);
        String version = extractVersion(content);
        String equipment = extractEquipment(content);

        // 按步骤分块
        String[] steps = splitBySteps(content);
        int stepNum = 1;

        for (String step : steps) {
            String trimmed = step.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            Map<String, String> chunkMeta = new HashMap<>();
            chunkMeta.put("sopTitle", sopTitle);
            chunkMeta.put("stepNumber", String.valueOf(stepNum));
            if (sopNo != null) {
                chunkMeta.put("sopNo", sopNo);
            }
            if (version != null) {
                chunkMeta.put("version", version);
            }
            if (equipment != null) {
                chunkMeta.put("equipment", equipment);
            }

            // 检测检查项
            List<CheckItem> checkItems = extractCheckItems(trimmed);
            if (!checkItems.isEmpty()) {
                chunkMeta.put("checkItems", serializeCheckItems(checkItems));
                chunkMeta.put("checkItemCount", String.valueOf(checkItems.size()));
            }

            // 检测安全提示
            boolean hasSafetyWarning = containsSafetyWarning(trimmed);
            if (hasSafetyWarning) {
                chunkMeta.put("safetyWarning", "true");
            }

            ChunkType chunkType = hasSafetyWarning ? ChunkType.SAFETY_NOTICE : ChunkType.SOP_STEP;

            // 构建内容文本
            StringBuilder contentText = new StringBuilder();
            contentText.append(String.format("【步骤 %d】%s", stepNum, trimmed));
            if (!checkItems.isEmpty()) {
                contentText.append("\n检查项:");
                for (CheckItem item : checkItems) {
                    contentText.append(String.format("\n  - %s → 判定标准: %s", item.name, item.standard));
                }
            }

            chunks.add(new DocumentChunk(
                    contentText.toString(),
                    stepNum,
                    "步骤 " + stepNum,
                    chunkType,
                    chunkMeta
            ));

            stepNum++;
        }

        // 提取安全注意事项段落（独立的安全段落，不属于步骤）
        List<String> safetySections = extractSafetySections(content);
        for (String safetySection : safetySections) {
            Map<String, String> chunkMeta = new HashMap<>();
            chunkMeta.put("safetyWarning", "true");
            chunkMeta.put("sopTitle", sopTitle);
            chunkMeta.put("standaloneSafety", "true");

            chunks.add(new DocumentChunk(
                    "⚠️ 安全注意事项：" + safetySection,
                    0,
                    "安全注意事项",
                    ChunkType.SAFETY_NOTICE,
                    chunkMeta
            ));
        }

        // 统计
        long safetyCount = chunks.stream()
                .filter(c -> "true".equals(c.metadata().get("safetyWarning"))).count();
        long checkItemCount = chunks.stream()
                .mapToLong(c -> c.metadata().containsKey("checkItemCount")
                        ? Long.parseLong(c.metadata().get("checkItemCount")) : 0)
                .sum();

        metadata.put("totalSteps", chunks.stream().filter(c -> c.chunkType() == ChunkType.SOP_STEP).count());
        metadata.put("sopTitle", sopTitle);
        metadata.put("sopNo", sopNo != null ? sopNo : "");
        metadata.put("version", version != null ? version : "");
        metadata.put("equipment", equipment != null ? equipment : "");
        metadata.put("safetyNoticeCount", safetyCount);
        metadata.put("checkItemCount", checkItemCount);
        metadata.put("totalChunks", chunks.size());

        log.info("SOP 文档解析完成: {} - {}个步骤, {}个安全提示, {}个检查项",
                fileName, chunks.size(), safetyCount, checkItemCount);

        return new ParseResult(fileName, docType, chunks, metadata);
    }

    /**
     * 使用 FileContentExtractorService 提取文本内容
     */
    private String extractText(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String lowerName = fileName.toLowerCase();

        if (lowerName.endsWith(".docx") || lowerName.endsWith(".doc")) {
            File tempFile = File.createTempFile("sop_doc_", lowerName.endsWith(".docx") ? ".docx" : ".doc");
            try {
                file.transferTo(tempFile);
                return fileContentExtractorService.extractContentFromLocalFileContent(tempFile.getAbsolutePath());
            } finally {
                tempFile.delete();
            }
        } else if (lowerName.endsWith(".pdf")) {
            var result = fileContentExtractorService.extractPdfContent(file.getInputStream());
            return result.getRawContent();
        } else {
            return new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * 提取 SOP 标题（第一个非空短行）
     */
    private String extractSopTitle(String content) {
        String[] lines = content.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && trimmed.length() < 200 && !trimmed.matches("^[\\-=]+$")) {
                return trimmed;
            }
        }
        return "未命名SOP";
    }

    /**
     * 提取 SOP 编号
     */
    private String extractSopNo(String content) {
        Matcher m = SOP_NO_PATTERN.matcher(content);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    /**
     * 提取版本号
     */
    private String extractVersion(String content) {
        Matcher m = VERSION_PATTERN.matcher(content);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    /**
     * 提取设备/工具信息
     */
    private String extractEquipment(String content) {
        Matcher m = EQUIPMENT_PATTERN.matcher(content);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    /**
     * 按步骤分割文档
     */
    private String[] splitBySteps(String content) {
        List<String> steps = new ArrayList<>();
        Matcher matcher = STEP_PATTERN.matcher(content);
        int lastEnd = 0;

        while (matcher.find()) {
            if (lastEnd < matcher.start()) {
                String between = content.substring(lastEnd, matcher.start()).trim();
                if (!between.isEmpty()) {
                    steps.add(between);
                }
            }
            lastEnd = matcher.start();
        }
        if (lastEnd < content.length()) {
            String remaining = content.substring(lastEnd).trim();
            if (!remaining.isEmpty()) {
                steps.add(remaining);
            }
        }

        // 如果没有检测到步骤结构，按段落（双换行）分割
        if (steps.isEmpty()) {
            String[] paragraphs = content.split("\\n\\n+");
            for (String p : paragraphs) {
                if (!p.trim().isEmpty()) {
                    steps.add(p.trim());
                }
            }
        }

        return steps.toArray(new String[0]);
    }

    /**
     * 提取检查项
     */
    private List<CheckItem> extractCheckItems(String step) {
        List<CheckItem> items = new ArrayList<>();
        Matcher matcher = CHECK_PATTERN.matcher(step);
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            String standard = matcher.group(2) != null ? matcher.group(2).trim() : "合格";
            items.add(new CheckItem(name, standard));
        }
        return items;
    }

    /**
     * 检测是否包含安全警告
     */
    private boolean containsSafetyWarning(String step) {
        return SAFETY_PATTERN.matcher(step).find();
    }

    /**
     * 提取独立的安全注意事项段落（不在步骤中的安全段落）
     */
    private List<String> extractSafetySections(String content) {
        List<String> safetySections = new ArrayList<>();
        String[] paragraphs = content.split("\\n\\n+");
        boolean inSafetySection = false;

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // 检测安全段落标题
            if (SAFETY_PATTERN.matcher(trimmed).find() && trimmed.length() < 50) {
                inSafetySection = true;
                continue;
            }

            if (inSafetySection) {
                // 如果遇到新的非安全段落，结束安全区域
                if (!SAFETY_PATTERN.matcher(trimmed).find()
                        && STEP_PATTERN.matcher(trimmed).find()) {
                    inSafetySection = false;
                } else {
                    safetySections.add(trimmed);
                    inSafetySection = false; // 只取紧跟标题的一段
                }
            }
        }
        return safetySections;
    }

    /**
     * 序列化检查项列表
     */
    private String serializeCheckItems(List<CheckItem> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(items.get(i).name).append(" → ").append(items.get(i).standard);
        }
        return sb.toString();
    }

    /**
     * 检查项数据
     */
    private record CheckItem(String name, String standard) {}
}
