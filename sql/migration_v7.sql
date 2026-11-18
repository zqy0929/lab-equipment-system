-- migration_v7.sql：学号/工号唯一标识机制
-- 1) users 新增 employee_no 列（学号/教职工号/工号，全局唯一）
-- 2) 唯一索引：同一学号/工号只能注册 1 个账号
-- 3) 存量账号按角色回填编号（前缀+ID），便于演示工号登录

ALTER TABLE users ADD COLUMN employee_no VARCHAR(32) NULL COMMENT '学号/教职工号/工号（全局唯一）' AFTER username;
ALTER TABLE users ADD UNIQUE INDEX uk_users_employee_no (employee_no);

-- 存量回填：管理员 A / 学生 S / 教师 T / 保管员 K / 安全员 F + 4位ID
UPDATE users SET employee_no = CASE
    WHEN role = 'ADMIN'   THEN CONCAT('A', LPAD(id, 4, '0'))
    WHEN role = 'STUDENT' THEN CONCAT('S', LPAD(id, 4, '0'))
    WHEN role = 'TEACHER' THEN CONCAT('T', LPAD(id, 4, '0'))
    WHEN role = 'KEEPER'  THEN CONCAT('K', LPAD(id, 4, '0'))
    WHEN role = 'SAFETY'  THEN CONCAT('F', LPAD(id, 4, '0'))
    ELSE CONCAT('U', LPAD(id, 4, '0'))
END
WHERE employee_no IS NULL;
