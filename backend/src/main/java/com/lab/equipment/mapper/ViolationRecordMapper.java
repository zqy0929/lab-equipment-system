package com.lab.equipment.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.entity.ViolationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ViolationRecordMapper extends BaseMapper<ViolationRecord> {

    /** 违约记录关联用户名（分页） */
    @Select("""
            SELECT v.*, u.username, u.real_name,
                   o.real_name AS operator_name
            FROM violation_record v
            LEFT JOIN users u ON u.id = v.user_id
            LEFT JOIN users o ON o.id = v.operator_id
            ${ew.customSqlSegment}
            """)
    Page<ViolationRecord> selectWithUserPage(Page<ViolationRecord> page,
                                             @Param(Constants.WRAPPER) Wrapper<ViolationRecord> wrapper);

    /** 违约记录关联用户名（不分页） */
    @Select("""
            SELECT v.*, u.username, u.real_name,
                   o.real_name AS operator_name
            FROM violation_record v
            LEFT JOIN users u ON u.id = v.user_id
            LEFT JOIN users o ON o.id = v.operator_id
            ${ew.customSqlSegment}
            """)
    List<ViolationRecord> selectWithUserList(@Param(Constants.WRAPPER) Wrapper<ViolationRecord> wrapper);
}
