package com.wudao.kms.industrial.agent;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工业 Agent 工具服务
 * 让 LLM 能够调用外部工业系统的能力。
 * <p>
 * 作为统一入口，委托给四个专业 Service 执行实际查询：
 * <ul>
 *   <li>{@link EquipmentParamService} —— 设备参数查询</li>
 *   <li>{@link ScadaIntegrationService} —— SCADA 传感器实时数据</li>
 *   <li>{@link MesIntegrationService} —— MES 工单查询</li>
 *   <li>{@link FaultCodeService} —— 故障代码查询</li>
 * </ul>
 */
@Service
public class IndustrialAgentToolService {

    private static final Logger log = LoggerFactory.getLogger(IndustrialAgentToolService.class);

    @Resource
    private EquipmentParamService equipmentParamService;

    @Resource
    private ScadaIntegrationService scadaIntegrationService;

    @Resource
    private MesIntegrationService mesIntegrationService;

    @Resource
    private FaultCodeService faultCodeService;

    // ========== 数据结构 ==========

    /**
     * 工具定义（供 LLM Function Calling 使用）
     */
    public record ToolDefinition(
            String name,
            String description,
            Map<String, Object> parameters
    ) {}

    /**
     * 工具调用结果
     */
    public record ToolResult(
            String toolName,
            boolean success,
            String result,
            Map<String, Object> metadata
    ) {}

    // ========== 工具定义 ==========

    /**
     * 获取所有可用工具定义（供 LLM Function Calling 使用）
     */
    public List<ToolDefinition> getAvailableTools() {
        return List.of(
                new ToolDefinition(
                        "query_equipment_params",
                        "查询指定设备的工艺参数值，包括温度、压力、速度、转速等。可查询单个参数或全部参数。",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "equipment_id", Map.of("type", "string", "description", "设备编号，如 CNC-001, INJ-001, WLD-001"),
                                        "param_name", Map.of("type", "string", "description", "参数名称（可选），如 主轴转速、切削温度。不填则返回全部参数")
                                ),
                                "required", List.of("equipment_id")
                        )
                ),

