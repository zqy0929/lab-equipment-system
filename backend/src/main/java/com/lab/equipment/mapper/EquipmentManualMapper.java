package com.lab.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.equipment.entity.EquipmentManual;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EquipmentManualMapper extends BaseMapper<EquipmentManual> {

    /** 手册列表（关联设备名称/型号/分类/位置，可按关键词过滤设备名） */
    @Select("""
            <script>
            SELECT m.*, e.name AS equipment_name, e.model AS equipment_model,
                   e.category AS equipment_category, e.location AS equipment_location
            FROM equipment_manual m
            LEFT JOIN equipment e ON e.id = m.equipment_id
            <where>
                <if test="keyword != null and keyword != ''">
                    AND e.name LIKE CONCAT('%', #{keyword}, '%')
                </if>
            </where>
            ORDER BY e.category, e.name
            </script>
            """)
    List<EquipmentManual> selectWithEquipment(@Param("keyword") String keyword);
}
