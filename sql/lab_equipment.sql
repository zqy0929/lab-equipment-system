-- ============================================================
-- 实验室设备预约与管理系统 数据库初始化脚本
-- MySQL 8.0+，执行前请确认字符集 utf8mb4
-- ============================================================
CREATE DATABASE IF NOT EXISTS lab_equipment DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE lab_equipment;

-- 用户表（管理员账号由后端首次启动时自动创建：admin / admin123）
CREATE TABLE IF NOT EXISTS users (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  username    VARCHAR(50)  NOT NULL UNIQUE COMMENT '登录名',
  password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密密码',
  real_name   VARCHAR(50)  COMMENT '姓名',
  role        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色 ADMIN/USER',
  department  VARCHAR(100) COMMENT '院系/课题组',
  phone       VARCHAR(20),
  email       VARCHAR(100),
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '用户表';

-- 设备名录表
CREATE TABLE IF NOT EXISTS equipment (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(100) NOT NULL COMMENT '设备名称',
  model       VARCHAR(100) COMMENT '型号',
  category    VARCHAR(50)  COMMENT '分类',
  location    VARCHAR(100) COMMENT '存放位置',
  status      VARCHAR(30)  NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/UNDER_REPAIR/SCRAPPED',
  description TEXT COMMENT '备注说明',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_category (category),
  KEY idx_status (status)
) COMMENT '设备名录';

-- 设备预约/借用审批表
CREATE TABLE IF NOT EXISTS reservations (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT       NOT NULL COMMENT '预约人',
  equipment_id  BIGINT       NOT NULL COMMENT '设备',
  start_time    DATETIME     NOT NULL COMMENT '开始时间',
  end_time      DATETIME     NOT NULL COMMENT '结束时间',
  purpose       VARCHAR(500) COMMENT '用途说明',
  status        VARCHAR(30)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/RETURNED/CANCELLED',
  reject_reason VARCHAR(500) COMMENT '驳回原因',
  approver_id   BIGINT COMMENT '审批人',
  approver_name VARCHAR(50) COMMENT '审批人姓名',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_equipment_time (equipment_id, start_time),
  KEY idx_user (user_id),
  KEY idx_status (status)
) COMMENT '设备预约记录';

-- 损耗/报修记录表
CREATE TABLE IF NOT EXISTS damage_records (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  equipment_id  BIGINT        NOT NULL COMMENT '设备',
  reporter_id   BIGINT        NOT NULL COMMENT '上报人',
  reporter_name VARCHAR(50) COMMENT '上报人姓名',
  description   VARCHAR(1000) NOT NULL COMMENT '损耗情况描述',
  severity      VARCHAR(20)   NOT NULL DEFAULT 'MINOR' COMMENT 'MINOR/MODERATE/SEVERE',
  status        VARCHAR(20)   NOT NULL DEFAULT 'REPORTED' COMMENT 'REPORTED/FIXING/FIXED',
  cost          DECIMAL(10,2) COMMENT '维修费用',
  handler_name  VARCHAR(50) COMMENT '处理人',
  handle_note   VARCHAR(500) COMMENT '处理说明',
  handle_time   DATETIME COMMENT '处理时间',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_equipment (equipment_id)
) COMMENT '设备损耗记录';

-- 通知公告表
CREATE TABLE IF NOT EXISTS notices (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  title         VARCHAR(200) NOT NULL COMMENT '标题',
  content       TEXT COMMENT '内容',
  publisher_name VARCHAR(50) COMMENT '发布人',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '通知公告';

-- ============================================================
-- 示例数据
-- ============================================================
INSERT INTO equipment (name, model, category, location, status, description) VALUES
('光学显微镜', 'CX43', '显微成像', '实验楼 A201', 'AVAILABLE', '三目光学显微镜，放大倍数 40-1000 倍，适用于常规细胞观察'),
('荧光显微镜', 'DMi8', '显微成像', '实验楼 A203', 'AVAILABLE', '倒置荧光显微镜，配 GFP/RFP 荧光通道'),
('高速冷冻离心机', '5425R', '分离纯化', '实验楼 B105', 'AVAILABLE', '最高转速 25000 rpm，温控范围 -20°C ~ 40°C'),
('超速离心机', 'Optima XPN', '分离纯化', '实验楼 B107', 'UNDER_REPAIR', '转子需要校准，暂停外借'),
('实时荧光定量PCR仪', 'QuantStudio 5', '分子生物', '实验楼 C301', 'AVAILABLE', '96 孔快速 qPCR，支持高分辨熔解曲线'),
('梯度PCR仪', 'T100', '分子生物', '实验楼 C303', 'AVAILABLE', '基础梯度 PCR 扩增仪，升温速率 5°C/s'),
('紫外可见分光光度计', 'UV-1800', '光谱分析', '实验楼 A102', 'AVAILABLE', '波长范围 190-1100 nm，支持 DNA/蛋白浓度测定'),
('高效液相色谱仪', 'LC-20A', '光谱分析', '实验楼 D201', 'AVAILABLE', '二极管阵列检测器，需提前培训方可使用'),
('电子分析天平', 'ME204', '常规仪器', '实验楼 A101', 'AVAILABLE', '精度 0.1 mg，称量范围 0-220 g'),
('台式pH计', 'FE28', '常规仪器', '实验楼 A103', 'AVAILABLE', '实验室常规酸度计，配复合电极'),
('电热鼓风干燥箱', 'DHG-9070', '常规仪器', '实验楼 B201', 'AVAILABLE', '室温+10°C ~ 250°C，容积 70 L'),
('高压蒸汽灭菌锅', 'LDZH-100L', '清洗灭菌', '实验楼 B101', 'AVAILABLE', '100 L 立式灭菌锅，需持证操作'),
('超低温冰箱', 'DW-86L588', '储存设备', '实验楼 C101', 'AVAILABLE', '-86°C 超低温保存箱，存放样品需登记'),
('酶标仪', 'Multiskan GO', '光谱分析', '实验楼 C205', 'AVAILABLE', '全波长酶标仪，支持 ELISA 读数'),
('凝胶成像系统', 'ChemiDoc', '光谱分析', '实验楼 C202', 'AVAILABLE', '化学发光与荧光凝胶成像，支持 Western Blot 与核酸胶拍摄'),
('磁力搅拌器', 'RCT Basic', '常规仪器', '实验楼 A105', 'AVAILABLE', '加热磁力搅拌，转速 50-1500 rpm，最高加热 340℃'),
('恒温摇床', 'THZ-82A', '常规仪器', '实验楼 B203', 'AVAILABLE', '台式恒温振荡器，控温范围 室温+5℃ ~ 60℃，转速 40-300 rpm'),
('恒温水浴锅', 'HH-4', '常规仪器', '实验楼 A106', 'AVAILABLE', '四孔数显恒温水浴，控温范围 室温+5℃ ~ 100℃，精度 ±0.5℃'),
('超声波清洗器', 'KQ-250DE', '清洗灭菌', '实验楼 A107', 'AVAILABLE', '双频数控超声清洗，功率 250 W，槽容 10 L'),
('液氮罐', 'YDS-30', '储存设备', '实验楼 C102', 'AVAILABLE', '30 L 液氮生物容器，静态蒸发率低，用于细胞与样本冻存'),
('细胞培养箱', '3111', '常规仪器', '实验楼 C204', 'AVAILABLE', 'CO₂ 直热式培养箱，控温 37℃，CO₂ 浓度 0-20%'),
('旋转蒸发仪', 'RE-52AA', '分离纯化', '实验楼 D202', 'AVAILABLE', '20-180 rpm 旋转蒸发，配循环水真空泵，适用于溶剂回收'),
('真空泵', 'SHB-III', '分离纯化', '实验楼 D203', 'AVAILABLE', '循环水式多用真空泵，极限真空度 0.098 MPa'),
('制冰机', 'ZB-20', '常规仪器', '实验楼 B202', 'AVAILABLE', '实验室雪花制冰机，日产冰量 20 kg，储冰 6 kg');

INSERT INTO notices (title, content, publisher_name) VALUES
('实验室开放时间调整', '自本月起，实验室开放时间调整为工作日 8:30 - 21:00，周末 9:00 - 17:00，请各位师生合理安排预约。', '管理员'),
('设备使用培训通知', '液相色谱仪、qPCR 仪等大型设备须完成上岗培训后方可预约使用，培训报名请联系实验室管理员。', '管理员');

-- ============================================================
-- v2 迁移内容（附件/消息/配置/违约/信用分），新装环境直接包含
-- ============================================================
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  equipment_id BIGINT NOT NULL COMMENT '所属设备',
  file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
  file_path VARCHAR(500) NOT NULL COMMENT '存储路径',
  file_size BIGINT DEFAULT 0 COMMENT '字节数',
  file_type VARCHAR(100) DEFAULT '' COMMENT 'MIME/扩展名',
  uploader_id BIGINT COMMENT '上传人',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ef_equipment (equipment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备资料库附件';

-- 2. 站内消息表
CREATE TABLE IF NOT EXISTS message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL COMMENT '接收人',
  title VARCHAR(200) NOT NULL,
  content VARCHAR(1000) DEFAULT '',
  type VARCHAR(30) DEFAULT 'SYSTEM' COMMENT 'RESERVATION / SYSTEM',
  is_read TINYINT DEFAULT 0 COMMENT '0未读 1已读',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_msg_user (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息';

-- 3. 系统配置表（预约规则 / 信用阈值）
CREATE TABLE IF NOT EXISTS sys_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  config_key VARCHAR(50) NOT NULL UNIQUE,
  config_value VARCHAR(200) DEFAULT '',
  remark VARCHAR(200) DEFAULT '',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置';

-- 4. 违约记录表
CREATE TABLE IF NOT EXISTS violation_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL COMMENT '违约用户',
  reservation_id BIGINT COMMENT '关联预约（可空）',
  type VARCHAR(30) NOT NULL COMMENT 'NO_SHOW 爽约 / OVERDUE 超时归还',
  deduction INT NOT NULL DEFAULT 0 COMMENT '扣减分数',
  remark VARCHAR(500) DEFAULT '' COMMENT '违约备注',
  operator_id BIGINT COMMENT '登记人（管理员）',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_vr_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='违约记录';

-- 5. 用户表增加信用分（默认 100）
ALTER TABLE users
  ADD COLUMN credit_score INT NOT NULL DEFAULT 100 COMMENT '信用积分';

-- 6. 预约表增加提醒标记（用于"即将开始"只提醒一次）
ALTER TABLE reservations
  ADD COLUMN remind_sent TINYINT NOT NULL DEFAULT 0;

-- 7. 初始化系统配置（幂等）
INSERT INTO sys_config (config_key, config_value, remark) VALUES
  ('advance_days',         '7',   '设备可提前预约最大天数'),
  ('min_duration_minutes', '30',  '单次预约最短时长（分钟）'),
  ('max_duration_minutes', '240', '单次预约最长时长（分钟）'),
  ('cancel_cutoff_minutes','120', '开始前多少分钟内不可取消'),
  ('credit_threshold',     '60',  '信用分低于该值禁止预约')
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

-- ================= v3 增量（标签/角色/领用归还/候补/审计/隐患） =================
-- ============================================================
-- 迁移脚本 v3：角色精细化 / 设备标签 / 领用归还 / 候补 / 审计日志
--              安全须知记录 / 隐患上报 / 额度配置
-- ============================================================

-- 1. 设备标签（逗号分隔存储：光学仪器/电子测试设备/高温设备/危险化学品配套设备/可外借/仅限实验室使用）
ALTER TABLE equipment
    ADD COLUMN tags VARCHAR(200) NOT NULL DEFAULT '' COMMENT '标签，逗号分隔';

-- 2. 用户黑名单
ALTER TABLE users
    ADD COLUMN blacklisted TINYINT NOT NULL DEFAULT 0 COMMENT '黑名单 0/1';

-- 3. 预约：领用/归还照片与时间
ALTER TABLE reservations
    ADD COLUMN claim_time  DATETIME NULL COMMENT '领用确认时间',
    ADD COLUMN claim_photo VARCHAR(300) NULL COMMENT '领用照片路径',
    ADD COLUMN return_time DATETIME NULL COMMENT '归还时间',
    ADD COLUMN return_photo VARCHAR(300) NULL COMMENT '归还照片路径';

-- 4. 安全须知阅读记录（高危设备预约时强制阅读）
CREATE TABLE IF NOT EXISTS safety_read_record (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    equipment_id  BIGINT       NOT NULL,
    reservation_id BIGINT      NULL,
    ip            VARCHAR(64)  NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_srr_user (user_id),
    INDEX idx_srr_equipment (equipment_id)
) COMMENT '高危设备安全须知阅读记录';

-- 5. 候补队列
CREATE TABLE IF NOT EXISTS waitlist (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    equipment_id  BIGINT      NOT NULL,
    user_id       BIGINT      NOT NULL,
    start_time    DATETIME    NOT NULL,
    end_time      DATETIME    NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/NOTIFIED/FULFILLED/CANCELLED',
    notified_time DATETIME    NULL,
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wl_equipment (equipment_id),
    INDEX idx_wl_user (user_id)
) COMMENT '预约候补队列';

-- 6. 操作审计日志（只增不改不删）
CREATE TABLE IF NOT EXISTS op_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NULL,
    username    VARCHAR(50)  NULL COMMENT '操作人账号（系统操作为 system）',
    action      VARCHAR(50)  NOT NULL COMMENT '动作：CREATE_EQUIPMENT/UPDATE_RESERVATION/... ',
    detail      VARCHAR(1000) NULL COMMENT '操作内容（含修改前后信息）',
    ip          VARCHAR(64)  NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ol_user (user_id),
    INDEX idx_ol_time (create_time)
) COMMENT '操作审计日志（仅查看）';

-- 7. 安全隐患上报
CREATE TABLE IF NOT EXISTS hazard_report (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    equipment_id  BIGINT       NULL,
    content       VARCHAR(1000) NOT NULL COMMENT '隐患描述',
    status        VARCHAR(20)  NOT NULL DEFAULT 'REPORTED' COMMENT 'REPORTED/HANDLED',
    handler_name  VARCHAR(50)  NULL,
    handle_note   VARCHAR(500) NULL,
    handle_time   DATETIME     NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_hr_status (status)
) COMMENT '安全隐患上报';

-- 8. 额度/超时配置种子
INSERT INTO sys_config (config_key, config_value, remark) VALUES
    ('max_active_reservations', '2',  '单个学生同时持有的最大有效预约数'),
    ('weekly_highrisk_limit',   '3',  '每周高危设备预约次数上限'),
    ('claim_timeout_minutes',   '30', '预约开始后领用确认的超时时间（分钟），超时自动释放'),
    ('waitlist_expire_hours',   '2',  '候补资格有效时长（小时）')
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

-- 9. 存量设备打标签
UPDATE equipment SET tags = '光学仪器,可外借'               WHERE name LIKE '%显微镜%';
UPDATE equipment SET tags = '仅限实验室使用'                WHERE name LIKE '%超速离心%';
UPDATE equipment SET tags = '可外借'                       WHERE name LIKE '%离心%';
UPDATE equipment SET tags = '电子测试设备,仅限实验室使用'    WHERE name LIKE '%PCR%';
UPDATE equipment SET tags = '光学仪器,可外借'               WHERE name LIKE '%分光光度计%' OR name LIKE '%酶标仪%';
UPDATE equipment SET tags = '光学仪器,仅限实验室使用'        WHERE name LIKE '%色谱仪%' OR name LIKE '%凝胶成像%';
UPDATE equipment SET tags = '电子测试设备,可外借'           WHERE name LIKE '%天平%';
UPDATE equipment SET tags = '危险化学品配套设备,可外借'      WHERE name LIKE '%pH%';
UPDATE equipment SET tags = '高温设备,仅限实验室使用'        WHERE name LIKE '%干燥箱%' OR name LIKE '%灭菌锅%';
UPDATE equipment SET tags = '仅限实验室使用'                WHERE name LIKE '%冰箱%' OR name LIKE '%培养箱%' OR name LIKE '%液氮%';
UPDATE equipment SET tags = '危险化学品配套设备,仅限实验室使用' WHERE name LIKE '%液氮%' OR name LIKE '%旋转蒸发%';
UPDATE equipment SET tags = '高温设备,可外借'               WHERE name LIKE '%水浴%';
UPDATE equipment SET tags = '可外借'                       WHERE tags = '' ;
