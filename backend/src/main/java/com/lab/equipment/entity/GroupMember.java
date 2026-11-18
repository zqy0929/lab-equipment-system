package com.lab.equipment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 课题组成员关系：一人可同时加入多个课题组；负责人（创建者）不入此表，以 research_group.owner_id 标识 */
@Data
@TableName("group_member")
public class GroupMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课题组 ID */
    private Long groupId;

    /** 成员用户 ID */
    private Long userId;

    /** 加入时间 */
    private LocalDateTime joinTime;
}
