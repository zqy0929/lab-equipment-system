package com.lab.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    /** 学号 / 教职工号 / 工号：全局唯一标识，一个身份绑定唯一账号 */
    private String employeeNo;

    /** 返回给前端时隐藏密码字段 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String realName;
    /** 性别（男/女），选填 */
    private String gender;
    private String role;        // ADMIN / USER
    /** 注册审核状态：APPROVED 已通过 / PENDING 待审核 / REJECTED 已驳回（维持学生权限） */
    private String auditStatus;
    private String department;
    private String phone;
    private String email;
    private Integer status;     // 1 启用 0 禁用
    private Integer creditScore; // 信用积分（默认 100）
    private Integer blacklisted; // 黑名单 0/1，黑名单用户禁止发起预约
    /* 课题组关系已迁移至 group_member 关系表（一人可多组），users.group_id 列仅作历史备份保留 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
