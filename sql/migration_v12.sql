-- v12：预约取消限制
-- 1) cancel_record：用户主动取消预约记录（计入 30 天取消统计，weight 为计数权重）
--    开始前 24 小时以上取消不计入（不写记录）；24h~3h 计 1 次（weight=1）；3h 内计 2 次（weight=2）
-- 2) ban_lift：管理员/保管员手动解除预约封禁的记录（解禁时间晚于触发取消时间即视为已豁免）
CREATE TABLE IF NOT EXISTS cancel_record (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       NOT NULL COMMENT '取消用户',
    reservation_id BIGINT       NULL COMMENT '被取消的预约',
    cancel_time    DATETIME     NOT NULL COMMENT '取消时间',
    weight         INT          NOT NULL DEFAULT 1 COMMENT '计入次数（1 或 2）',
    detail         VARCHAR(255) NULL COMMENT '计次说明（距开始时长区间）',
    INDEX idx_cancel_user_time (user_id, cancel_time),
    INDEX idx_cancel_time (cancel_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约取消记录（30天取消统计口径）';

CREATE TABLE IF NOT EXISTS ban_lift (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT      NOT NULL COMMENT '被解禁用户',
    lift_time     DATETIME    NOT NULL COMMENT '解禁时间',
    operator_id   BIGINT      NULL COMMENT '操作人',
    operator_name VARCHAR(50) NULL COMMENT '操作人姓名',
    INDEX idx_banlift_user_time (user_id, lift_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约封禁解除记录';

-- 3) 系统配置默认值：30 天取消次数阈值 / 封禁天数（后台系统配置页可改）
INSERT INTO sys_config (config_key, config_value, remark)
SELECT 'cancel_limit_count', '5', '30天内取消超过该次数则限制提交新预约'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'cancel_limit_count');

INSERT INTO sys_config (config_key, config_value, remark)
SELECT 'cancel_ban_days', '7', '取消次数超限后的封禁天数'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'cancel_ban_days');
