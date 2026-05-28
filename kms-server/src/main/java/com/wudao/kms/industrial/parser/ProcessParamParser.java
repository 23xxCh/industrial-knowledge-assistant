package com.wudao.kms.industrial.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工艺参数解析器
 * 处理 Excel 格式的工艺参数表
 *
 * 特化能力：
 * - 表头行自动识别（参数名/目标值/上限/下限/单位）
 * - 数据行逐行提取
 * - 工位/设备分组
 * - 参数单位自动推断
 */
@Slf4j
@Component
public class ProcessParamParser implements IndustrialDocumentParser {

    /** 表头关键词模式 */
    private static final Pattern HEADER_PARAM_NAME = Pattern.compile(
            "(?:参数[名称]*|名称|项目|参数名|Parameter|Param|Item|Name)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_TARGET = Pattern.compile(
            "(?:目标值|标准值|设定值|期望值|Target|Standard|Set|Nominal)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_UPPER = Pattern.compile(
            "(?:上限|上偏差|最大值|上限值|Upper|Max|USL)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_LOWER = Pattern.compile(
            "(?:下限|下偏差|最小值|下限值|Lower|Min|LSL)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_UNIT = Pattern.compile(
            "(?:单位|量纲|Unit)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_STATION = Pattern.compile(
            "(?:工位|设备|产线|机台|Station|Equipment|Line|Machine)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_REMARK = Pattern.compile(
            "(?:备注|说明|描述|Remark|Note|Description)", Pattern.CASE_INSENSITIVE);

    /** 工位/设备行检测 */
    private static final Pattern STATION_ROW_PATTERN = Pattern.compile(
            "^\\s*(?:工位|设备|产线|机台|Line|Station|Equipment|Machine)\\s*[:：]?\\s*(.+)",
            Pattern.CASE_INSENSITIVE
    );

    /** 单位 → 参数类别映射 */
    private static final Map<String, String> UNIT_MAP = Map.ofEntries(
            Map.entry("°C", "温度"), Map.entry("℃", "温度"),
            Map.entry("MPa", "压力"), Map.entry("kPa", "压力"), Map.entry("Pa", "压力"), Map.entry("bar", "压力"),
            Map.entry("m/min", "速度"), Map.entry("mm/s", "速度"), Map.entry("m/s", "速度"),
            Map.entry("rpm", "转速"), Map.entry("r/min", "转速"),
            Map.entry("kW", "功率"), Map.entry("W", "功率"),
            Map.entry("V", "电压"), Map.entry("kV", "电压"),
            Map.entry("A", "电流"), Map.entry("mA", "电流"),
            Map.entry("Hz", "频率"),
            Map.entry("%", "百分比"),
            Map.entry("mm", "长度"), Map.entry("cm", "长度"), Map.entry("m", "长度"), Map.entry("μm", "长度"),
            Map.entry("kg", "重量"), Map.entry("g", "重量"), Map.entry("t", "重量"),
            Map.entry("s", "时间"), Map.entry("min", "时间"), Map.entry("h", "时间"),
            Map.entry("L/min", "流量"), Map.entry("m³/h", "流量"),
            Map.entry("N·m", "扭矩"), Map.entry("Nm", "扭矩")
    );

    /** 参数名称 → 推断单位 */
    private static final Map<String, String> PARAM_NAME_UNIT_HINTS = Map.ofEntries(
            Map.entry("温度", "°C"), Map.entry("加热温度", "°C"), Map.entry("冷却温度", "°C"),
            Map.entry("压力", "MPa"), Map.entry("气压", "MPa"), Map.entry("液压", "MPa"),
            Map.entry("速度", "m/min"), Map.entry("线速度", "m/min"), Map.entry("进给速度", "mm/s"),
            Map.entry("转速", "rpm"), Map.entry("主轴转速", "rpm"),
            Map.entry("功率", "kW"), Map.entry("电压", "V"), Map.entry("电流", "A"),
            Map.entry("频率", "Hz"), Map.entry("温度上限", "°C"), Map.entry("温度下限", "°C")
    );

    @Override
    public boolean supports(String fileType) {
        return "process_param".equalsIgnoreCase(fileType)
                || "工艺参数".equals(fileType)
                || "工艺参数表".equals(fileType);
    }

