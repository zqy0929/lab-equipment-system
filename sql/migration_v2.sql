-- ============================================================
-- 实验室设备预约管理系统 增量迁移脚本 v2
-- 新增：设备资料库 / 站内消息 / 系统配置 / 违约记录 / 用户信用分
-- 在已建库（lab_equipment）基础上执行一次即可
-- ============================================================
USE lab_equipment;

-- 1. 设备资料库附件表（每台设备独立，互不可见）
CREATE TABLE IF NOT EXISTS equipment_file (
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
