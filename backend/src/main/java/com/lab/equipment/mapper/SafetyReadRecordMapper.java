package com.lab.equipment.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.entity.SafetyReadRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SafetyReadRecordMapper extends BaseMapper<SafetyReadRecord> {

    @Select("""
            SELECT s.*, u.real_name AS username, e.name AS equipment_name
            FROM safety_read_record s
            LEFT JOIN users u ON u.id = s.user_id
            LEFT JOIN equipment e ON e.id = s.equipment_id
            ${ew.customSqlSegment}
            """)
    Page<SafetyReadRecord> selectWithDetailPage(Page<SafetyReadRecord> page,
                                                @Param(Constants.WRAPPER) Wrapper<SafetyReadRecord> wrapper);
}
