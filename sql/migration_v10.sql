-- ============================================================
-- 迁移脚本 v10：设备手册阅读确认记录
-- 说明：学生预约某台设备前必须阅读该设备手册并确认，
--      每次确认写入一条记录，用于追溯阅读行为。
-- ============================================================
USE lab_equipment;

CREATE TABLE IF NOT EXISTS manual_read_record (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT       NOT NULL COMMENT '阅读人 ID',
    equipment_id   BIGINT       NOT NULL COMMENT '设备 ID',
    equipment_name VARCHAR(120)          DEFAULT NULL COMMENT '设备名称快照',
    read_time      DATETIME     NOT NULL COMMENT '确认阅读时间',
    ip             VARCHAR(64)           DEFAULT NULL COMMENT '确认来源 IP',
    PRIMARY KEY (id),
    KEY idx_read_user_equip (user_id, equipment_id),
    KEY idx_read_equip (equipment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备手册阅读确认记录';

-- 校验：表结构
SHOW CREATE TABLE manual_read_record;
