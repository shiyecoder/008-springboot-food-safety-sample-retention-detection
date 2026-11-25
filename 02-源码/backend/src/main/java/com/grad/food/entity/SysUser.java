package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;import java.math.BigDecimal;
/** 用户。password 为 BCrypt 密文，序列化时由 DTO 排除，绝不返回前端。 */
@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String avatar;
    private Long orgId;
    private Long roleId;
    private String phone;
    private String email;
    private Integer status;
    private Integer failCount;
    private LocalDateTime lockUntil;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private String remark;
    @TableLogic
    private Integer deleted;
    @Version
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