    @Override
    public ParseResult parse(MultipartFile file, String docType) throws Exception {
        String fileName = file.getOriginalFilename();
        log.info("开始解析工艺参数表: {}, docType: {}", fileName, docType);

        List<DocumentChunk> chunks = new ArrayList<>();
        Map<String, Object> metadata = new HashMap<>();
        Map<String, List<DocumentChunk>> stationGroups = new LinkedHashMap<>();

        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {

            for (int sheetIdx = 0; sheetIdx < workbook.getNumberOfSheets(); sheetIdx++) {
                Sheet sheet = workbook.getSheetAt(sheetIdx);
                String sheetName = sheet.getSheetName();
                log.debug("处理 Sheet: {}", sheetName);

                // 解析表头
                HeaderMapping headerMapping = null;
                int headerRowIndex = -1;
                String currentStation = "默认工位";

                for (int rowIdx = sheet.getFirstRowNum(); rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                    Row row = sheet.getRow(rowIdx);
                    if (row == null) {
                        continue;
                    }

                    // 尝试识别表头行
                    if (headerMapping == null) {
                        HeaderMapping candidate = detectHeader(row);
                        if (candidate != null) {
                            headerMapping = candidate;
                            headerRowIndex = rowIdx;
                            log.debug("检测到表头行: row={}, mapping={}", rowIdx, candidate);
                            continue;
                        }
                        // 检测工位标题行
                        String stationFromRow = detectStationFromRow(row);
                        if (stationFromRow != null) {
                            currentStation = stationFromRow;
                        }
                        continue;
                    }

                    // 跳过表头和空行
                    if (rowIdx <= headerRowIndex || isRowEmpty(row)) {
                        continue;
                    }

                    // 检测工位分组行（合并单元格或特殊标记）
                    String stationFromRow = detectStationFromRow(row);
                    if (stationFromRow != null) {
                        currentStation = stationFromRow;
                        continue;
                    }

                    // 提取数据行
                    ProcessParameter param = extractParameterFromRow(row, headerMapping);
                    if (param != null) {
                        // 补充工位信息
                        String station = param.station != null && !param.station.isBlank()
                                ? param.station : currentStation;

                        Map<String, String> chunkMeta = new HashMap<>();
                        chunkMeta.put("station", station);
                        chunkMeta.put("paramName", param.name);
                        chunkMeta.put("targetValue", param.targetValue);
                        chunkMeta.put("lowerLimit", param.lowerLimit);
                        chunkMeta.put("upperLimit", param.upperLimit);
                        chunkMeta.put("unit", param.unit);
                        chunkMeta.put("sheetName", sheetName);

                        String contentText = String.format(
                                "工位: %s | 参数: %s | 目标值: %s%s | 范围: %s ~ %s%s",
                                station, param.name, param.targetValue, param.unit,
                                param.lowerLimit, param.upperLimit, param.unit);

                        DocumentChunk chunk = new DocumentChunk(
                                contentText,
                                rowIdx,
                                station + " - " + param.name,
                                ChunkType.PARAMETER,
                                chunkMeta);

                        chunks.add(chunk);
                        stationGroups.computeIfAbsent(station, k -> new ArrayList<>()).add(chunk);
                    }
                }
            }
        }

        metadata.put("totalParameters", chunks.size());
        metadata.put("stationCount", stationGroups.size());
        metadata.put("stations", String.join(", ", stationGroups.keySet()));

        log.info("工艺参数表解析完成: {} - {}个参数, {}个工位",
                fileName, chunks.size(), stationGroups.size());

        return new ParseResult(fileName, docType, chunks, metadata);
    }

    /**
     * 检测表头行，返回列映射
     */
    private HeaderMapping detectHeader(Row row) {
        HeaderMapping mapping = new HeaderMapping();
        int matchCount = 0;

        for (int colIdx = row.getFirstCellNum(); colIdx <= row.getLastCellNum(); colIdx++) {
            Cell cell = row.getCell(colIdx);
            if (cell == null) {
                continue;
            }
            String value = getCellStringValue(cell).trim();
            if (value.isEmpty()) {
                continue;
            }

            if (HEADER_PARAM_NAME.matcher(value).find() && mapping.paramNameCol < 0) {
                mapping.paramNameCol = colIdx;
                matchCount++;
            } else if (HEADER_TARGET.matcher(value).find() && mapping.targetCol < 0) {
                mapping.targetCol = colIdx;
                matchCount++;
            } else if (HEADER_UPPER.matcher(value).find() && mapping.upperCol < 0) {
                mapping.upperCol = colIdx;
                matchCount++;
            } else if (HEADER_LOWER.matcher(value).find() && mapping.lowerCol < 0) {
                mapping.lowerCol = colIdx;
                matchCount++;
            } else if (HEADER_UNIT.matcher(value).find() && mapping.unitCol < 0) {
                mapping.unitCol = colIdx;
                matchCount++;
            } else if (HEADER_STATION.matcher(value).find() && mapping.stationCol < 0) {
                mapping.stationCol = colIdx;
                matchCount++;
            } else if (HEADER_REMARK.matcher(value).find() && mapping.remarkCol < 0) {
                mapping.remarkCol = colIdx;
            }
        }

        // 至少匹配参数名和一个数值列才认为是有效表头
        if (mapping.paramNameCol >= 0 && (mapping.targetCol >= 0 || mapping.upperCol >= 0 || mapping.lowerCol >= 0)) {
            return mapping;
        }
        return null;
    }

