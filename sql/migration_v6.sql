-- v6：课题组模块（选填，一个用户同一时间只能加入一个课题组）
CREATE TABLE IF NOT EXISTS research_group (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL COMMENT '课题组名称',
    owner_id    BIGINT NOT NULL COMMENT '负责人（教师）用户ID',
    invite_code VARCHAR(16) NOT NULL COMMENT '邀请码（加入课题组用）',
    description VARCHAR(255) DEFAULT NULL COMMENT '课题组简介',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_invite_code (invite_code),
    KEY idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课题组';

-- 用户当前所在课题组（选填；教师名下课题组通过 owner_id 关联，不占 group_id）
ALTER TABLE users ADD COLUMN group_id BIGINT DEFAULT NULL COMMENT '当前加入的课题组ID（选填）';
