-- ============================================================
-- 迁移脚本 v3：角色精细化 / 设备标签 / 领用归还 / 候补 / 审计日志
--              安全须知记录 / 隐患上报 / 额度配置
-- ============================================================
USE lab_equipment;

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
