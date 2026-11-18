-- migration_v13：用户性别字段 + 候补自动转正关联
-- 1) 用户增加性别（选填，男/女）
ALTER TABLE users ADD COLUMN gender VARCHAR(10) NULL COMMENT '性别（男/女）' AFTER real_name;
-- 2) 候补记录关联自动转正生成的预约
ALTER TABLE waitlist ADD COLUMN reservation_id BIGINT NULL COMMENT '候补自动转正生成的预约ID' AFTER status;
