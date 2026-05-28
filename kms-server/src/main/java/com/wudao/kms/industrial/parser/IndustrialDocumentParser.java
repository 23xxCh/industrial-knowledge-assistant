package com.wudao.kms.industrial.parser;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 工业文档解析器接口
 * 针对制造业文档的特殊格式提供专用解析能力
 */
public interface IndustrialDocumentParser {

    /**
     * 判断是否支持该文件类型
     */
    boolean supports(String fileType);

    /**
     * 解析文档，返回结构化内容
     * @param file 上传的文件
     * @param docType 文档类型（equipment_manual / process_param / sop）
     * @return 解析结果，包含分块内容和元数据
     */
    ParseResult parse(MultipartFile file, String docType) throws Exception;

    /**
     * 解析结果
     */
    record ParseResult(
        String fileName,
        String docType,
        List<DocumentChunk> chunks,
        Map<String, Object> metadata
    ) {}

    /**
     * 文档分块
     */
    record DocumentChunk(
        String content,
        int pageNumber,
        String sectionTitle,
        ChunkType chunkType,
        Map<String, String> metadata
    ) {}

    /**
     * 分块类型
     */
    enum ChunkType {
        TEXT,           // 普通文本
        TABLE,          // 表格
        PARAMETER,      // 工艺参数
        FAULT_CODE,     // 故障代码
        SOP_STEP,       // SOP 步骤
        SAFETY_NOTICE   // 安全提示
    }
}
