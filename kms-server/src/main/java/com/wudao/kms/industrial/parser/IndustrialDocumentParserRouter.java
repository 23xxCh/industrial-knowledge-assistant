package com.wudao.kms.industrial.parser;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 工业文档解析路由器
 * 根据文件类型和 docType 自动选择合适的解析器
 */
@Slf4j
@Service
public class IndustrialDocumentParserRouter {

    @Resource
    private List<IndustrialDocumentParser> parsers;

    /**
     * 路由解析请求到合适的解析器
     *
     * @param file   上传的文件
     * @param docType 文档类型（equipment_manual / process_param / sop / auto）
     * @return 解析结果
     */
    public IndustrialDocumentParser.ParseResult route(MultipartFile file, String docType) throws Exception {
        String fileName = file.getOriginalFilename();
        log.info("路由文档解析: fileName={}, docType={}", fileName, docType);

        // 1. 优先通过 docType 匹配解析器
        if (docType != null && !"auto".equalsIgnoreCase(docType)) {
            for (IndustrialDocumentParser parser : parsers) {
                if (parser.supports(docType)) {
                    log.info("通过 docType={} 匹配到解析器: {}", docType, parser.getClass().getSimpleName());
                    return parser.parse(file, docType);
                }
            }
        }

        // 2. 通过文件扩展名自动推断
        String inferredDocType = inferDocTypeFromFileName(fileName);
        if (inferredDocType != null) {
            for (IndustrialDocumentParser parser : parsers) {
                if (parser.supports(inferredDocType)) {
                    log.info("通过文件名推断 docType={} 匹配到解析器: {}", inferredDocType, parser.getClass().getSimpleName());
                    return parser.parse(file, inferredDocType);
                }
            }
        }

        // 3. 兜底：按 docType 依次尝试所有解析器
        String fallbackDocType = docType != null ? docType : "equipment_manual";
        log.warn("未找到精确匹配的解析器，使用默认 docType={}", fallbackDocType);
        for (IndustrialDocumentParser parser : parsers) {
            if (parser.supports(fallbackDocType)) {
                return parser.parse(file, fallbackDocType);
            }
        }

        throw new UnsupportedOperationException(
                String.format("无法找到合适的解析器: fileName=%s, docType=%s, 已注册解析器数量=%d",
                        fileName, docType, parsers.size()));
    }

    /**
     * 根据文件名推断文档类型
     */
    private String inferDocTypeFromFileName(String fileName) {
        if (fileName == null) {
            return null;
        }

        String lower = fileName.toLowerCase();

        // Excel 文件 → 工艺参数
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return "process_param";
        }

        // 含 SOP 关键词 → SOP 文档
        if (lower.contains("sop") || lower.contains("作业指导") || lower.contains("标准作业")) {
            return "sop";
        }

        // 含设备/手册/维护 关键词 → 设备手册
        if (lower.contains("手册") || lower.contains("manual") || lower.contains("维护")
                || lower.contains("maintenance") || lower.contains("设备")) {
            return "equipment_manual";
        }

        // PDF 和 Word 文档默认按设备手册处理
        if (lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".doc")) {
            return "equipment_manual";
        }

        return null;
    }
}
