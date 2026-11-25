package com.grad.food.dto;

import lombok.Data;

/** 用户新增/编辑入参，密码为空表示不修改。 */
@Data
public class UserSaveDTO {
    private Long id;
    private String username;
    private String realName;
    private String password;
    private Long orgId;
    private Long roleId;
    private String phone;
    private String email;
    private Integer status;
    private String remark;
}
