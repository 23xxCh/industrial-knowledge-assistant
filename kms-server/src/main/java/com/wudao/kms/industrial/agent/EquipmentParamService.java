package com.wudao.kms.industrial.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 设备参数查询服务
 * <p>
 * 当前为 Mock 实现，返回模拟的设备工艺参数数据。
 * 生产环境可替换为数据库查询或对接设备管理系统 API。
 * </p>
 *
 * <h3>扩展点</h3>
 * <ul>
 *   <li>接入真实设备管理平台 REST API</li>
 *   <li>接入 OPC-UA 服务器读取 PLC 参数</li>
 *   <li>接入数据库（equipment_params 表）查询</li>
 * </ul>
 */
@Service
public class EquipmentParamService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentParamService.class);

    /**
     * 设备参数记录
     */
    public record ParamRecord(
            String equipmentId,
            String paramName,
            String value,
            String unit,
            String range,
            LocalDateTime updateTime,
            String status
    ) {}

    /**
     * 查询设备参数
     *
     * @param equipmentId 设备编号（必填）
     * @param paramName   参数名称（可选，null 或 "all" 返回全部参数）
     * @return 参数记录列表
     */
    public List<ParamRecord> queryEquipmentParams(String equipmentId, String paramName) {
        log.info("查询设备参数: equipmentId={}, paramName={}", equipmentId, paramName);

        // ---- Mock 实现：模拟数据库查询 ----
        List<ParamRecord> allParams = buildMockParams(equipmentId);

        if (paramName == null || "all".equalsIgnoreCase(paramName)) {
            return allParams;
        }

        return allParams.stream()
                .filter(p -> p.paramName().contains(paramName) || p.paramName().equalsIgnoreCase(paramName))
                .toList();
    }

    /**
     * 获取所有支持的设备编号（Mock）
     */
    public List<String> listEquipmentIds() {
        return List.of("CNC-001", "CNC-002", "INJ-001", "INJ-002", "WLD-001", "ASM-001");
    }

    // ========== Mock 数据构造 ==========

    private List<ParamRecord> buildMockParams(String equipmentId) {
        LocalDateTime now = LocalDateTime.now();
        List<ParamRecord> params = new ArrayList<>();

        return switch (equipmentId.toUpperCase()) {
            case "CNC-001" -> {
                params.add(new ParamRecord(equipmentId, "主轴转速", "3200", "RPM", "2800-3600", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "进给速度", "120", "mm/min", "100-150", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "切削温度", "78.5", "°C", "70-90", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "冷却液压力", "0.52", "MPa", "0.4-0.7", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "刀具磨损量", "0.12", "mm", "0-0.3", now, "NORMAL"));
                yield params;
            }
            case "CNC-002" -> {
                params.add(new ParamRecord(equipmentId, "主轴转速", "2800", "RPM", "2800-3600", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "进给速度", "135", "mm/min", "100-150", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "切削温度", "92.1", "°C", "70-90", now, "WARNING"));
                params.add(new ParamRecord(equipmentId, "冷却液压力", "0.48", "MPa", "0.4-0.7", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "刀具磨损量", "0.25", "mm", "0-0.3", now, "WARNING"));
                yield params;
            }
            case "INJ-001" -> {
                params.add(new ParamRecord(equipmentId, "料筒温度", "230", "°C", "220-250", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "模具温度", "65", "°C", "60-80", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "注射压力", "85", "MPa", "70-100", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "保压时间", "8", "s", "6-12", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "冷却时间", "15", "s", "12-20", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "锁模力", "1200", "kN", "1000-1500", now, "NORMAL"));
                yield params;
            }
            case "INJ-002" -> {
                params.add(new ParamRecord(equipmentId, "料筒温度", "245", "°C", "220-250", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "模具温度", "72", "°C", "60-80", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "注射压力", "95", "MPa", "70-100", now, "WARNING"));
                params.add(new ParamRecord(equipmentId, "保压时间", "10", "s", "6-12", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "冷却时间", "18", "s", "12-20", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "锁模力", "1350", "kN", "1000-1500", now, "NORMAL"));
                yield params;
            }
            case "WLD-001" -> {
                params.add(new ParamRecord(equipmentId, "焊接电流", "185", "A", "160-200", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "焊接电压", "24.5", "V", "22-28", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "焊接速度", "45", "cm/min", "35-55", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "送丝速度", "8.5", "m/min", "7-10", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "气体流量", "18", "L/min", "15-25", now, "NORMAL"));
                yield params;
            }
            case "ASM-001" -> {
                params.add(new ParamRecord(equipmentId, "装配压力", "0.8", "kN", "0.5-1.2", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "拧紧扭矩", "25", "N·m", "20-30", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "节拍时间", "45", "s", "40-55", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "合格率", "98.5", "%", "≥97", now, "NORMAL"));
                yield params;
            }
            default -> {
                log.warn("未知设备编号: {}, 返回通用参数", equipmentId);
                params.add(new ParamRecord(equipmentId, "运行状态", "运行中", "-", "-", now, "NORMAL"));
                params.add(new ParamRecord(equipmentId, "累计运行时间", "1234.5", "h", "-", now, "NORMAL"));
                yield params;
            }
        };
    }
}
