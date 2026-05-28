package com.wudao.kms.industrial.parser;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SOP 文档解析器
 * 处理标准作业程序（Standard Operating Procedure）文档
 * 
 * 特化能力：
 * - 步骤序列提取（Step 1, Step 2...）
 * - 检查项与判定标准结构化
 * - 关联设备与工位信息
 * - 安全注意事项标记
 */
@Component
public class SopDocumentParser implements IndustrialDocumentParser {

    // 步骤模式
    private static final Pattern STEP_PATTERN = Pattern.compile(
        "^(?:Step\\s*\\d+|步骤\\s*\\d+|第[一二三四五六七八九十]+步|\\d+[.、])\\s*[：:.]?\\s*(.+)",
        Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );

    // 检查项模式
    private static final Pattern CHECK_PATTERN = Pattern.compile(
        "(?:检查|确认|验证|Check|Verify|Inspect)\\s*[:：]?\\s*(.+?)(?:\\s*[，,]\\s*(?:标准|判定|要求)\\s*[:：]?\\s*(.+))?$",
        Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );

    // 设备关联模式
    private static final Pattern EQUIPMENT_PATTERN = Pattern.compile(
        "(?:使用设备|所需工具|Equipment|Tools?)\\s*[:：]?\\s*(.+)",
        Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );

    @Override
    public boolean supports(String fileType) {
        return "sop".equalsIgnoreCase(fileType) ||
               "标准作业程序".equals(fileType) ||
               "作业指导书".equals(fileType);
    }

    @Override
    public ParseResult parse(MultipartFile file, String docType) throws Exception {
        String fileName = file.getOriginalFilename();
        String content = extractText(file);
        
        List<DocumentChunk> chunks = new ArrayList<>();
        Map<String, Object> metadata = new HashMap<>();
        
        // 提取 SOP 元信息
        String sopTitle = extractSopTitle(content);
        String equipment = extractEquipment(content);
        
        // 按步骤分块
        String[] steps = splitBySteps(content);
        int stepNum = 1;
        
        for (String step : steps) {
            Map<String, String> chunkMeta = new HashMap<>();
            chunkMeta.put("sopTitle", sopTitle);
            chunkMeta.put("stepNumber", String.valueOf(stepNum));
            if (equipment != null) {
                chunkMeta.put("equipment", equipment);
            }
            
            // 检测检查项
            List<CheckItem> checkItems = extractCheckItems(step);
            if (!checkItems.isEmpty()) {
                chunkMeta.put("checkItems", checkItems.toString());
            }
            
            // 检测安全提示
            boolean hasSafetyWarning = containsSafetyWarning(step);
            if (hasSafetyWarning) {
                chunkMeta.put("safetyWarning", "true");
            }
            
            ChunkType chunkType = hasSafetyWarning ? ChunkType.SAFETY_NOTICE : ChunkType.SOP_STEP;
            
            String contentText = String.format("【步骤 %d】%s", stepNum, step.trim());
            if (!checkItems.isEmpty()) {
                contentText += "\n检查项: " + checkItems.stream()
                    .map(c -> c.name() + " → " + c.standard())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");
            }
            
            chunks.add(new DocumentChunk(
                contentText,
                stepNum,
                "步骤 " + stepNum,
                chunkType,
                chunkMeta
            ));
            
            stepNum++;
        }
        
        metadata.put("totalSteps", chunks.size());
        metadata.put("sopTitle", sopTitle);
        metadata.put("equipment", equipment);
        
        return new ParseResult(fileName, docType, chunks, metadata);
    }

    private String extractText(MultipartFile file) throws Exception {
        // TODO: 集成 Apache POI (Word) / PDFBox (PDF) 解析
        return new String(file.getBytes(), "UTF-8");
    }

    private String extractSopTitle(String content) {
        String[] lines = content.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && trimmed.length() < 200) {
                return trimmed;
            }
        }
        return "未命名SOP";
    }

    private String extractEquipment(String content) {
        Matcher matcher = EQUIPMENT_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

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
        
        // 如果没有检测到步骤结构，按段落分割
        if (steps.isEmpty()) {
            steps = Arrays.asList(content.split("\\n\\n+"));
        }
        
        return steps.toArray(new String[0]);
    }

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

    private boolean containsSafetyWarning(String step) {
        String[] keywords = {"⚠", "警告", "注意", "危险", "WARNING", "CAUTION", "DANGER", "安全"};
        String lower = step.toLowerCase();
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    record CheckItem(String name, String standard) {}
}
