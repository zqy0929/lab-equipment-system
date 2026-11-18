-- ============================================================
-- 迁移 v5：角色权限改造 + 设备手册
-- 1. 新增 equipment_manual 表（每台设备一份操作手册：操作文档 + 安全注意事项 + 附件）
-- 2. damage_records / message 无结构变更（权限过滤在查询层实现）
-- ============================================================
USE lab_equipment;

CREATE TABLE IF NOT EXISTS equipment_manual (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    equipment_id     BIGINT       NOT NULL COMMENT '设备 ID（每台设备一份手册）',
    content          TEXT         NULL COMMENT '操作文档（步骤/规范说明）',
    safety_notes     TEXT         NULL COMMENT '安全注意事项',
    attachment_name  VARCHAR(255) NULL COMMENT '手册附件原始文件名',
    attachment_path  VARCHAR(500) NULL COMMENT '手册附件存储路径',
    attachment_type  VARCHAR(20)  NULL COMMENT '附件类型（pdf/doc/png 等）',
    editor_id        BIGINT       NULL COMMENT '最近编辑人 ID',
    editor_name      VARCHAR(50)  NULL COMMENT '最近编辑人姓名',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_equipment (equipment_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设备操作手册';

SELECT COUNT(*) AS manual_count FROM equipment_manual;
