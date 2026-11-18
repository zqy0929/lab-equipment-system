-- v11: 课题组多成员模型
-- 原「一人一组」（users.group_id）升级为「一人可加入多个课题组」（group_member 关系表）
-- 负责人 = 创建者（research_group.owner_id），无需加入本组

CREATE TABLE IF NOT EXISTS group_member (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  group_id  BIGINT      NOT NULL COMMENT '课题组 ID',
  user_id   BIGINT      NOT NULL COMMENT '成员用户 ID',
  join_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  UNIQUE KEY uk_group_user (group_id, user_id),
  KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '课题组成员关系（支持一人多组）';

-- 存量迁移：原 users.group_id 的单组关系写入关系表（幂等）
INSERT IGNORE INTO group_member (group_id, user_id, join_time)
SELECT group_id, id, NOW()
FROM users
WHERE group_id IS NOT NULL;

-- users.group_id 仅作历史备份保留，代码不再读写
