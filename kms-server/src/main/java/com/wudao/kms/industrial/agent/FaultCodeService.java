package com.wudao.kms.industrial.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 故障代码查询服务
 * <p>
 * 提供故障代码的含义、可能原因和处理方案查询。
 * 当前为内存 Mock 实现，生产环境建议接入故障代码数据库表。
 * </p>
 *
 * <h3>扩展点</h3>
 * <ul>
 *   <li>接入 fault_code_library 数据库表</li>
 *   <li>对接设备厂商故障代码 API</li>
 *   <li>对接知识库（KMS）进行语义检索</li>
 * </ul>
 */
@Service
public class FaultCodeService {

    private static final Logger log = LoggerFactory.getLogger(FaultCodeService.class);

    // ========== 数据结构 ==========

    /**
     * 故障代码记录
     */
    public record FaultCodeRecord(
            String faultCode,
            String equipmentType,
            String description,
            List<String> possibleCauses,
            List<String> solutions,
            String severity,        // 低, 中等, 高, 严重
            String category,        // 电气, 机械, 温度, 压力, 通信, 传感器
            String manualRef        // 维修手册参考章节
    ) {}

    /**
     * 故障严重程度枚举
     */
    public enum Severity {
        LOW("低"), MEDIUM("中等"), HIGH("高"), CRITICAL("严重");