                new ToolDefinition(
                        "get_scada_realtime",
                        "获取 SCADA 系统的传感器实时数据，包括温度、压力、振动、电流等指标。",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "sensor_id", Map.of("type", "string", "description", "传感器编号，如 TEMP-101, PRES-201, VIBR-301"),
                                        "metric", Map.of("type", "string", "description", "指标类型（可选）：temperature/pressure/vibration/current。不填则返回全部指标")
                                ),
                                "required", List.of("sensor_id")
                        )
                ),

                new ToolDefinition(
                        "query_mes_workorder",
                        "查询 MES 系统中的生产工单状态和进度，包括产品、数量、完成率、产线等信息。",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "workorder_id", Map.of("type", "string", "description", "工单编号（可选），如 WO-2026-0501"),
                                        "status", Map.of("type", "string", "description", "工单状态筛选（可选）：pending/in_progress/completed/cancelled")
                                ),
                                "required", List.of()
                        )
                ),

                new ToolDefinition(
                        "lookup_fault_code",
                        "查询故障代码的含义、可能原因和处理方案。支持按设备类型筛选。",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "fault_code", Map.of("type", "string", "description", "故障代码，如 E01, F01, V01, P01, C01, S01"),
                                        "equipment_type", Map.of("type", "string", "description", "设备类型（可选）：CNC数控机床/注塑机/焊接设备/通用")
                                ),
                                "required", List.of("fault_code")
                        )
                )
        );
    }

    // ========== 工具执行 ==========

    /**
     * 执行工具调用（统一入口）
     */
    public ToolResult executeTool(String toolName, Map<String, Object> params) {
        log.info("执行工具调用: toolName={}, params={}", toolName, params);
        try {
            return switch (toolName) {
                case "query_equipment_params" -> executeQueryEquipmentParams(params);
                case "get_scada_realtime"     -> executeGetScadaRealtime(params);
                case "query_mes_workorder"    -> executeQueryMesWorkorder(params);
                case "lookup_fault_code"      -> executeLookupFaultCode(params);
                default -> new ToolResult(toolName, false, "未知工具: " + toolName, Map.of());
            };
        } catch (Exception e) {
            log.error("工具执行异常: toolName={}", toolName, e);
            return new ToolResult(toolName, false, "工具执行失败: " + e.getMessage(), Map.of("error", e.getClass().getSimpleName()));
        }
    }

    // ========== 具体工具实现（委托给专业 Service） ==========

    /**
     * 查询设备参数 —— 委托给 EquipmentParamService
     */
    private ToolResult executeQueryEquipmentParams(Map<String, Object> params) {
        String equipmentId = (String) params.get("equipment_id");
        String paramName = (String) params.getOrDefault("param_name", "all");

        var records = equipmentParamService.queryEquipmentParams(equipmentId, paramName);

        if (records.isEmpty()) {
            return new ToolResult("query_equipment_params", true,
                    "未找到设备 " + equipmentId + " 的参数记录。可用设备: " + equipmentParamService.listEquipmentIds(),
                    Map.of("source", "equipment_param_service", "count", 0));
        }

        // 格式化输出
        StringBuilder sb = new StringBuilder();
        sb.append("设备 ").append(equipmentId).append(" 参数如下：\n");
        for (var r : records) {
            sb.append(String.format("  - %s: %s %s（正常范围: %s~%s）[%s]\n",
                    r.paramName(), r.value(), r.unit(), r.range().split("-")[0],
                    r.range().contains("-") ? r.range().split("-")[1] : r.range(),
                    r.status()));
        }

        return new ToolResult("query_equipment_params", true, sb.toString(),
                Map.of("source", "equipment_param_service", "count", records.size()));
    }

    /**
     * 获取 SCADA 实时数据 —— 委托给 ScadaIntegrationService
     */
    private ToolResult executeGetScadaRealtime(Map<String, Object> params) {
        String sensorId = (String) params.get("sensor_id");
        String metric = (String) params.getOrDefault("metric", "all");

        var data = scadaIntegrationService.getRealtimeData(sensorId, metric);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("传感器 %s 实时数据（%s）:\n", data.sensorId(), data.timestamp()));
        data.values().forEach((k, v) -> {
            String unit = switch (k) {
                case "temperature" -> "°C";
                case "pressure"    -> "MPa";
                case "vibration"   -> "mm/s";
                case "current"     -> "A";
                default -> "";
            };
            sb.append(String.format("  - %s: %s %s\n", k, v, unit));
        });
        sb.append("  - 状态: ").append(data.status());
        if (!"NORMAL".equals(data.status())) {
            sb.append(" ⚠️");
        }

        return new ToolResult("get_scada_realtime", true, sb.toString(),
                Map.of("source", "scada_service", "status", data.status(), "data_source", data.dataSource()));
    }

    /**
     * 查询 MES 工单 —— 委托给 MesIntegrationService
     */
    private ToolResult executeQueryMesWorkorder(Map<String, Object> params) {
        String workorderId = (String) params.get("workorder_id");
        String status = (String) params.get("status");

        var orders = mesIntegrationService.queryWorkorder(workorderId, status);

        if (orders.isEmpty()) {
            return new ToolResult("query_mes_workorder", true,
                    "未找到匹配的工单记录。",
                    Map.of("source", "mes_service", "count", 0));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("查询到 %d 条工单：\n", orders.size()));
        for (var w : orders) {
            double progress = w.quantity() > 0 ? (double) w.completed() / w.quantity() * 100 : 0;
            sb.append(String.format("  [%s] %s\n", w.status().toUpperCase(), w.workorderId()));
            sb.append(String.format("    产品: %s | 规格: %s\n", w.product(), w.productSpec()));
            sb.append(String.format("    进度: %d/%d（%.1f%%）| 产线: %s\n", w.completed(), w.quantity(), progress, w.line()));
            sb.append(String.format("    优先级: %s | 班组: %s | 不良率: %.1f%%\n", w.priority(), w.assignedTeam(), w.defectRate()));
        }

        return new ToolResult("query_mes_workorder", true, sb.toString(),
                Map.of("source", "mes_service", "count", orders.size()));
    }

    /**
     * 查询故障代码 —— 委托给 FaultCodeService
     */
    private ToolResult executeLookupFaultCode(Map<String, Object> params) {
        String faultCode = (String) params.get("fault_code");
        String equipmentType = (String) params.getOrDefault("equipment_type", "通用");

        var records = faultCodeService.lookupFaultCode(faultCode, equipmentType);

        if (records.isEmpty()) {
            return new ToolResult("lookup_fault_code", true,
                    "未找到故障代码 " + faultCode + " 的记录。",
                    Map.of("source", "fault_code_service", "count", 0));
        }

        StringBuilder sb = new StringBuilder();
        for (var r : records) {
            sb.append(String.format("故障代码 %s: %s\n", r.faultCode(), r.description()));
            sb.append(String.format("  适用设备: %s | 严重程度: %s | 分类: %s\n", r.equipmentType(), r.severity(), r.category()));
            sb.append("  可能原因:\n");
            for (int i = 0; i < r.possibleCauses().size(); i++) {
                sb.append(String.format("    %d. %s\n", i + 1, r.possibleCauses().get(i)));
            }
            sb.append("  处理方案:\n");
            for (int i = 0; i < r.solutions().size(); i++) {
                sb.append(String.format("    %d. %s\n", i + 1, r.solutions().get(i)));
            }
            if (r.manualRef() != null) {
                sb.append("  参考: ").append(r.manualRef()).append("\n");
            }
        }

        return new ToolResult("lookup_fault_code", true, sb.toString(),
                Map.of("source", "fault_code_service", "count", records.size()));
    }
}
