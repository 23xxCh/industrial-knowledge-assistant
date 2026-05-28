-- ============================================================
-- 工业知识助手 - 工业系统数据库表
-- 数据库: PostgreSQL 14+
-- 创建时间: 2026-05-28
-- ============================================================

-- -----------------------------------------------------------
-- 1. 设备参数表 (equipment_params)
-- 存储设备工艺参数的当前值和历史记录
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS equipment_params (
    id              BIGSERIAL       PRIMARY KEY,
    equipment_id    VARCHAR(32)     NOT NULL,           -- 设备编号
    equipment_name  VARCHAR(64),                        -- 设备名称
    equipment_type  VARCHAR(32),                        -- 设备类型（CNC/注塑机/焊接机/装配线）
    param_name      VARCHAR(64)     NOT NULL,           -- 参数名称
    param_value     VARCHAR(128)    NOT NULL,           -- 参数值
    param_unit      VARCHAR(16),                        -- 单位
    range_min       VARCHAR(32),                        -- 下限
    range_max       VARCHAR(32),                        -- 上限
    status          VARCHAR(16)     DEFAULT 'NORMAL',   -- NORMAL / WARNING / ALARM
    update_time     TIMESTAMP       DEFAULT NOW(),      -- 最后更新时间
    create_time     TIMESTAMP       DEFAULT NOW()
);

-- 索引
CREATE INDEX idx_equipment_params_eid ON equipment_params(equipment_id);
CREATE INDEX idx_equipment_params_name ON equipment_params(equipment_id, param_name);
CREATE INDEX idx_equipment_params_status ON equipment_params(status);

-- 注释
COMMENT ON TABLE  equipment_params IS '设备工艺参数表';
COMMENT ON COLUMN equipment_params.equipment_id IS '设备编号，如 CNC-001';
COMMENT ON COLUMN equipment_params.param_name  IS '参数名称，如 主轴转速、料筒温度';
COMMENT ON COLUMN equipment_params.status      IS '参数状态: NORMAL-正常, WARNING-告警, ALARM-报警';

-- -----------------------------------------------------------
-- 2. 传感器实时数据表 (sensor_realtime_data)
-- 存储 SCADA 系统的传感器读数
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS sensor_realtime_data (
    id              BIGSERIAL       PRIMARY KEY,
    sensor_id       VARCHAR(32)     NOT NULL,           -- 传感器编号
    sensor_name     VARCHAR(64),                        -- 传感器名称
    metric_type     VARCHAR(32)     NOT NULL,           -- 指标类型: temperature/pressure/vibration/current
    metric_value    DOUBLE PRECISION NOT NULL,          -- 指标值
    metric_unit     VARCHAR(16),                        -- 单位
    status          VARCHAR(16)     DEFAULT 'NORMAL',   -- NORMAL / WARNING / ALARM / OFFLINE
    data_source     VARCHAR(16)     DEFAULT 'MOCK',     -- MOCK / OPC_UA / MQTT / REST
    timestamp       TIMESTAMP       NOT NULL DEFAULT NOW(),  -- 数据采集时间
    create_time     TIMESTAMP       DEFAULT NOW()
);

-- 索引（时序数据，按时间分区查询）
CREATE INDEX idx_sensor_data_sid       ON sensor_realtime_data(sensor_id);
CREATE INDEX idx_sensor_data_sid_time  ON sensor_realtime_data(sensor_id, timestamp DESC);
CREATE INDEX idx_sensor_data_metric    ON sensor_realtime_data(metric_type);
CREATE INDEX idx_sensor_data_status    ON sensor_realtime_data(status);

COMMENT ON TABLE  sensor_realtime_data IS '传感器实时数据表（SCADA）';
COMMENT ON COLUMN sensor_realtime_data.metric_type IS '指标类型: temperature-温度, pressure-压力, vibration-振动, current-电流';
COMMENT ON COLUMN sensor_realtime_data.data_source IS '数据来源: MOCK-模拟, OPC_UA-OPC-UA协议, MQTT-MQTT协议, REST-REST接口';

