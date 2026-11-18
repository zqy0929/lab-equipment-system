package com.lab.equipment.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.lab.equipment.entity.Waitlist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WaitlistMapper extends BaseMapper<Waitlist> {

    @Select("""
            SELECT w.*, e.name AS equipment_name, u.real_name AS username
            FROM waitlist w
            LEFT JOIN equipment e ON e.id = w.equipment_id
            LEFT JOIN users u ON u.id = w.user_id
            ${ew.customSqlSegment}
            """)
    List<Waitlist> selectWithDetailList(@Param(Constants.WRAPPER) Wrapper<Waitlist> wrapper);
}