        private final String label;
        Severity(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    // ========== 业务方法 ==========

    /**
     * 查询故障代码
     *
     * @param faultCode     故障代码（如 E01, F05）
     * @param equipmentType 设备类型（可选，null 不筛选）
     * @return 匹配的故障代码记录列表
     */
    public List<FaultCodeRecord> lookupFaultCode(String faultCode, String equipmentType) {
        log.info("查询故障代码: faultCode={}, equipmentType={}", faultCode, equipmentType);

        List<FaultCodeRecord> allRecords = buildFaultCodeLibrary();

        return allRecords.stream()
                .filter(r -> r.faultCode().equalsIgnoreCase(faultCode))
                .filter(r -> equipmentType == null || equipmentType.isBlank()
                        || "all".equalsIgnoreCase(equipmentType)
                        || r.equipmentType().contains(equipmentType)
                        || "通用".equals(r.equipmentType()))
                .toList();
    }

    /**
     * 按设备类型查询所有故障代码
     */
    public List<FaultCodeRecord> listByEquipmentType(String equipmentType) {
        return buildFaultCodeLibrary().stream()
                .filter(r -> r.equipmentType().contains(equipmentType) || "通用".equals(r.equipmentType()))
                .toList();
    }

    /**
     * 按严重程度查询故障代码
     */
    public List<FaultCodeRecord> listBySeverity(String severity) {
        return buildFaultCodeLibrary().stream()
                .filter(r -> r.severity().equalsIgnoreCase(severity))
                .toList();
    }

    // ========== 故障代码库（Mock 数据） ==========

    private List<FaultCodeRecord> buildFaultCodeLibrary() {
        return List.of(
                // ---- 温度类故障 ----
                new FaultCodeRecord(
                        "E01", "CNC数控机床,通用",
                        "主轴温度过高",
                        List.of(
                                "冷却液流量不足或冷却系统故障",
                                "主轴轴承磨损导致摩擦发热",
                                "切削参数不合理（进给过快、切深过大）",
                                "环境温度过高或散热通道堵塞"
                        ),
                        List.of(
                                "检查冷却液液位和流量，清洗冷却管路",
                                "检查主轴轴承状态，必要时更换",
                                "适当降低进给速度或切削深度",
                                "清理设备散热通道，检查车间通风"
                        ),
                        "高", "温度", "手册 §4.2 主轴系统维护"
                ),
                new FaultCodeRecord(
                        "E02", "注塑机",
                        "料筒温度异常",
                        List.of(
                                "加热圈损坏或接触不良",
                                "热电偶故障或接线松动",
                                "温控器参数设定错误",
                                "料筒隔热层老化"
                        ),
                        List.of(
                                "逐段检查加热圈，更换损坏件",
                                "校验热电偶，紧固接线端子",
                                "重新设定 PID 参数并自整定",
                                "更换隔热保温层"
                        ),
                        "高", "温度", "手册 §5.1 温控系统"
                ),
                new FaultCodeRecord(
                        "E03", "通用",
                        "环境温度超出设备允许范围",
                        List.of(
                                "车间空调/通风系统故障",
                                "设备安装位置靠近热源",
                                "季节性高温导致"
                        ),
                        List.of(
                                "检查车间通风和空调系统",
                                "评估设备安装位置，必要时调整",
                                "增加临时降温措施（风扇、冰块）"
                        ),
                        "中等", "温度", "手册 §2.1 环境要求"
                ),

                // ---- 压力类故障 ----
                new FaultCodeRecord(
                        "F01", "注塑机",
                        "注射压力不足",
                        List.of(
                                "液压油油位不足或油质劣化",
                                "液压泵磨损",
                                "比例阀故障",
                                "注射油缸密封件泄漏"
                        ),
                        List.of(
                                "补充液压油，必要时更换新油",
                                "检测液压泵输出压力，必要时维修/更换",
                                "检查比例阀信号和阀芯动作",
                                "更换油缸密封件"
                        ),
                        "高", "压力", "手册 §5.3 液压系统"
                ),
                new FaultCodeRecord(
                        "F02", "CNC数控机床",
                        "冷却液压力异常",
                        List.of(
                                "冷却泵故障",
                                "管路堵塞或泄漏",
                                "压力传感器漂移",
                                "过滤器堵塞"
                        ),
                        List.of(
                                "检查冷却泵运行状态",
                                "排查管路堵塞点，修复泄漏",
                                "校准或更换压力传感器",
                                "清洗或更换过滤器"
                        ),
                        "中等", "压力", "手册 §4.5 冷却系统"
                ),

                // ---- 振动类故障 ----
                new FaultCodeRecord(
                        "V01", "CNC数控机床",
                        "设备振动异常",
                        List.of(
                                "主轴动平衡失调",
                                "刀具磨损严重或安装不当",
                                "地脚螺栓松动",
                                "导轨/丝杠磨损"
                        ),
                        List.of(
                                "重新做主轴动平衡校正",
                                "检查刀具磨损量，重新安装或更换",
                                "紧固地脚螺栓，检查基础水平",
                                "检查导轨间隙和丝杠精度"
                        ),
                        "高", "机械", "手册 §4.3 主轴与传动"
                ),
                new FaultCodeRecord(
                        "V02", "注塑机",
                        "合模振动过大",
                        List.of(
                                "合模机构润滑不良",
                                "曲肘连杆机构磨损",
                                "锁模力设定不当"
                        ),
                        List.of(
                                "补充润滑脂，检查润滑系统",
                                "检查曲肘连杆磨损情况，更换磨损件",
                                "重新设定锁模力参数"
                        ),
                        "中等", "机械", "手册 §5.4 合模机构"
                ),

                // ---- 电气类故障 ----
                new FaultCodeRecord(
                        "P01", "通用",
                        "电机过电流",
                        List.of(
                                "负载过大或卡死",
                                "电机绕组短路",
                                "变频器参数设置不当",
                                "电源电压异常"
                        ),
                        List.of(
                                "检查负载是否异常，排除卡死故障",
                                "测量电机绝缘电阻，必要时维修/更换",
                                "重新设定变频器参数",
                                "检查电源电压稳定性"
                        ),
                        "高", "电气", "手册 §3.2 电机维护"
                ),
                new FaultCodeRecord(
                        "P02", "焊接设备",
                        "焊接电流不稳定",
                        List.of(
                                "焊枪电缆接触不良",
                                "送丝机构故障",
                                "焊接电源内部元件老化",
                                "接地不良"
                        ),
                        List.of(
                                "检查并紧固焊枪电缆接头",
                                "检查送丝轮和导丝管",
                                "检修焊接电源，更换老化元件",
                                "重新接地，确保接地电阻合格"
                        ),
                        "高", "电气", "手册 §6.1 焊接电源"
                ),

                // ---- 通信类故障 ----
                new FaultCodeRecord(
                        "C01", "通用",
                        "PLC 通信中断",
                        List.of(
                                "网线/通信线缆损坏或松动",
                                "交换机或通信模块故障",
                                "PLC 程序异常或死机",
                                "IP 地址冲突"
                        ),
                        List.of(
                                "检查并重新插拔通信线缆",
                                "重启交换机/通信模块",
                                "重启 PLC，检查程序运行状态",
                                "检查网络配置，排除 IP 冲突"
                        ),
                        "严重", "通信", "手册 §7.1 通信系统"
                ),
                new FaultCodeRecord(
                        "C02", "通用",
                        "HMI 触摸屏无响应",
                        List.of(
                                "触摸屏硬件故障",
                                "HMI 与 PLC 通信中断",
                                "HMI 程序异常"
                        ),
                        List.of(
                                "重启 HMI 设备",
                                "检查 HMI 通信配置和线缆",
                                "重新下载 HMI 程序"
                        ),
                        "中等", "通信", "手册 §7.2 HMI 维护"
                ),

                // ---- 传感器类故障 ----
                new FaultCodeRecord(
                        "S01", "通用",
                        "温度传感器异常",
                        List.of(
                                "传感器接线松动或断路",
                                "传感器损坏（开路/短路）",
                                "环境温度超出传感器量程",
                                "信号干扰"
                        ),
                        List.of(
                                "检查传感器接线是否牢固",
                                "使用万用表测量传感器电阻值",
                                "确认环境温度在传感器量程内",
                                "检查信号线是否与动力线分开走线"
                        ),
                        "中等", "传感器", "手册 §3.5 传感器维护"
                ),
                new FaultCodeRecord(
                        "S02", "通用",
                        "压力传感器漂移",
                        List.of(
                                "传感器长期使用后零点漂移",
                                "传感器膜片受损",
                                "接线端子氧化"
                        ),
                        List.of(
                                "重新校准传感器零点和满量程",
                                "检查膜片状态，必要时更换传感器",
                                "清洁接线端子，涂导电脂"
                        ),
                        "低", "传感器", "手册 §3.5 传感器维护"
                )
        );
    }
}