-- -----------------------------------------------------------
-- 3. 工单表 (mes_workorders)
-- 存储 MES 系统的生产工单
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_workorders (
    id              BIGSERIAL       PRIMARY KEY,
    workorder_id    VARCHAR(32)     NOT NULL UNIQUE,    -- 工单编号
    product_name    VARCHAR(128)    NOT NULL,           -- 产品名称
    product_spec    VARCHAR(256),                       -- 产品规格
    quantity         INTEGER         NOT NULL,           -- 计划数量
    completed       INTEGER         DEFAULT 0,          -- 已完成数量
    status          VARCHAR(16)     DEFAULT 'pending',  -- pending/in_progress/completed/cancelled
    line_name       VARCHAR(32),                        -- 产线名称
    priority        VARCHAR(8)      DEFAULT 'medium',   -- high/medium/low
    plan_start      TIMESTAMP,                          -- 计划开始时间
    plan_end        TIMESTAMP,                          -- 计划结束时间
    actual_start    TIMESTAMP,                          -- 实际开始时间
    actual_end      TIMESTAMP,                          -- 实际结束时间
    assigned_team   VARCHAR(32),                        -- 负责班组
    defect_rate     DOUBLE PRECISION DEFAULT 0.0,       -- 不良率 (%)
    remark          TEXT,                               -- 备注
    create_time     TIMESTAMP       DEFAULT NOW(),
    update_time     TIMESTAMP       DEFAULT NOW()
);

-- 索引
CREATE INDEX idx_workorder_id     ON mes_workorders(workorder_id);
CREATE INDEX idx_workorder_status ON mes_workorders(status);
CREATE INDEX idx_workorder_line   ON mes_workorders(line_name);
CREATE INDEX idx_workorder_plan   ON mes_workorders(plan_start, plan_end);

COMMENT ON TABLE  mes_workorders IS 'MES 生产工单表';
COMMENT ON COLUMN mes_workorders.status   IS '工单状态: pending-待排产, in_progress-生产中, completed-已完成, cancelled-已取消';
COMMENT ON COLUMN mes_workorders.priority IS '优先级: high-高, medium-中, low-低';

-- -----------------------------------------------------------
-- 4. 故障代码库表 (fault_code_library)
-- 存储设备故障代码及其处理方案
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS fault_code_library (
    id                BIGSERIAL       PRIMARY KEY,
    fault_code        VARCHAR(16)     NOT NULL,           -- 故障代码
    equipment_type    VARCHAR(64)     DEFAULT '通用',     -- 适用设备类型
    description       VARCHAR(256)    NOT NULL,           -- 故障描述
    possible_causes   TEXT[]          NOT NULL,           -- 可能原因（数组）
    solutions         TEXT[]          NOT NULL,           -- 处理方案（数组）
    severity          VARCHAR(16)     DEFAULT '中等',     -- 低/中等/高/严重
    category          VARCHAR(32),                        -- 分类: 电气/机械/温度/压力/通信/传感器
    manual_ref        VARCHAR(128),                       -- 维修手册参考
    create_time       TIMESTAMP       DEFAULT NOW(),
    update_time       TIMESTAMP       DEFAULT NOW(),
    
    UNIQUE(fault_code, equipment_type)
);

-- 索引
CREATE INDEX idx_fault_code        ON fault_code_library(fault_code);
CREATE INDEX idx_fault_equip_type  ON fault_code_library(equipment_type);
CREATE INDEX idx_fault_severity    ON fault_code_library(severity);
CREATE INDEX idx_fault_category    ON fault_code_library(category);

COMMENT ON TABLE  fault_code_library IS '故障代码库';
COMMENT ON COLUMN fault_code_library.severity IS '严重程度: 低, 中等, 高, 严重';
COMMENT ON COLUMN fault_code_library.category IS '故障分类: 电气, 机械, 温度, 压力, 通信, 传感器';
COMMENT ON COLUMN fault_code_library.possible_causes IS '可能原因，PostgreSQL 数组类型';
COMMENT ON COLUMN fault_code_library.solutions IS '处理方案，PostgreSQL 数组类型';

-- ============================================================
-- 示例数据
-- ============================================================

-- -----------------------------------------------------------
-- 设备参数示例数据
-- -----------------------------------------------------------
INSERT INTO equipment_params (equipment_id, equipment_name, equipment_type, param_name, param_value, param_unit, range_min, range_max, status) VALUES
-- CNC 数控机床
('CNC-001', 'CNC加工中心1号', 'CNC数控机床', '主轴转速',   '3200', 'RPM',    '2800', '3600', 'NORMAL'),
('CNC-001', 'CNC加工中心1号', 'CNC数控机床', '进给速度',   '120',  'mm/min', '100',  '150',  'NORMAL'),
('CNC-001', 'CNC加工中心1号', 'CNC数控机床', '切削温度',   '78.5', '°C',     '70',   '90',   'NORMAL'),
('CNC-001', 'CNC加工中心1号', 'CNC数控机床', '冷却液压力', '0.52', 'MPa',    '0.4',  '0.7',  'NORMAL'),
('CNC-001', 'CNC加工中心1号', 'CNC数控机床', '刀具磨损量', '0.12', 'mm',     '0',    '0.3',  'NORMAL'),

