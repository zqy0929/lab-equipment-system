package com.lab.equipment.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.entity.DamageRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DamageRecordMapper extends BaseMapper<DamageRecord> {

    /** 关联设备表（分页） */
    @Select("""
            SELECT d.*, e.name AS equipment_name
            FROM damage_records d
            LEFT JOIN equipment e ON e.id = d.equipment_id
            ${ew.customSqlSegment}
            """)
    Page<DamageRecord> selectWithDetailPage(Page<DamageRecord> page,
                                            @Param(Constants.WRAPPER) Wrapper<DamageRecord> wrapper);

    /** 关联查询（不分页） */
    @Select("""
            SELECT d.*, e.name AS equipment_name
            FROM damage_records d
            LEFT JOIN equipment e ON e.id = d.equipment_id
            ${ew.customSqlSegment}
            """)
    List<DamageRecord> selectWithDetailList(@Param(Constants.WRAPPER) Wrapper<DamageRecord> wrapper);
}
