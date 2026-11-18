package com.lab.equipment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 设备分类预约时长规则：每类设备独立的单次预约最短/最长时长 */
@Data
@TableName("category_rule")
public class CategoryRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备分类名称（与 equipment.category 对应） */
    private String category;

    /** 单次预约最短时长（分钟） */
    private Integer minDuration;

    /** 单次预约最长时长（分钟） */
    private Integer maxDuration;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