('CNC-002', 'CNC加工中心2号', 'CNC数控机床', '主轴转速',   '2800', 'RPM',    '2800', '3600', 'NORMAL'),
('CNC-002', 'CNC加工中心2号', 'CNC数控机床', '进给速度',   '135',  'mm/min', '100',  '150',  'NORMAL'),
('CNC-002', 'CNC加工中心2号', 'CNC数控机床', '切削温度',   '92.1', '°C',     '70',   '90',   'WARNING'),
('CNC-002', 'CNC加工中心2号', 'CNC数控机床', '冷却液压力', '0.48', 'MPa',    '0.4',  '0.7',  'NORMAL'),
('CNC-002', 'CNC加工中心2号', 'CNC数控机床', '刀具磨损量', '0.25', 'mm',     '0',    '0.3',  'WARNING'),

-- 注塑机
('INJ-001', '注塑机1号', '注塑机', '料筒温度',   '230',  '°C',   '220', '250',  'NORMAL'),
('INJ-001', '注塑机1号', '注塑机', '模具温度',   '65',   '°C',   '60',  '80',   'NORMAL'),
('INJ-001', '注塑机1号', '注塑机', '注射压力',   '85',   'MPa',  '70',  '100',  'NORMAL'),
('INJ-001', '注塑机1号', '注塑机', '保压时间',   '8',    's',    '6',   '12',   'NORMAL'),
('INJ-001', '注塑机1号', '注塑机', '冷却时间',   '15',   's',    '12',  '20',   'NORMAL'),
('INJ-001', '注塑机1号', '注塑机', '锁模力',     '1200', 'kN',   '1000','1500', 'NORMAL'),

('INJ-002', '注塑机2号', '注塑机', '料筒温度',   '245',  '°C',   '220', '250',  'NORMAL'),
('INJ-002', '注塑机2号', '注塑机', '模具温度',   '72',   '°C',   '60',  '80',   'NORMAL'),
('INJ-002', '注塑机2号', '注塑机', '注射压力',   '95',   'MPa',  '70',  '100',  'WARNING'),
('INJ-002', '注塑机2号', '注塑机', '保压时间',   '10',   's',    '6',   '12',   'NORMAL'),
('INJ-002', '注塑机2号', '注塑机', '冷却时间',   '18',   's',    '12',  '20',   'NORMAL'),
('INJ-002', '注塑机2号', '注塑机', '锁模力',     '1350', 'kN',   '1000','1500', 'NORMAL'),

-- 焊接设备
('WLD-001', '焊接机器人1号', '焊接设备', '焊接电流',   '185',  'A',      '160', '200',  'NORMAL'),
('WLD-001', '焊接机器人1号', '焊接设备', '焊接电压',   '24.5', 'V',      '22',  '28',   'NORMAL'),
('WLD-001', '焊接机器人1号', '焊接设备', '焊接速度',   '45',   'cm/min', '35',  '55',   'NORMAL'),
('WLD-001', '焊接机器人1号', '焊接设备', '送丝速度',   '8.5',  'm/min',  '7',   '10',   'NORMAL'),
('WLD-001', '焊接机器人1号', '焊接设备', '气体流量',   '18',   'L/min',  '15',  '25',   'NORMAL'),

-- 装配线
('ASM-001', '装配线1号', '装配线', '装配压力',   '0.8',  'kN',    '0.5', '1.2',  'NORMAL'),
('ASM-001', '装配线1号', '装配线', '拧紧扭矩',   '25',   'N·m',   '20',  '30',   'NORMAL'),
('ASM-001', '装配线1号', '装配线', '节拍时间',   '45',   's',     '40',  '55',   'NORMAL'),
('ASM-001', '装配线1号', '装配线', '合格率',     '98.5', '%',     '97',  '100',  'NORMAL');

