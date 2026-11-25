package com.grad.food.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 修改密码入参。 */
@Data
public class PasswordDTO {
    @NotBlank(message = "请输入当前密码")
    private String oldPassword;
    @NotBlank(message = "请输入新密码")
    private String newPassword;
}
