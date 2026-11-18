-- ============================================================
-- 迁移 v4：预约时长规则按设备分类拆分
-- 1. 新增 category_rule 表（每类设备独立的单次预约最短/最长时长）
-- 2. 全局 min_duration_minutes / max_duration_minutes 保留作为兜底，不再在页面暴露
-- ============================================================
USE lab_equipment;

CREATE TABLE IF NOT EXISTS category_rule (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    category        VARCHAR(50)  NOT NULL COMMENT '设备分类名称',
    min_duration    INT          NOT NULL DEFAULT 30  COMMENT '单次预约最短时长（分钟）',
    max_duration    INT          NOT NULL DEFAULT 240 COMMENT '单次预约最长时长（分钟）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category (category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设备分类预约时长规则';

-- 7 类设备默认规则
INSERT INTO category_rule (category, min_duration, max_duration) VALUES
    ('常规仪器', 30, 240),
    ('光谱分析', 30, 240),
    ('分子生物', 30, 240),
    ('分离纯化', 30, 240),
    ('显微成像', 30, 240),
    ('清洗灭菌', 30, 240),
    ('储存设备', 30, 240)
ON DUPLICATE KEY UPDATE category = VALUES(category);

SELECT id, category, min_duration, max_duration FROM category_rule;