-- -----------------------------------------------------------
-- 传感器实时数据示例数据
-- -----------------------------------------------------------
INSERT INTO sensor_realtime_data (sensor_id, sensor_name, metric_type, metric_value, metric_unit, status, data_source, timestamp) VALUES
('TEMP-101', '注塑车间-环境温度',   'temperature', 28.5,  '°C',     'NORMAL',  'MOCK', NOW()),
('TEMP-102', 'CNC车间-冷却液温度',  'temperature', 22.3,  '°C',     'NORMAL',  'MOCK', NOW()),
('TEMP-103', '焊接车间-工件温度',    'temperature', 156.8, '°C',     'WARNING', 'MOCK', NOW()),
('PRES-201', '注塑机-液压压力',      'pressure',    12.5,  'MPa',    'NORMAL',  'MOCK', NOW()),
('PRES-202', '空压站-管网压力',      'pressure',    0.72,  'MPa',    'NORMAL',  'MOCK', NOW()),
('VIBR-301', 'CNC主轴-振动',         'vibration',   2.8,   'mm/s',   'NORMAL',  'MOCK', NOW()),
('VIBR-302', '注塑机合模-振动',      'vibration',   5.2,   'mm/s',   'ALARM',   'MOCK', NOW()),
('CURR-401', '焊接电源-电流',        'current',     185.3, 'A',      'NORMAL',  'MOCK', NOW()),
('CURR-402', '空压机-电机电流',      'current',     45.6,  'A',      'NORMAL',  'MOCK', NOW());

-- -----------------------------------------------------------
-- 工单示例数据
-- -----------------------------------------------------------
INSERT INTO mes_workorders (workorder_id, product_name, product_spec, quantity, completed, status, line_name, priority, plan_start, plan_end, actual_start, assigned_team, defect_rate) VALUES
('WO-2026-0501', '连接器外壳 A12',   'PA66-GF30, 黑色, 85×45×32mm',     5000, 5000, 'completed',   '产线1-注塑', 'high',   NOW() - INTERVAL '5 days', NOW() - INTERVAL '2 days', NOW() - INTERVAL '5 days', '甲班', 0.8),
('WO-2026-0502', '散热器底板 B07',   'ADC12, 银色阳极氧化, 120×80×10mm', 3000, 2150, 'in_progress', '产线5-CNC',  'high',   NOW() - INTERVAL '3 days', NOW() + INTERVAL '2 days', NOW() - INTERVAL '3 days', '乙班', 1.2),
('WO-2026-0503', '电机端盖 C03',     'HT250, 喷涂黑, Φ95×35mm',          2000, 1200, 'in_progress', '产线5-CNC',  'medium', NOW() - INTERVAL '2 days', NOW() + INTERVAL '3 days', NOW() - INTERVAL '2 days', '甲班', 0.5),
('WO-2026-0504', '线束总成 D15',     'RVV 3×1.5mm², L=1200mm',           8000, 3600, 'in_progress', '产线4-装配', 'medium', NOW() - INTERVAL '1 day',  NOW() + INTERVAL '5 days', NOW() - INTERVAL '1 day',  '丙班', 0.3),
('WO-2026-0505', '密封圈 E09',       'FKM, Φ50×3.5mm, 耐油',             10000, 0,   'pending',     '产线2-注塑', 'low',    NOW() + INTERVAL '1 day',  NOW() + INTERVAL '8 days', NULL, '甲班', 0.0),
('WO-2026-0506', '焊接支架 F22',     'Q235B, 镀锌, 200×60×40mm',          1500, 0,   'pending',     '产线3-焊接', 'medium', NOW() + INTERVAL '2 days', NOW() + INTERVAL '7 days', NULL, '乙班', 0.0),
('WO-2026-0507', '控制面板壳体 G04', 'ABS, 白色, 300×200×50mm',           4000, 4000, 'completed',   '产线1-注塑', 'low',    NOW() - INTERVAL '10 days', NOW() - INTERVAL '4 days', NOW() - INTERVAL '10 days', '丙班', 1.5);

-- -----------------------------------------------------------
-- 故障代码库示例数据
-- -----------------------------------------------------------
INSERT INTO fault_code_library (fault_code, equipment_type, description, possible_causes, solutions, severity, category, manual_ref) VALUES
-- 温度类
('E01', 'CNC数控机床,通用', '主轴温度过高',
 ARRAY['冷却液流量不足或冷却系统故障','主轴轴承磨损导致摩擦发热','切削参数不合理（进给过快、切深过大）','环境温度过高或散热通道堵塞'],
 ARRAY['检查冷却液液位和流量，清洗冷却管路','检查主轴轴承状态，必要时更换','适当降低进给速度或切削深度','清理设备散热通道，检查车间通风'],
 '高', '温度', '手册 §4.2 主轴系统维护'),

('E02', '注塑机', '料筒温度异常',
 ARRAY['加热圈损坏或接触不良','热电偶故障或接线松动','温控器参数设定错误','料筒隔热层老化'],
 ARRAY['逐段检查加热圈，更换损坏件','校验热电偶，紧固接线端子','重新设定 PID 参数并自整定','更换隔热保温层'],
 '高', '温度', '手册 §5.1 温控系统'),

