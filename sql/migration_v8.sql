-- migration_v8.sql：注册审核机制
-- users 新增 audit_status：APPROVED 已通过（默认，存量账号全部视为已通过）
--                        PENDING 待审核（注册时选择 教师/安全员/设备保管员/管理员）
--                        REJECTED 已驳回（维持默认学生权限）

ALTER TABLE users ADD COLUMN audit_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED'
    COMMENT '注册审核状态：APPROVED/PENDING/REJECTED' AFTER role;