    /**
     * 从行中检测工位标记
     */
    private String detectStationFromRow(Row row) {
        Cell firstCell = row.getCell(row.getFirstCellNum());
        if (firstCell == null) {
            return null;
        }
        String value = getCellStringValue(firstCell).trim();
        Matcher m = STATION_ROW_PATTERN.matcher(value);
        if (m.find()) {
            return m.group(1).trim();
        }
        // 检查合并单元格是否是工位名（通常第一列合并多行）
        if (value.length() > 0 && value.length() < 50
                && (value.contains("工位") || value.contains("设备") || value.contains("产线")
                || value.matches("^(?:Line|Station|Machine).*"))) {
            return value;
        }
        return null;
    }

    /**
     * 从数据行提取参数
     */
    private ProcessParameter extractParameterFromRow(Row row, HeaderMapping mapping) {
        String paramName = getCellString(row, mapping.paramNameCol);
        if (paramName == null || paramName.isBlank()) {
            return null;
        }

        String target = getCellString(row, mapping.targetCol);
        String upper = getCellString(row, mapping.upperCol);
        String lower = getCellString(row, mapping.lowerCol);
        String unit = getCellString(row, mapping.unitCol);
        String station = getCellString(row, mapping.stationCol);
        String remark = getCellString(row, mapping.remarkCol);

        // 单位自动推断
        if (unit == null || unit.isBlank()) {
            unit = inferUnit(paramName);
        }

        // 如果上下限为空，尝试从目标值中提取
        if ((target == null || target.isBlank()) && upper == null && lower == null) {
            return null; // 完全没有数值，跳过
        }

        return new ProcessParameter(paramName, target, lower, upper, unit, station, remark);
    }

    /**
     * 根据参数名称推断单位
     */
    private String inferUnit(String paramName) {
        // 先查精确匹配
        for (Map.Entry<String, String> entry : PARAM_NAME_UNIT_HINTS.entrySet()) {
            if (paramName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        // 再查单位类别关键词
        for (Map.Entry<String, String> entry : UNIT_MAP.entrySet()) {
            if (paramName.contains(entry.getValue())) {
                return entry.getKey();
            }
        }
        return "";
    }

    /**
     * 安全获取单元格字符串值
     */
    private String getCellString(Row row, int colIdx) {
        if (colIdx < 0) {
            return null;
        }
        Cell cell = row.getCell(colIdx);
        return cell == null ? null : getCellStringValue(cell).trim();
    }

    /**
     * 获取单元格的字符串值（处理各种类型）
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                double numVal = cell.getNumericCellValue();
                // 整数不带小数点
                if (numVal == Math.floor(numVal) && !Double.isInfinite(numVal)) {
                    return String.valueOf((long) numVal);
                }
                return String.valueOf(numVal);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        return cell.getCellFormula();
                    }
                }
            case BLANK:
                return "";
            default:
                return cell.toString();
        }
    }

    /**
     * 判断行是否为空
     */
    private boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }
        for (int colIdx = row.getFirstCellNum(); colIdx <= row.getLastCellNum(); colIdx++) {
            Cell cell = row.getCell(colIdx);
            if (cell != null && !getCellStringValue(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 表头列映射
     */
    private static class HeaderMapping {
        int paramNameCol = -1;
        int targetCol = -1;
        int upperCol = -1;
        int lowerCol = -1;
        int unitCol = -1;
        int stationCol = -1;
        int remarkCol = -1;
    }

    /**
     * 工艺参数数据
     */
    private record ProcessParameter(
            String name,
            String targetValue,
            String lowerLimit,
            String upperLimit,
            String unit,
            String station,
            String remark
    ) {}
}
