package com.wudao.kms.industrial.parser;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工艺参数解析器
 * 处理 Excel 格式的工艺参数表
 * 
 * 特化能力：
 * - 参数表结构识别（参数名/目标值/上下限/单位）
 * - 参数单位自动识别（温度°C、压力MPa、速度m/min）
 * - 参数范围与约束条件提取
 * - 工位/设备关联信息提取
 */
@Component
public class ProcessParamParser implements IndustrialDocumentParser {

    // 参数行模式
    private static final Pattern PARAM_ROW_PATTERN = Pattern.compile(
        "([\\u4e00-\\u9fa5a-zA-Z]+(?:名称|参数|项目)?)\\s*[|,，]\\s*(\\d+\\.?\\d*)\\s*[|,，]\\s*(\\d+\\.?\\d*)\\s*[|,，]\\s*(\\d+\\.?\\d*)\\s*(\\S+)?"
    );

    // 单位识别
    private static final Map<String, String> UNIT_MAP = Map.ofEntries(
        Map.entry("°C", "温度"),
        Map.entry("℃", "温度"),
        Map.entry("MPa", "压力"),
        Map.entry("kPa", "压力"),
        Map.entry("m/min", "速度"),
        Map.entry("mm/s", "速度"),
        Map.entry("rpm", "转速"),
        Map.entry("r/min", "转速"),
        Map.entry("kW", "功率"),
        Map.entry("W", "功率"),
        Map.entry("V", "电压"),
        Map.entry("A", "电流"),
        Map.entry("Hz", "频率"),
        Map.entry("%", "百分比"),
        Map.entry("mm", "长度"),
        Map.entry("μm", "厚度")
    );

    @Override
    public boolean supports(String fileType) {
        return "process_param".equalsIgnoreCase(fileType) ||
               "工艺参数".equals(fileType) ||
               "工艺参数表".equals(fileType);
    }

    @Override
    public ParseResult parse(MultipartFile file, String docType) throws Exception {
        String fileName = file.getOriginalFilename();
        String content = extractExcelContent(file);
        
        List<DocumentChunk> chunks = new ArrayList<>();
        Map<String, Object> metadata = new HashMap<>();
        
        // 按工位/设备分组
        Map<String, List<String>> stationGroups = groupByStation(content);
        
        for (Map.Entry<String, List<String>> entry : stationGroups.entrySet()) {
            String station = entry.getKey();
            List<String> params = entry.getValue();
            
            for (String paramLine : params) {
                Map<String, String> chunkMeta = new HashMap<>();
                chunkMeta.put("station", station);
                
                // 解析参数
                ProcessParameter param = parseParameterLine(paramLine);
                if (param != null) {
                    chunkMeta.put("paramName", param.name());
                    chunkMeta.put("targetValue", param.targetValue());
                    chunkMeta.put("lowerLimit", param.lowerLimit());
                    chunkMeta.put("upperLimit", param.upperLimit());
                    chunkMeta.put("unit", param.unit());
                    
                    String contentText = String.format(
                        "工位: %s | 参数: %s | 目标值: %s%s | 范围: %s ~ %s%s",
                        station, param.name(), param.targetValue(), param.unit(),
                        param.lowerLimit(), param.upperLimit(), param.unit()
                    );
                    
                    chunks.add(new DocumentChunk(
                        contentText,
                        1,
                        station + " - " + param.name(),
                        ChunkType.PARAMETER,
                        chunkMeta
                    ));
                }
            }
        }
        
        metadata.put("totalParameters", chunks.size());
        metadata.put("stationCount", stationGroups.size());
        
        return new ParseResult(fileName, docType, chunks, metadata);
    }

    private String extractExcelContent(MultipartFile file) throws Exception {
        // TODO: 集成 Apache POI 进行 Excel 解析
        // 实现要点：
        // 1. 使用 XSSFWorkbook 读取 .xlsx
        // 2. 遍历 Sheet 和 Row
        // 3. 识别表头行（参数名/目标值/上限/下限/单位）
        // 4. 提取数据行
        return new String(file.getBytes(), "UTF-8");
    }

    private Map<String, List<String>> groupByStation(String content) {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        String currentStation = "默认工位";
        
        for (String line : content.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            
            // 检测工位标题行
            if (trimmed.matches("^(工位|设备|产线|Line).*")) {
                currentStation = trimmed;
                groups.putIfAbsent(currentStation, new ArrayList<>());
            } else {
                groups.computeIfAbsent(currentStation, k -> new ArrayList<>()).add(trimmed);
            }
        }
        
        return groups;
    }

    private ProcessParameter parseParameterLine(String line) {
        Matcher matcher = PARAM_ROW_PATTERN.matcher(line);
        if (matcher.find()) {
            String name = matcher.group(1);
            String target = matcher.group(2);
            String lower = matcher.group(3);
            String upper = matcher.group(4);
            String unit = matcher.group(5);
            
            if (unit == null || unit.isEmpty()) {
                unit = inferUnit(name);
            }
            
            return new ProcessParameter(name, target, lower, upper, unit);
        }
        return null;
    }

    private String inferUnit(String paramName) {
        for (Map.Entry<String, String> entry : UNIT_MAP.entrySet()) {
            if (paramName.contains(entry.getValue())) {
                return entry.getKey();
            }
        }
        return "";
    }

    record ProcessParameter(
        String name,
        String targetValue,
        String lowerLimit,
        String upperLimit,
        String unit
    ) {}
}
