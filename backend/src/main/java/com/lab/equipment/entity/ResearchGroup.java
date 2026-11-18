package com.lab.equipment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 课题组：教师创建，成员凭邀请码加入；一个用户同一时间只能加入一个课题组 */
@Data
@TableName("research_group")
public class ResearchGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课题组名称 */
    private String name;

    /** 负责人（教师）用户 ID */
    private Long ownerId;

    /** 邀请码（凭码加入） */
    private String inviteCode;

    /** 课题组简介 */
    private String description;

    private LocalDateTime createTime;
}
