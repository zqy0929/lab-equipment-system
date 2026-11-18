package com.lab.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册 DTO：注册者只填 账号 / 密码 / 学号（教职工号）/ 姓名。
 * 角色不可自选，注册后默认学生基础权限，由管理员在用户管理页面分配。
 */
@Data
public class RegisterDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 30, message = "用户名长度须为 3-30 个字符")
    private String username;
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度须为 6-32 个字符")
    private String password;
    /** 学号 / 教职工号 / 工号：全局唯一（仅数字），同一编号只能注册 1 次 */
    @NotBlank(message = "学号 / 工号不能为空")
    @Pattern(regexp = "^\\d{3,20}$", message = "学号 / 工号须为 3-20 位数字")
    private String employeeNo;
    /** 注册角色：五类均可选择；非学生角色注册后需管理员审核 */
    @NotBlank(message = "请选择注册角色")
    private String role;
    @NotBlank(message = "姓名不能为空")
    @Size(max = 30, message = "姓名长度不能超过 30 个字符")
    private String realName;
    /** 选填：性别（男/女） */
    @Pattern(regexp = "^(男|女)$", message = "性别取值仅支持男 / 女")
    private String gender;
    /** 选填：院系 / 课题组，可由管理员后续维护 */
    @Size(max = 100, message = "院系 / 课题组长度不能超过 100 个字符")
    private String department;
    private String phone;
    private String email;

    /** 图形验证码：captchaId 为服务端签发的凭证，captchaCode 为用户输入的图片字符 */
    @NotBlank(message = "请输入图形验证码")
    private String captchaId;
    @NotBlank(message = "请输入图形验证码")
    private String captchaCode;
}
