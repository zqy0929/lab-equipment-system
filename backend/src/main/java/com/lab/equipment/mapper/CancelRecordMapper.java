package com.lab.equipment.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.entity.CancelRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CancelRecordMapper extends BaseMapper<CancelRecord> {

    /** 取消记录关联用户与设备名（分页，供管理员查看全体用户取消记录） */
    @Select("""
            SELECT c.*, u.username, u.real_name, u.role, e.name AS equipment_name
            FROM cancel_record c
            LEFT JOIN users u ON u.id = c.user_id
            LEFT JOIN reservations r ON r.id = c.reservation_id
            LEFT JOIN equipment e ON e.id = r.equipment_id
            ${ew.customSqlSegment}
            """)
    Page<CancelRecord> selectWithUserPage(Page<CancelRecord> page,
                                          @Param(Constants.WRAPPER) Wrapper<CancelRecord> wrapper);
}
