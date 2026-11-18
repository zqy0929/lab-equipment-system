-- ============================================================
-- 迁移脚本 v9：单人最大同时预约设备数量
-- 说明：仅补充/修正配置项，不改变表结构；已存在的值不会被覆盖
-- ============================================================
USE lab_equipment;

INSERT INTO sys_config (config_key, config_value, remark) VALUES
    ('max_active_reservations', '2', '单人最大同时预约设备数量（仅统计待审批 / 借用中的预约，默认 2 台）')
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

-- 校验：查看当前配置
SELECT config_key, config_value, remark FROM sys_config WHERE config_key = 'max_active_reservations';
