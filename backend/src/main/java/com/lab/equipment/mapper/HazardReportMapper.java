package com.lab.equipment.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.entity.HazardReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface HazardReportMapper extends BaseMapper<HazardReport> {

    @Select("""
            SELECT h.*, u.real_name AS username, e.name AS equipment_name
            FROM hazard_report h
            LEFT JOIN users u ON u.id = h.user_id
            LEFT JOIN equipment e ON e.id = h.equipment_id
            ${ew.customSqlSegment}
            """)
    Page<HazardReport> selectWithDetailPage(Page<HazardReport> page,
                                            @Param(Constants.WRAPPER) Wrapper<HazardReport> wrapper);
}
