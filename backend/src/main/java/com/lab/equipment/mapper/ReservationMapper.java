package com.lab.equipment.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.entity.Reservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReservationMapper extends BaseMapper<Reservation> {

    /** 关联设备表与用户表，返回带设备名/预约人姓名/设备标签的记录（分页） */
    @Select("""
            SELECT r.*, e.name AS equipment_name, e.model AS equipment_model,
                   e.location AS equipment_location, e.tags AS equipment_tags, u.real_name AS username
            FROM reservations r
            LEFT JOIN equipment e ON e.id = r.equipment_id
            LEFT JOIN users u ON u.id = r.user_id
            ${ew.customSqlSegment}
            """)
    Page<Reservation> selectWithDetailPage(Page<Reservation> page,
                                           @Param(Constants.WRAPPER) Wrapper<Reservation> wrapper);

    /** 关联查询（不分页） */
    @Select("""
            SELECT r.*, e.name AS equipment_name, e.model AS equipment_model,
                   e.location AS equipment_location, e.tags AS equipment_tags, u.real_name AS username
            FROM reservations r
            LEFT JOIN equipment e ON e.id = r.equipment_id
            LEFT JOIN users u ON u.id = r.user_id
            ${ew.customSqlSegment}
            """)
    List<Reservation> selectWithDetailList(@Param(Constants.WRAPPER) Wrapper<Reservation> wrapper);
}