('E03', '通用', '环境温度超出设备允许范围',
 ARRAY['车间空调/通风系统故障','设备安装位置靠近热源','季节性高温导致'],
 ARRAY['检查车间通风和空调系统','评估设备安装位置，必要时调整','增加临时降温措施（风扇、冰块）'],
 '中等', '温度', '手册 §2.1 环境要求'),

-- 压力类
('F01', '注塑机', '注射压力不足',
 ARRAY['液压油油位不足或油质劣化','液压泵磨损','比例阀故障','注射油缸密封件泄漏'],
 ARRAY['补充液压油，必要时更换新油','检测液压泵输出压力，必要时维修/更换','检查比例阀信号和阀芯动作','更换油缸密封件'],
 '高', '压力', '手册 §5.3 液压系统'),

('F02', 'CNC数控机床', '冷却液压力异常',
 ARRAY['冷却泵故障','管路堵塞或泄漏','压力传感器漂移','过滤器堵塞'],
 ARRAY['检查冷却泵运行状态','排查管路堵塞点，修复泄漏','校准或更换压力传感器','清洗或更换过滤器'],
 '中等', '压力', '手册 §4.5 冷却系统'),

-- 振动类
('V01', 'CNC数控机床', '设备振动异常',
 ARRAY['主轴动平衡失调','刀具磨损严重或安装不当','地脚螺栓松动','导轨/丝杠磨损'],
 ARRAY['重新做主轴动平衡校正','检查刀具磨损量，重新安装或更换','紧固地脚螺栓，检查基础水平','检查导轨间隙和丝杠精度'],
 '高', '机械', '手册 §4.3 主轴与传动'),

('V02', '注塑机', '合模振动过大',
 ARRAY['合模机构润滑不良','曲肘连杆机构磨损','锁模力设定不当'],
 ARRAY['补充润滑脂，检查润滑系统','检查曲肘连杆磨损情况，更换磨损件','重新设定锁模力参数'],
 '中等', '机械', '手册 §5.4 合模机构'),

-- 电气类
('P01', '通用', '电机过电流',
 ARRAY['负载过大或卡死','电机绕组短路','变频器参数设置不当','电源电压异常'],
 ARRAY['检查负载是否异常，排除卡死故障','测量电机绝缘电阻，必要时维修/更换','重新设定变频器参数','检查电源电压稳定性'],
 '高', '电气', '手册 §3.2 电机维护'),

('P02', '焊接设备', '焊接电流不稳定',
 ARRAY['焊枪电缆接触不良','送丝机构故障','焊接电源内部元件老化','接地不良'],
 ARRAY['检查并紧固焊枪电缆接头','检查送丝轮和导丝管','检修焊接电源，更换老化元件','重新接地，确保接地电阻合格'],
 '高', '电气', '手册 §6.1 焊接电源'),

-- 通信类
('C01', '通用', 'PLC 通信中断',
 ARRAY['网线/通信线缆损坏或松动','交换机或通信模块故障','PLC 程序异常或死机','IP 地址冲突'],
 ARRAY['检查并重新插拔通信线缆','重启交换机/通信模块','重启 PLC，检查程序运行状态','检查网络配置，排除 IP 冲突'],
 '严重', '通信', '手册 §7.1 通信系统'),

('C02', '通用', 'HMI 触摸屏无响应',
 ARRAY['触摸屏硬件故障','HMI 与 PLC 通信中断','HMI 程序异常'],
 ARRAY['重启 HMI 设备','检查 HMI 通信配置和线缆','重新下载 HMI 程序'],
 '中等', '通信', '手册 §7.2 HMI 维护'),

-- 传感器类
('S01', '通用', '温度传感器异常',
 ARRAY['传感器接线松动或断路','传感器损坏（开路/短路）','环境温度超出传感器量程','信号干扰'],
 ARRAY['检查传感器接线是否牢固','使用万用表测量传感器电阻值','确认环境温度在传感器量程内','检查信号线是否与动力线分开走线'],
 '中等', '传感器', '手册 §3.5 传感器维护'),

('S02', '通用', '压力传感器漂移',
 ARRAY['传感器长期使用后零点漂移','传感器膜片受损','接线端子氧化'],
 ARRAY['重新校准传感器零点和满量程','检查膜片状态，必要时更换传感器','清洁接线端子，涂导电脂'],
 '低', '传感器', '手册 §3.5 传感器维护');
