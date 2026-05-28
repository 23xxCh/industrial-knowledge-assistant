package com.wudao.kms.industrial.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * MES 系统集成服务
 * <p>
 * 当前为 Mock 实现，返回模拟的工单和生产数据。
 * 生产环境可对接真实 MES 系统 API。
 * </p>
 *
 * <h3>扩展点</h3>
 * <ul>
 *   <li>对接 MES REST API（如西门子 OpCenter、达索 DELMIA 等）</li>
 *   <li>对接数据库 mes_workorders 表</li>
 *   <li>支持消息队列实时推送工单状态变更</li>
 * </ul>
 */
@Service
public class MesIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(MesIntegrationService.class);

    // ========== 数据结构 ==========

    /**
     * 工单记录
     */
    public record Workorder(
            String workorderId,
            String product,
            String productSpec,
            int quantity,
            int completed,
            String status,          // pending, in_progress, completed, cancelled
            String line,
            String priority,        // high, medium, low
            LocalDateTime planStart,
            LocalDateTime planEnd,
            LocalDateTime actualStart,
            String assignedTeam,
            double defectRate
    ) {}

    /**
     * 工单状态枚举
     */
    public enum WorkorderStatus {
        PENDING, IN_PROGRESS, COMPLETED, CANCELLED
    }

    // ========== 业务方法 ==========

    /**
     * 查询工单
     *
     * @param workorderId 工单编号（可选，null 则查询全部）
     * @param status      状态筛选（可选，null 不筛选）
     * @return 工单列表
     */
    public List<Workorder> queryWorkorder(String workorderId, String status) {
        log.info("查询 MES 工单: workorderId={}, status={}", workorderId, status);

        List<Workorder> allOrders = buildMockWorkorders();

        // 按工单号筛选
        if (workorderId != null && !workorderId.isBlank()) {
            return allOrders.stream()
                    .filter(w -> w.workorderId().equalsIgnoreCase(workorderId))
                    .toList();
        }

        // 按状态筛选
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            return allOrders.stream()
                    .filter(w -> w.status().equalsIgnoreCase(status))
                    .toList();
        }

        return allOrders;
    }

    /**
     * 获取产线列表（Mock）
     */
    public List<String> listProductionLines() {
        return List.of("产线1-注塑", "产线2-注塑", "产线3-焊接", "产线4-装配", "产线5-CNC");
    }

    /**
     * 获取产线当前状态概览
     */
    public Map<String, Object> getProductionOverview() {
        List<Workorder> orders = buildMockWorkorders();
        long inProgress = orders.stream().filter(w -> "in_progress".equals(w.status())).count();
        long pending = orders.stream().filter(w -> "pending".equals(w.status())).count();
        long completed = orders.stream().filter(w -> "completed".equals(w.status())).count();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("total_orders", orders.size());
        overview.put("in_progress", inProgress);
        overview.put("pending", pending);
        overview.put("completed", completed);
        overview.put("lines", listProductionLines());
        overview.put("update_time", LocalDateTime.now().toString());
        return overview;
    }

    // ========== Mock 数据构造 ==========

    private List<Workorder> buildMockWorkorders() {
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                new Workorder(
                        "WO-2026-0501",
                        "连接器外壳 A12",
                        "PA66-GF30, 黑色, 85×45×32mm",
                        5000, 5000, "completed",
                        "产线1-注塑", "high",
                        now.minusDays(5), now.minusDays(2), now.minusDays(5),
                        "甲班", 0.8
                ),
                new Workorder(
                        "WO-2026-0502",
                        "散热器底板 B07",
                        "ADC12, 银色阳极氧化, 120×80×10mm",
                        3000, 2150, "in_progress",
                        "产线5-CNC", "high",
                        now.minusDays(3), now.plusDays(2), now.minusDays(3),
                        "乙班", 1.2
                ),
                new Workorder(
                        "WO-2026-0503",
                        "电机端盖 C03",
                        "HT250, 喷涂黑, Φ95×35mm",
                        2000, 1200, "in_progress",
                        "产线5-CNC", "medium",
                        now.minusDays(2), now.plusDays(3), now.minusDays(2),
                        "甲班", 0.5
                ),
                new Workorder(
                        "WO-2026-0504",
                        "线束总成 D15",
                        "RVV 3×1.5mm², L=1200mm",
                        8000, 3600, "in_progress",
                        "产线4-装配", "medium",
                        now.minusDays(1), now.plusDays(5), now.minusDays(1),
                        "丙班", 0.3
                ),
                new Workorder(
                        "WO-2026-0505",
                        "密封圈 E09",
                        "FKM, Φ50×3.5mm, 耐油",
                        10000, 0, "pending",
                        "产线2-注塑", "low",
                        now.plusDays(1), now.plusDays(8), null,
                        "甲班", 0.0
                ),
                new Workorder(
                        "WO-2026-0506",
                        "焊接支架 F22",
                        "Q235B, 镀锌, 200×60×40mm",
                        1500, 0, "pending",
                        "产线3-焊接", "medium",
                        now.plusDays(2), now.plusDays(7), null,
                        "乙班", 0.0
                ),
                new Workorder(
                        "WO-2026-0507",
                        "控制面板壳体 G04",
                        "ABS, 白色, 300×200×50mm",
                        4000, 4000, "completed",
                        "产线1-注塑", "low",
                        now.minusDays(10), now.minusDays(4), now.minusDays(10),
                        "丙班", 1.5
                )
        );
    }
}
